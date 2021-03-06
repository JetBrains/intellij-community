// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.proxy;

import com.intellij.openapi.application.PathManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class SharedProxyConfig {
  private static final Path CONFIG_FILE = PathManager.getConfigDir().resolve("proxy_config");
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
    public final char @NotNull [] password;

    public ProxyParameters(@Nullable String host, int port) {
      this(host, port, null, new char[0]);
    }

    public ProxyParameters(@Nullable String host, int port, @Nullable String login, char @NotNull [] password) {
      this.host = host;
      this.port = port;
      this.login = login;
      this.password = password;
    }
  }

  public static boolean clear() {
    try {
      return Files.deleteIfExists(CONFIG_FILE);
    }
    catch (IOException e) {
      return false;
    }
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
      try {
        Files.deleteIfExists(CONFIG_FILE);
      }
      catch (IOException ignore) {
      }
    }
    return false;
  }
}
