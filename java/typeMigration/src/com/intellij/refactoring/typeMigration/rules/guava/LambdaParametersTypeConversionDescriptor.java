/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.refactoring.typeMigration.rules.guava;

import com.intellij.codeInspection.AnonymousCanBeLambdaInspection;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.refactoring.typeMigration.TypeEvaluator;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

/**
 * @author Dmitry Batkovich
 */
class LambdaParametersTypeConversionDescriptor extends TypeConversionDescriptor {
  private static final Logger LOG = Logger.getInstance(LambdaParametersTypeConversionDescriptor.class);

  LambdaParametersTypeConversionDescriptor(@NonNls String stringToReplace, @NonNls String replaceByString) {
    super(stringToReplace, replaceByString);
  }


  @Override
  public PsiExpression replace(PsiExpression expression, TypeEvaluator evaluator) throws IncorrectOperationException {
     LOG.assertTrue(expression instanceof PsiMethodCallExpression);
    PsiMethodCallExpression methodCall = (PsiMethodCallExpression)expression;
    final PsiExpression[] arguments = methodCall.getArgumentList().getExpressions();
    if (arguments.length == 1) {
      final PsiExpression functionArg = arguments[0];
      customizeParameter(convertParameter(functionArg, evaluator));
    }
    return super.replace(expression, evaluator);
  }

  protected void customizeParameter(PsiExpression parameter) {

  }

  private static PsiExpression addApplyReference(final PsiExpression expression, TypeEvaluator evaluator) {
    String samMethodName = null;
    PsiType type = evaluator.evaluateType(expression);
    if (type instanceof PsiClassType) {
      PsiClass resolvedClass = ((PsiClassType)type).resolve();
      if (resolvedClass != null) {
        final JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(expression.getProject());
        final GlobalSearchScope scope = resolvedClass.getResolveScope();
        if (InheritanceUtil.isInheritorOrSelf(resolvedClass, javaPsiFacade.findClass(GuavaSupplierConversionRule.GUAVA_SUPPLIER, scope), true)) {
          samMethodName = "get";
        }
        else if (InheritanceUtil.isInheritorOrSelf(resolvedClass, javaPsiFacade.findClass(GuavaFunctionConversionRule.GUAVA_FUNCTION, scope), true) ||
                 InheritanceUtil.isInheritorOrSelf(resolvedClass, javaPsiFacade.findClass(GuavaPredicateConversionRule.GUAVA_PREDICATE, scope), true)) {
          samMethodName = "apply";
        }
      }
    }
    if (samMethodName == null) {
      return expression;
    }
    return (PsiExpression)expression.replace(
      JavaPsiFacade.getElementFactory(expression.getProject()).createExpressionFromText(expression.getText() + "::" + samMethodName, null));
  }

  public static PsiExpression convertParameter(PsiExpression expression, TypeEvaluator evaluator) {
    if (expression instanceof PsiNewExpression) {
      final PsiAnonymousClass anonymousClass = ((PsiNewExpression)expression).getAnonymousClass();
      if (anonymousClass != null) {
        if (AnonymousCanBeLambdaInspection.canBeConvertedToLambda(anonymousClass, true)) {
          AnonymousCanBeLambdaInspection.replacePsiElementWithLambda(expression, true, true);
        }
      }
      else {
        return addApplyReference(expression, evaluator);
      }
    }
    else if (!(expression instanceof PsiFunctionalExpression)) {
      return addApplyReference(expression, evaluator);
    }
    else if (expression instanceof PsiMethodReferenceExpression) {
      final PsiElement qualifier = ((PsiMethodReferenceExpression)expression).getQualifier();
      PsiType qualifierType;
      if (qualifier instanceof PsiExpression && (qualifierType = evaluator.evaluateType((PsiExpression)qualifier)) != null) {
        final PsiClass qualifierClass = PsiTypesUtil.getPsiClass(qualifierType);
        if (qualifierClass != null && (Comparing.equal(qualifierClass.getQualifiedName(), GuavaFunctionConversionRule.JAVA_UTIL_FUNCTION_FUNCTION) ||
            Comparing.equal(qualifierClass.getQualifiedName(), GuavaOptionalConversionRule.JAVA_OPTIONAL) ||
            Comparing.equal(qualifierClass.getQualifiedName(), GuavaSupplierConversionRule.JAVA_SUPPLIER) ||
            Comparing.equal(qualifierClass.getQualifiedName(), GuavaPredicateConversionRule.JAVA_PREDICATE)))
        return (PsiExpression)expression.replace(qualifier);
      }
    }
    return expression;
  }
}
