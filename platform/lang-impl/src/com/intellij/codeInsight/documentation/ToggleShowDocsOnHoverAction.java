// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.documentation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import org.jetbrains.annotations.NotNull;

public final class ToggleShowDocsOnHoverAction extends ToggleAction implements HintManagerImpl.ActionToIgnore {

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
