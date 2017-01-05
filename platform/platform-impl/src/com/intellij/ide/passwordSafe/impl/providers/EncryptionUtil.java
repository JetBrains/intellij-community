/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.passwordSafe.impl.providers;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.OneTimeString;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.annotations.NotNull;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utilities used to encrypt/decrypt passwords in case of Java-based implementation of PasswordSafe.
 * The class internal and could change without notice.
 */
public class EncryptionUtil {
  /**
   * The hash algorithm used for keys
   */
  private static final String HASH_ALGORITHM = "SHA-256";
  /**
   * The hash algorithm used for keys
   */
  private static final String SECRET_KEY_ALGORITHM = "AES";
  /**
   * The hash algorithm used for encrypting password
   */
  private static final String ENCRYPT_KEY_ALGORITHM = "AES/CBC/NoPadding";
  /**
   * The hash algorithm used for encrypting data
   */
  private static final String ENCRYPT_DATA_ALGORITHM = "AES/CBC/PKCS5Padding";
  /**
   * The secret key size (available for international encryption)
   */
  private static final int SECRET_KEY_SIZE = 128;
  /**
   * The secret key size (available for international encryption)
   */
  public static final int SECRET_KEY_SIZE_BYTES = SECRET_KEY_SIZE / 8;

  /**
   * 128 bits salt for AES-CBC with for data values (stable non-secret value)
   */
  private static final IvParameterSpec CBC_SALT_DATA =
    new IvParameterSpec(new byte[]{119, 111, -93, 2, -43, -12, 117, 82, 12, 40, 69, -34, 78, 86, -97, 95});

  /**
   * 128 bits salt for AES-CBC with for key values (stable non-secret value)
   */
  private static final IvParameterSpec CBC_SALT_KEY =
    new IvParameterSpec(new byte[]{-84, 125, 61, 61, 95, -34, -112, -9, 7, 25, -42, 96, 11, 89, -101, -70});

  /**
   * The private constructor
   */
  private EncryptionUtil() {
    // do nothing
  }

  static byte[] rawKey(@NotNull CredentialAttributes attributes) {
    return hash(getUTF8Bytes(attributes.getServiceName() + "/" + attributes.getUserName()));
  }

  /**
   * Generate key based on secure random
   *
   * @param keyBytes the key to use
   * @return the generated key
   */
  public static byte[] genKey(byte[] keyBytes) {
    byte[] key = new byte[SECRET_KEY_SIZE_BYTES];
    for (int i = 0; i < keyBytes.length; i++) {
      key[i % SECRET_KEY_SIZE_BYTES] ^= keyBytes[i];
    }
    return key;
  }

  /**
   * Generate key based on password
   *
   * @param password the password to use
   * @return the generated key
   */
  public static byte[] genPasswordKey(@NotNull String password) {
    return genKey(hash(getUTF8Bytes(password)));
  }


  /**
   * Encrypt key (does not use salting, so the encryption result is the same for the same input)
   *
   * @param password the secret key to use
   * @param rawKey   the raw key to encrypt
   * @return the encrypted key
   */
  @NotNull
  public static byte[] encryptKey(@NotNull byte[] password, byte[] rawKey) {
    try {
      Cipher c = Cipher.getInstance(ENCRYPT_KEY_ALGORITHM);
      c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(password, SECRET_KEY_ALGORITHM), CBC_SALT_KEY);
      return c.doFinal(rawKey);
    }
    catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Decrypt key (does not use salting, so the encryption result is the same for the same input)
   *
   * @param password     the secret key to use
   * @param encryptedKey the key to decrypt
   * @return the decrypted key
   */
  public static byte[] decryptKey(byte[] password, byte[] encryptedKey) {
    try {
      Cipher c = Cipher.getInstance(ENCRYPT_KEY_ALGORITHM);
      c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(password, SECRET_KEY_ALGORITHM), CBC_SALT_KEY);
      return c.doFinal(encryptedKey);
    }
    catch (Exception e) {
      throw new IllegalStateException(ENCRYPT_KEY_ALGORITHM + " is not available", e);
    }
  }


  /**
   * Encrypt key (does not use salting, so the encryption result is the same for the same input)
   *
   * @param password the secret key to use
   * @param data     the data to encrypt
   * @return the encrypted data
   */
  static byte[] encryptData(byte[] password, int size, byte[] data) {
    try {
      Cipher c = Cipher.getInstance(ENCRYPT_DATA_ALGORITHM);
      c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(password, SECRET_KEY_ALGORITHM), CBC_SALT_DATA);
      c.update(new byte[]{(byte)(size >> 24), (byte)(size >> 16), (byte)(size >> 8), (byte)(size)});
      return c.doFinal(data);
    }
    catch (Exception e) {
      throw new IllegalStateException(ENCRYPT_DATA_ALGORITHM + " is not available", e);
    }
  }

  public static byte[] encryptText(byte[] password, @NotNull OneTimeString value) {
    byte[] data = value.toByteArray(false);
    return encryptData(password, data.length, data);
  }

  /**
   * Decrypt key (does not use salting, so the encryption result is the same for the same input)
   *
   * @param password      the secret key to use
   * @param encryptedData the data to decrypt
   * @return the decrypted data (the first four bytes is real data length in Big Endian)
   */
  static byte[] decryptData(byte[] password, byte[] encryptedData) {
    try {
      Cipher c = Cipher.getInstance(ENCRYPT_DATA_ALGORITHM);
      c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(password, SECRET_KEY_ALGORITHM), CBC_SALT_DATA);
      return c.doFinal(encryptedData);
    }
    catch (Exception e) {
      throw new IllegalStateException(ENCRYPT_DATA_ALGORITHM + " is not available", e);
    }
  }

  @NotNull
  public static OneTimeString decryptText(byte[] password, byte[] data) {
    byte[] plain = decryptData(password, data);
    int len = ((plain[0] & 0xff) << 24) + ((plain[1] & 0xff) << 16) + ((plain[2] & 0xff) << 8) + (plain[3] & 0xff);
    if (len < 0 || len > plain.length - 4) {
      throw new IllegalStateException("Unmatched password is used");
    }
    return CredentialAttributesKt.OneTimeString(plain, 4, len);
  }

  /**
   * Convert string to UTF-8 bytes
   *
   * @param string the to convert to bytes
   * @return the UTF-8 encoded string
   */
  public static byte[] getUTF8Bytes(String string) {
    return string.getBytes(CharsetToolkit.UTF8_CHARSET);
  }

  /**
   * Hash the specified sequence of bytes
   *
   * @param data the data to hash
   * @return the digest value
   */
  public static byte[] hash(byte[]... data) {
    try {
      MessageDigest h = MessageDigest.getInstance(HASH_ALGORITHM);
      for (byte[] d : data) {
        h.update(d);
      }
      return h.digest();
    }
    catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("The hash algorithm " + HASH_ALGORITHM + " is not available", e);
    }
  }
}
