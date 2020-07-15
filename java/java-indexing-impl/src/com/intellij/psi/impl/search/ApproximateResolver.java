// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
public final class ApproximateResolver {
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

    return expression instanceof PsiTypeCastExpression || expression instanceof PsiThisExpression ? extractClass(expression.getType()) :
           expression instanceof PsiMethodCallExpression ? getCallType(expression, maxDepth) :
           expression instanceof PsiReferenceExpression ? getNonCallType((PsiReferenceExpression)expression, maxDepth) :
           expression instanceof PsiNewExpression ? getNewType((PsiNewExpression)expression) :
           expression instanceof PsiConditionalExpression ? getConditionalType((PsiConditionalExpression)expression, maxDepth) :
           null;
  }

  @Nullable
  private static Set<PsiClass> getConditionalType(PsiConditionalExpression expression, int maxDepth) {
    PsiExpression thenBranch = expression.getThenExpression();
    PsiExpression elseBranch = expression.getElseExpression();
    if (thenBranch != null && elseBranch != null) {
      PsiClass thenType = ContainerUtil.getOnlyItem(getPossibleTypes(thenBranch, maxDepth / 2));
      PsiClass elseType = ContainerUtil.getOnlyItem(getPossibleTypes(elseBranch, maxDepth / 2));
      if (thenType != null && thenType.equals(elseType)) {
        return Collections.singleton(thenType);
      }
    }
    return null;
  }

  @Nullable
  private static Set<PsiClass> getNewType(PsiNewExpression expression) {
    if (expression.isArrayCreation()) return null;
    PsiAnonymousClass aClass = expression.getAnonymousClass();
    if (aClass != null) return Collections.singleton(aClass);
    PsiJavaCodeReferenceElement reference = expression.getClassReference();
    if (reference != null) {
      PsiClass psiClass = ObjectUtils.tryCast(reference.resolve(), PsiClass.class);
      if (psiClass != null) {
        return Collections.singleton(psiClass);
      }
    }
    return null;
  }

  @Nullable
  private static Set<PsiClass> getCallType(@NotNull PsiExpression expression, int maxDepth) {
    PsiReferenceExpression ref = ((PsiMethodCallExpression)expression).getMethodExpression();
    PsiExpression qualifier = ref.getQualifierExpression();
    if (qualifier == null) return extractClass(expression.getType());

    Set<PsiClass> qualifierType = getPossibleTypes(qualifier, maxDepth - 1);
    String refName = ref.getReferenceName();
    int argCount = ((PsiMethodCallExpression)expression).getArgumentList().getExpressionCount();
    List<PsiMethod> methods = refName == null || qualifierType == null ? null : getPossibleMethods(qualifierType, refName, argCount);
    return methods == null ? null : getDefiniteSymbolTypes(methods, qualifierType);
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
    return members == null ? null : getDefiniteSymbolTypes(members, qualifierType);
  }

  @Nullable
  private static Set<PsiClass> extractClass(PsiType type) {
    PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(type);
    return psiClass == null || psiClass instanceof PsiTypeParameter ? null : Collections.singleton(psiClass);
  }

  @NotNull
  public static List<PsiMethod> getPossibleMethods(@NotNull Set<? extends PsiClass> symbols, @NotNull String name, int callArgCount) {
    return JBIterable.from(symbols).
      flatMap(sym -> Arrays.asList(sym.findMethodsByName(name, true))).
      filter(m -> canHaveArgCount(m, callArgCount)).
      toList();
  }

  @NotNull
  public static List<PsiMember> getPossibleNonMethods(@NotNull Set<? extends PsiClass> symbols, @NotNull String name) {
    List<PsiMember> result = new ArrayList<>();
    for (PsiClass sym : symbols) {
      ContainerUtil.addIfNotNull(result, sym.findFieldByName(name, true));
      ContainerUtil.addIfNotNull(result, sym.findInnerClassByName(name, true));
    }
    return result;
  }

  @Nullable
  public static Set<PsiClass> getDefiniteSymbolTypes(@NotNull List<? extends PsiMember> candidates, @NotNull Set<? extends PsiClass> qualifierType) {
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
        if (typeClass == null) return null;
        if (typeClass instanceof PsiTypeParameter) {
          PsiClass containingClass = candidate.getContainingClass();
          if (containingClass == null) return null;
          typeClass = null;
          // Sometimes we have Child extends Parent<Bar> and Parent<T> { T foo(); }
          // In this case it's still desired to resolve that child.foo() returns Bar
          for (PsiClass qualifierClass : qualifierType) {
            PsiSubstitutor substitutor =
              TypeConversionUtil.getMaybeSuperClassSubstitutor(containingClass, qualifierClass, PsiSubstitutor.EMPTY);
            if (substitutor != null) {
              PsiClass substitutedTypeClass = PsiUtil.resolveClassInClassTypeOnly(substitutor.substitute(type));
              if (substitutedTypeClass instanceof PsiTypeParameter || substitutedTypeClass == null ||
                  (typeClass != null && substitutedTypeClass != typeClass)) {
                return null;
              }
              typeClass = substitutedTypeClass;
            }
          }
          if (typeClass == null) {
            return null;
          }
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
