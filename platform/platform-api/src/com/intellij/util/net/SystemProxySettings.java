// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net;

import com.intellij.openapi.util.SystemInfoRt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.IOException;
import java.net.URI;

@ApiStatus.Internal
public abstract class SystemProxySettings {
  public abstract void openProxySettings() throws IOException;
  public abstract boolean isProxySettingsOpenSupported();

  public static @NotNull SystemProxySettings getInstance() {
    return Holder.instance;
  }

  private static class Holder {
    private static final @NotNull SystemProxySettings instance;
    static {
      if (SystemInfoRt.isMac) {
        instance = new MacOSSettings();
      } else if (SystemInfoRt.isWindows) {
        instance = new WindowsSettings();
      } else {
        instance = new UnsupportedOSSettings();
      }
    }
  }

  private static class MacOSSettings extends SystemProxySettings {
    @Override
    public void openProxySettings() throws IOException {
      Desktop.getDesktop().browse(URI.create("x-apple.systempreferences:com.apple.Network-Settings.extension?Proxies"));
    }

    @Override
    public boolean isProxySettingsOpenSupported() {
      return SystemInfoRt.isMac && Desktop.isDesktopSupported();
    }
  }

  private static class WindowsSettings extends SystemProxySettings {
    @Override
    public void openProxySettings() throws IOException {
      Desktop.getDesktop().browse(URI.create("ms-settings:network-proxy"));
    }

    @Override
    public boolean isProxySettingsOpenSupported() {
      return SystemInfoRt.isWindows && Desktop.isDesktopSupported();
    }
  }

  private static class UnsupportedOSSettings extends SystemProxySettings {
    @Override
    public void openProxySettings() { throw new UnsupportedOperationException(); }

    @Override
    public boolean isProxySettingsOpenSupported() { return false; }
  }
}
