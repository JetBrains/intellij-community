package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;

public class MoveCatchUpFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.DeleteCatchFix");

  private final PsiCatchSection myCatchSection;
  private final PsiCatchSection myMoveBeforeSection;

    public MoveCatchUpFix(PsiCatchSection catchSection, PsiCatchSection moveBeforeSection) {
    this.myCatchSection = catchSection;
        myMoveBeforeSection = moveBeforeSection;
    }

  public String getText() {
    return QuickFixBundle.message("move.catch.up.text",
                                  HighlightUtil.formatType(myCatchSection.getCatchType()),
                                  HighlightUtil.formatType(myMoveBeforeSection.getCatchType()));
  }

  public String getFamilyName() {
    return QuickFixBundle.message("move.catch.up.family");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return
           myCatchSection != null
        && myCatchSection.isValid()
        && myCatchSection.getManager().isInProject(myCatchSection)
        && myMoveBeforeSection != null
        && myMoveBeforeSection.isValid()
        && myCatchSection.getCatchType() != null
        && PsiUtil.resolveClassInType(myCatchSection.getCatchType()) != null
        && myMoveBeforeSection.getCatchType() != null
        && PsiUtil.resolveClassInType(myMoveBeforeSection.getCatchType()) != null
        && !myCatchSection.getManager().areElementsEquivalent(
                PsiUtil.resolveClassInType(myCatchSection.getCatchType()),
                PsiUtil.resolveClassInType(myMoveBeforeSection.getCatchType()))
        ;
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(myCatchSection.getContainingFile())) return;
    try {
      PsiTryStatement statement = myCatchSection.getTryStatement();
      statement.addBefore(myCatchSection, myMoveBeforeSection);
      myCatchSection.delete();
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }

}
