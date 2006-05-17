package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public abstract class FixMethodArgumentAction implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.FixMethodArgumentAction");

  protected final PsiExpressionList myArgList;
  protected final int myIndex;
  private ArgumentFixerActionFactory myArgumentFixerActionFactory;
  protected final PsiType myToType;

  protected FixMethodArgumentAction(PsiExpressionList list, int i, PsiType toType, ArgumentFixerActionFactory fixerActionFactory) {
    myArgList = list;
    myIndex = i;
    myArgumentFixerActionFactory = fixerActionFactory;
    myToType = toType instanceof PsiEllipsisType ? ((PsiEllipsisType) toType).toArrayType() : toType;
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return
        myToType != null
        && myToType.isValid()
        && myArgList != null
        && myArgList.getExpressions().length > myIndex
        && myArgList.getExpressions()[myIndex] != null
        && myArgList.getExpressions()[myIndex].isValid();
  }

  public boolean startInWriteAction() {
    return true;
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;
    PsiExpression expression = myArgList.getExpressions()[myIndex];

    try {
      PsiExpression modified = myArgumentFixerActionFactory.getModifiedArgument(expression, myToType);
      LOG.assertTrue(modified != null);
      expression.replace(modified);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }


  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("fix.argument.family");
  }
}
