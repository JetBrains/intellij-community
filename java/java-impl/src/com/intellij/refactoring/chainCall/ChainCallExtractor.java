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
package com.intellij.refactoring.chainCall;

import com.intellij.codeInspection.LambdaCanBeMethodReferenceInspection;
import com.intellij.codeInspection.util.OptionalUtil;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.tryCast;

public interface ChainCallExtractor {
  ExtensionPointName<ChainCallExtractor> KEY = ExtensionPointName.create("com.intellij.java.refactoring.chainCallExtractor");

  /**
   * Returns true if the mapping chain call can be extracted from lambda passed to the given call.
   *
   * @param call call to check
   * @param expression expression to extract
   * @param expressionType resulting type of the extracted expression
   * @return true if this extractor can create a mapping step from given expression
   */
  boolean canExtractChainCall(@NotNull PsiMethodCallExpression call, PsiExpression expression, PsiType expressionType);

  /**
   * Returns chain call string representation (starting from "." like {@code .map(x -> x.getName())}).
   *
   * @param variable variable to be used as chain call input
   * @param expression mapping expression
   * @param expressionType target expression type
   * @return chain call. Result is correct only if {@link #canExtractChainCall} was checked before
   * for given expression and expressionType
   */
  default String buildChainCall(PsiVariable variable, PsiExpression expression, PsiType expressionType) {
    if(expression instanceof PsiArrayInitializerExpression) {
      expression = RefactoringUtil.convertInitializerToNormalExpression(expression, expressionType);
    }
    String typeArgument = OptionalUtil.getMapTypeArgument(expression, expressionType);
    return "." + typeArgument + getMethodName(variable, expression, expressionType) +
           "(" + variable.getName() + "->" + expression.getText() + ")";
  }

  /**
   * Returns a name of the method to be extracted
   *
   * @param variable variable to be used as chain call input
   * @param expression mapping expression
   * @param expressionType target expression type
   * @return chain call. Result is correct only if {@link #canExtractChainCall} was checked before
   * for given expression and expressionType
   */
  String getMethodName(PsiVariable variable, PsiExpression expression, PsiType expressionType);

  /**
   * Returns new name for the existing call from which element is to be extracted. Sometimes it should be renamed
   * depending on the new element type (e.g. {@code map} to {@code mapToObj}).
   *
   * @param call call to fix the name
   * @param newElementType new element type (to be passed as lambda parameter)
   * @return new call name. Default implementation returns current name.
   */
  default String fixCallName(PsiMethodCallExpression call, PsiType newElementType) {
    return call.getMethodExpression().getReferenceName();
  }

  @Contract("null, _, _ -> null")
  static ChainCallExtractor findExtractor(@Nullable PsiLambdaExpression lambda, PsiExpression expression, PsiType targetType) {
    if (lambda == null) return null;
    PsiParameterList parameters = lambda.getParameterList();
    if (parameters.getParametersCount() != 1) return null;
    PsiExpressionList args = tryCast(lambda.getParent(), PsiExpressionList.class);
    if (args == null || args.getExpressionCount() != 1) return null;
    PsiParameter parameter = parameters.getParameters()[0];
    if (ExpressionUtils.isReferenceTo(expression, parameter) && parameter.getType().equals(targetType)) {
      // No-op extraction is useless
      return null;
    }
    PsiMethodCallExpression call = tryCast(args.getParent(), PsiMethodCallExpression.class);
    if (call == null) return null;
    for(ChainCallExtractor extractor : KEY.getExtensions()) {
      if(extractor.canExtractChainCall(call, expression, targetType) &&
         StringUtil.isNotEmpty(extractor.getMethodName(parameter, expression, targetType))) {
        return extractor;
      }
    }
    return null;
  }

  static PsiLambdaExpression extractMappingStep(@NotNull Project project, PsiLocalVariable variable) {
    if (variable == null) return null;
    String name = variable.getName();
    if (name == null) return null;
    PsiExpression initializer = variable.getInitializer();
    if (initializer == null) return null;
    PsiLambdaExpression lambda = PsiTreeUtil.getParentOfType(variable, PsiLambdaExpression.class);
    if (lambda == null) return null;
    PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(lambda, PsiMethodCallExpression.class);
    if (call == null) return null;
    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    if (qualifier == null) return null;
    String methodName = call.getMethodExpression().getReferenceName();
    if (methodName == null) return null;
    PsiParameter parameter = ArrayUtil.getFirstElement(lambda.getParameterList().getParameters());
    if (parameter == null) return null;
    ChainCallExtractor extractor = findExtractor(lambda, initializer, variable.getType());
    if (extractor == null) return null;
    String newMethodName = extractor.fixCallName(call, variable.getType());

    String mapOperation = extractor.buildChainCall(parameter, initializer, variable.getType());
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    if (!mapOperation.isEmpty()) {
      qualifier = (PsiExpression)qualifier.replace(factory.createExpressionFromText(qualifier.getText() + mapOperation, qualifier));
    }
    parameter = (PsiParameter)parameter.replace(factory.createParameter(variable.getName(), variable.getType(), parameter));
    variable.delete();
    LambdaRefactoringUtil.simplifyToExpressionLambda(lambda);
    PsiTypeElement typeElement = parameter.getTypeElement();
    if (typeElement != null) {
      typeElement.delete();
    }
    ExpressionUtils.bindCallTo(call, newMethodName);
    if (qualifier instanceof PsiMethodCallExpression) {
      PsiLambdaExpression newLambda =
        tryCast(ArrayUtil.getFirstElement(((PsiMethodCallExpression)qualifier).getArgumentList().getExpressions()),
                PsiLambdaExpression.class);
      if (newLambda != null) {
        LambdaCanBeMethodReferenceInspection.replaceLambdaWithMethodReference(newLambda);
      }
    }
    call = (PsiMethodCallExpression)CodeStyleManager.getInstance(project).reformat(call);
    return tryCast(ArrayUtil.getFirstElement(call.getArgumentList().getExpressions()), PsiLambdaExpression.class);
  }
}
