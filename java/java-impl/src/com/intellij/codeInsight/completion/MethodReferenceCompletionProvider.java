// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.completion.scope.CompletionElement;
import com.intellij.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.codeInsight.daemon.impl.analysis.PsiMethodReferenceHighlightingUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Conditions;
import com.intellij.psi.*;
import com.intellij.psi.filters.types.AssignableFromFilter;
import com.intellij.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;

class MethodReferenceCompletionProvider {
  private static final Logger LOG = Logger.getInstance(MethodReferenceCompletionProvider.class);

  static void addCompletions(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    if (!PsiUtil.isLanguageLevel8OrHigher(parameters.getOriginalFile())) return;

    PsiElement position = parameters.getPosition();
    PsiMethodReferenceExpression refPlace = (PsiMethodReferenceExpression)position.getParent();
    if (refPlace == null || !LambdaUtil.isValidLambdaContext(refPlace.getParent())) return;

    final ExpectedTypeInfo[] expectedTypes = JavaSmartCompletionContributor.getExpectedTypes(parameters);
    for (ExpectedTypeInfo expectedType : expectedTypes) {
      final PsiType defaultType = expectedType.getDefaultType();
      if (LambdaUtil.isFunctionalType(defaultType)) {
        final PsiType functionalType = FunctionalInterfaceParameterizationUtil.getGroundTargetType(defaultType);
        final PsiType returnType = LambdaUtil.getFunctionalInterfaceReturnType(functionalType);
        if (returnType != null && functionalType != null) {
          JavaCompletionProcessor processor = new JavaCompletionProcessor(
            position, new AssignableFromFilter(returnType), JavaCompletionProcessor.Options.DEFAULT_OPTIONS, Conditions.alwaysTrue());
          refPlace.processVariants(processor);
          Iterable<PsiMethod> matchingMethods = JBIterable.from(processor.getResults())
            .map(CompletionElement::getElement)
            .filter(PsiMethod.class);

          for (PsiMethod method : matchingMethods) {
            PsiMethodReferenceExpression referenceExpression = createMethodReferenceExpression(method, refPlace);
            LambdaUtil.performWithTargetType(referenceExpression, functionalType, () -> {
              if (referenceExpression.isReferenceTo(method) &&
                  PsiMethodReferenceHighlightingUtil.checkMethodReferenceContext(referenceExpression, method, functionalType) == null) {
                result.addElement(new JavaMethodReferenceElement(method, refPlace));
              }
              return null;
            });
          }
        }
      }
    }
  }

  private static PsiMethodReferenceExpression createMethodReferenceExpression(PsiMethod method, PsiMethodReferenceExpression place) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(method.getProject());
    PsiMethodReferenceExpression copy = (PsiMethodReferenceExpression)place.copy();
    PsiElement referenceNameElement = copy.getReferenceNameElement();
    LOG.assertTrue(referenceNameElement != null, copy);
    referenceNameElement.replace(method.isConstructor() ? factory.createKeyword("new") : factory.createIdentifier(method.getName()));
    return copy;
  }

}
