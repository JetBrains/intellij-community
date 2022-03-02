// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.application.options.RegistryManager;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.ToolbarSettings;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ToggleDistractionFreeModeAction extends DumbAwareAction implements LightEditCompatible {
  private static final String KEY = "editor.distraction.free.mode";
  private static final String BEFORE = "BEFORE.DISTRACTION.MODE.";
  private static final String AFTER = "AFTER.DISTRACTION.MODE.";

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    if (e.getProject() == null) {
      presentation.setEnabled(false);
      return;
    }

    String text = ActionsBundle.message(isDistractionFreeModeEnabled() ?
                                        "action.ToggleDistractionFreeMode.exit" :
                                        "action.ToggleDistractionFreeMode.enter");
    presentation.setText(text);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    RegistryValue value = RegistryManager.getInstance().get(KEY);
    boolean enter = !value.asBoolean();
    value.setValue(enter);

    Project project = e.getProject();
    if (project == null) {
      return;
    }

    applyAndSave(PropertiesComponent.getInstance(),
                 UISettings.getInstance(),
                 ToolbarSettings.getInstance(),
                 EditorSettingsExternalizable.getInstance().getOptions(),
                 DaemonCodeAnalyzerSettings.getInstance(),
                 enter ? BEFORE : AFTER,
                 enter ? AFTER : BEFORE,
                 !enter);
    if (enter) {
      TogglePresentationModeAction.storeToolWindows(project);
    }

    UISettings.getInstance().fireUISettingsChanged();
    LafManager.getInstance().updateUI();
    EditorUtil.reinitSettings();
    DaemonCodeAnalyzer.getInstance(project).settingsChanged();
    EditorFactory.getInstance().refreshAllEditors();
    if (!enter) {
      TogglePresentationModeAction.restoreToolWindows(project, false);
    }
    JFrame projectJFrame = WindowManager.getInstance().getFrame(project);
    if (projectJFrame != null) {
      projectJFrame.transferFocus();
    }
  }

  private static void applyAndSave(@NotNull PropertiesComponent p,
                                   @NotNull UISettings uiSettings,
                                   @NotNull ToolbarSettings toolbarSettings,
                                   @NotNull EditorSettingsExternalizable.OptionSet eo,
                                   @NotNull DaemonCodeAnalyzerSettings ds,
                                   String before, String after, boolean value) {
    var ui = uiSettings.getState();
    // @formatter:off
    p.setValue(before + "SHOW_STATUS_BAR",          String.valueOf(ui.getShowStatusBar()));           ui.setShowStatusBar(p.getBoolean(after + "SHOW_STATUS_BAR",  value));

    p.setValue(before + "SHOW_MAIN_TOOLBAR",        String.valueOf(uiSettings.getShowMainToolbar()));         uiSettings.setShowMainToolbar(p.getBoolean(after + "SHOW_MAIN_TOOLBAR", value));
    p.setValue(before + "SHOW_NAVIGATION_BAR",      String.valueOf(uiSettings.getShowNavigationBar()));       uiSettings.setShowNavigationBar(p.getBoolean(after + "SHOW_NAVIGATION_BAR", value));
    p.setValue(before + "SHOW_NEW_MAIN_TOOLBAR", String.valueOf(toolbarSettings.isVisible())); toolbarSettings.setVisible(p.getBoolean(after + "SHOW_NEW_MAIN_TOOLBAR", value));

    p.setValue(before + "IS_FOLDING_OUTLINE_SHOWN", String.valueOf(eo.IS_FOLDING_OUTLINE_SHOWN));  eo.IS_FOLDING_OUTLINE_SHOWN = p.getBoolean(after + "IS_FOLDING_OUTLINE_SHOWN", value);
    p.setValue(before + "IS_WHITESPACES_SHOWN",     String.valueOf(eo.IS_WHITESPACES_SHOWN));      eo.IS_WHITESPACES_SHOWN     = p.getBoolean(after + "IS_WHITESPACES_SHOWN", value);
    p.setValue(before + "ARE_LINE_NUMBERS_SHOWN",   String.valueOf(eo.ARE_LINE_NUMBERS_SHOWN));    eo.ARE_LINE_NUMBERS_SHOWN   = p.getBoolean(after + "ARE_LINE_NUMBERS_SHOWN", value);
    p.setValue(before + "ARE_GUTTER_ICONS_SHOWN",   String.valueOf(eo.ARE_GUTTER_ICONS_SHOWN));    eo.ARE_GUTTER_ICONS_SHOWN   = p.getBoolean(after + "ARE_GUTTER_ICONS_SHOWN", value);
    p.setValue(before + "IS_RIGHT_MARGIN_SHOWN",    String.valueOf(eo.IS_RIGHT_MARGIN_SHOWN));     eo.IS_RIGHT_MARGIN_SHOWN    = p.getBoolean(after + "IS_RIGHT_MARGIN_SHOWN", value);
    p.setValue(before + "IS_INDENT_GUIDES_SHOWN",   String.valueOf(eo.IS_INDENT_GUIDES_SHOWN));    eo.IS_INDENT_GUIDES_SHOWN   = p.getBoolean(after + "IS_INDENT_GUIDES_SHOWN", value);
    p.setValue(before + "SHOW_BREADCRUMBS",         String.valueOf(eo.SHOW_BREADCRUMBS));          eo.SHOW_BREADCRUMBS         = p.getBoolean(after + "SHOW_BREADCRUMBS", value);

    p.setValue(before + "SHOW_METHOD_SEPARATORS",   String.valueOf(ds.SHOW_METHOD_SEPARATORS));    ds.SHOW_METHOD_SEPARATORS   = p.getBoolean(after + "SHOW_METHOD_SEPARATORS", value);

    p.setValue(before + "HIDE_TOOL_STRIPES",        String.valueOf(ui.getHideToolStripes()));         ui.setHideToolStripes(p.getBoolean(after + "HIDE_TOOL_STRIPES", !value));
    p.setValue(before + "EDITOR_TAB_PLACEMENT",     String.valueOf(ui.getEditorTabPlacement()));      ui.setEditorTabPlacement(p.getInt(after + "EDITOR_TAB_PLACEMENT", value ? SwingConstants.TOP : UISettings.TABS_NONE));
    // @formatter:on
  }

  public static int getStandardTabPlacement() {
    if (!isDistractionFreeModeEnabled()) return UISettings.getInstance().getEditorTabPlacement();
    return PropertiesComponent.getInstance().getInt(BEFORE + "EDITOR_TAB_PLACEMENT", SwingConstants.TOP);
  }

  public static boolean isDistractionFreeModeEnabled() {
    return RegistryManager.getInstance().is(KEY);
  }
}
