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

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.search.searches.DeepestSuperMethodsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class RecursionWeigher extends CompletionWeigher {

  private enum Result {
    recursive,
    passingObjectToItself,
    normal,
    delegation,
  }

  public Result weigh(@NotNull final LookupElement element, @Nullable final CompletionLocation location) {
    if (location == null) {
      return null;
    }
    if (location.getCompletionType() != CompletionType.BASIC && location.getCompletionType() != CompletionType.SMART) return Result.normal;

    final Object object = element.getObject();
    if (!(object instanceof PsiModifierListOwner) && !(object instanceof PsiExpression)) return Result.normal;

    final PsiMethod positionMethod = JavaCompletionUtil.POSITION_METHOD.getValue(location);
    if (positionMethod == null) return Result.normal;

    final PsiElement position = location.getCompletionParameters().getPosition();
    final ElementFilter filter = JavaCompletionUtil.recursionFilter(position);
    if (filter != null && !filter.isAcceptable(object, position)) {
      return Result.recursive;
    }

    final PsiMethodCallExpression expression = PsiTreeUtil.getParentOfType(position, PsiMethodCallExpression.class, true, PsiClass.class);
    final PsiReferenceExpression reference = expression != null ? expression.getMethodExpression() : PsiTreeUtil.getParentOfType(position, PsiReferenceExpression.class);
    if (reference == null) return Result.normal;

    final PsiExpression qualifier = reference.getQualifierExpression();
    boolean isDelegate = qualifier != null && !(qualifier instanceof PsiThisExpression);

    if (isPassingObjectToItself(object, qualifier, isDelegate)) {
      return Result.passingObjectToItself;
    }

    if (expression != null) {
      final ExpectedTypeInfo[] expectedInfos = JavaCompletionUtil.EXPECTED_TYPES.getValue(location);
      if (expectedInfos != null) {
        final PsiType itemType = JavaCompletionUtil.getLookupElementType(element);
        if (itemType != null) {
          for (final ExpectedTypeInfo expectedInfo : expectedInfos) {
            if (positionMethod.equals(expectedInfo.getCalledMethod()) && expectedInfo.getType().isAssignableFrom(itemType)) {
              return isDelegate ? Result.delegation : Result.recursive;
            }
          }
        }
      }
      return Result.normal;
    }

    if (object instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)object;
      if (PsiTreeUtil.isAncestor(reference, position, false) &&
          Comparing.equal(method.getName(), positionMethod.getName()) &&
          method.getParameterList().getParametersCount() == positionMethod.getParameterList().getParametersCount()) {
        if (findDeepestSuper(method).equals(findDeepestSuper(positionMethod))) {
          return isDelegate ? Result.delegation : Result.recursive;
        }
      }
    }

    return Result.normal;
  }

  private static boolean isPassingObjectToItself(Object object, PsiExpression qualifier, boolean delegate) {
    if (object instanceof PsiThisExpression) {
      return !delegate || qualifier instanceof PsiSuperExpression;
    }
    return qualifier instanceof PsiReferenceExpression &&
           object.equals(((PsiReferenceExpression)qualifier).advancedResolve(true).getElement());
  }

  @NotNull
  private static PsiMethod findDeepestSuper(@NotNull final PsiMethod method) {
    final PsiMethod first = DeepestSuperMethodsSearch.search(method).findFirst();
    return first == null ? method : first;
  }
}
