// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.browsers.chrome.ChromeSettings;
import com.intellij.ide.browsers.firefox.FirefoxSettings;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

public enum BrowserFamily implements Iconable {
  CHROME(IdeBundle.message("browsers.chrome"), "chrome", "google-chrome", "Google Chrome", AllIcons.Xml.Browsers.Chrome) {
    @Override
    public BrowserSpecificSettings createBrowserSpecificSettings() {
      return new ChromeSettings();
    }
  },
  FIREFOX(IdeBundle.message("browsers.firefox"), "firefox", "firefox", "Firefox", AllIcons.Xml.Browsers.Firefox) {
    @Override
    public BrowserSpecificSettings createBrowserSpecificSettings() {
      return new FirefoxSettings();
    }
  },
  SAFARI(IdeBundle.message("browsers.safari"), "safari", null, "Safari", AllIcons.Xml.Browsers.Safari);

  private final @Nls String myName;
  private final String myWindowsPath;
  private final String myUnixPath;
  private final String myMacPath;
  private final Icon myIcon;

  BrowserFamily(@NotNull @Nls String name,
                @NotNull String windowsPath,
                @Nullable String unixPath,
                @Nullable String macPath,
                @NotNull Icon icon) {
    myName = name;
    myWindowsPath = windowsPath;
    myUnixPath = unixPath;
    myMacPath = macPath;
    myIcon = icon;
  }

  public @Nullable BrowserSpecificSettings createBrowserSpecificSettings() {
    return null;
  }

  public @Nullable String getExecutionPath() {
    if (SystemInfo.isWindows) {
      return myWindowsPath;
    }
    else if (SystemInfo.isMac) {
      return myMacPath;
    }
    else {
      return myUnixPath;
    }
  }

  public @Nls String getName() {
    return myName;
  }

  public Icon getIcon() {
    return myIcon;
  }

  @Override
  public String toString() {
    return myName;
  }

  @Override
  public Icon getIcon(@IconFlags int flags) {
    return getIcon();
  }
}