/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.surroundWith.JavaWithTryCatchSurrounder;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author mike
 * Date: Aug 19, 2002
 */
public class SurroundWithTryCatchFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.SurroundWithTryCatchFix");

  private PsiElement myElement;

  public SurroundWithTryCatchFix(@NotNull PsiElement element) {
    if (element instanceof PsiStatement ||
        element instanceof PsiResourceVariable ||
        (element instanceof PsiExpression &&
         !(element instanceof PsiMethodReferenceExpression) &&
         ControlFlowUtils.canExtractStatement((PsiExpression)element, false))) {
      myElement = element;
    }
  }

  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message("surround.with.try.catch.fix");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("surround.with.try.catch.fix");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myElement != null && myElement.isValid();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    int col = editor.getCaretModel().getLogicalPosition().column;
    int line = editor.getCaretModel().getLogicalPosition().line;
    editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(0, 0));

    if (myElement instanceof PsiExpression) {
      myElement = RefactoringUtil.ensureCodeBlock((PsiExpression)myElement);
    }
    myElement = RefactoringUtil.getParentStatement(myElement, false);
    if (myElement == null) return;

    TextRange range = null;

    try{
      JavaWithTryCatchSurrounder handler = new JavaWithTryCatchSurrounder();
      range = handler.surroundElements(project, editor, new PsiElement[]{myElement});
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }
    LogicalPosition pos = new LogicalPosition(line, col);
    editor.getCaretModel().moveToLogicalPosition(pos);
    if (range != null) {
      int offset = range.getStartOffset();
      editor.getCaretModel().moveToOffset(offset);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
