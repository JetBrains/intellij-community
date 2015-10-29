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

import static com.intellij.patterns.PsiJavaPatterns.psiMethod;

/**
 * @author peter
 */
class ChainedCallCompletion {
  private static final PsiMethodPattern OBJECT_METHOD_PATTERN = psiMethod().withName(
    StandardPatterns.string().oneOf("hashCode", "equals", "finalize", "wait", "notify", "notifyAll", "getClass", "clone", "toString")).
    definedInClass(CommonClassNames.JAVA_LANG_OBJECT);

  static void addChains(final PsiElement place, LookupElement qualifierItem,
                        final Consumer<LookupElement> result,
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

    if (object instanceof PsiMethod && ((PsiMethod)object).getParameterList().getParametersCount() > 0) {
      return;
    }

    final PsiReferenceExpression mockRef = ReferenceExpressionCompletionContributor.createMockReference(place, qualifierType, qualifierItem);
    if (mockRef == null) {
      return;
    }

    final ElementFilter filter = ReferenceExpressionCompletionContributor.getReferenceFilter(place, true);
    for (final LookupElement item : ReferenceExpressionCompletionContributor.completeFinalReference(place, mockRef, filter, parameters)) {
      if (shouldChain(place, qualifierType, expectedType, item)) {
        result.consume(new JavaChainLookupElement(qualifierItem, item) {
          @Override
          public void handleInsert(InsertionContext context) {
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

    if (object instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)object;
      if (psiMethod().withName("toArray").withParameterCount(1)
        .definedInClass(CommonClassNames.JAVA_UTIL_COLLECTION).accepts(method)) {
        return false;
      }
      final PsiMethod parentMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
      if (isUselessObjectMethod(method, parentMethod, qualifierType)) {
        return false;
      }

      final PsiType type = method.getReturnType();
      if (type instanceof PsiClassType) {
        final PsiClassType classType = (PsiClassType)type;
        final PsiClass psiClass = classType.resolve();
        if (psiClass instanceof PsiTypeParameter && method.getTypeParameterList() == psiClass.getParent()) {
          final PsiTypeParameter typeParameter = (PsiTypeParameter)psiClass;
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
