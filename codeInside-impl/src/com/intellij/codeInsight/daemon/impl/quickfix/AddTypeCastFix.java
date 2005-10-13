/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jul 26, 2002
 * Time: 2:16:32 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;

public class AddTypeCastFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.AddTypeCastFix");
  private final PsiType myType;
  private final PsiElement myElement;

  public AddTypeCastFix(PsiType type, PsiElement element) {
    myType = type;
    myElement = element;
  }

  public String getText() {
    return QuickFixBundle.message("add.typecast.text", myType.getCanonicalText());
  }

  public String getFamilyName() {
    return QuickFixBundle.message("add.typecast.family");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return myType.isValid() && myElement.isValid() && myElement.getManager().isInProject(myElement);
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;
    addTypeCast(project, myElement, myType);
  }

  static void addTypeCast(Project project, PsiElement origElement, PsiType type) {
    try {
      PsiTypeCastExpression typeCast = createCastExpression(origElement, project, type);

      origElement.replace(typeCast);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  static PsiTypeCastExpression createCastExpression(PsiElement origElement, Project project, PsiType type) throws IncorrectOperationException {
    // remove nested casts
    PsiElement element = origElement;
    if (element instanceof PsiExpression) {
      element = PsiUtil.deparenthesizeExpression((PsiExpression)element);
    }
    PsiElementFactory factory = origElement.getManager().getElementFactory();

    PsiTypeCastExpression typeCast = (PsiTypeCastExpression)factory.createExpressionFromText("(Type)value", null);
    typeCast = (PsiTypeCastExpression)CodeStyleManager.getInstance(project).reformat(typeCast);
    typeCast.getCastType().replace(factory.createTypeElement(type));
    typeCast.getOperand().replace(element);
    return typeCast;
  }

  public boolean startInWriteAction() {
    return true;
  }

}
