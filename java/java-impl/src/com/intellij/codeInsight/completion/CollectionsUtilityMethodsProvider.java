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
  private final PsiElement myElement;
  private final PsiType myExpectedType;
  private final PsiType myDefaultType;
  @NotNull private final Consumer<LookupElement> myResult;

  CollectionsUtilityMethodsProvider(PsiElement position,
                                    PsiType expectedType,
                                    PsiType defaultType, @NotNull final Consumer<LookupElement> result) {
    myResult = result;
    myElement = position;
    myExpectedType = expectedType;
    myDefaultType = defaultType;
  }

  public void addCompletions(boolean showAll) {
    final PsiElement parent = myElement.getParent();
    if (parent instanceof PsiReferenceExpression && ((PsiReferenceExpression)parent).getQualifierExpression() != null) return;

    final PsiClass collectionsClass =
        JavaPsiFacade.getInstance(myElement.getProject()).findClass(JAVA_UTIL_COLLECTIONS, myElement.getResolveScope());
    if (collectionsClass == null) return;

    final PsiElement pparent = parent.getParent();
    if (showAll ||
        pparent instanceof PsiReturnStatement ||
        pparent instanceof PsiConditionalExpression && pparent.getParent() instanceof PsiReturnStatement) {
      addCollectionMethod(JAVA_UTIL_LIST, "emptyList", collectionsClass);
      addCollectionMethod(JAVA_UTIL_SET, "emptySet", collectionsClass);
      addCollectionMethod(JAVA_UTIL_MAP, "emptyMap", collectionsClass);
    }

    if (showAll) {
      addCollectionMethod(JAVA_UTIL_LIST, "singletonList", collectionsClass);
      addCollectionMethod(JAVA_UTIL_SET, "singleton", collectionsClass);
      addCollectionMethod(JAVA_UTIL_MAP, "singletonMap", collectionsClass);

      addCollectionMethod(JAVA_UTIL_COLLECTION, "unmodifiableCollection", collectionsClass);
      addCollectionMethod(JAVA_UTIL_LIST, "unmodifiableList", collectionsClass);
      addCollectionMethod(JAVA_UTIL_SET, "unmodifiableSet", collectionsClass);
      addCollectionMethod(JAVA_UTIL_MAP, "unmodifiableMap", collectionsClass);
      addCollectionMethod("java.util.SortedSet", "unmodifiableSortedSet", collectionsClass);
      addCollectionMethod("java.util.SortedMap", "unmodifiableSortedMap", collectionsClass);
    }

  }

  private void addCollectionMethod(final String baseClassName,
                                   @NonNls final String method, @NotNull final PsiClass collectionsClass) {
    if (isClassType(myExpectedType, baseClassName) || isClassType(myExpectedType, JAVA_UTIL_COLLECTION)) {
      addMethodItem(myExpectedType, method, collectionsClass);
    } else if (isClassType(myDefaultType, baseClassName) || isClassType(myDefaultType, JAVA_UTIL_COLLECTION)) {
      addMethodItem(myDefaultType, method, collectionsClass);
    }
  }

  private void addMethodItem(PsiType expectedType, String methodName, PsiClass containingClass) {
    final PsiMethod[] methods = containingClass.findMethodsByName(methodName, false);
    if (methods.length == 0) {
      return;
    }
    
    final PsiMethod method = methods[0];
    final JavaMethodCallElement item = new JavaMethodCallElement(method, false, false);
    item.setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
    item.setInferenceSubstitutor(SmartCompletionDecorator.calculateMethodReturnTypeSubstitutor(method, expectedType), myElement);
    myResult.consume(item);
  }

  private static boolean isClassType(final PsiType type, final String className) {
    if (type instanceof PsiClassType) {
      final PsiClass psiClass = ((PsiClassType)type).resolve();
      return psiClass != null && className.equals(psiClass.getQualifiedName());
    }
    return false;
  }

}
