/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ToggleDistractionFreeModeAction extends AnAction implements DumbAware {
  private static final String key = "editor.distraction.free.mode";

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (e.getProject() == null) {
      e.getPresentation().setEnabled(false);
      return;
    }
    RegistryValue value = Registry.get(key);
    boolean selected = value.asBoolean();
    e.getPresentation().setText((selected ? "Exit" : "Enter") + " Distraction Free Mode");
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    RegistryValue value = Registry.get(key);
    boolean v = !value.asBoolean();
    value.setValue(v);

    if (project == null) return;

    PropertiesComponent p = PropertiesComponent.getInstance(project);
    UISettings ui = UISettings.getInstance();
    EditorSettingsExternalizable.OptionSet eo = EditorSettingsExternalizable.getInstance().getOptions();
    DaemonCodeAnalyzerSettings ds = DaemonCodeAnalyzerSettings.getInstance();

    String before = "BEFORE.DISTRACTION.MODE.";
    if (v) {
      p.setValue(before + "SHOW_STATUS_BAR", String.valueOf(ui.SHOW_STATUS_BAR)); ui.SHOW_STATUS_BAR = false; 
      p.setValue(before + "SHOW_MAIN_TOOLBAR", String.valueOf(ui.SHOW_MAIN_TOOLBAR)); ui.SHOW_MAIN_TOOLBAR = false; 
      p.setValue(before + "SHOW_NAVIGATION_BAR", String.valueOf(ui.SHOW_NAVIGATION_BAR)); ui.SHOW_NAVIGATION_BAR = false; 
      p.setValue(before + "HIDE_TOOL_STRIPES", String.valueOf(ui.HIDE_TOOL_STRIPES)); ui.HIDE_TOOL_STRIPES = true;
      p.setValue(before + "EDITOR_TAB_PLACEMENT", String.valueOf(ui.EDITOR_TAB_PLACEMENT)); ui.EDITOR_TAB_PLACEMENT = UISettings.TABS_NONE;

      p.setValue(before + "IS_FOLDING_OUTLINE_SHOWN", String.valueOf(eo.IS_FOLDING_OUTLINE_SHOWN)); eo.IS_FOLDING_OUTLINE_SHOWN = false; 
      p.setValue(before + "IS_WHITESPACES_SHOWN", String.valueOf(eo.IS_WHITESPACES_SHOWN)); eo.IS_WHITESPACES_SHOWN = false; 
      p.setValue(before + "ARE_LINE_NUMBERS_SHOWN", String.valueOf(eo.ARE_LINE_NUMBERS_SHOWN)); eo.ARE_LINE_NUMBERS_SHOWN = false; 
      //p.setValue(before + "IS_RIGHT_MARGIN_SHOWN", String.valueOf(eo.IS_RIGHT_MARGIN_SHOWN)); eo.IS_RIGHT_MARGIN_SHOWN = false; 
      p.setValue(before + "IS_INDENT_GUIDES_SHOWN", String.valueOf(eo.IS_INDENT_GUIDES_SHOWN)); eo.IS_INDENT_GUIDES_SHOWN = false; 
      
      p.setValue(before + "SHOW_METHOD_SEPARATORS", String.valueOf(ds.SHOW_METHOD_SEPARATORS)); ds.SHOW_METHOD_SEPARATORS = false; 
      
      TogglePresentationModeAction.storeToolWindows(project);
    }
    else {
      ui.SHOW_STATUS_BAR = p.getBoolean(before + "SHOW_STATUS_BAR", true);
      ui.SHOW_MAIN_TOOLBAR = p.getBoolean(before + "SHOW_MAIN_TOOLBAR", true);
      ui.SHOW_NAVIGATION_BAR = p.getBoolean(before + "SHOW_NAVIGATION_BAR", true);
      ui.HIDE_TOOL_STRIPES = p.getBoolean(before + "HIDE_TOOL_STRIPES", true);
      ui.EDITOR_TAB_PLACEMENT = p.getOrInitInt(before + "EDITOR_TAB_PLACEMENT", SwingConstants.TOP);

      eo.IS_FOLDING_OUTLINE_SHOWN = p.getBoolean(before + "IS_FOLDING_OUTLINE_SHOWN", true);
      eo.IS_WHITESPACES_SHOWN = p.getBoolean(before + "IS_WHITESPACES_SHOWN", false);
      eo.ARE_LINE_NUMBERS_SHOWN = p.getBoolean(before + "ARE_LINE_NUMBERS_SHOWN", false);
      //eo.IS_RIGHT_MARGIN_SHOWN = p.getBoolean(before + "IS_RIGHT_MARGIN_SHOWN", true);
      eo.IS_INDENT_GUIDES_SHOWN = p.getBoolean(before + "IS_INDENT_GUIDES_SHOWN", false);
      
      ds.SHOW_METHOD_SEPARATORS = p.getBoolean(before + "SHOW_METHOD_SEPARATORS", false);

      TogglePresentationModeAction.restoreToolWindows(project, true, false);
    }

    UISettings.getInstance().fireUISettingsChanged();
    LafManager.getInstance().updateUI();
    EditorUtil.reinitSettings();
    DaemonCodeAnalyzer.getInstance(project).settingsChanged();
    EditorFactory.getInstance().refreshAllEditors();
  }
}
