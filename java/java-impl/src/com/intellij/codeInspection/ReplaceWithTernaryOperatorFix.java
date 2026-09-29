// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.java.JavaBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.LambdaUtil;
import com.intellij.psi.PsiCallExpression;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;

public class ReplaceWithTernaryOperatorFix extends PsiUpdateModCommandQuickFix {
  private final String myText;

  @Override
  public @NotNull String getName() {
    return JavaBundle.message("inspection.replace.ternary.quickfix", myText);
  }

  public ReplaceWithTernaryOperatorFix(@NotNull PsiExpression expressionToAssert) {
    myText = ParenthesesUtils.getText(expressionToAssert, ParenthesesUtils.BINARY_AND_PRECEDENCE);
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("inspection.surround.if.family");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    while (true) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiCallExpression || parent instanceof PsiJavaCodeReferenceElement) {
        element = parent;
      }
      else {
        break;
      }
    }
    if (!(element instanceof PsiExpression expression)) {
      return;
    }

    replaceWithConditionalExpression(updater, myText, expression);
  }

  static void replaceWithConditionalExpression(@NotNull ModPsiUpdater updater,
                                               @NotNull String varToCheck,
                                               @NotNull PsiExpression expression) {
    PsiType type = expression.getType();
    String defaultValue = PsiTypesUtil.getDefaultValueOfType(type);
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(updater.getProject());

    final PsiElement parent = expression.getParent();
    final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)factory.createExpressionFromText(
      varToCheck + "!=null ? " + expression.getText() + " : " + defaultValue,
      parent
    );

    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(updater.getProject());
    PsiConditionalExpression result = (PsiConditionalExpression)expression.replace(codeStyleManager.reformat(conditionalExpression));
    PsiExpression elseExpression = result.getElseExpression();
    if (elseExpression != null) {
      if (elseExpression.textMatches(JavaKeywords.FALSE)) {
        updater.templateBuilder()
          .field(elseExpression, new ConstantNode(JavaKeywords.FALSE).withLookupStrings(JavaKeywords.FALSE, JavaKeywords.TRUE));
      } else {
        updater.templateBuilder().field(elseExpression, elseExpression.getText());
      }
    }
  }

  public static boolean isAvailable(@NotNull PsiExpression qualifier, @NotNull PsiExpression expression) {
    if (!qualifier.isValid() || qualifier.getText() == null) {
      return false;
    }

    return !(expression.getParent() instanceof PsiExpressionStatement) && !PsiUtil.isAccessedForWriting(expression);
  }

  public static class ReplaceMethodRefWithTernaryOperatorFix extends PsiUpdateModCommandQuickFix {
    @Override
    public @NotNull String getFamilyName() {
      return JavaBundle.message("inspection.replace.methodref.ternary.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      PsiMethodReferenceExpression element = ObjectUtils.tryCast(startElement, PsiMethodReferenceExpression.class);
      if (element == null) return;
      PsiLambdaExpression lambda =
        LambdaRefactoringUtil.convertMethodReferenceToLambda(element, false, true);
      if (lambda == null) return;
      PsiExpression expression = LambdaUtil.extractSingleExpressionFromBody(lambda.getBody());
      if (expression == null) return;
      PsiParameter parameter = ArrayUtil.getFirstElement(lambda.getParameterList().getParameters());
      if (parameter == null) return;
      String text = parameter.getName();
      replaceWithConditionalExpression(updater, text, expression);
    }
  }
}
