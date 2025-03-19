// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.render;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

public final class ToggleRenderAllDocs extends ToggleAction implements DumbAware {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return EditorSettingsExternalizable.getInstance().isDocCommentRenderingEnabled();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    EditorSettingsExternalizable.getInstance().setDocCommentRenderingEnabled(state);
    DocRenderManager.resetAllEditorsToDefaultState();
  }
}
