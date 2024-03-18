// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.migration;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public final class CollectionsFieldAccessReplaceableByMethodCallInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("collections.field.access.replaceable.by.method.call.problem.descriptor", infos[1]);
  }

  @Override
  @Nullable
  protected LocalQuickFix buildFix(Object... infos) {
    final PsiReferenceExpression expression = (PsiReferenceExpression)infos[0];
    return new CollectionsFieldAccessReplaceableByMethodCallFix(expression.getReferenceName());
  }

  private static class CollectionsFieldAccessReplaceableByMethodCallFix extends PsiUpdateModCommandQuickFix {

    private final String replacementText;

    CollectionsFieldAccessReplaceableByMethodCallFix(String referenceName) {
      replacementText = getCollectionsMethodCallName(referenceName);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("collections.field.access.replaceable.by.method.call.fix.family.name");
    }

    @Override
    @NotNull
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", replacementText);
    }

    @NonNls
    private static String getCollectionsMethodCallText(PsiReferenceExpression referenceExpression) {
      final String referenceName = referenceExpression.getReferenceName();
      final PsiElement parent = referenceExpression.getParent();
      if (!(parent instanceof PsiExpressionList)) {
        return getUntypedCollectionsMethodCallText(referenceName);
      }
      final PsiType type = ExpectedTypeUtils.findExpectedType(referenceExpression, false);
      if (!(type instanceof PsiClassType classType)) {
        return getUntypedCollectionsMethodCallText(referenceName);
      }
      final PsiType[] parameterTypes = classType.getParameters();
      boolean useTypeParameter = false;
      final String[] canonicalTexts = new String[parameterTypes.length];
      for (int i = 0, parameterTypesLength = parameterTypes.length; i < parameterTypesLength; i++) {
        final PsiType parameterType = parameterTypes[i];
        if (parameterType instanceof PsiWildcardType wildcardType) {
          final PsiType bound = wildcardType.getBound();
          if (bound != null) {
            if (!bound.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
              useTypeParameter = true;
            }
            canonicalTexts[i] = bound.getCanonicalText();
          }
          else {
            canonicalTexts[i] = CommonClassNames.JAVA_LANG_OBJECT;
          }
        }
        else {
          if (!parameterType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
            useTypeParameter = true;
          }
          canonicalTexts[i] = parameterType.getCanonicalText();
        }
      }
      return useTypeParameter
             ? "Collections.<" + StringUtil.join(canonicalTexts, ",") + '>' + getCollectionsMethodCallName(referenceName)
             : getUntypedCollectionsMethodCallText(referenceName);
    }

    @NonNls
    private static String getUntypedCollectionsMethodCallText(String referenceName) {
      return "Collections." + getCollectionsMethodCallName(referenceName);
    }

    @NonNls
    private static String getCollectionsMethodCallName(@NonNls String referenceName) {
      if ("EMPTY_LIST".equals(referenceName)) {
        return "emptyList()";
      }
      else if ("EMPTY_MAP".equals(referenceName)) {
        return "emptyMap()";
      }
      else if ("EMPTY_SET".equals(referenceName)) {
        return "emptySet()";
      }
      else {
        throw new AssertionError("unknown collections field name: " + referenceName);
      }
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof PsiReferenceExpression referenceExpression)) {
        return;
      }
      final String newMethodCallText = getCollectionsMethodCallText(referenceExpression);
      PsiReplacementUtil.replaceExpression(referenceExpression, "java.util." + newMethodCallText);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CollectionsFieldAccessReplaceableByMethodCallVisitor();
  }

  @Override
  public boolean shouldInspect(@NotNull PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file);
  }

  private static class CollectionsFieldAccessReplaceableByMethodCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      @NonNls final String name = expression.getReferenceName();
      @NonNls final String replacement;
      if ("EMPTY_LIST".equals(name)) {
        replacement = "emptyList()";
      }
      else if ("EMPTY_MAP".equals(name)) {
        replacement = "emptyMap()";
      }
      else if ("EMPTY_SET".equals(name)) {
        replacement = "emptySet()";
      }
      else {
        return;
      }
      final PsiElement target = expression.resolve();
      if (!(target instanceof PsiField field)) {
        return;
      }
      final PsiClass containingClass = field.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final String qualifiedName = containingClass.getQualifiedName();
      if (!CommonClassNames.JAVA_UTIL_COLLECTIONS.equals(qualifiedName)) {
        return;
      }
      final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(expression);
      if (parent instanceof PsiExpression && ComparisonUtils.isEqualityComparison((PsiExpression)parent)) {
        return;
      }
      registerError(expression, expression, replacement);
    }
  }
}