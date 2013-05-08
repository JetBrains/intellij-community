/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.impl.IdeFrameImpl;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class TogglePresentationModeAction extends AnAction implements DumbAware {
  @Override
  public void update(AnActionEvent e) {
    boolean selected = UISettings.getInstance().PRESENTATION_MODE;
    e.getPresentation().setText(selected ? "Exit Presentation Mode" : "Enter Presentation Mode");
  }

  @Override
  public void actionPerformed(AnActionEvent e){
    UISettings settings = UISettings.getInstance();
    Project project = e.getProject();

    if (project != null) {
      HideAllToolWindowsAction.performAction(project);
    }

    settings.PRESENTATION_MODE = !settings.PRESENTATION_MODE;
    settings.fireUISettingsChanged();

    if (project != null) {
      Window frame = IdeFrameImpl.getActiveFrame();
      if (frame instanceof IdeFrameImpl) {
        final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(project);
        if (settings.PRESENTATION_MODE) {
          propertiesComponent.setValue("full.screen.before.presentation.mode", String.valueOf(((IdeFrameImpl)frame).isInFullScreen()));
          ((IdeFrameImpl)frame).toggleFullScreen(true);
        } else {
          final String value = propertiesComponent.getValue("full.screen.before.presentation.mode");
          ((IdeFrameImpl)frame).toggleFullScreen("true".equalsIgnoreCase(value));
        }
      }
    }

    EditorUtil.reinitSettings();
  }
}
