// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInsight.options.JavaClassValidator;
import com.intellij.codeInspection.AddToInspectionOptionListFix;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.module.JdkApiCompatibilityService;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.OrderedSet;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.OrderedBinaryExpression;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.codeInspection.options.OptPane.*;

public final class SizeReplaceableByIsEmptyInspection extends BaseInspection {
  @SuppressWarnings("PublicField")
  public boolean ignoreNegations = false;
  @SuppressWarnings("PublicField")
  public OrderedSet<String> ignoredTypes = new OrderedSet<>();

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("expression.can.be.replaced.problem.descriptor", infos[0]);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      stringList("ignoredTypes", InspectionGadgetsBundle.message("options.label.ignored.classes"),
                 new JavaClassValidator().withTitle(InspectionGadgetsBundle.message("choose.class.type.to.ignore"))),
      checkbox("ignoreNegations", InspectionGadgetsBundle.message("size.replaceable.by.isempty.negation.ignore.option"))
    );
  }

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    final List<LocalQuickFix> result = new SmartList<>();
    final PsiExpression expression = (PsiExpression)infos[1];
    final String methodName = (String)infos[2];
    final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(expression.getType());
    if (aClass != null) {
      final String name = aClass.getQualifiedName();
      if (name != null) {
        String message = InspectionGadgetsBundle.message("size.replaceable.by.isempty.fix.ignore.calls", methodName, name);
        result.add(new AddToInspectionOptionListFix<>(this, message, name, tool -> tool.ignoredTypes));
      }
    }
    result.add(new SizeReplaceableByIsEmptyFix());
    return result.toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  protected static class SizeReplaceableByIsEmptyFix extends PsiUpdateModCommandQuickFix {

    @Override
    public @NotNull String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "isEmpty()");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      if (!(startElement instanceof PsiExpression expression)) return;
      final OrderedBinaryExpression<PsiMethodCallExpression, PsiExpression> orderedBinaryExpression = OrderedBinaryExpression.from(expression, PsiMethodCallExpression.class);
      if (orderedBinaryExpression == null) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression = orderedBinaryExpression.getFirstOperand();
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (qualifierExpression == null) {
        return;
      }
      CommentTracker commentTracker = new CommentTracker();
      @NonNls String newExpression = commentTracker.text(qualifierExpression);
      final Object object = ExpressionUtils.computeConstantExpression(orderedBinaryExpression.getSecondOperand());
      if (!(object instanceof Integer) && !(object instanceof Long)) {
        return;
      }
      int comparedValue = ((Number)object).intValue();
      if ((comparedValue == 0 && !JavaTokenType.EQEQ.equals(orderedBinaryExpression.getTokenType())) || (comparedValue == 1 && !JavaTokenType.LT.equals(orderedBinaryExpression.getTokenType()))) {
        newExpression = '!' + newExpression;
      }
      newExpression += ".isEmpty()";

      PsiReplacementUtil.replaceExpression(expression, newExpression, commentTracker);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SizeReplaceableByIsEmptyVisitor();
  }

  private class SizeReplaceableByIsEmptyVisitor extends BaseInspectionVisitor {
    private static boolean isLanguageLevelCompatible(PsiElement element, PsiMethod method) {
      LanguageLevel languageLevel = PsiUtil.getLanguageLevel(element);
      LanguageLevel firstCompatibleLanguageLevel = JdkApiCompatibilityService.getInstance().firstCompatibleLanguageLevel(method, languageLevel);
      return firstCompatibleLanguageLevel == null || firstCompatibleLanguageLevel.isLessThan(languageLevel);
    }

    @Override
    public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      if (!ComparisonUtils.isComparison(expression)) {
        return;
      }
      final OrderedBinaryExpression<PsiMethodCallExpression, PsiExpression> orderedBinaryExpression = OrderedBinaryExpression.from(expression, PsiMethodCallExpression.class);
      if (orderedBinaryExpression == null) {
        return;
      }
      final PsiMethodCallExpression callExpression = orderedBinaryExpression.getFirstOperand();
      @NonNls String isEmptyCall = null;
      final PsiReferenceExpression methodExpression = callExpression.getMethodExpression();
      final String referenceName = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.SIZE.equals(referenceName) && !HardcodedMethodConstants.LENGTH.equals(referenceName)) {
        return;
      }
      if (!callExpression.getArgumentList().isEmpty()) {
        return;
      }
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (qualifierExpression == null) {
        return;
      }
      final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(qualifierExpression.getType());
      if (aClass == null || PsiTreeUtil.isAncestor(aClass, callExpression, true)) {
        return;
      }
      for (String ignoredType : ignoredTypes) {
        if (InheritanceUtil.isInheritor(aClass, ignoredType)) {
          return;
        }
      }
      for (PsiMethod method : aClass.findMethodsByName("isEmpty", true)) {
        final PsiParameterList parameterList = method.getParameterList();
        if (parameterList.isEmpty() && isLanguageLevelCompatible(expression, method)) {
          isEmptyCall = qualifierExpression.getText() + ".isEmpty()";
          break;
        }
      }
      if (isEmptyCall == null) {
        return;
      }
      final Object object = ExpressionUtils.computeConstantExpression(orderedBinaryExpression.getSecondOperand());
      if (!(object instanceof Integer) && !(object instanceof Long)) {
        return;
      }
      int comparedValue = ((Number)object).intValue();
      if (comparedValue == 0) {
        final IElementType tokenType = orderedBinaryExpression.getTokenType();
        if (JavaTokenType.EQEQ.equals(tokenType)) {
          registerError(expression, isEmptyCall, qualifierExpression, referenceName);
        }
        if (ignoreNegations) {
          return;
        }
        if (JavaTokenType.NE.equals(tokenType) || JavaTokenType.GT.equals(tokenType)) {
          registerError(expression, '!' + isEmptyCall, qualifierExpression, referenceName);
        }
      }
      else if (comparedValue == 1) {
        final IElementType tokenType = orderedBinaryExpression.getTokenType();
        if (JavaTokenType.LT.equals(tokenType)) {
          registerError(expression, isEmptyCall, qualifierExpression, referenceName);
        }
        if (ignoreNegations) {
          return;
        }
        if (JavaTokenType.GE.equals(tokenType)) {
          registerError(expression, '!' + isEmptyCall, qualifierExpression, referenceName);
        }
      }
    }
  }
}