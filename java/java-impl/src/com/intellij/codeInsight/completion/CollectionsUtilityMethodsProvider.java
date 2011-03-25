/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.*;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.CommonClassNames.*;

/**
* @author peter
*/
class CollectionsUtilityMethodsProvider {
  public static void addCompletions(@NotNull final JavaSmartCompletionParameters parameters,
                                    @NotNull final Consumer<LookupElement> result) {
    final PsiElement element = parameters.getPosition();

    final PsiElement parent = element.getParent();
    if (parent instanceof PsiReferenceExpression && ((PsiReferenceExpression)parent).getQualifierExpression() != null) return;

    final PsiClass collectionsClass =
        JavaPsiFacade.getInstance(element.getProject()).findClass(JAVA_UTIL_COLLECTIONS, element.getResolveScope());
    if (collectionsClass == null) return;

    final PsiType type = parameters.getExpectedType();
    final PsiType defaultType = parameters.getDefaultType();
    final PsiElement pparent = parent.getParent();
    if (parameters.getParameters().getInvocationCount() > 1 ||
        pparent instanceof PsiReturnStatement ||
        pparent instanceof PsiConditionalExpression && pparent.getParent() instanceof PsiReturnStatement) {
      addCollectionMethod(result, type, defaultType, JAVA_UTIL_LIST, "emptyList", collectionsClass);
      addCollectionMethod(result, type, defaultType, JAVA_UTIL_SET, "emptySet", collectionsClass);
      addCollectionMethod(result, type, defaultType, JAVA_UTIL_MAP, "emptyMap", collectionsClass);
    }

    if (parameters.getParameters().getInvocationCount() > 1) {
      addCollectionMethod(result, type, defaultType, JAVA_UTIL_LIST, "singletonList", collectionsClass);
      addCollectionMethod(result, type, defaultType, JAVA_UTIL_SET, "singleton", collectionsClass);
      addCollectionMethod(result, type, defaultType, JAVA_UTIL_MAP, "singletonMap", collectionsClass);

      addCollectionMethod(result, type, defaultType, JAVA_UTIL_COLLECTION, "unmodifiableCollection", collectionsClass);
      addCollectionMethod(result, type, defaultType, JAVA_UTIL_LIST, "unmodifiableList", collectionsClass);
      addCollectionMethod(result, type, defaultType, JAVA_UTIL_SET, "unmodifiableSet", collectionsClass);
      addCollectionMethod(result, type, defaultType, JAVA_UTIL_MAP, "unmodifiableMap", collectionsClass);
      addCollectionMethod(result, type, defaultType, "java.util.SortedSet", "unmodifiableSortedSet", collectionsClass);
      addCollectionMethod(result, type, defaultType, "java.util.SortedMap", "unmodifiableSortedMap", collectionsClass);
    }

  }

  private static void addCollectionMethod(final Consumer<LookupElement> result, final PsiType expectedType,
                                   final PsiType defaultType, final String baseClassName,
                                   @NonNls final String method, @NotNull final PsiClass collectionsClass) {
    if (isClassType(expectedType, baseClassName) || isClassType(expectedType, JAVA_UTIL_COLLECTION)) {
      addMethodItem(result, expectedType, method, collectionsClass);
    } else if (isClassType(defaultType, baseClassName) || isClassType(defaultType, JAVA_UTIL_COLLECTION)) {
      addMethodItem(result, defaultType, method, collectionsClass);
    }
  }

  private static void addMethodItem(Consumer<LookupElement> result, PsiType expectedType, String methodName, PsiClass containingClass) {
    final PsiMethod[] methods = containingClass.findMethodsByName(methodName, false);
    if (methods.length == 0) {
      return;
    }
    
    final PsiMethod method = methods[0];
    final JavaMethodCallElement item = new JavaMethodCallElement(method);
    JavaCompletionUtil.qualify(item).setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
    item.setInferenceSubstitutor(SmartCompletionDecorator.calculateMethodReturnTypeSubstitutor(method, expectedType));
    result.consume(item);
  }

  private static boolean isClassType(final PsiType type, final String className) {
    if (type instanceof PsiClassType) {
      final PsiClass psiClass = ((PsiClassType)type).resolve();
      return psiClass != null && className.equals(psiClass.getQualifiedName());
    }
    return false;
  }

}
