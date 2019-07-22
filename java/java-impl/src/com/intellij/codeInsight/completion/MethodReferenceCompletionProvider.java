// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypeInfoImpl;
import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

public class MethodReferenceCompletionProvider extends CompletionProvider<CompletionParameters> {
  private static final Logger LOG = Logger.getInstance(MethodReferenceCompletionProvider.class);

  @Override
  protected void addCompletions(@NotNull CompletionParameters parameters,
                                @NotNull ProcessingContext context,
                                @NotNull final CompletionResultSet result) {
    if (!PsiUtil.isLanguageLevel8OrHigher(parameters.getOriginalFile())) return;

    final PsiElement rulezzRef = parameters.getPosition().getParent();
    if (rulezzRef == null || !LambdaUtil.isValidLambdaContext(rulezzRef.getParent())) return;

    final ExpectedTypeInfo[] expectedTypes = JavaSmartCompletionContributor.getExpectedTypes(parameters);
    for (ExpectedTypeInfo expectedType : expectedTypes) {
      final PsiType defaultType = expectedType.getDefaultType();
      if (LambdaUtil.isFunctionalType(defaultType)) {
        final PsiType functionalType = FunctionalInterfaceParameterizationUtil.getGroundTargetType(defaultType);
        final PsiType returnType = LambdaUtil.getFunctionalInterfaceReturnType(functionalType);
        if (returnType != null && functionalType != null) {
          final PsiElement position = parameters.getPosition();
          final PsiElement refPlace = position.getParent();
          final ExpectedTypeInfoImpl typeInfo =
            new ExpectedTypeInfoImpl(returnType, ExpectedTypeInfo.TYPE_OR_SUBTYPE, returnType, TailType.UNKNOWN, null, ExpectedTypeInfoImpl.NULL);
          Consumer<LookupElement> noTypeCheck = new Consumer<LookupElement>() {
            @Override
            public void consume(final LookupElement lookupElement) {
              final PsiElement element = lookupElement.getPsiElement();
              if (element instanceof PsiMethod) {
                final PsiMethodReferenceExpression referenceExpression = createMethodReferenceExpression((PsiMethod)element);
                if (referenceExpression == null) {
                  return;
                }

                LambdaUtil.performWithTargetType(referenceExpression, functionalType, () -> {
                  final PsiElement resolve = referenceExpression.resolve();
                  if (resolve != null && PsiEquivalenceUtil.areElementsEquivalent(element, resolve) &&
                      PsiMethodReferenceUtil.checkMethodReferenceContext(referenceExpression, resolve, functionalType) == null) {
                    result.addElement(new JavaMethodReferenceElement((PsiMethod)element, refPlace));
                  }
                  return null;
                });
              }
            }

            private PsiMethodReferenceExpression createMethodReferenceExpression(PsiMethod method) {
              PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(method.getProject());
              if (refPlace instanceof PsiMethodReferenceExpression) {
                final PsiMethodReferenceExpression referenceExpression = (PsiMethodReferenceExpression)refPlace.copy();
                final PsiElement referenceNameElement = referenceExpression.getReferenceNameElement();
                LOG.assertTrue(referenceNameElement != null, referenceExpression);
                referenceNameElement.replace(method.isConstructor() ? elementFactory.createKeyword("new") : elementFactory.createIdentifier(method.getName()));
                return referenceExpression;
              }
              else if (method.hasModifierProperty(PsiModifier.STATIC)) {
                final PsiClass aClass = method.getContainingClass();
                LOG.assertTrue(aClass != null);
                final String qualifiedName = aClass.getQualifiedName();
                return (PsiMethodReferenceExpression)elementFactory.createExpressionFromText(
                  qualifiedName + "::" + (method.isConstructor() ? "new" : method.getName()), refPlace);
              }
              else {
                return null;
              }
            }
          };

          final Runnable runnable = ReferenceExpressionCompletionContributor
            .fillCompletionVariants(new JavaSmartCompletionParameters(parameters, typeInfo), noTypeCheck);
          if (runnable != null) {
            runnable.run();
          }
        }
      }
    }
  }
}
