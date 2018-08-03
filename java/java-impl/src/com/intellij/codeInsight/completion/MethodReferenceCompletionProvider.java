// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypeInfoImpl;
import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

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
        if (returnType != null) {
          final PsiElement position = parameters.getPosition();
          final PsiElement refPlace = position.getParent();
          final ExpectedTypeInfoImpl typeInfo =
            new ExpectedTypeInfoImpl(returnType, ExpectedTypeInfo.TYPE_OR_SUBTYPE, returnType, TailType.UNKNOWN, null, ExpectedTypeInfoImpl.NULL);
          final Map<PsiElement, PsiType> map = LambdaUtil.getFunctionalTypeMap();
          Consumer<LookupElement> noTypeCheck = new Consumer<LookupElement>() {
            @Override
            public void consume(final LookupElement lookupElement) {
              final PsiElement element = lookupElement.getPsiElement();
              if (element instanceof PsiMethod) {
                final PsiMethodReferenceExpression referenceExpression = createMethodReferenceExpression((PsiMethod)element);
                if (referenceExpression == null) {
                  return;
                }

                final PsiType added = map.put(referenceExpression, functionalType);
                try {
                  final PsiElement resolve = referenceExpression.resolve();
                  if (resolve != null && PsiEquivalenceUtil.areElementsEquivalent(element, resolve) && 
                      PsiMethodReferenceUtil.checkMethodReferenceContext(referenceExpression, resolve, functionalType) == null) {
                    result.addElement(new JavaMethodReferenceElement((PsiMethod)element, refPlace));
                  }
                }
                finally {
                  if (added == null) {
                    map.remove(referenceExpression);
                  }
                }
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

  private static class JavaMethodReferenceElement extends JavaMethodCallElement {
    private final PsiMethod myMethod;
    private final PsiElement myRefPlace;

    public JavaMethodReferenceElement(PsiMethod method, PsiElement refPlace) {
      super(method, method.isConstructor() ? "new" : method.getName());
      myMethod = method;
      myRefPlace = refPlace;
    }

    @Override
    public void handleInsert(@NotNull InsertionContext context) {
      if (!(myRefPlace instanceof PsiMethodReferenceExpression)) {
        final PsiClass containingClass = myMethod.getContainingClass();
        LOG.assertTrue(containingClass != null);
        final String qualifiedName = containingClass.getQualifiedName();
        LOG.assertTrue(qualifiedName != null);

        final Editor editor = context.getEditor();
        final Document document = editor.getDocument();
        final int startOffset = context.getStartOffset();

        document.insertString(startOffset, qualifiedName + "::");
        JavaCompletionUtil.shortenReference(context.getFile(), startOffset + qualifiedName.length() - 1);
        JavaCompletionUtil.insertTail(context, this, handleCompletionChar(context.getEditor(), this, context.getCompletionChar()), false);
      }
    }
  }
}
