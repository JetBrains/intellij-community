/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.WindowManager;

import java.awt.*;

public class AboutAction extends AnAction implements DumbAware {

  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(!SystemInfo.isMacSystemMenu);
    e.getPresentation().setDescription("Show information about " + ApplicationNamesInfo.getInstance().getFullProductName());
  }

  public void actionPerformed(AnActionEvent e) {
    Window window = WindowManager.getInstance().suggestParentWindow(e.getData(CommonDataKeys.PROJECT));

    showAboutDialog(window);
  }

  public static void showAbout() {
    Window window = WindowManager.getInstance().suggestParentWindow(
      CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext()));

    showAboutDialog(window);
  }

  private static void showAboutDialog(Window window) {
    new AboutDialog(window).setVisible(true);
  }
}
