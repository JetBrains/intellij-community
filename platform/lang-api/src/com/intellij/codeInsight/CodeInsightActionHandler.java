// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight;

import com.intellij.codeInsight.actions.CodeInsightAction;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.lang.ContextAwareActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Performs the actual work of an {@link CodeInsightAction}.
 * 
 * To hide an action from popups but allow access by shortcut, main menu or find use {@link ContextAwareActionHandler#isAvailableForQuickList(Editor, PsiFile, com.intellij.openapi.actionSystem.DataContext)}.
 */
public interface CodeInsightActionHandler extends FileModifier {

  /**
   * Called when user invokes corresponding {@link CodeInsightAction}. This method is called inside command on EDT.
   * If {@link #startInWriteAction()} returns {@code true}, this method is also called
   * inside write action.
   *
   * @param project the project where action is invoked.
   * @param editor  the editor where action is invoked.
   * @param psiFile    the file open in the editor.
   */
  void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile);
}