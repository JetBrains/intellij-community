// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.passwordSafe.impl.providers;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.OneTimeString;
import com.intellij.credentialStore.OneTimeStringKt;
import com.intellij.util.io.DigestUtil;
import org.jetbrains.annotations.NotNull;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;

/**
 * Utilities used to encrypt/decrypt passwords in case of Java-based implementation of PasswordSafe.
 * The class internal and could change without notice.
 */
public final class EncryptionUtil {
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

  public static byte[] rawKey(@NotNull CredentialAttributes attributes) {
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
   * Encrypt key (does not use salting, so the encryption result is the same for the same input)
   *
   * @param password the secret key to use
   * @param rawKey   the raw key to encrypt
   * @return the encrypted key
   */
  public static byte @NotNull [] encryptKey(byte @NotNull [] password, byte[] rawKey) {
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
    return OneTimeStringKt.OneTimeString(plain, 4, len);
  }

  /**
   * Convert string to UTF-8 bytes
   *
   * @param string the to convert to bytes
   * @return the UTF-8 encoded string
   */
  public static byte[] getUTF8Bytes(String string) {
    return string.getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Hash the specified sequence of bytes
   *
   * @param data the data to hash
   * @return the digest value
   */
  public static byte[] hash(byte[]... data) {
    MessageDigest h = DigestUtil.sha256();
    for (byte[] d : data) {
      h.update(d);
    }
    return h.digest();
  }
}
