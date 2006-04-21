package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;

public class DeleteCatchFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.DeleteCatchFix");

  private final PsiParameter myCatchParameter;

  public DeleteCatchFix(PsiParameter myCatchParameter) {
    this.myCatchParameter = myCatchParameter;
  }

  public String getText() {
    return QuickFixBundle.message("delete.catch.text", HighlightUtil.formatType(myCatchParameter.getType()));
  }

  public String getFamilyName() {
    return QuickFixBundle.message("delete.catch.family");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return myCatchParameter != null
           && myCatchParameter.isValid()
           && PsiManager.getInstance(project).isInProject(myCatchParameter.getContainingFile());
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(myCatchParameter.getContainingFile())) return;
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
