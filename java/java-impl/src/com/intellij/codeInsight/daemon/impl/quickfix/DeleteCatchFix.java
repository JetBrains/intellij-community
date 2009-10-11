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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class DeleteCatchFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.DeleteCatchFix");

  private final PsiParameter myCatchParameter;

  public DeleteCatchFix(PsiParameter myCatchParameter) {
    this.myCatchParameter = myCatchParameter;
  }

  @NotNull
  public String getText() {
    return QuickFixBundle.message("delete.catch.text", HighlightUtil.formatType(myCatchParameter.getType()));
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("delete.catch.family");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myCatchParameter != null
           && myCatchParameter.isValid()
           && PsiManager.getInstance(project).isInProject(myCatchParameter.getContainingFile());
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtilBase.prepareFileForWrite(myCatchParameter.getContainingFile())) return;
    try {
      PsiTryStatement tryStatement = ((PsiCatchSection)myCatchParameter.getDeclarationScope()).getTryStatement();
      PsiElement tryParent = tryStatement.getParent();
      if (tryStatement.getCatchBlocks().length == 1 && tryStatement.getFinallyBlock() == null) {
        PsiCodeBlock tryBlock = tryStatement.getTryBlock();
        PsiElement firstElement = tryBlock.getFirstBodyElement();
        PsiElement lastAddedStatement = null;
        if (firstElement != null) {
          PsiElement endElement = tryBlock.getLastBodyElement();

          tryParent.addRangeBefore(firstElement, endElement, tryStatement);
          lastAddedStatement = tryStatement.getPrevSibling();
          while (lastAddedStatement != null && (lastAddedStatement instanceof PsiWhiteSpace || lastAddedStatement.getTextLength() == 0)) {
            lastAddedStatement = lastAddedStatement.getPrevSibling();
          }          
        }
        tryStatement.delete();
        if (lastAddedStatement != null) {
          editor.getCaretModel().moveToOffset(lastAddedStatement.getTextRange().getEndOffset());
        }

        return;
      }

      // delete catch section
      LOG.assertTrue(myCatchParameter.getParent() instanceof PsiCatchSection);
      final PsiElement catchSection = myCatchParameter.getParent();
      //save previous element to move caret to
      PsiElement previousElement = catchSection.getPrevSibling();
      while (previousElement instanceof PsiWhiteSpace) {
        previousElement = previousElement.getPrevSibling();
      }
      catchSection.delete();
      if (previousElement != null) {
        //move caret to previous catch section
        editor.getCaretModel().moveToOffset(previousElement.getTextRange().getEndOffset());
      }
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }

}
