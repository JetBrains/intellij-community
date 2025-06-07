/*
 * Copyright 2006-2025 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.AddTypeCastFix;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public final class PrimitiveArrayArgumentToVariableArgMethodInspection extends BaseInspection {

  @Override
  public @NotNull String getID() {
    return "PrimitiveArrayArgumentToVarargsMethod";
  }

  @Override
  public @NotNull String getAlternativeID() {
    return "PrimitiveArrayArgumentToVariableArgMethod"; // keep old suppression working
  }

  @Override
  public @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("primitive.array.argument.to.var.arg.method.problem.descriptor");
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
  protected @NotNull LocalQuickFix buildFix(Object... infos) {
    final PsiExpression argument = (PsiExpression)infos[0];
    Project project = argument.getProject();
    final PsiType type = PsiType.getJavaLangObject(PsiManager.getInstance(project), GlobalSearchScope.allScope(project));
    return LocalQuickFix.from(new AddTypeCastFix(type, argument));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PrimitiveArrayArgumentToVariableArgVisitor();
  }

  private static class PrimitiveArrayArgumentToVariableArgVisitor extends BaseInspectionVisitor {

    @Override
    public void visitEnumConstant(@NotNull PsiEnumConstant enumConstant) {
      super.visitEnumConstant(enumConstant);
      visitCall(enumConstant);
    }

    @Override
    public void visitCallExpression(@NotNull PsiCallExpression callExpression) {
      super.visitCallExpression(callExpression);
      visitCall(callExpression);
    }

    private void visitCall(@NotNull PsiCall call) {
      final PsiExpressionList argumentList = call.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        return;
      }
      final PsiExpression lastArgument = arguments[arguments.length - 1];
      if (!isConfusingArgument(call, lastArgument, arguments)) {
        return;
      }
      registerError(lastArgument, lastArgument);
    }
  }

  public static boolean isConfusingArgument(@NotNull PsiCall call, PsiExpression argument, PsiExpression[] arguments) {
    if (!isPrimitiveArrayType(argument.getType())) {
      return false;
    }
    final JavaResolveResult result = call.resolveMethodGenerics();
    final PsiMethod method = (PsiMethod)result.getElement();
    if (method == null || !method.isVarArgs()
        || AnnotationUtil.isAnnotated(method, CommonClassNames.JAVA_LANG_INVOKE_MH_POLYMORPHIC, 0)) {
      return false;
    }
    final PsiParameterList parameterList = method.getParameterList();
    int count = parameterList.getParametersCount();
    if (count != arguments.length) {
      return false;
    }
    final PsiParameter lastParameter = parameterList.getParameter(count - 1);
    if (lastParameter == null || !lastParameter.isVarArgs()) {
      return false;
    }
    final PsiEllipsisType parameterType = (PsiEllipsisType)lastParameter.getType();
    final PsiType componentType = parameterType.getComponentType();
    if (!TypeUtils.isJavaLangObject(result.getSubstitutor().substitute(componentType))) {
      return false;
    }
    return true;
  }

  private static boolean isPrimitiveArrayType(PsiType type) {
    return type instanceof PsiArrayType arrayType && TypeConversionUtil.isPrimitiveAndNotNull(arrayType.getComponentType());
  }
}