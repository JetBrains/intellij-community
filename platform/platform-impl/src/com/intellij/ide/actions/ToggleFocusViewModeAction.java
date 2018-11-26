// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import org.jetbrains.annotations.NotNull;

public class ToggleFocusViewModeAction extends DumbAwareAction {
  private static final String key = "editor.focus.mode";

  protected ToggleFocusViewModeAction() {
    super("Toggle Focus Mode");
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    if (e.getProject() == null) {
      presentation.setEnabled(false);
      return;
    }
    String text = Registry.is(key) ? ActionsBundle.message("action.ToggleFocusMode.exit") : ActionsBundle.message("action.ToggleFocusMode.enter");
    presentation.setText(text);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    RegistryValue value = Registry.get(key);
    value.setValue(!value.asBoolean());

    Project project = e.getProject();
    if (project == null) return;

    EditorUtil.reinitSettings();
    DaemonCodeAnalyzer.getInstance(e.getProject()).settingsChanged();
    EditorFactory.getInstance().refreshAllEditors();
  }
}
