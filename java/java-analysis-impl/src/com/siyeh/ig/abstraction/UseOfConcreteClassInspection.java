/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CloneUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.LibraryUtil;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.codeInspection.options.OptPane.*;

public final class UseOfConcreteClassInspection extends BaseInspection {
  private static final CallMatcher OBJECT_GET_CLASS =
    CallMatcher.exactInstanceCall(CommonClassNames.JAVA_LANG_OBJECT, "getClass").parameterCount(0);
  private static final CallMatcher CLASS_CAST =
    CallMatcher.exactInstanceCall(CommonClassNames.JAVA_LANG_CLASS, "cast").parameterCount(1);

  @SuppressWarnings("PublicField")
  public boolean ignoreAbstractClasses = false;
  public boolean ignoreRecords = true;
  public boolean reportMethodReturns = true;
  public boolean reportMethodParameters = true;
  public boolean reportLocalVariables = true;
  public boolean reportStaticFields = true;
  public boolean reportInstanceFields = true;
  public boolean reportInstanceOf = true;
  public boolean reportCast = true;

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return (String)infos[0];
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreAbstractClasses", InspectionGadgetsBundle.message("use.of.concrete.class.option.ignore.abstract")),
      checkbox("ignoreRecords", InspectionGadgetsBundle.message("use.of.concrete.class.option.ignore.records")),
      checkbox("reportMethodReturns", InspectionGadgetsBundle.message("use.of.concrete.class.option.report.method.returns")),
      checkbox("reportMethodParameters", InspectionGadgetsBundle.message("use.of.concrete.class.option.report.parameter")),
      checkbox("reportLocalVariables", InspectionGadgetsBundle.message("use.of.concrete.class.option.report.local.variable")),
      checkbox("reportStaticFields", InspectionGadgetsBundle.message("use.of.concrete.class.option.report.static.fields")),
      checkbox("reportInstanceFields", InspectionGadgetsBundle.message("use.of.concrete.class.option.report.instance.fields")),
      checkbox("reportInstanceOf", InspectionGadgetsBundle.message("use.of.concrete.class.option.report.instanceof")),
      checkbox("reportCast", InspectionGadgetsBundle.message("use.of.concrete.class.option.report.cast")));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MethodReturnOfConcreteClassVisitor();
  }

  @Contract("null -> false")
  private boolean typeIsConcreteClass(@Nullable PsiTypeElement typeElement) {
    if (typeElement == null || typeElement.isInferredType()) {
      return false;
    }
    final PsiType type = typeElement.getType();
    return typeIsConcreteClass(type);
  }

  @Contract("null -> false")
  private boolean typeIsConcreteClass(@Nullable PsiType type) {
    if (type == null) {
      return false;
    }
    final PsiType baseType = type.getDeepComponentType();
    final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(baseType);
    if (aClass == null) {
      return false;
    }
    if (ignoreAbstractClasses && aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return false;
    }
    if (ignoreRecords && aClass.isRecord()) {
      return false;
    }
    if (aClass.isInterface() || aClass.isEnum() || aClass.isAnnotationType()) {
      return false;
    }
    if (aClass instanceof PsiTypeParameter) {
      return false;
    }
    return !LibraryUtil.classIsInLibrary(aClass);
  }

  private class MethodReturnOfConcreteClassVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      if (!reportMethodReturns) return;
      if (method.isConstructor()) return;
      final PsiTypeElement typeElement = method.getReturnTypeElement();
      if (!typeIsConcreteClass(typeElement)) return;
      registerError(typeElement, InspectionGadgetsBundle.message("method.return.concrete.class.problem.descriptor"));
    }

    @Override
    public void visitParameter(@NotNull PsiParameter parameter) {
      PsiElement scope = parameter.getDeclarationScope();
      boolean methodParameter = scope instanceof PsiMethod;
      boolean catchParameter = scope instanceof PsiCatchSection;
      boolean report = methodParameter && reportMethodParameters ||
                       !methodParameter && !catchParameter && reportLocalVariables;
      if (!report) return;
      if (parameter instanceof PsiPatternVariable) {
        // Will be reported in instanceof check
        return;
      }
      final PsiTypeElement typeElement = parameter.getTypeElement();
      if (!typeIsConcreteClass(typeElement)) return;
      final String variableName = parameter.getName();
      registerError(typeElement, InspectionGadgetsBundle.message(
        methodParameter ? "concrete.class.method.parameter.problem.descriptor" :
        "local.variable.of.concrete.class.problem.descriptor", variableName));
    }

    @Override
    public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
      if (!reportLocalVariables) return;
      final PsiTypeElement typeElement = variable.getTypeElement();
      if (!typeIsConcreteClass(typeElement)) return;
      PsiMethod method = PsiTreeUtil.getParentOfType(typeElement, PsiMethod.class, true, PsiClass.class, PsiLambdaExpression.class);
      if (MethodUtils.isEquals(method)) return;
      registerError(typeElement, InspectionGadgetsBundle.message(
        "local.variable.of.concrete.class.problem.descriptor", variable.getName()));
    }

    @Override
    public void visitField(@NotNull PsiField field) {
      boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
      boolean report = isStatic && reportStaticFields ||
                       !isStatic && reportInstanceFields;
      if (!report) return;
      final PsiTypeElement typeElement = field.getTypeElement();
      if (!typeIsConcreteClass(typeElement)) return;
      final String variableName = field.getName();
      registerError(typeElement, InspectionGadgetsBundle.message(
        isStatic ? "static.variable.of.concrete.class.problem.descriptor"
                 : "instance.variable.of.concrete.class.problem.descriptor", variableName));
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
      if (reportInstanceOf && OBJECT_GET_CLASS.test(call)) {
        PsiExpression other = ExpressionUtils.getExpressionComparedTo(call);
        if (other instanceof PsiClassObjectAccessExpression) {
          PsiTypeElement typeElement = ((PsiClassObjectAccessExpression)other).getOperand();
          if (typeIsConcreteClass(typeElement)) {
            PsiMethod method = PsiTreeUtil.getParentOfType(call, PsiMethod.class, true, PsiClass.class, PsiLambdaExpression.class);
            if (!MethodUtils.isEquals(method)) {
              registerError(typeElement, InspectionGadgetsBundle.message("instanceof.concrete.class.equality.problem.descriptor"));
            }
          }
        }
      }
      else if (reportCast && CLASS_CAST.test(call)) {
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        if (qualifier != null) {
          PsiType qualifierType = qualifier.getType();
          PsiType targetClass = PsiUtil.substituteTypeParameter(qualifierType, CommonClassNames.JAVA_LANG_CLASS, 0, false);
          if (!typeIsConcreteClass(targetClass)) return;
          PsiMethod method = PsiTreeUtil.getParentOfType(call, PsiMethod.class, true, PsiClass.class, PsiLambdaExpression.class);
          if (!MethodUtils.isEquals(method) && !CloneUtils.isClone(method)) {
            registerMethodCallError(call, InspectionGadgetsBundle
              .message("cast.to.concrete.class.problem.descriptor", targetClass.getPresentableText()));
          }
        }
      }
    }

    @Override
    public void visitTypeTestPattern(@NotNull PsiTypeTestPattern pattern) {
      PsiTypeElement typeElement = pattern.getCheckType();
      processInstanceOfCheck(typeElement, InspectionGadgetsBundle.message("instanceof.concrete.class.pattern.problem.descriptor"));
    }

    @Override
    public void visitInstanceOfExpression(@NotNull PsiInstanceOfExpression expression) {
      super.visitInstanceOfExpression(expression);
      if (expression.getPattern() == null) {
        processInstanceOfCheck(expression.getCheckType(), InspectionGadgetsBundle.message("instanceof.concrete.class.problem.descriptor"));
      }
    }

    private void processInstanceOfCheck(@Nullable PsiTypeElement typeElement, @NotNull @Nls String message) {
      if (!reportInstanceOf) return;
      if (!typeIsConcreteClass(typeElement)) return;
      PsiMethod method = PsiTreeUtil.getParentOfType(typeElement, PsiMethod.class, true, PsiClass.class, PsiLambdaExpression.class);
      if (MethodUtils.isEquals(method)) return;
      registerError(typeElement, message);
    }

    @Override
    public void visitTypeCastExpression(@NotNull PsiTypeCastExpression expression) {
      if (!reportCast) return;
      final PsiTypeElement typeElement = expression.getCastType();
      if (!typeIsConcreteClass(typeElement)) return;
      final PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, true, PsiClass.class, PsiLambdaExpression.class);
      if (MethodUtils.isEquals(method) || CloneUtils.isClone(method)) return;
      registerError(typeElement, InspectionGadgetsBundle
        .message("cast.to.concrete.class.problem.descriptor", typeElement.getType().getPresentableText()));
    }
  }
}