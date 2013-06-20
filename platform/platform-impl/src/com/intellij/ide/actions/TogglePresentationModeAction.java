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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.impl.DesktopLayout;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.util.containers.HashMap;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.util.Enumeration;

/**
 * @author Konstantin Bulenkov
 */
public class TogglePresentationModeAction extends AnAction implements DumbAware {
  private static final HashMap<Object, Font> oldFonts = new HashMap<Object, Font>();

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
        if (key instanceof String && ((String)key).endsWith(".font")) {
          Font font = defaults.getFont(key);
          oldFonts.put(key, font);
        }
      }
      for (Object key : oldFonts.keySet()) {
        Font font = oldFonts.get(key);
        defaults.put(key, new FontUIResource(font.getName(), font.getStyle(), Math.min(20, settings.PRESENTATION_MODE_FONT_SIZE)));
      }
    } else {
      for (Object key : oldFonts.keySet()) {
        defaults.put(key, oldFonts.get(key));
      }
      oldFonts.clear();
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

    int fontSize = settings.PRESENTATION_MODE
                   ? settings.PRESENTATION_MODE_FONT_SIZE
                   : EditorColorsManager.getInstance().getGlobalScheme().getEditorFontSize();
    for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
      if (editor instanceof EditorEx) {
        ((EditorEx)editor).setFontSize(fontSize);
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
