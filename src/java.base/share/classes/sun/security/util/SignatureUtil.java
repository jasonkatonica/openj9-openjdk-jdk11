/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package sun.security.util;

import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.spec.*;
import java.util.Locale;
import sun.security.rsa.RSAUtil;
import jdk.internal.misc.SharedSecrets;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import sun.security.x509.X509Key;
import java.security.interfaces.RSAPublicKey;
import java.security.interfaces.DSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64.Decoder;
/**
 * Utility class for Signature related operations. Currently used by various
 * internal PKI classes such as sun.security.x509.X509CertImpl,
 * sun.security.pkcs.SignerInfo, for setting signature parameters.
 *
 * @since   11
 */
public class SignatureUtil {

    private static String checkName(String algName) throws ProviderException {
        if (algName.indexOf(".") == -1) {
            return algName;
        }
        // convert oid to String
        try {
            return Signature.getInstance(algName).getAlgorithm();
        } catch (Exception e) {
            throw new ProviderException("Error mapping algorithm name", e);
        }
    }

    // Utility method of creating an AlgorithmParameters object with
    // the specified algorithm name and encoding
    private static AlgorithmParameters createAlgorithmParameters(String algName,
            byte[] paramBytes) throws ProviderException {

        try {
            algName = checkName(algName);
            AlgorithmParameters result =
                AlgorithmParameters.getInstance(algName);
            result.init(paramBytes);
            return result;
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new ProviderException(e);
        }
    }

    // Utility method for converting the specified AlgorithmParameters object
    // into an AlgorithmParameterSpec object.
    public static AlgorithmParameterSpec getParamSpec(String sigName,
            AlgorithmParameters params)
            throws ProviderException {

        sigName = checkName(sigName).toUpperCase(Locale.ENGLISH);
        AlgorithmParameterSpec paramSpec = null;
        if (params != null) {
            // AlgorithmParameters.getAlgorithm() may returns oid if it's
            // created during DER decoding. Convert to use the standard name
            // before passing it to RSAUtil
            if (params.getAlgorithm().indexOf(".") != -1) {
                try {
                    params = createAlgorithmParameters(sigName,
                        params.getEncoded());
                } catch (IOException e) {
                    throw new ProviderException(e);
                }
            }

            if (sigName.indexOf("RSA") != -1) {
                paramSpec = RSAUtil.getParamSpec(params);
            } else if (sigName.indexOf("ECDSA") != -1) {
                try {
                    paramSpec = params.getParameterSpec(ECParameterSpec.class);
                } catch (Exception e) {
                    throw new ProviderException("Error handling EC parameters", e);
                }
            } else {
                throw new ProviderException
                    ("Unrecognized algorithm for signature parameters " +
                     sigName);
            }
        }
        return paramSpec;
    }

    // Utility method for converting the specified parameter bytes into an
    // AlgorithmParameterSpec object.
    public static AlgorithmParameterSpec getParamSpec(String sigName,
            byte[] paramBytes)
            throws ProviderException {
        sigName = checkName(sigName).toUpperCase(Locale.ENGLISH);
        AlgorithmParameterSpec paramSpec = null;

        if (paramBytes != null) {
            if (sigName.indexOf("RSA") != -1) {
                AlgorithmParameters params =
                    createAlgorithmParameters(sigName, paramBytes);
                paramSpec = RSAUtil.getParamSpec(params);
            } else if (sigName.indexOf("ECDSA") != -1) {
                try {
                    Provider p = Signature.getInstance(sigName).getProvider();
                    paramSpec = ECUtil.getECParameterSpec(p, paramBytes);
                } catch (Exception e) {
                    throw new ProviderException("Error handling EC parameters", e);
                }
                // ECUtil discards exception and returns null, so we need to check
                // the returned value
                if (paramSpec == null) {
                    throw new ProviderException("Error handling EC parameters");
                }
            } else {
                throw new ProviderException
                     ("Unrecognized algorithm for signature parameters " +
                      sigName);
            }
        }
        return paramSpec;
    }

    // Utility method for initializing the specified Signature object
    // for verification with the specified key and params (may be null)
    public static void initVerifyWithParam(Signature s, PublicKey key,
            AlgorithmParameterSpec params)
            throws ProviderException, InvalidAlgorithmParameterException,
            InvalidKeyException {
        System.out.println("SignatureUtil.java.initVerifyWithParam");
        if (key instanceof sun.security.x509.X509Key) {
            System.out.println("SigtureUtil.java.initVerifyWithParam X509Key");
            DSAPublicKey convertedKey = null;
            try {
                convertedKey = convertX509Key((X509Key)key, params);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            SharedSecrets.getJavaSecuritySignatureAccess().initVerify(s, convertedKey, params);
        } else {
            SharedSecrets.getJavaSecuritySignatureAccess().initVerify(s, key, params);
        }
    }

    // Utility method for initializing the specified Signature object
    // for verification with the specified Certificate and params (may be null)
    public static void initVerifyWithParam(Signature s,
            java.security.cert.Certificate cert,
            AlgorithmParameterSpec params)
            throws ProviderException, InvalidAlgorithmParameterException,
            InvalidKeyException {
        SharedSecrets.getJavaSecuritySignatureAccess().initVerify(s, cert, params);
    }

    // Utility method for initializing the specified Signature object
    // for signing with the specified key and params (may be null)
    public static void initSignWithParam(Signature s, PrivateKey key,
            AlgorithmParameterSpec params, SecureRandom sr)
            throws ProviderException, InvalidAlgorithmParameterException,
            InvalidKeyException {
        SharedSecrets.getJavaSecuritySignatureAccess().initSign(s, key, params, sr);
    }

    public static DSAPublicKey convertX509Key(X509Key x509Key, AlgorithmParameterSpec params) throws Exception {
        KeyFactory kf = KeyFactory.getInstance("DSA");
        System.out.println("Algorithm: " + x509Key.getAlgorithm());
        KeyPairGenerator kpg - KeyPairGenerator.getInstance("DSA");
        kpg.initialize(params);
        KeyPair keyPair = kpg.generateKeyPair();
        return (DSAPublicKey)keyPair.getPublic();
        
        //System.out.println("Encoded:");
        //byte[] encodedKey = x509Key.getEncoded();
        //printBytes(encodedKey);
        //(DSAPublicKey)kf.generatePublic(encodedKey);

        //X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encodedKey);
        //DSAPublicKey newKey = new sun.security.provider.DSAPublicKey(encodedKey);

        //DSAPublicKey anotherPublicKey = new sun.security.provider.DSAPublicKey(newKey.getY(),newKey.getY(), newKey.getY(), newKey.getY());
        //return anotherPublicKey;
    }

    private static void printBytes(byte[] bytes) {
        System.out.println("Hex: ");
        for ( byte b: bytes) {
            System.out.print(String.format("%02X ",b));
        }
        System.out.println("");

        System.out.println("String: " + new String(bytes));

    }
}