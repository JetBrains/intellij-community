// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PsiMethodUtil {
  /// Returns a predicate that evaluates to true when passed a class that _can be_ a main class, i.e., a class that has a main method.
  ///
  /// It does not check whether the class actually has a main method. For that, use [#hasMainMethod(PsiClass)].
  public static final Condition<@NotNull PsiClass> MAIN_CLASS = JvmMainMethodSearcher.MAIN_CLASS;

  private PsiMethodUtil() { }

  public static @Nullable PsiMethod findMainMethod(@NotNull final PsiClass aClass) {
    return JavaMainMethodSearcher.INSTANCE.findMainMethod(aClass);
  }

  /**
   * see {@link JavaMainMethodSearcher#findMainMethodInClassOrParent(PsiClass)}
   */
  public static @Nullable PsiMethod findMainMethodInClassOrParent(PsiClass aClass) {
    return JavaMainMethodSearcher.INSTANCE.findMainMethodInClassOrParent(aClass);
  }

  /**
   * see {@link JavaMainMethodSearcher#isMainMethod(PsiMethod)}
   */
  @Contract("null -> false")
  public static boolean isMainMethod(final @Nullable PsiMethod method) {
    return JavaMainMethodSearcher.INSTANCE.isMainMethod(method);
  }

  /**
   * see {@link JavaMainMethodSearcher#hasMainMethod(PsiClass)}
   */
  public static boolean hasMainMethod(final PsiClass psiClass) {
    return JavaMainMethodSearcher.INSTANCE.hasMainMethod(psiClass);
  }

  /**
   * see {@link JavaMainMethodSearcher#isMainMethodWithProvider(PsiClass, PsiElement)}
   */
  public static boolean isMainMethodWithProvider(@NotNull PsiClass psiClass, @NotNull PsiElement psiElement) {
    return JavaMainMethodSearcher.INSTANCE.isMainMethodWithProvider(psiClass, psiElement);
  }

  /**
   * see {@link JavaMainMethodSearcher#getMainJVMClassName(PsiClass)}
   *
   * @deprecated Use {@link #getMainClassQualifiedName(PsiClass)} instead.
   */
  @Deprecated
  public static @Nullable String getMainJVMClassName(@NotNull PsiClass psiClass) {
    return JavaMainMethodSearcher.INSTANCE.getMainJVMClassName(psiClass);
  }

  /**
   * see {@link JavaMainMethodSearcher#getMainClassQualifiedName(PsiClass)}
   */
  public static @Nullable String getMainClassQualifiedName(@NotNull PsiClass psiClass) {
    return JavaMainMethodSearcher.INSTANCE.getMainClassQualifiedName(psiClass);
  }

  /**
   * see {@link JavaMainMethodSearcher#findMainInClass(PsiClass)
   */
  public static @Nullable PsiMethod findMainInClass(final @NotNull PsiClass aClass) {
    return JavaMainMethodSearcher.INSTANCE.findMainInClass(aClass);
  }

  /**
   * see {@link JavaMainMethodSearcher#hasMainInClass(PsiClass)}
   */
  public static boolean hasMainInClass(final @NotNull PsiClass aClass) {
    return JavaMainMethodSearcher.INSTANCE.hasMainInClass(aClass);
  }
}
