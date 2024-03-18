// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.options.JavaClassValidator;
import com.intellij.codeInspection.AddToInspectionOptionListFix;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.OrderedSet;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.SuppressForTestsScopeFix;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.codeInspection.options.OptPane.pane;
import static com.intellij.codeInspection.options.OptPane.stringList;

/**
 * @author Bas Leijdekkers
 */
public final class ResultOfObjectAllocationIgnoredInspection extends BaseInspection {

  @SuppressWarnings("PublicField") public OrderedSet<String> ignoredClasses = new OrderedSet<>();

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      stringList("ignoredClasses", InspectionGadgetsBundle.message("options.label.ignored.classes"),
                 new JavaClassValidator().withTitle(
                          InspectionGadgetsBundle.message("result.of.object.allocation.ignored.options.chooserTitle"))));
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiElement context = (PsiElement)infos[0];
    return SuppressForTestsScopeFix.build(this, context);
  }

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    final List<LocalQuickFix> result = new SmartList<>();
    final PsiExpression expression = (PsiExpression)infos[0];
    final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(expression.getType());
    if (aClass != null) {
      final String name = aClass.getQualifiedName();
      if (name != null) {
        result.add(new AddToInspectionOptionListFix<>(this, InspectionGadgetsBundle.message("result.of.object.allocation.fix.name", name), 
                                                      name, tool -> tool.ignoredClasses));
      }
    }
    ContainerUtil.addIfNotNull(result, SuppressForTestsScopeFix.build(this, expression));
    return result.toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("result.of.object.allocation.ignored.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ResultOfObjectAllocationIgnoredVisitor();
  }

  private class ResultOfObjectAllocationIgnoredVisitor extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      if (!ExpressionUtils.isVoidContext(expression)) {
        return;
      }
      if (expression.isArrayCreation()) {
        return;
      }
      final PsiJavaCodeReferenceElement reference = expression.getClassOrAnonymousClassReference();
      if (reference == null) {
        return;
      }
      final PsiElement target = reference.resolve();
      if (!(target instanceof PsiClass aClass)) {
        return;
      }
      if (!(expression instanceof PsiAnonymousClass) && ignoredClasses.contains(aClass.getQualifiedName())) {
        return;
      }
      registerNewExpressionError(expression, expression);
    }
  }
}
