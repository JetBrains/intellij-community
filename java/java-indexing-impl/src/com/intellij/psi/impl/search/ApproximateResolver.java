/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.search;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
public class ApproximateResolver {
  /**
   * Tries to calculate the type of a given expression using lightweight resolve: no type inference for method calls, no exact candidate selection, works only with raw types.
   * If it returns a non-empty set, the "real" type class is guaranteed to be present in that set. This method can be used to quickly
   * discard search variants of certainly incompatible types without doing full resolve (which is likely to be more expensive).
   *
   * @param expression the expression to determine the type of
   * @param maxDepth a guard against too long chained calls, determines how deeply this resolve will go into a chained call
   * @return a set of possible classes corresponding to types that this expression might have, or null if it can't be determined
   */
  @Nullable
  static Set<PsiClass> getPossibleTypes(@NotNull PsiExpression expression, int maxDepth) {
    if (maxDepth == 0) return null;

    expression = PsiUtil.skipParenthesizedExprDown(expression);

    return expression instanceof PsiTypeCastExpression ? extractClass(expression.getType()) :
           expression instanceof PsiMethodCallExpression ? getCallType(expression, maxDepth) :
           expression instanceof PsiReferenceExpression ? getNonCallType((PsiReferenceExpression)expression, maxDepth) :
           null;
  }

  @Nullable
  private static Set<PsiClass> getCallType(@NotNull PsiExpression expression, int maxDepth) {
    PsiReferenceExpression ref = ((PsiMethodCallExpression)expression).getMethodExpression();
    PsiExpression qualifier = ref.getQualifierExpression();
    if (qualifier == null) return extractClass(expression.getType());

    Set<PsiClass> qualifierType = getPossibleTypes(qualifier, maxDepth - 1);
    String refName = ref.getReferenceName();
    int argCount = ((PsiMethodCallExpression)expression).getArgumentList().getExpressions().length;
    List<PsiMethod> methods = refName == null || qualifierType == null ? null : getPossibleMethods(qualifierType, refName, argCount);
    return methods == null ? null : getDefiniteSymbolTypes(methods);
  }

  @Nullable
  private static Set<PsiClass> getNonCallType(@NotNull PsiReferenceExpression expression, int maxDepth) {
    PsiExpression qualifier = expression.getQualifierExpression();
    if (qualifier == null) {
      PsiElement target = expression.resolve();
      return target instanceof PsiClass ? Collections.singleton((PsiClass)target) : extractClass(expression.getType());
    }

    Set<PsiClass> qualifierType = getPossibleTypes(qualifier, maxDepth - 1);
    String refName = expression.getReferenceName();
    List<? extends PsiMember> members = refName == null || qualifierType == null ? null : getPossibleNonMethods(qualifierType, refName);
    return members == null ? null : getDefiniteSymbolTypes(members);
  }

  @Nullable
  private static Set<PsiClass> extractClass(PsiType type) {
    PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(type);
    return psiClass == null || psiClass instanceof PsiTypeParameter ? null : Collections.singleton(psiClass);
  }

  @NotNull
  public static List<PsiMethod> getPossibleMethods(@NotNull Set<PsiClass> symbols, @NotNull String name, int callArgCount) {
    return JBIterable.from(symbols).
      flatMap(sym -> Arrays.asList(sym.findMethodsByName(name, true))).
      filter(m -> canHaveArgCount(m, callArgCount)).
      toList();
  }

  @NotNull
  public static List<PsiMember> getPossibleNonMethods(@NotNull Set<PsiClass> symbols, @NotNull String name) {
    List<PsiMember> result = new ArrayList<>();
    for (PsiClass sym : symbols) {
      ContainerUtil.addIfNotNull(result, sym.findFieldByName(name, true));
      ContainerUtil.addIfNotNull(result, sym.findInnerClassByName(name, true));
    }
    return result;
  }

  @Nullable
  public static Set<PsiClass> getDefiniteSymbolTypes(@NotNull List<? extends PsiMember> candidates) {
    Set<PsiClass> possibleTypes = new HashSet<>();
    for (PsiMember candidate : candidates) {
      if (candidate instanceof PsiClass) {
        possibleTypes.add((PsiClass)candidate);
      }
      else if (candidate instanceof PsiMethod && ((PsiMethod)candidate).isConstructor()) {
        ContainerUtil.addIfNotNull(possibleTypes, candidate.getContainingClass());
      }
      else {
        //noinspection ConstantConditions
        PsiType type = candidate instanceof PsiField ? ((PsiField)candidate).getType() : ((PsiMethod)candidate).getReturnType();
        if (type instanceof PsiPrimitiveType) continue;
        if (type instanceof PsiArrayType) {
          type = PsiType.getJavaLangObject(candidate.getManager(), candidate.getResolveScope());
        }

        PsiClass typeClass = PsiUtil.resolveClassInClassTypeOnly(type);
        if (typeClass == null || typeClass instanceof PsiTypeParameter) {
          return null;
        }
        possibleTypes.add(typeClass);
      }
    }
    return possibleTypes;
  }

  public static boolean canHaveArgCount(PsiMethod method, int argCount) {
    return method.isVarArgs() ? argCount >= method.getParameterList().getParametersCount() - 1
                              : argCount == method.getParameterList().getParametersCount();
  }
}
