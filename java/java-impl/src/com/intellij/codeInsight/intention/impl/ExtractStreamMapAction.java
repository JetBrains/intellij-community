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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInspection.LambdaCanBeMethodReferenceInspection;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.StreamApiUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

import static com.intellij.util.ObjectUtils.tryCast;

/**
 * @author Tagir Valeev
 */
public class ExtractStreamMapAction extends PsiElementBaseIntentionAction {
  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    PsiLocalVariable variable =
      PsiTreeUtil.getParentOfType(element, PsiLocalVariable.class, false, PsiStatement.class, PsiLambdaExpression.class);
    if (!isApplicable(variable)) return false;
    setText("Extract variable '" + Objects.requireNonNull(variable.getName()) + "' to separate stream step");
    return true;
  }

  @Contract("null -> false")
  private static boolean isApplicable(PsiLocalVariable variable) {
    if (variable == null || variable.getName() == null) return false;
    if (!StreamApiUtil.isSupportedStreamElement(variable.getType())) return false;
    PsiExpression initializer = variable.getInitializer();
    if (initializer == null) return false;
    PsiDeclarationStatement declaration = tryCast(variable.getParent(), PsiDeclarationStatement.class);
    if (declaration == null || declaration.getDeclaredElements().length != 1) return false;
    PsiCodeBlock block = tryCast(declaration.getParent(), PsiCodeBlock.class);
    if (block == null) return false;
    PsiLambdaExpression lambda = tryCast(block.getParent(), PsiLambdaExpression.class);
    if (lambda == null) return false;
    PsiParameterList parameters = lambda.getParameterList();
    if (parameters.getParametersCount() != 1) return false;
    PsiExpressionList args = tryCast(lambda.getParent(), PsiExpressionList.class);
    if (args == null || args.getExpressions().length != 1) return false;
    PsiMethodCallExpression call = tryCast(args.getParent(), PsiMethodCallExpression.class);
    if (call == null ||
        !InlineStreamMapAction.NEXT_METHODS.contains(call.getMethodExpression().getReferenceName()) ||
        call.getMethodExpression().getQualifierExpression() == null) {
      return false;
    }
    PsiMethod method = call.resolveMethod();
    if (method == null ||
        method.getParameterList().getParametersCount() != 1 ||
        !InheritanceUtil.isInheritor(method.getContainingClass(), CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM)) {
      return false;
    }
    PsiParameter parameter = parameters.getParameters()[0];
    if (ExpressionUtils.isReferenceTo(initializer, parameter) && parameter.getType().equals(variable.getType())) {
      // If conversion is applied in this case, then extracted previous step will be silently removed.
      // While this is correct, it may confuse the user. Having "Local variable is redundant" warning with "inline" fix is enough here.
      return false;
    }
    if (method.getName().startsWith("flatMap")) {
      PsiType outType = StreamApiUtil.getStreamElementType(call.getType());
      // flatMap from primitive type works only if the stream element type matches
      if (variable.getType() instanceof PsiPrimitiveType && !variable.getType().equals(outType)) return false;
    }
    return ReferencesSearch.search(parameter).forEach(
      (Processor<PsiReference>)ref -> PsiTreeUtil.isAncestor(initializer, ref.getElement(), false));
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    PsiLocalVariable variable =
      PsiTreeUtil.getParentOfType(element, PsiLocalVariable.class, false, PsiStatement.class, PsiLambdaExpression.class);
    if (variable == null) return;
    String name = variable.getName();
    if (name == null) return;
    PsiExpression initializer = variable.getInitializer();
    if (initializer == null) return;
    PsiLambdaExpression lambda = PsiTreeUtil.getParentOfType(variable, PsiLambdaExpression.class);
    if (lambda == null) return;
    PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(lambda, PsiMethodCallExpression.class);
    if (call == null) return;
    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    if (qualifier == null) return;
    String methodName = call.getMethodExpression().getReferenceName();
    if (methodName == null) return;
    PsiParameter parameter = ArrayUtil.getFirstElement(lambda.getParameterList().getParameters());
    if (parameter == null) return;
    PsiType outType = StreamApiUtil.getStreamElementType(call.getType(), false);

    String mapOperation = StreamApiUtil.generateMapOperation(parameter, variable.getType(), initializer);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    if (!mapOperation.isEmpty()) {
      qualifier = (PsiExpression)qualifier.replace(factory.createExpressionFromText(qualifier.getText() + mapOperation, qualifier));
    }
    parameter = (PsiParameter)parameter.replace(factory.createParameter(variable.getName(), variable.getType(), parameter));
    variable.delete();
    LambdaRefactoringUtil.simplifyToExpressionLambda(lambda);
    if (methodName.startsWith("map")) {
      String replacement = StreamApiUtil.generateMapOperation(parameter, outType, lambda.getBody());
      if (!replacement.isEmpty()) {
        call = (PsiMethodCallExpression)call.replace(factory.createExpressionFromText(qualifier.getText() + replacement, call));
        qualifier = call.getMethodExpression().getQualifierExpression();
      }
      else {
        qualifier = (PsiExpression)call.replace(qualifier);
      }
    }
    else {
      PsiTypeElement typeElement = parameter.getTypeElement();
      if (typeElement != null) {
        if (methodName.startsWith("flatMap")) {
          String targetName = "flatMap";
          if (!(typeElement.getType() instanceof PsiPrimitiveType)) {
            if (PsiType.INT.equals(outType)) {
              targetName = "flatMapToInt";
            }
            else if (PsiType.LONG.equals(outType)) {
              targetName = "flatMapToLong";
            }
            else if (PsiType.DOUBLE.equals(outType)) {
              targetName = "flatMapToDouble";
            }
          }
          ExpressionUtils.bindCallTo(call, targetName);
        }
        typeElement.delete();
      }
    }
    if (qualifier instanceof PsiMethodCallExpression) {
      PsiLambdaExpression newLambda =
        tryCast(ArrayUtil.getFirstElement(((PsiMethodCallExpression)qualifier).getArgumentList().getExpressions()),
                PsiLambdaExpression.class);
      if (newLambda != null) {
        LambdaCanBeMethodReferenceInspection.replaceLambdaWithMethodReference(newLambda);
      }
    }
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return "Extract to separate stream step";
  }
}
