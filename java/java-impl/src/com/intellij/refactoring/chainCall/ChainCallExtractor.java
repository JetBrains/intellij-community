// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.chainCall;

import com.intellij.codeInspection.LambdaCanBeMethodReferenceInspection;
import com.intellij.codeInspection.util.OptionalRefactoringUtil;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.tryCast;

/**
 * An extension to support intermediate mapping steps extraction
 * in fluent lambda chains.
 * <p>
 * For example, {@code stream.map(x -> x.y().z())} can be automatically refactored to
 * {@code stream.map(x -> x.y()).map(y -> y.z())}. Implement this extension
 * to plug custom fluent APIs to existing refactorings.
 */
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
  @Contract(pure = true)
  boolean canExtractChainCall(@NotNull PsiMethodCallExpression call, @NotNull PsiExpression expression, @Nullable PsiType expressionType);

  /**
   * Returns chain call string representation (starting from "." like {@code .map(x -> x.getName())}).
   *
   * @param variable variable to be used as chain call input
   * @param expression mapping expression
   * @param expressionType target expression type
   * @return chain call. Result is correct only if {@link #canExtractChainCall} was checked before
   * for given expression and expressionType
   */
  @Contract(pure = true)
  default @NonNls String buildChainCall(PsiVariable variable, PsiExpression expression, PsiType expressionType) {
    if(expression instanceof PsiArrayInitializerExpression) {
      expression = CommonJavaRefactoringUtil.convertInitializerToNormalExpression(expression, expressionType);
    }
    String typeArgument = OptionalRefactoringUtil.getMapTypeArgument(expression, expressionType);
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
  @Contract(pure = true)
  @NonNls
  String getMethodName(PsiVariable variable, PsiExpression expression, PsiType expressionType);

  /**
   * Returns new name for the existing call from which element is to be extracted. Sometimes it should be renamed
   * depending on the new element type (e.g. {@code map} to {@code mapToObj}).
   *
   * @param call call to fix the name
   * @param newElementType new element type (to be passed as lambda parameter)
   * @return new call name. Default implementation returns current name.
   */
  @Contract(pure = true)
  default String fixCallName(PsiMethodCallExpression call, PsiType newElementType) {
    return call.getMethodExpression().getReferenceName();
  }

  /**
   * @param lambda lambda expression to find the suitable extractor for
   * @param expression a subexpression from the lambda body that should be extracted
   * @param targetType type of the new intermediate lambda parameter
   * @return a ChainCallExtractor that supports extracting given subexpression from the given lambda; null if there's no such an extractor
   * registered.
   */
  @Contract(value = "null, _, _ -> null", pure = true)
  static ChainCallExtractor findExtractor(@Nullable PsiLambdaExpression lambda,
                                          @NotNull PsiExpression expression,
                                          @Nullable PsiType targetType) {
    if (lambda == null) return null;
    PsiParameterList parameters = lambda.getParameterList();
    if (parameters.getParametersCount() != 1) return null;
    PsiExpressionList args = tryCast(PsiUtil.skipParenthesizedExprUp(lambda.getParent()), PsiExpressionList.class);
    if (args == null || args.getExpressionCount() != 1) return null;
    PsiParameter parameter = parameters.getParameter(0);
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
