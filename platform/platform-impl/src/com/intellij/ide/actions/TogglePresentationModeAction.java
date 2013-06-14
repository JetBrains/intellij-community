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

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.impl.DesktopLayout;
import com.intellij.openapi.wm.impl.IdeFrameImpl;

import javax.swing.*;
import java.awt.*;
import java.util.Enumeration;

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

    settings.PRESENTATION_MODE = !settings.PRESENTATION_MODE;

    if (project != null) {
      hideToolWindows(project);
    }

    settings.fireUISettingsChanged();

    UIDefaults defaults = UIManager.getDefaults();
    Enumeration<Object> keys = defaults.keys();
    if (settings.PRESENTATION_MODE) {
      while (keys.hasMoreElements()) {
        Object key = keys.nextElement();
        if (key instanceof String && !((String)key).startsWith("old.") && ((String)key).endsWith(".font")) {
          Font font = defaults.getFont(key);
          defaults.put("old." + key, font);
          defaults.put(key, font.deriveFont((float)Math.min(20, settings.PRESENTATION_MODE_FONT_SIZE)));
        }
      }
    } else {
      while (keys.hasMoreElements()) {
        Object key = keys.nextElement();
        if (key instanceof String && ((String)key).startsWith("old.") && ((String)key).endsWith(".font")) {
          Font font = defaults.getFont(key);
          defaults.put(((String)key).substring(4), font);
          defaults.put(key, font);
        }
      }
    }

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

    UISettings.getInstance().fireUISettingsChanged();
    LafManager.getInstance().updateUI();

    EditorUtil.reinitSettings();
  }

  private static void hideToolWindows(Project project) {
    final ToolWindowManagerEx mgr = ToolWindowManagerEx.getInstanceEx(project);

    final DesktopLayout layout = new DesktopLayout();
    layout.copyFrom(mgr.getLayout());

    // to clear windows stack
    mgr.clearSideStack();

    final String[] ids = mgr.getToolWindowIds();
    boolean hasVisible = false;
    for (String id : ids) {
      final ToolWindow toolWindow = mgr.getToolWindow(id);
      if (toolWindow.isVisible()) {
        toolWindow.hide(null);
        hasVisible = true;
      }
    }

    if (hasVisible && UISettings.getInstance().PRESENTATION_MODE) {
      mgr.setLayoutToRestoreLater(layout);
      mgr.activateEditorComponent();
    }
    else if (!UISettings.getInstance().PRESENTATION_MODE && !hasVisible) {
      final DesktopLayout restoreLayout = mgr.getLayoutToRestoreLater();
      if (restoreLayout != null) {
        mgr.setLayout(restoreLayout);
      }
    }
  }
}
