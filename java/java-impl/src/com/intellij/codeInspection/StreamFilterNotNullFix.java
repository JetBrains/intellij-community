/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.psiutils.StreamApiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.tryCast;

public class StreamFilterNotNullFix implements LocalQuickFix, HighPriorityAction {
  @Override
  @NotNull
  public String getFamilyName() {
    return InspectionsBundle.message("inspection.data.flow.filter.notnull.quickfix");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiFunctionalExpression function = findFunction(descriptor.getStartElement());
    if (function == null) return;
    PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(function, PsiMethodCallExpression.class);
    if (call == null) return;
    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    if (qualifier == null) return;
    String name = suggestVariableName(function, qualifier);
    // We create first lambda, then convert to method reference as user code style might be set to prefer lambdas
    PsiExpression replacement = JavaPsiFacade.getElementFactory(project)
      .createExpressionFromText(qualifier.getText() + ".filter(" + name + "->" + name + "!=null)", qualifier);
    PsiMethodCallExpression result = (PsiMethodCallExpression)qualifier.replace(replacement);
    LambdaCanBeMethodReferenceInspection.replaceAllLambdasWithMethodReferences(result.getArgumentList());
  }

  @NotNull
  private static String suggestVariableName(@NotNull PsiFunctionalExpression function, @NotNull PsiExpression qualifier) {
    String name = null;
    if (function instanceof PsiLambdaExpression) {
      PsiParameter parameter = ArrayUtil.getFirstElement(((PsiLambdaExpression)function).getParameterList().getParameters());
      if (parameter != null) {
        name = parameter.getName();
      }
    }
    PsiType type = StreamApiUtil.getStreamElementType(qualifier.getType());
    JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(function.getProject());
    SuggestedNameInfo info = javaCodeStyleManager.suggestVariableName(VariableKind.PARAMETER, name, null, type, true);
    name = ArrayUtil.getFirstElement(info.names);
    return javaCodeStyleManager.suggestUniqueVariableName(name == null ? "obj" : name, qualifier, false);
  }

  @Nullable
  private static PsiFunctionalExpression findFunction(PsiElement reference) {
    if (reference instanceof PsiFunctionalExpression) {
      return (PsiFunctionalExpression)reference;
    }
    if (reference instanceof PsiIdentifier) {
      // in "str.trim()" go from "trim" to "str"
      reference = reference.getParent();
      if (reference instanceof PsiReferenceExpression) {
        reference = PsiUtil.skipParenthesizedExprDown(((PsiReferenceExpression)reference).getQualifierExpression());
      }
    }
    if (reference instanceof PsiReferenceExpression) {
      PsiParameter parameter = tryCast(((PsiReferenceExpression)reference).resolve(), PsiParameter.class);
      if (parameter == null) return null;
      PsiParameterList parameterList = tryCast(parameter.getParent(), PsiParameterList.class);
      if (parameterList == null || parameterList.getParametersCount() != 1) return null;
      return tryCast(parameterList.getParent(), PsiLambdaExpression.class);
    }
    return null;
  }

  public static StreamFilterNotNullFix makeFix(PsiElement reference) {
    PsiFunctionalExpression fn = findFunction(reference);
    if (fn == null) return null;
    PsiExpressionList args = tryCast(PsiUtil.skipParenthesizedExprUp(fn.getParent()), PsiExpressionList.class);
    if (args == null || args.getExpressions().length != 1) return null;
    PsiMethodCallExpression call = tryCast(args.getParent(), PsiMethodCallExpression.class);
    if (call == null) return null;
    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    if (qualifier == null || !InheritanceUtil.isInheritor(qualifier.getType(), CommonClassNames.JAVA_UTIL_STREAM_STREAM)) return null;
    return new StreamFilterNotNullFix();
  }
}
