// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.event.KeyEvent;

@ApiStatus.Internal
public final class ShowErrorDescriptionAction extends BaseCodeInsightAction implements DumbAware {
  private boolean myRequestFocus;

  public ShowErrorDescriptionAction() {
    super(false);
    setEnabledInModalContext(true);
  }

  @Override
  protected @NotNull CodeInsightActionHandler getHandler() {
    return new ShowErrorDescriptionHandler(myRequestFocus);
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return ShowErrorDescriptionHandler.findInfoUnderCaret(project, editor) != null;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    // The tooltip gets the focus if using a screen reader and invocation through a keyboard shortcut.
    myRequestFocus = ScreenReader.isActive() && (e.getInputEvent() instanceof KeyEvent);
    super.actionPerformed(e);
  }
}
