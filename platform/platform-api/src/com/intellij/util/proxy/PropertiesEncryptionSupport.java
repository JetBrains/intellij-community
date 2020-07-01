// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.proxy;

import org.jetbrains.annotations.NotNull;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.SecureRandom;
import java.util.Properties;

/**
 * @author Eugene Zhuravlev
 */
public final class PropertiesEncryptionSupport {
  private final Key myKey;

  public PropertiesEncryptionSupport(Key key) {
    myKey = key;
  }

  public PropertiesEncryptionSupport() {
    this(generateKey());
  }

  public static Key generateKey() {
    final byte[] bytes = new byte[16];
    new SecureRandom().nextBytes(bytes);
    return new SecretKeySpec(bytes, "AES");
  }

  @NotNull
  public Properties load(@NotNull Path file) throws Exception {
    byte[] bytes = decrypt(Files.readAllBytes(file));
    Properties props = new Properties();
    props.load(new ByteArrayInputStream(bytes));
    return props;
  }

  public void store(@NotNull Properties props, @NotNull String comments, @NotNull Path file) throws Exception {
    if (props.isEmpty()) {
      Files.deleteIfExists(file);
      return;
    }

    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      props.store(out, comments);
      Files.createDirectories(file.getParent());
      Files.write(file, encrypt(out.toByteArray()));
    }
  }

  public byte[] encrypt(byte[] bytes) throws Exception {
    return encrypt(bytes, myKey);
  }

  public byte[] decrypt(byte[] bytes) throws Exception {
    return decrypt(bytes, myKey);
  }

  private static byte[] encrypt(byte[] msgBytes, Key key) throws Exception {
    final Cipher ciph = Cipher.getInstance("AES/CBC/PKCS5Padding");
    ciph.init(Cipher.ENCRYPT_MODE, key);
    final byte[] body = ciph.doFinal(msgBytes);
    final byte[] iv = ciph.getIV();

    final byte[] data = new byte[4 + iv.length + body.length];

    final int length = body.length;
    data[0] = (byte)((length >> 24)& 0xFF);
    data[1] = (byte)((length >> 16)& 0xFF);
    data[2] = (byte)((length >> 8)& 0xFF);
    data[3] = (byte)(length & 0xFF);

    System.arraycopy(iv, 0, data, 4, iv.length);
    System.arraycopy(body, 0, data, 4 + iv.length, body.length);
    return data;
  }

  private static byte[] decrypt(byte[] data, Key key) throws Exception {
    final int bodyLength = ((data[0] & 0xFF) << 24) |
                           ((data[1] & 0xFF) << 16) |
                           ((data[2] & 0xFF) <<  8) |
                           ((data[3] & 0xFF)      );

    final int ivlength = data.length - 4 - bodyLength;

    final Cipher ciph = Cipher.getInstance("AES/CBC/PKCS5Padding");
    ciph.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(data, 4, ivlength));
    return ciph.doFinal(data, 4 + ivlength, bodyLength);
  }

  //public static void main(String[] args) throws Exception {
  //  final PropertiesEncryptionSupport support = new PropertiesEncryptionSupport();
  //  final Charset charset = Charset.forName("utf-8");
  //  final SecureRandom rnd = new SecureRandom();
  //  for (int idx = 0; idx < 90; idx++) {
  //    final String message = new BigInteger(10 * 1024, rnd).toString();
  //    final byte[] encrypted = support.encrypt(message.getBytes(charset));
  //    String decrypted = new String(support.decrypt(encrypted), charset);
  //    if (message.equals(decrypted)) {
  //      System.out.println("Test " + idx + " ok");
  //    }
  //    else {
  //      System.out.println("Test " + idx + " FAILED");
  //    }
  //  }
  //}


}
