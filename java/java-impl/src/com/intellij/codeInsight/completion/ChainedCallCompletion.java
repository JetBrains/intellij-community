// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.patterns.PsiMethodPattern;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PsiJavaPatterns.psiMethod;

final class ChainedCallCompletion {
  static final PsiMethodPattern OBJECT_METHOD_PATTERN = psiMethod().withName(
    StandardPatterns.string().oneOf("hashCode", "equals", "finalize", "wait", "notify", "notifyAll", "getClass", "clone", "toString")).
    definedInClass(CommonClassNames.JAVA_LANG_OBJECT);

  static void addChains(final PsiElement place, LookupElement qualifierItem,
                        final Consumer<? super LookupElement> result,
                        PsiType qualifierType,
                        final PsiType expectedType, JavaSmartCompletionParameters parameters) throws
                                                                                                           IncorrectOperationException {
    final Object object = qualifierItem.getObject();
    if (OBJECT_METHOD_PATTERN.accepts(object) && !allowGetClass(object, parameters)) {
      return;
    }

    if (parameters.getParameters().getInvocationCount() < 3 && qualifierType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
      return;
    }

    if (object instanceof PsiMethod && !((PsiMethod)object).getParameterList().isEmpty()) {
      return;
    }

    final PsiReferenceExpression mockRef = ReferenceExpressionCompletionContributor.createMockReference(place, qualifierType, qualifierItem);
    if (mockRef == null) {
      return;
    }

    final ElementFilter filter = ReferenceExpressionCompletionContributor.getReferenceFilter(place, true);
    for (LookupElement item : ReferenceExpressionCompletionContributor.completeFinalReference(place, mockRef, filter, expectedType, parameters.getParameters())) {
      if (shouldChain(place, qualifierType, expectedType, item)) {
        result.consume(new JavaChainLookupElement(qualifierItem, item) {
          @Override
          public void handleInsert(@NotNull InsertionContext context) {
            FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.SECOND_SMART_COMPLETION_CHAIN);
            super.handleInsert(context);
          }
        });
      }
    }
  }

  private static boolean shouldChain(PsiElement element, PsiType qualifierType, PsiType expectedType, LookupElement item) {
    Object object = item.getObject();
    if (object instanceof PsiModifierListOwner && ((PsiModifierListOwner)object).hasModifierProperty(PsiModifier.STATIC)) {
      return false;
    }

    if (object instanceof PsiMethod method) {
      if (psiMethod().withName("toArray").withParameterCount(1)
        .definedInClass(CommonClassNames.JAVA_UTIL_COLLECTION).accepts(method)) {
        return false;
      }
      final PsiMethod parentMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
      if (isUselessObjectMethod(method, parentMethod, qualifierType)) {
        return false;
      }

      final PsiType type = method.getReturnType();
      if (type instanceof PsiClassType classType) {
        final PsiClass psiClass = classType.resolve();
        if (psiClass instanceof PsiTypeParameter typeParameter && method.getTypeParameterList() == psiClass.getParent()) {
          if (typeParameter.getExtendsListTypes().length == 0) return false;
          if (!expectedType.isAssignableFrom(TypeConversionUtil.typeParameterErasure(typeParameter))) return false;
        }
      }
    }
    return true;
  }

  private static boolean isUselessObjectMethod(PsiMethod method, PsiMethod parentMethod, PsiType qualifierType) {
    if (!OBJECT_METHOD_PATTERN.accepts(method)) {
      return false;
    }

    if (OBJECT_METHOD_PATTERN.accepts(parentMethod) && method.getName().equals(parentMethod.getName())) {
      return false;
    }

    if ("toString".equals(method.getName())) {
      if (qualifierType.equalsToText(CommonClassNames.JAVA_LANG_STRING_BUFFER) ||
          InheritanceUtil.isInheritor(qualifierType, CommonClassNames.JAVA_LANG_ABSTRACT_STRING_BUILDER)) {
        return false;
      }
    }

    return true;
  }

  private static boolean allowGetClass(final Object object, final JavaSmartCompletionParameters parameters) {
    if (!"getClass".equals(((PsiMethod)object).getName())) return false;

    final PsiType type = parameters.getDefaultType();
    @NonNls final String canonicalText = type.getCanonicalText();
    if ("java.lang.ClassLoader".equals(canonicalText)) return true;
    if (canonicalText.startsWith("java.lang.reflect.")) return true;
    return false;
  }
}
