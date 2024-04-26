// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.testFrameworks;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.DeclarationSearchUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * @author Bas Leijdekkers
 */
public final class MisorderedAssertEqualsArgumentsInspection extends BaseInspection {

  @NonNls
  private static final Set<String> methodNames =
    ContainerUtil.newHashSet("assertEquals", "assertEqualsNoOrder", "assertNotEquals", "assertArrayEquals", "assertSame",
                             "assertNotSame", "failNotSame", "failNotEquals");

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("misordered.assert.equals.arguments.problem.descriptor");
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new FlipArgumentsFix();
  }

  private class FlipArgumentsFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("misordered.assert.equals.arguments.flip.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement methodNameIdentifier, @NotNull ModPsiUpdater updater) {
      final PsiElement parent = methodNameIdentifier.getParent();
      if (parent == null) {
        return;
      }
      final PsiMethodCallExpression callExpression = (PsiMethodCallExpression)parent.getParent();
      if (callExpression == null) {
        return;
      }

      final AssertHint hint = createAssertHint(callExpression);
      if (hint == null) {
        return;
      }
      final PsiExpression expectedArgument = hint.getExpected();
      final PsiExpression actualArgument = hint.getActual();
      final PsiElement copy = expectedArgument.copy();
      expectedArgument.replace(actualArgument);
      actualArgument.replace(copy);
    }
  }

  AssertHint createAssertHint(@NotNull PsiMethodCallExpression expression) {
    return AssertHint.create(expression, methodName -> methodNames.contains(methodName) ? 2 : null);
  }

  static boolean looksLikeExpectedArgument(PsiExpression expression, ParameterPosition parameterPosition) {
    if (expression == null) {
      return false;
    }

    final Ref<Boolean> expectedArgument = Ref.create(Boolean.TRUE);
    final List<PsiExpression> expressions = new SmartList<>();
    expressions.add(expression);
    while (!expressions.isEmpty()) {
      expressions.remove(expressions.size() - 1).accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitReferenceExpression(@NotNull PsiReferenceExpression referenceExpression) {
          if (!expectedArgument.get().booleanValue()) {
            return;
          }
          super.visitReferenceExpression(referenceExpression);
          final PsiElement target = referenceExpression.resolve();
          if (target instanceof PsiEnumConstant || target instanceof PsiClass) {
            return;
          }
          else if (target instanceof PsiField field) {
            if (field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.FINAL)) {
              return;
            }
          }
          else if (target instanceof PsiParameter parameter) {
            if ("expected".equals(parameter.getName())) {
              return;
            }
            expectedArgument.set(Boolean.FALSE);
            return;
          }
          else if (target instanceof PsiLocalVariable) {
            final PsiVariable variable = (PsiLocalVariable)target;
            final PsiCodeBlock block = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
            if (block == null) {
              return; // broken code
            }
            final PsiExpression definition = DeclarationSearchUtils.findDefinition(referenceExpression, variable);
            if (definition == null) {
              expectedArgument.set(Boolean.FALSE);
              return;
            }
            if (PsiUtil.isConstantExpression(definition) || PsiTypes.nullType().equals(definition.getType())) {
              return;
            }
            final PsiElement[] refs = DefUseUtil.getRefs(block, variable, definition);
            final int offset = referenceExpression.getTextOffset();
            for (PsiElement ref : refs) {
              if (ref.getTextOffset() < offset) {
                expectedArgument.set(Boolean.FALSE);
                return;
              }
            }
            expressions.add(definition);
          }
          else if (target instanceof PsiMethod method && parameterPosition == ParameterPosition.ACTUAL) {
            if (!"expected".equals(method.getName())) {
              expectedArgument.set(Boolean.FALSE);
            }
          }
          if (!(target instanceof PsiCompiledElement)) {
            expectedArgument.set(Boolean.FALSE);
          }
        }
      });
    }
    return expectedArgument.get().booleanValue();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MisorderedAssertEqualsParametersVisitor();
  }

  private class MisorderedAssertEqualsParametersVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final AssertHint hint = createAssertHint(expression);
      if (hint == null) {
        return;
      }
      if (looksLikeExpectedArgument(hint.getExpected(), ParameterPosition.EXPECTED) ||
          !looksLikeExpectedArgument(hint.getActual(), ParameterPosition.ACTUAL)) {
        return;
      }
      registerMethodCallError(expression);
    }
  }

  private enum ParameterPosition {
    EXPECTED,
    ACTUAL
  }
}
