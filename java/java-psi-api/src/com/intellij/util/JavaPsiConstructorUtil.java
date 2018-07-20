// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class JavaPsiConstructorUtil {
  /**
   * Finds call to another constructor within this constructor (either chained or super)
   * @param constructor constructor to search in
   * @return found this/super constructor method call or null if not found or supplied method is null or not a constructor
   */
  @Contract("null -> null")
  @Nullable
  public static PsiMethodCallExpression findThisOrSuperCallInConstructor(@Nullable PsiMethod constructor) {
    if (constructor == null || !constructor.isConstructor()) return null;
    PsiCodeBlock body = constructor.getBody();
    if (body == null) return null;
    PsiElement bodyElement = body.getFirstBodyElement();
    while (bodyElement != null && !(bodyElement instanceof PsiStatement)) {
      bodyElement = bodyElement.getNextSibling();
    }
    if (!(bodyElement instanceof PsiExpressionStatement)) return null;
    PsiMethodCallExpression call =
      ObjectUtils.tryCast(((PsiExpressionStatement)bodyElement).getExpression(), PsiMethodCallExpression.class);
    if (isConstructorCall(call)) return call;
    return null;
  }

  /**
   * Returns true if given element is a chained constructor call
   * @param call element to check
   * @return true if given element is a chained constructor call
   */
  @Contract("null -> false")
  public static boolean isChainedConstructorCall(@Nullable PsiElement call) {
    if (!(call instanceof PsiMethodCallExpression)) return false;
    PsiElement child = ((PsiMethodCallExpression)call).getMethodExpression().getReferenceNameElement();
    return child instanceof PsiKeyword && child.textMatches(PsiKeyword.THIS);
  }

  /**
   * Returns true if given element is a super constructor call
   * @param call element to check
   * @return true if given element is a super constructor call
   */
  @Contract("null -> false")
  public static boolean isSuperConstructorCall(@Nullable PsiElement call) {
    if (!(call instanceof PsiMethodCallExpression)) return false;
    PsiElement child = ((PsiMethodCallExpression)call).getMethodExpression().getReferenceNameElement();
    return child instanceof PsiKeyword && child.textMatches(PsiKeyword.SUPER);
  }

  /**
   * Returns true if given element is chained or super constructor call
   * @param call element to check
   * @return true if given element is chained or super constructor call
   */
  @Contract("null -> false")
  public static boolean isConstructorCall(@Nullable PsiElement call) {
    if (!(call instanceof PsiMethodCallExpression)) return false;
    PsiElement child = ((PsiMethodCallExpression)call).getMethodExpression().getReferenceNameElement();
    return child instanceof PsiKeyword && (child.textMatches(PsiKeyword.SUPER) || child.textMatches(PsiKeyword.THIS));
  }

  public static PsiMethod findConstructorInSuper(PsiMethod constructor) {
    return findConstructorInSuper(constructor, new HashSet<>());
  }

  private static PsiMethod findConstructorInSuper(PsiMethod constructor, Set<PsiMethod> visited) {
    if (visited.contains(constructor)) return null;
    visited.add(constructor);
    PsiMethodCallExpression call = findThisOrSuperCallInConstructor(constructor);
    if (isSuperConstructorCall(call)) {
      PsiMethod superConstructor = call.resolveMethod();
      if (superConstructor != null) {
        return superConstructor;
      }
    } else if (isChainedConstructorCall(call)) {
      PsiMethod chainedConstructor = call.resolveMethod();
      if (chainedConstructor != null) {
        return findConstructorInSuper(chainedConstructor, visited);
      }
      return null;
    }

    PsiClass containingClass = constructor.getContainingClass();
    if (containingClass != null) {
      PsiClass superClass = containingClass.getSuperClass();
      if (superClass != null && superClass.getName() != null) {
        MethodSignature defConstructor = MethodSignatureUtil.createMethodSignature(superClass.getName(), PsiType.EMPTY_ARRAY,
                                                                                   PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY, true);
        return MethodSignatureUtil.findMethodBySignature(superClass, defConstructor, false);
      }
    }
    return null;
  }
}
