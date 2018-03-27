/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static java.lang.String.valueOf;

public class ToggleDistractionFreeModeAction extends DumbAwareAction {
  private static final String key = "editor.distraction.free.mode";

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (e.getProject() == null) {
      e.getPresentation().setEnabled(false);
      return;
    }
    //noinspection ConditionalExpressionWithIdenticalBranches
    String text = Registry.is(key) ? ActionsBundle.message("action.ToggleDistractionFreeMode.exit")
                                   : ActionsBundle.message("action.ToggleDistractionFreeMode.enter");
    e.getPresentation().setText(text);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    alternateCurrentDistractionFreeModeSetting();
    boolean enter = isDistractionFreeModeEnabled();

    if (project == null) return;

    PropertiesComponent p = PropertiesComponent.getInstance();
    UISettings ui = UISettings.getInstance();
    EditorSettingsExternalizable.OptionSet eo = EditorSettingsExternalizable.getInstance().getOptions();
    DaemonCodeAnalyzerSettings ds = DaemonCodeAnalyzerSettings.getInstance();

    String before = "BEFORE.DISTRACTION.MODE.";
    String after = "AFTER.DISTRACTION.MODE.";
    if (enter) {
      applyAndSave(p, ui, eo, ds, before, after, false);
      TogglePresentationModeAction.storeToolWindows(project);
    }
    else {
      applyAndSave(p, ui, eo, ds, after, before, true);    
      TogglePresentationModeAction.restoreToolWindows(project, true, false);
    }

    UISettings.getInstance().fireUISettingsChanged();
    LafManager.getInstance().updateUI();
    EditorUtil.reinitSettings();
    DaemonCodeAnalyzer.getInstance(project).settingsChanged();
    EditorFactory.getInstance().refreshAllEditors();
    final JFrame projectJFrame = WindowManager.getInstance().getFrame(project);
    if (projectJFrame != null) {
      projectJFrame.transferFocus();
    }
  }

  public static void applyAndSave(@NotNull PropertiesComponent p,
                                  @NotNull UISettings ui,
                                  @NotNull EditorSettingsExternalizable.OptionSet eo,
                                  @NotNull DaemonCodeAnalyzerSettings ds,
                                  String before, String after, boolean value) {
    // @formatter:off
    p.setValue(before + "SHOW_STATUS_BAR",          valueOf(ui.getShowStatusBar()));           ui.setShowStatusBar(p.getBoolean(after + "SHOW_STATUS_BAR",  value));
    p.setValue(before + "SHOW_MAIN_TOOLBAR",        valueOf(ui.getShowMainToolbar()));         ui.setShowMainToolbar(p.getBoolean(after + "SHOW_MAIN_TOOLBAR", value));
    p.setValue(before + "SHOW_NAVIGATION_BAR",      valueOf(ui.getShowNavigationBar()));       ui.setShowNavigationBar(p.getBoolean(after + "SHOW_NAVIGATION_BAR", value));

    p.setValue(before + "IS_FOLDING_OUTLINE_SHOWN", valueOf(eo.IS_FOLDING_OUTLINE_SHOWN));  eo.IS_FOLDING_OUTLINE_SHOWN = p.getBoolean(after + "IS_FOLDING_OUTLINE_SHOWN", value); 
    p.setValue(before + "IS_WHITESPACES_SHOWN",     valueOf(eo.IS_WHITESPACES_SHOWN));      eo.IS_WHITESPACES_SHOWN     = p.getBoolean(after + "IS_WHITESPACES_SHOWN", value); 
    p.setValue(before + "ARE_LINE_NUMBERS_SHOWN",   valueOf(eo.ARE_LINE_NUMBERS_SHOWN));    eo.ARE_LINE_NUMBERS_SHOWN   = p.getBoolean(after + "ARE_LINE_NUMBERS_SHOWN", value); 
    p.setValue(before + "ARE_GUTTER_ICONS_SHOWN",   valueOf(eo.ARE_GUTTER_ICONS_SHOWN));    eo.ARE_GUTTER_ICONS_SHOWN   = p.getBoolean(after + "ARE_GUTTER_ICONS_SHOWN", value); 
    p.setValue(before + "IS_RIGHT_MARGIN_SHOWN",    valueOf(eo.IS_RIGHT_MARGIN_SHOWN));     eo.IS_RIGHT_MARGIN_SHOWN    = p.getBoolean(after + "IS_RIGHT_MARGIN_SHOWN", value); 
    p.setValue(before + "IS_INDENT_GUIDES_SHOWN",   valueOf(eo.IS_INDENT_GUIDES_SHOWN));    eo.IS_INDENT_GUIDES_SHOWN   = p.getBoolean(after + "IS_INDENT_GUIDES_SHOWN", value); 
    p.setValue(before + "SHOW_BREADCRUMBS",         valueOf(eo.SHOW_BREADCRUMBS));          eo.SHOW_BREADCRUMBS         = p.getBoolean(after + "SHOW_BREADCRUMBS", value); 

    p.setValue(before + "SHOW_METHOD_SEPARATORS",   valueOf(ds.SHOW_METHOD_SEPARATORS));    ds.SHOW_METHOD_SEPARATORS   = p.getBoolean(after + "SHOW_METHOD_SEPARATORS", value);
    
    p.setValue(before + "HIDE_TOOL_STRIPES",        valueOf(ui.getHideToolStripes()));         ui.setHideToolStripes(p.getBoolean(after + "HIDE_TOOL_STRIPES", !value));
    p.setValue(before + "EDITOR_TAB_PLACEMENT",     valueOf(ui.getEditorTabPlacement()));      ui.setEditorTabPlacement(p.getInt(after + "EDITOR_TAB_PLACEMENT", value ? SwingConstants.TOP : UISettings.TABS_NONE));
    // @formatter:on
  }

  public static boolean isDistractionFreeModeEnabled() {
    return Registry.get(key).asBoolean();
  }

  private static void alternateCurrentDistractionFreeModeSetting() {
    RegistryValue value = Registry.get(key);
    value.setValue(!value.asBoolean());
  }
}
