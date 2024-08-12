// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.move.MoveHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MoveAction extends BaseRefactoringAction {

  public MoveAction() {
    setInjectedContext(true);
  }

  @Override
  public boolean isAvailableInEditorOnly() {
    return false;
  }

  @Override
  public boolean isEnabledOnElements(PsiElement @NotNull [] elements) {
    return MoveHandler.canMove(elements, null);
  }

  @Override
  protected boolean isAvailableOnElementInEditorAndFile(@NotNull PsiElement element,
                                                        @NotNull Editor editor,
                                                        @NotNull PsiFile file,
                                                        @NotNull DataContext context,
                                                        @NotNull String place) {
    if (place.equals(ActionPlaces.REFACTORING_QUICKLIST)) {
      PsiElement caretElement = BaseRefactoringAction.getElementAtCaret(editor, file);
      if (PsiTreeUtil.isAncestor(element, caretElement, false)) {
        return isEnabledOnElements(new PsiElement[]{element});
      } else {
        return isEnabledOnElements(new PsiElement[]{caretElement});
      }
    }
    return super.isAvailableOnElementInEditorAndFile(element, editor, file, context, place);
  }

  @Override
  protected boolean isEnabledOnDataContext(@NotNull DataContext dataContext) {
    return MoveHandler.canMove(dataContext);
  }

  @Override
  protected boolean disableOnCompiledElement() {
    return false;
  }

  @Override
  public RefactoringActionHandler getHandler(@NotNull DataContext dataContext) {
    return new MoveHandler();
  }

  @Override
  protected @Nullable String getActionName(@NotNull DataContext dataContext) {
    return MoveHandler.getActionName(dataContext);
  }
}