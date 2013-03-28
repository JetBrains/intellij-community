/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide.ui.laf.darcula;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.JBColor;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaInstaller {

  public static void uninstall() {
    JBColor.setDark(false);
    IconLoader.setUseDarkIcons(false);
    if (DarculaLaf.NAME.equals(EditorColorsManager.getInstance().getGlobalScheme().getName())) {
      final EditorColorsScheme scheme = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.DEFAULT_SCHEME_NAME);
      if (scheme != null) {
        EditorColorsManager.getInstance().setGlobalScheme(scheme);
      }
    }

    update();
  }

  private static void update() {
    UISettings.getInstance().fireUISettingsChanged();
    EditorFactory.getInstance().refreshAllEditors();

    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    for (Project openProject : openProjects) {
      FileStatusManager.getInstance(openProject).fileStatusesChanged();
      DaemonCodeAnalyzer.getInstance(openProject).restart();
    }
    for (IdeFrame frame : WindowManagerEx.getInstanceEx().getAllProjectFrames()) {
      if (frame instanceof IdeFrameImpl) {
        ((IdeFrameImpl)frame).updateView();
      }
    }
    //Editor[] editors = EditorFactory.getInstance().getAllEditors();
    //for (Editor editor : editors) {
    //  ((EditorEx)editor).reinitSettings();
    //}
    ActionToolbarImpl.updateAllToolbarsImmediately();

    restart(); //todo[kb] remove when get fixed ToolbarDecorator and toolwindow tabs
  }

  private static void restart() {
    if (Messages.showOkCancelDialog("You must restart the IDE to changes take effect. Restart now?", "Restart Is Required", "Restart", "Postpone", Messages.getQuestionIcon()) == Messages.OK) {
      ApplicationManagerEx.getApplicationEx().restart(true);
    }
  }

  public static void install() {
    JBColor.setDark(true);
    IconLoader.setUseDarkIcons(true);
    if (!DarculaLaf.NAME.equals(EditorColorsManager.getInstance().getGlobalScheme().getName())) {
      final EditorColorsScheme scheme = EditorColorsManager.getInstance().getScheme(DarculaLaf.NAME);
      if (scheme != null) {
        EditorColorsManager.getInstance().setGlobalScheme(scheme);
      }
    }
    update();
  }
}
