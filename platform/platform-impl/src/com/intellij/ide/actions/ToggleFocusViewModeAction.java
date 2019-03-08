// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class ToggleFocusViewModeAction extends ToggleAction {
  protected ToggleFocusViewModeAction() {
    super("Highlight Only Current Declaration");
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return EditorSettingsExternalizable.getInstance().isFocusMode();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    EditorSettingsExternalizable.getInstance().setFocusMode(state);

    Project project = e.getProject();
    if (project == null) return;

    EditorFactory.getInstance().refreshAllEditors();
    DaemonCodeAnalyzer.getInstance(project).restart();
  }
}
