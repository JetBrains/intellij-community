/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class AboutAction extends AnAction implements DumbAware {
  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(!SystemInfo.isMacSystemMenu || !ActionPlaces.MAIN_MENU.equals(e.getPlace()));
    e.getPresentation().setDescription("Show information about " + ApplicationNamesInfo.getInstance().getFullProductName());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    Window window = WindowManager.getInstance().suggestParentWindow(project);
    showAboutDialog(window);
  }

  public static void showAbout() {
    @SuppressWarnings("deprecation") Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
    Window window = WindowManager.getInstance().suggestParentWindow(project);
    showAboutDialog(window);
  }

  private static void showAboutDialog(@Nullable Window window) {
    AboutPopup.show(window);
  }
}
