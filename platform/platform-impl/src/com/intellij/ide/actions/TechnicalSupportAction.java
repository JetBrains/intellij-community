// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.system.CpuArch;
import org.jetbrains.annotations.NotNull;

public class TechnicalSupportAction extends AnAction implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setVisible(ApplicationInfoImpl.getShadowInstance().getSupportUrl() != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ApplicationInfoEx appInfo = ApplicationInfoImpl.getShadowInstance();
    String url = appInfo.getSupportUrl()
      .replace("$BUILD", appInfo.getBuild().asStringWithoutProductCode())
      .replace("$OS", getOSName())
      .replace("$TIMEZONE", System.getProperty("user.timezone"));
    BrowserUtil.browse(StringUtil.replace(url, " ", "%20"), e.getProject());
  }

  /*
  Supported values for https://intellij-support.jetbrains.com
    Linux               - linux
    macOS               - mac
    Windows 10          - win-10[-64]
    Windows 8           - win-8[-64]
    Windows 7 or older  - win-7[-64]
    Other               - other-os
   */
  private static String getOSName() {
    String name;
    if (SystemInfo.isWindows) {
      name = "win-";
      if (SystemInfo.isWin10OrNewer) {
        name += "-10";
      }
      else if (SystemInfo.isWin8OrNewer) {
        name += "-8";
      }
      else {
        name += "-7";
      }
      if (!CpuArch.is32Bit()) {
        name += "-64";
      }
    }
    else if (SystemInfo.isLinux) {
      name = "linux";
    }
    else if (SystemInfo.isMac) {
      name = "mac";
    }
    else {
      name = "other-os";
    }
    return name;
  }
}
