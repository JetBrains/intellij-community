// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.fix;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author peter
 */
public final class ReplaceWithObjectsEqualsFix implements LocalQuickFix {
  private final String myQualifierText;
  private final String myReplacementText;

  private ReplaceWithObjectsEqualsFix(String qualifierText, String replacementText) {
    myQualifierText = qualifierText;
    myReplacementText = replacementText;
  }

  @Nls
  @NotNull
  @Override
  public String getName() {
    return CommonQuickFixBundle.message("fix.replace.x.with.y", myQualifierText + ".equals(...)", "Objects.equals(" + myReplacementText + ", ...)");
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return CommonQuickFixBundle.message("fix.replace.x.with.y", ".equals()", "Objects.equals()");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethodCallExpression.class);
    if (call == null) return;

    PsiExpression[] args = call.getArgumentList().getExpressions();
    if (args.length != 1) return;

    String replacementText = "java.util.Objects.equals(" + myReplacementText + ", " + args[0].getText() + ")";
    PsiElement replaced = call.replace(JavaPsiFacade.getElementFactory(project).createExpressionFromText(replacementText, call));
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(((PsiMethodCallExpression)replaced).getMethodExpression());
  }

  @Nullable
  public static ReplaceWithObjectsEqualsFix createFix(@NotNull PsiMethodCallExpression call,
                                                      @NotNull PsiReferenceExpression methodExpression) {
    if (!"equals".equals(methodExpression.getReferenceName()) ||
        call.getArgumentList().getExpressionCount() != 1 ||
        !PsiUtil.getLanguageLevel(call).isAtLeast(LanguageLevel.JDK_1_7)) {
      return null;
    }

    PsiExpression qualifier = methodExpression.getQualifierExpression();
    PsiExpression noParens = PsiUtil.skipParenthesizedExprDown(qualifier);
    if (noParens == null) return null;

    PsiMethod method = call.resolveMethod();
    if (method != null &&
        method.getParameterList().getParametersCount() == 1 &&
        Objects.requireNonNull(method.getParameterList().getParameter(0)).getType().equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
      return new ReplaceWithObjectsEqualsFix(qualifier.getText(), noParens.getText());
    }
    return null;
  }
}
