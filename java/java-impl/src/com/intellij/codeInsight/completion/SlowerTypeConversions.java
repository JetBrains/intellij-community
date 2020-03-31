// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

import static com.intellij.codeInsight.completion.ReferenceExpressionCompletionContributor.getSpace;
import static com.intellij.patterns.PsiJavaPatterns.psiElement;

/**
 * @author peter
 */
class SlowerTypeConversions implements Runnable {
  private static final PrefixMatcher TRUE_MATCHER = new PrefixMatcher("") {
    @Override
    public boolean prefixMatches(@NotNull String name) {
      return true;
    }

    @NotNull
    @Override
    public PrefixMatcher cloneWithPrefix(@NotNull String prefix) {
      return this;
    }
  };
  private final Set<? extends LookupElement> myBase;
  private final PsiElement myElement;
  private final PsiJavaCodeReferenceElement myReference;
  private final JavaSmartCompletionParameters myParameters;
  private final Consumer<? super LookupElement> myResult;

  SlowerTypeConversions(Set<? extends LookupElement> base,
                        PsiElement element,
                        PsiJavaCodeReferenceElement reference,
                        JavaSmartCompletionParameters parameters, Consumer<? super LookupElement> result) {
    myBase = base;
    myElement = element;
    myReference = reference;
    myParameters = parameters;
    myResult = result;
  }

  @Override
  public void run() {
    final Set<Pair<LookupElement, String>> processedChains = new HashSet<>();
    for (final LookupElement item : myBase) {
      addSecondCompletionVariants(myElement, myReference, item, myParameters, lookupElement -> {
        ContainerUtil.addIfNotNull(processedChains, chainInfo(lookupElement));
        myResult.consume(lookupElement);
      });
    }
    if (!psiElement().afterLeaf(".").accepts(myElement)) {
      BasicExpressionCompletionContributor.processDataflowExpressionTypes(myParameters, null, TRUE_MATCHER,
                                                                          baseItem -> addSecondCompletionVariants(myElement, myReference, baseItem, myParameters, lookupElement -> {
                                                                            if (!processedChains.contains(chainInfo(lookupElement))) {
                                                                              myResult.consume(lookupElement);
                                                                            }
                                                                          }));
    }
  }

  private static void addSecondCompletionVariants(PsiElement element, PsiReference reference, LookupElement baseItem,
                                                  JavaSmartCompletionParameters parameters, Consumer<? super LookupElement> result) {
    final Object object = baseItem.getObject();

    try {
      PsiType itemType = JavaCompletionUtil.getLookupElementType(baseItem);
      if (itemType instanceof PsiWildcardType) {
        itemType = ((PsiWildcardType)itemType).getExtendsBound();
      }
      if (itemType == null) return;
      assert itemType.isValid() : baseItem + "; " + baseItem.getClass();

      final PsiElement element1 = reference.getElement();
      final PsiElement qualifier =
        element1 instanceof PsiJavaCodeReferenceElement ? ((PsiJavaCodeReferenceElement)element1).getQualifier() : null;
      final PsiType expectedType = parameters.getExpectedType();
      ChainedCallCompletion.addChains(element, baseItem, result, itemType, expectedType, parameters);

      final PsiFile file = parameters.getParameters().getOriginalFile();
      final String prefix = getItemText(file, object);
      if (prefix == null) return;

      FromArrayConversion.addConversions(element, prefix, itemType, result, qualifier, expectedType);

      ToArrayConversion.addConversions(file, element, prefix, itemType, result, qualifier, expectedType);

      ArrayMemberAccess.addMemberAccessors(element, prefix, itemType, qualifier, result, (PsiModifierListOwner)object, expectedType);
    }
    catch (IncorrectOperationException ignored) {
    }
  }

  @Nullable
  private static String getItemText(@NotNull PsiFile file, Object o) {
    if (o instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)o;
      final PsiType type = method.getReturnType();
      if (PsiType.VOID.equals(type) || PsiType.NULL.equals(type)) return null;
      if (!method.getParameterList().isEmpty()) return null;
      return method.getName() + "(" +
             getSpace(CodeStyle.getLanguageSettings(file).SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES) + ")";
    }
    else if (o instanceof PsiVariable) {
      return ((PsiVariable)o).getName();
    }
    return null;
  }

  @Nullable
  private static Pair<LookupElement, String> chainInfo(LookupElement lookupElement) {
    if (lookupElement instanceof JavaChainLookupElement) {
      Object object = lookupElement.getObject();
      if (object instanceof PsiMember) {
        LookupElement qualifier = ((JavaChainLookupElement)lookupElement).getQualifier();
        if (qualifier instanceof CastingLookupElementDecorator) {
          qualifier = ((CastingLookupElementDecorator)qualifier).getDelegate();
        }
        return Pair.create(qualifier, lookupElement.getLookupString());
      }
    }
    return null;
  }

}
