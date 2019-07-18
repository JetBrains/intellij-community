// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public class TechnicalSupportAction extends AnAction implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setVisible(ApplicationInfoImpl.getShadowInstance().getSupportUrl() != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ApplicationInfoEx appInfo = ApplicationInfoImpl.getShadowInstance();
    String urlTemplate = appInfo.getSupportUrl();

    //Note: 66731 is the internal Zendesk ID for all IntelliJ-based IDEs
    String url = urlTemplate
      .replace("$BUILD", appInfo.getBuild().asStringWithoutProductCode())
      .replace("$OS", getOSName())
      .replace("$TIMEZONE", System.getProperty("user.timezone"));
    BrowserUtil.browse(StringUtil.replace(url, " ", "%20"), e.getProject());
  }

  /*
  Supported values for https://intellij-support.jetbrains.com
    Linux: Fedora        - fedora
    Linux: Other         - linux
    Linux: Ubuntu        - ubuntu
    Mac OS X 10.5-10.7   - mac-old
    Mac OS X 10.8+       - mac
    Other                - other-os
    Solaris              - solaris
    Windows 10           - win-10
    Windows 2003         - win-2003
    Windows 2003 64-bit  - win-2003-64
    Windows 7            - win-7
    Windows 7 64-bit     - win-7-64
    Windows 8            - win-8
    Windows Vista        - win-vista
    Windows Vista 64-bit - win-vista-64
    Windows XP           - win-xp
    Windows XP 64-bit    - win-xp-64
   */
  private static String getOSName() {
    String name = "";
    if (SystemInfo.isWindows) {
      name += "win-";
      name += getWindowsVersion();
      if (SystemInfo.is64Bit) {
        name += "-64";
      }
    }
    else if (SystemInfo.isLinux) {
      name += "linux";
    }
    else if (SystemInfo.isSolaris) {
      name += "solaris";
    }
    else if (SystemInfo.isMac) {
      name += "mac";
      if (!SystemInfo.isOsVersionAtLeast("10.8")) {
        name += "-old";
      }
    }
    else {
      name += "other-os";
    }
    return name;
  }

  private static boolean isWindowsVersion(String version) {
    return StringUtil.compareVersionNumbers(SystemInfo.OS_VERSION, version) == 0;
  }

  private static String getWindowsVersion() {
    if (isWindowsVersion("5.1")) return "xp";
    if (isWindowsVersion("5.2")) return "2003";
    if (isWindowsVersion("6.0")) return "vista";
    if (isWindowsVersion("6.1")) return "7";
    if (isWindowsVersion("6.2")) return "8";
    if (isWindowsVersion("10.0")) return "10";
    return "";
  }

}