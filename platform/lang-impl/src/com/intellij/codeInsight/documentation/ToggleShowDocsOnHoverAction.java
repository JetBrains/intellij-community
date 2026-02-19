// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.project.DumbAwareToggleAction;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

@Internal
public final class ToggleShowDocsOnHoverAction extends DumbAwareToggleAction implements HintManagerImpl.ActionToIgnore {

  public ToggleShowDocsOnHoverAction() {
    super(CodeInsightBundle.messagePointer("javadoc.show.on.mouse.move"));
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return EditorSettingsExternalizable.getInstance().isShowQuickDocOnMouseOverElement();
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    EditorSettingsExternalizable.getInstance().setShowQuickDocOnMouseOverElement(state);
  }
}
