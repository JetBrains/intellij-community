// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.UpdateInBackground;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author Vladimir Kondratyev
 */
public class RefCardAction extends DumbAwareAction implements UpdateInBackground {
  private static final @NonNls String REF_CARD_PATH =
    PathManager.getHomePath() + "/help/" + (SystemInfo.isMac ? "ReferenceCardForMac.pdf" : "ReferenceCard.pdf");

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    File file = getRefCardFile();
    if (file.isFile()) {
      BrowserUtil.browse(file);
    }
    else {
      String webUrl = getKeymapUrl();
      if (webUrl != null) {
        BrowserUtil.browse(webUrl);
      }
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(isRefCardAvailable());
  }

  private static boolean isRefCardAvailable() {
    return getRefCardFile().exists() || getKeymapUrl() != null;
  }

  private static String getKeymapUrl() {
    final ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
    return SystemInfo.isMac ? appInfo.getMacKeymapUrl() : appInfo.getWinKeymapUrl();
  }

  private static @NotNull File getRefCardFile() {
    return new File(FileUtil.toSystemDependentName(REF_CARD_PATH));
  }
}
