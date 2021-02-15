/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.refactoring.actions;

import com.intellij.lang.Language;
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

public class MoveAction extends BaseRefactoringAction {

  public MoveAction() {
    setInjectedContext(true);
  }

  @Override
  public boolean isAvailableInEditorOnly() {
    return false;
  }

  @Override
  protected boolean isAvailableForLanguage(Language language){
    // move is supported in any language
    return true;
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

  @Nullable
  @Override
  protected String getActionName(@NotNull DataContext dataContext) {
    return MoveHandler.getActionName(dataContext);
  }
}