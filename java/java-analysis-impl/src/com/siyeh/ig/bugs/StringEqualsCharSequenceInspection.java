// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public final class StringEqualsCharSequenceInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiType type = (PsiType)infos[0];
    return InspectionGadgetsBundle.message("string.equals.char.sequence.problem.descriptor", type.getPresentableText());
  }

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    final PsiReferenceExpression expression = (PsiReferenceExpression)infos[1];
    if (PsiUtil.isLanguageLevel5OrHigher(expression) && !isStringEqualsCall(expression)) {
      return null;
    }
    return new StringEqualsCharSequenceFix();
  }

  private static boolean isStringEqualsCall(PsiReferenceExpression expression) {
    if (!"equals".equals(expression.getReferenceName())) {
      return false;
    }
    final PsiElement target = expression.resolve();
    if (!(target instanceof PsiMethod method)) {
      return false;
    }
    final PsiClass aClass = method.getContainingClass();
    return aClass != null && CommonClassNames.JAVA_LANG_STRING.equals(aClass.getQualifiedName());
  }

  private static class StringEqualsCharSequenceFix extends PsiUpdateModCommandQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "contentEquals()");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiIdentifier identifier = JavaPsiFacade.getElementFactory(project).createIdentifier("contentEquals");
      element.replace(identifier);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringEqualsCharSequenceVisitor();
  }

  private static class StringEqualsCharSequenceVisitor extends BaseEqualsVisitor {

    @Override
    boolean checkTypes(@NotNull PsiReferenceExpression expression, @NotNull PsiType leftType, @NotNull PsiType rightType) {
      if (!leftType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        return false;
      }
      if (rightType.equalsToText(CommonClassNames.JAVA_LANG_STRING) ||
          !InheritanceUtil.isInheritor(rightType, CommonClassNames.JAVA_LANG_CHAR_SEQUENCE)) {
        return false;
      }
      final PsiElement name = expression.getReferenceNameElement();
      assert name != null;
      registerError(name, rightType, expression);
      return true;
    }
  }
}
