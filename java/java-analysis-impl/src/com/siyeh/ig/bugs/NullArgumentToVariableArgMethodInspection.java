/*
 * Copyright 2003-2025 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.impl.quickfix.AddTypeCastFix;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.function.Supplier;

public final class NullArgumentToVariableArgMethodInspection extends BaseInspection {

  @Override
  public @NotNull String getID() {
    return "ConfusingArgumentToVarargsMethod";
  }

  @Override
  public @NotNull String getAlternativeID() {
    return "NullArgumentToVariableArgMethod"; // old suppressions should keep working
  }

  @Override
  public @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("null.argument.to.var.arg.method.problem.descriptor");
  }

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    final PsiExpression argument = (PsiExpression)infos[0];
    final PsiType type1 = (PsiType)infos[1];
    final PsiType type2 = (PsiType)infos[2];
    return new LocalQuickFix[] {
      LocalQuickFix.from(new AddTypeCastFix(type1, argument)),
      LocalQuickFix.from(new AddTypeCastFix(type2, argument)),
    };
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.VARARGS);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NullArgumentToVariableArgVisitor();
  }

  /**
   * Checks if it's unclear from the first glance if a method is called as varargs or not
   * 
   * @return true iff {@code call} is varargs method call and {@code lastArgumentType} is {@code null} type or array type, 
   *              which is assignable from vararg parameter component type
   */
  public static boolean isSuspiciousVararg(PsiCall call, PsiType lastArgumentType, Supplier<? extends PsiMethod> methodSupplier) {
    return NullArgumentToVariableArgVisitor.getSuspiciousVarargType(call, lastArgumentType, methodSupplier) != null;
  }

  private static class NullArgumentToVariableArgVisitor extends BaseInspectionVisitor {

    @Override
    public void visitEnumConstant(@NotNull PsiEnumConstant enumConstant) {
      super.visitEnumConstant(enumConstant);
      visitCall(enumConstant);
    }

    @Override
    public void visitCallExpression(@NotNull PsiCallExpression call) {
      super.visitCallExpression(call);
      visitCall(call);
    }

    private static PsiArrayType getSuspiciousVarargType(PsiCall call, PsiType type, Supplier<? extends PsiMethod> resolver) {
      final boolean checkArray;
      if (PsiTypes.nullType().equals(type)) {
        checkArray = false;
      }
      else if (type instanceof PsiArrayType) {
        checkArray = true;
      }
      else {
        return null;
      }
      final PsiMethod method = resolver.get();
      if (method == null) {
        return null;
      }
      final PsiParameterList parameterList = method.getParameterList();
      PsiExpressionList argumentList = call.getArgumentList();
      if (argumentList == null || parameterList.getParametersCount() != argumentList.getExpressionCount()) {
        return null;
      }
      final PsiParameter[] parameters = parameterList.getParameters();
      final PsiParameter lastParameter = parameters[parameters.length - 1];
      if (!lastParameter.isVarArgs()) {
        return null;
      }
      final PsiType type1 = lastParameter.getType();
      if (!(type1 instanceof PsiEllipsisType ellipsisType)) {
        return null;
      }

      final PsiArrayType arrayType = (PsiArrayType)ellipsisType.toArrayType();
      final PsiType componentType = arrayType.getComponentType();
      if (checkArray) {
        if (!componentType.equals(TypeUtils.getObjectType(call))) {
          return null;
        }
        if (type.isAssignableFrom(arrayType) || !arrayType.isAssignableFrom(type)) {
          return null;
        }
      }
      return arrayType;
    }

    private void visitCall(PsiCall call) {
      final PsiExpressionList argumentList = call.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        return;
      }
      final PsiExpression lastArgument = arguments[arguments.length - 1];
      final PsiType type = lastArgument.getType();

      final JavaResolveResult resolveResult = call.resolveMethodGenerics();
      PsiArrayType arrayType = getSuspiciousVarargType(call, type, () -> (PsiMethod)resolveResult.getElement());
      if (arrayType == null) {
        return;
      }
      final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
      final PsiType componentType = substitutor.substitute(arrayType.getComponentType());
      final PsiType substitutedArrayType = substitutor.substitute(arrayType);
      registerError(lastArgument, lastArgument, componentType, substitutedArrayType);
    }
  }
}