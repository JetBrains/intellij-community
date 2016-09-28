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

import java.io.File;
import java.util.Properties;

public class SharedProxyConfig {
  private static final File CONFIG_FILE = new File(PathManager.getConfigPath(), "proxy_config");
  private static final String HOST = "host";
  private static final String PORT = "port";
  private static final String LOGIN = "login";
  private static final String PASSWORD = "password";
  private static final PropertiesEncryptionSupport ourEncryptionSupport = new PropertiesEncryptionSupport();  // the key is valid for the same session only

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
      final Properties props = ourEncryptionSupport.load(CONFIG_FILE);
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
        ourEncryptionSupport.store(props, "Proxy Configuration", CONFIG_FILE);
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

}
