/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author Vladimir Kondratyev
 */
public class RefCardAction extends AnAction implements DumbAware {
  private static final String REF_CARD_PATH = PathManager.getHomePath() + "/help/" + (SystemInfo.isMac ? "ReferenceCardForMac.pdf" : "ReferenceCard.pdf");

  public void actionPerformed(AnActionEvent e) {
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

  public void update(AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(isRefCardAvailable());
    boolean atWelcome = ActionPlaces.WELCOME_SCREEN.equals(e.getPlace());
    e.getPresentation().setIcon(atWelcome ? AllIcons.General.DefaultKeymap : null);
  }

  private static boolean isRefCardAvailable() {
    return getRefCardFile().exists() || getKeymapUrl() != null;
  }

  private static String getKeymapUrl() {
    final ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
    return SystemInfo.isMac ? appInfo.getMacKeymapUrl() : appInfo.getWinKeymapUrl();
  }

  @NotNull
  private static File getRefCardFile() {
    return new File(FileUtil.toSystemDependentName(REF_CARD_PATH));
  }
}
