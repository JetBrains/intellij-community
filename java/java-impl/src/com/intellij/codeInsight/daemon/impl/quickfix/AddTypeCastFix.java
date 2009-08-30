/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jul 26, 2002
 * Time: 2:16:32 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class AddTypeCastFix implements IntentionAction {
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

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myType.isValid() && myExpression.isValid() && myExpression.getManager().isInProject(myExpression);
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;
    addTypeCast(project, myExpression, myType);
  }

  private static void addTypeCast(Project project, PsiExpression originalExpression, PsiType type) throws IncorrectOperationException {
    PsiExpression typeCast = createCastExpression(originalExpression, project, type);
    originalExpression.replace(typeCast);
  }

  static PsiExpression createCastExpression(PsiExpression originalExpression, Project project, PsiType type) throws IncorrectOperationException {
    // remove nested casts
    PsiElement element = PsiUtil.deparenthesizeExpression(originalExpression);
    PsiElementFactory factory = JavaPsiFacade.getInstance(originalExpression.getProject()).getElementFactory();

    PsiTypeCastExpression typeCast = (PsiTypeCastExpression)factory.createExpressionFromText("(Type)value", null);
    typeCast = (PsiTypeCastExpression)CodeStyleManager.getInstance(project).reformat(typeCast);
    typeCast.getCastType().replace(factory.createTypeElement(type));
    
    if (element instanceof PsiConditionalExpression) {
      // we'd better cast one branch of ternary expression if we could
      PsiConditionalExpression expression = (PsiConditionalExpression)element.copy();
      PsiExpression thenE = expression.getThenExpression();
      PsiExpression elseE = expression.getElseExpression();
      PsiType thenType = thenE == null ? null : thenE.getType();
      PsiType elseType = elseE == null ? null : elseE.getType();
      if (elseType != null && thenType != null) {
        boolean replaceThen = !TypeConversionUtil.isAssignable(type, thenType);
        boolean replaceElse = !TypeConversionUtil.isAssignable(type, elseType);
        if (replaceThen != replaceElse) {
          if (replaceThen) {
            typeCast.getOperand().replace(thenE);
            thenE.replace(typeCast);
          }
          else {
            typeCast.getOperand().replace(elseE);
            elseE.replace(typeCast);
          }
          return expression;
        }
      }
    }
    typeCast.getOperand().replace(element);
    return typeCast;
  }

  public boolean startInWriteAction() {
    return true;
  }

}
