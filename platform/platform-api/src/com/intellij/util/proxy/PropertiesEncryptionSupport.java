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
package com.intellij.util.proxy;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.security.Key;
import java.security.SecureRandom;
import java.util.Properties;

/**
 * @author Eugene Zhuravlev
 *         Date: 08-Jul-16
 */
public class PropertiesEncryptionSupport {
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
  public Properties load(@NotNull File file) throws Exception {
    final byte[] bytes = decrypt(FileUtil.loadFileBytes(file));
    final Properties props = new Properties();
    props.load(new ByteArrayInputStream(bytes));
    return props;
  }

  public void store(@NotNull Properties props, @NotNull String comments, @NotNull File file) throws Exception {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    props.store(out, comments);
    out.close();
    FileUtil.writeToFile(file, encrypt(out.toByteArray()));
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

}
