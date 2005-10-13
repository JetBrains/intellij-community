package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

public class GeneralizeCatchFix implements IntentionAction {
//  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.DeleteCatchFix");

  private final PsiElement myElement;
  private final PsiClassType myUnhandledException;
  private PsiTryStatement myTryStatement;
  private PsiParameter myCatchParameter;

  public GeneralizeCatchFix(PsiElement element, PsiClassType unhandledException) {
    myElement = element;
    myUnhandledException = unhandledException;
  }

  public String getText() {
    return QuickFixBundle.message("generalize.catch.text",
                                  HighlightUtil.formatType(myCatchParameter.getType()),
                                  HighlightUtil.formatType(myUnhandledException));
  }

  public String getFamilyName() {
    return QuickFixBundle.message("generalize.catch.family");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    PsiElement element1 = myElement.getContainingFile();
    if (!(myElement != null
        && myElement.isValid()
        && myUnhandledException != null
        && myUnhandledException.isValid()
        && element1.getManager().isInProject(element1))) return false;
    // final enclosing try
    PsiElement element = myElement;
    while (element != null) {
      if (element instanceof PsiCodeBlock
          && element.getParent() instanceof PsiTryStatement
          && ((PsiTryStatement) element.getParent()).getTryBlock() == element) {
        myTryStatement = (PsiTryStatement) element.getParent();
        break;
      }
      if (element instanceof PsiMethod || (element instanceof PsiClass && !(element instanceof PsiAnonymousClass))) break;
      element = element.getParent();
    }
    if (myTryStatement == null) return false;
    // check we can generalize at least one catch
    PsiParameter[] catchBlockParameters = myTryStatement.getCatchBlockParameters();
    for (PsiParameter catchBlockParameter : catchBlockParameters) {
      PsiType type = catchBlockParameter.getType();
      if (type == null) continue;
      if (myUnhandledException.isAssignableFrom(type)) {
        myCatchParameter = catchBlockParameter;
        break;
      }
    }
    return myCatchParameter != null;
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtil.prepareFileForWrite(myElement.getContainingFile())) return;
    PsiElementFactory factory = myElement.getManager().getElementFactory();
    PsiTypeElement type = factory.createTypeElement(myUnhandledException);
    myCatchParameter.getTypeElement().replace(type);
  }

  public boolean startInWriteAction() {
    return true;
  }

}
