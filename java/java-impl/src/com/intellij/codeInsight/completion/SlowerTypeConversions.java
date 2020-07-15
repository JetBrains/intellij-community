// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.codeInsight.completion.ReferenceExpressionCompletionContributor.getSpace;

/**
 * @author peter
 */
final class SlowerTypeConversions {
  static void addChainedSuggestions(CompletionParameters parameters,
                                    CompletionResultSet result,
                                    Set<ExpectedTypeInfo> expectedInfos,
                                    List<LookupElement> base) {
    PsiElement position = parameters.getPosition();
    PsiJavaCodeReferenceElement reference = (PsiJavaCodeReferenceElement)position.getParent();
    List<LookupElement> chainable = ContainerUtil.filter(base, item -> isChainable(item.getObject()));
    if (chainable.isEmpty()) return;

    for (ExpectedTypeInfo info : expectedInfos) {
      Set<Pair<LookupElement, String>> processedChains = new HashSet<>();
      JavaSmartCompletionParameters smartParams = new JavaSmartCompletionParameters(parameters, info);
      for (LookupElement item : chainable) {
        addSecondCompletionVariants(position, reference, item, smartParams, lookupElement -> {
          ContainerUtil.addIfNotNull(processedChains, chainInfo(lookupElement));
          result.consume(JavaSmartCompletionContributor.decorate(lookupElement, expectedInfos));
        });
      }
      if (!reference.isQualified()) {
        BasicExpressionCompletionContributor.processDataflowExpressionTypes(smartParams, null, PrefixMatcher.ALWAYS_TRUE,
                                                                            baseItem -> addSecondCompletionVariants(position, reference, baseItem, smartParams, lookupElement -> {
                                                                              if (!processedChains.contains(chainInfo(lookupElement))) {
                                                                                result.consume(JavaSmartCompletionContributor.decorate(lookupElement, expectedInfos));
                                                                              }
                                                                            }));
      }

    }
  }

  private static boolean isChainable(Object object) {
    return object instanceof PsiVariable || object instanceof PsiMethod || object instanceof PsiExpression;
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
