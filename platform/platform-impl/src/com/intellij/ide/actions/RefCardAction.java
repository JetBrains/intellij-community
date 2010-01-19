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

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NonNls;

import java.io.File;

/**
 * @author Vladimir Kondratyev
 */
public class RefCardAction extends AnAction implements DumbAware {
  @NonNls private static final String KEYMAP_URL = PathManager.getHomePath() + "/help/" + (SystemInfo.isMac ? "ReferenceCardForMac.pdf" : "ReferenceCard.pdf");

  public void actionPerformed(AnActionEvent e) {
    final String url = KEYMAP_URL;
    if (new File(url).isFile()) {
      BrowserUtil.launchBrowser(url);
    }
    else {
      final ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
      String webUrl = SystemInfo.isMac ? appInfo.getMacKeymapUrl() : appInfo.getWinKeymapUrl();
      if (webUrl != null) {
        BrowserUtil.launchBrowser(webUrl);
      }
    }
  }

  public void update(AnActionEvent e) {
    super.update(e);
    if (!ActionPlaces.WELCOME_SCREEN.equals(e.getPlace())) {
      e.getPresentation().setIcon(null);
    }
  }
}
