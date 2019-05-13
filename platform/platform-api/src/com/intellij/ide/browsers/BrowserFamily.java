package com.intellij.ide.browsers;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.browsers.chrome.ChromeSettings;
import com.intellij.ide.browsers.firefox.FirefoxSettings;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public enum BrowserFamily implements Iconable {
  CHROME(IdeBundle.message("browsers.chrome"), "chrome", "google-chrome", "Google Chrome", AllIcons.Xml.Browsers.Chrome16) {
    @Override
    public BrowserSpecificSettings createBrowserSpecificSettings() {
      return new ChromeSettings();
    }
  },
  FIREFOX(IdeBundle.message("browsers.firefox"), "firefox", "firefox", "Firefox", AllIcons.Xml.Browsers.Firefox16) {
    @Override
    public BrowserSpecificSettings createBrowserSpecificSettings() {
      return new FirefoxSettings();
    }
  },
  EXPLORER(IdeBundle.message("browsers.explorer"), "iexplore", null, null, AllIcons.Xml.Browsers.Explorer16),
  OPERA(IdeBundle.message("browsers.opera"), "opera", "opera", "Opera", AllIcons.Xml.Browsers.Opera16),
  SAFARI(IdeBundle.message("browsers.safari"), "safari", null, "Safari", AllIcons.Xml.Browsers.Safari16);

  private final String myName;
  private final String myWindowsPath;
  private final String myUnixPath;
  private final String myMacPath;
  private final Icon myIcon;

  BrowserFamily(@NotNull String name,
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

  @Nullable
  public BrowserSpecificSettings createBrowserSpecificSettings() {
    return null;
  }

  @Nullable
  public String getExecutionPath() {
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

  public String getName() {
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