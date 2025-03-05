// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class JavaPsiConstructorUtil {

  private static final @NotNull TokenSet CONSTRUCTOR_CALL_TOKENS = TokenSet.create(JavaTokenType.SUPER_KEYWORD, JavaTokenType.THIS_KEYWORD);

  /**
   * Finds an explicit call to another constructor within the specified constructor (either chained or super).
   * If there are multiple explicit constructor calls (uncompilable), the first one is returned.
   * This method is Flexible Constructor Bodies (JEP 492) aware.
   *
   * @param constructor constructor to search in
   * @return found this/super constructor method call or null if not found or the supplied method is not a constructor
   */
  public static @Nullable PsiMethodCallExpression findThisOrSuperCallInConstructor(@NotNull PsiMethod constructor) {
    if (!constructor.isConstructor()) return null;
    return CachedValuesManager.getCachedValue(constructor, () -> {
      PsiCodeBlock body = constructor.getBody();
      if (body == null) return new CachedValueProvider.Result<>(null, PsiModificationTracker.MODIFICATION_COUNT);
      Ref<PsiMethodCallExpression> result = new Ref<>();
      PsiTreeUtil.processElements(body, PsiMethodCallExpression.class, call -> {
        if (isConstructorCall(call) && PsiTreeUtil.getParentOfType(call, PsiMethod.class) == constructor) {
          result.set(call);
          return false;
        }
        return true;
      });
      return new CachedValueProvider.Result<>(result.get(), PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  /**
   * Returns true if given element is a chained constructor call.
   *
   * @param call element to check
   * @return true, if the specified element is a chained constructor call
   */
  @Contract("null -> false")
  public static boolean isChainedConstructorCall(@Nullable PsiElement call) {
    if (!(call instanceof PsiMethodCallExpression)) return false;
    PsiElement child = ((PsiMethodCallExpression)call).getMethodExpression().getReferenceNameElement();
    return PsiUtil.isJavaToken(child, JavaTokenType.THIS_KEYWORD);
  }

  /**
   * Checks if the specified element is a super() constructor call.
   *
   * @param element element to check
   * @return true if given element is a {@code super} constructor call
   */
  @Contract("null -> false")
  public static boolean isSuperConstructorCall(@Nullable PsiElement element) {
    if (!(element instanceof PsiMethodCallExpression)) return false;
    PsiElement child = ((PsiMethodCallExpression)element).getMethodExpression().getReferenceNameElement();
    return PsiUtil.isJavaToken(child, JavaTokenType.SUPER_KEYWORD);
  }

  /**
   * Checks if the specified element is a this() or super() constructor call.
   *
   * @param call element to check
   * @return true if given element is {@code this()} or {@code super()} constructor call
   */
  @Contract("null -> false")
  public static boolean isConstructorCall(@Nullable PsiElement call) {
    if (!(call instanceof PsiMethodCallExpression)) return false;
    PsiElement child = ((PsiMethodCallExpression)call).getMethodExpression().getReferenceNameElement();
    return PsiUtil.isJavaToken(child, CONSTRUCTOR_CALL_TOKENS);
  }

  public static PsiMethod findConstructorInSuper(@NotNull PsiMethod constructor) {
    return findConstructorInSuper(constructor, new HashSet<>());
  }

  private static PsiMethod findConstructorInSuper(@NotNull PsiMethod constructor, @NotNull Set<? super PsiMethod> visited) {
    if (!visited.add(constructor)) return null;
    PsiMethodCallExpression call = findThisOrSuperCallInConstructor(constructor);
    if (isSuperConstructorCall(call)) {
      PsiMethod superConstructor = call.resolveMethod();
      if (superConstructor != null) {
        return superConstructor;
      }
    }
    else if (isChainedConstructorCall(call)) {
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

  /**
   * return all constructors which are referred from this constructor by
   * this (...) at the beginning of the constructor body
   *
   * @return referring constructor
   */
  public static @NotNull List<PsiMethod> getChainedConstructors(@NotNull PsiMethod constructor) {
    ConstructorVisitorInfo info = new ConstructorVisitorInfo();
    visitConstructorChain(constructor, info);
    if (info.visitedConstructors != null) info.visitedConstructors.remove(constructor);
    return ObjectUtils.notNull(info.visitedConstructors, Collections.emptyList());
  }

  private static void visitConstructorChain(@NotNull PsiMethod entry, @NotNull ConstructorVisitorInfo info) {
    PsiMethod constructor = entry;
    while (true) {
      PsiMethodCallExpression methodCall = findThisOrSuperCallInConstructor(constructor);
      if (!isChainedConstructorCall(methodCall)) return;
      PsiMethod method = methodCall.resolveMethod();
      if (method == null) return;
      if (info.visitedConstructors != null && info.visitedConstructors.contains(method)) {
        info.recursivelyCalledConstructor = method;
        return;
      }
      if (info.visitedConstructors == null) info.visitedConstructors = new ArrayList<>(5);
      info.visitedConstructors.add(method);
      constructor = method;
    }
  }

  /**
   * @param constructor constructor to check
   * @return true if constructor is called recursively
   */
  public static boolean isRecursivelyCalledConstructor(@NotNull PsiMethod constructor) {
    ConstructorVisitorInfo info = new ConstructorVisitorInfo();
    visitConstructorChain(constructor, info);
    if (info.recursivelyCalledConstructor == null) return false;
    // our constructor is reached from some other constructor by constructor chain
    return info.visitedConstructors.indexOf(info.recursivelyCalledConstructor) <= info.visitedConstructors.indexOf(constructor);
  }

  private static class ConstructorVisitorInfo {
    List<PsiMethod> visitedConstructors;
    PsiMethod recursivelyCalledConstructor;
  }
}
