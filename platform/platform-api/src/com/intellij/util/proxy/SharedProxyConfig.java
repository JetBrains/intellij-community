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

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.security.Key;
import java.security.SecureRandom;
import java.util.Properties;

public class SharedProxyConfig {
  private static final File CONFIG_FILE = new File(PathManager.getConfigPath(), "proxy_config");
  private static final String HOST = "host";
  private static final String PORT = "port";
  private static final String LOGIN = "login";
  private static final String PASSWORD = "password";
  private static final Key ENCRYPTION_KEY;  // the key is valid for the same session only
  static {
    final byte[] bytes = new byte[16];
    new SecureRandom().nextBytes(bytes);
    ENCRYPTION_KEY = new SecretKeySpec(bytes, "AES");
  }

  public static final class ProxyParameters {
    @Nullable
    public final String host;
    public final int port;
    @Nullable
    public final String login;
    @NotNull
    public final char[] password;

    public ProxyParameters(@Nullable String host, int port) {
      this(host, port, null, new char[0]);
    }

    public ProxyParameters(@Nullable String host, int port, @Nullable String login, @NotNull char[] password) {
      this.host = host;
      this.port = port;
      this.login = login;
      this.password = password;
    }
  }

  public static boolean clear() {
    return FileUtil.delete(CONFIG_FILE);
  }

  @Nullable
  public static ProxyParameters load() {
    try {
      final byte[] bytes = decrypt(FileUtil.loadFileBytes(CONFIG_FILE));
      final Properties props = new Properties();
      props.load(new ByteArrayInputStream(bytes));
      final String password = props.getProperty(PASSWORD, "");
      return new ProxyParameters(
        props.getProperty(HOST, null),
        Integer.parseInt(props.getProperty(PORT, "0")),
        props.getProperty(LOGIN, null),
        password.toCharArray()
      );
    }
    catch (Exception ignored) {
    }
    return null;
  }

  public static boolean store(@NotNull ProxyParameters params) {
    if (params.host != null) {
      try {
        final Properties props = new Properties();
        props.setProperty(HOST, params.host);
        props.setProperty(PORT, String.valueOf(params.port));
        if (params.login != null) {
          props.setProperty(LOGIN, params.login);
          props.setProperty(PASSWORD, new String(params.password));
        }
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        props.store(out, "Proxy Configuration");
        out.close();
        FileUtil.writeToFile(CONFIG_FILE, encrypt(out.toByteArray()));
        return true;
      }
      catch (Exception ignored) {
      }
    }
    else {
      FileUtil.delete(CONFIG_FILE);
    }
    return false;
  }

  private static byte[] encrypt(byte[] bytes) throws Exception {
    return encrypt(bytes, ENCRYPTION_KEY);
  }

  private static byte[] decrypt(byte[] bytes) throws Exception {
    return decrypt(bytes, ENCRYPTION_KEY);
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
    int bodyLength = data[0] & 0xFF;
    bodyLength = (bodyLength << 8) + data[1] & 0xFF;
    bodyLength = (bodyLength << 8) + data[2] & 0xFF;
    bodyLength = (bodyLength << 8) + data[3] & 0xFF;

    final int ivlength = data.length - 4 - bodyLength;

    final Cipher ciph = Cipher.getInstance("AES/CBC/PKCS5Padding");
    ciph.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(data, 4, ivlength));
    return ciph.doFinal(data, 4 + ivlength, bodyLength);
  }

}
