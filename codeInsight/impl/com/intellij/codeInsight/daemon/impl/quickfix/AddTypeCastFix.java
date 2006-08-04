/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jul 26, 2002
 * Time: 2:16:32 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class AddTypeCastFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.AddTypeCastFix");
  private final PsiType myType;
  private final PsiExpression myExpression;

  public AddTypeCastFix(PsiType type, PsiExpression expression) {
    myType = type;
    myExpression = expression;
  }

  @NotNull
  public String getText() {
    return QuickFixBundle.message("add.typecast.text", myType.getCanonicalText());
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("add.typecast.family");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return myType.isValid() && myExpression.isValid() && myExpression.getManager().isInProject(myExpression);
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;
    addTypeCast(project, myExpression, myType);
  }

  private static void addTypeCast(Project project, PsiExpression originalExpression, PsiType type) throws IncorrectOperationException {
    PsiTypeCastExpression typeCast = createCastExpression(originalExpression, project, type);
    originalExpression.replace(typeCast);
  }

  static PsiTypeCastExpression createCastExpression(PsiExpression originalExpression, Project project, PsiType type) throws IncorrectOperationException {
    // remove nested casts
    PsiElement element = PsiUtil.deparenthesizeExpression(originalExpression);
    PsiElementFactory factory = originalExpression.getManager().getElementFactory();

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
