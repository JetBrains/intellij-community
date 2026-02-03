// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.runner;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An extension that can provide a custom entry point for Java application (like a {@code main()} method).
 * For example, a custom framework (like JavaFX) may have a separate entry point.
 * These extensions are used in some methods in {@link PsiMethodUtil}, like {@link PsiMethodUtil#hasMainMethod(PsiClass)},
 * and can affect the behavior of various IDE features.
 * <p>
 * The methods of this extension might be called while the IDE is in {@linkplain com.intellij.openapi.project.DumbService dumb mode}.
 */
public interface JavaMainMethodProvider extends PossiblyDumbAware {

  ExtensionPointName<JavaMainMethodProvider> EP_NAME = ExtensionPointName.create("com.intellij.javaMainMethodProvider");

  /**
   * @param clazz class to check
   * @return true if this provider is applicable for a given class.
   */
  @Contract(pure = true)
  boolean isApplicable(@NotNull PsiClass clazz);

  /**
   * @param clazz class to check
   * @return true if the supplied class has the entry point method. The results are unspecified if
   * {@link #isApplicable(PsiClass)} method returns false for the same class.
   */
  @Contract(pure = true)
  boolean hasMainMethod(@NotNull PsiClass clazz);

  /**
   * @param clazz class to check
   * @return a method of given class that is the entry point method, null if there's no entry point method
   * in a given class. The results are unspecified if {@link #isApplicable(PsiClass)}
   * method returns false for the same class.
   */
  @Contract(pure = true)
  @Nullable PsiMethod findMainInClass(@NotNull PsiClass clazz);

  /**
   * @param clazz class to check
   * @return a pretty class name. e.x. for Kotlin it allows returning `MyClass` instead of `MyClass.Companion`
   * @deprecated Use {@link #getMainClassQualifiedName(PsiClass)} instead.
   */
  @Deprecated
  @Contract(pure = true)
  default @Nullable String getMainClassName(@NotNull PsiClass clazz) {
    return ClassUtil.getJVMClassName(clazz);
  }

  /**
   * @param clazz class to check
   * @return a "prettified" qualified class name. E.g., for Kotlin it allows returning `MyClass` instead of `MyClass.Companion`
   */
  @Contract(pure = true)
  default @Nullable String getMainClassQualifiedName(@NotNull PsiClass clazz) {
    return clazz.getQualifiedName();
  }

  /**
   * @param psiElement element to check
   * @return true if the given element or its parent is the main method, false otherwise
   */
  @Contract(pure = true)
  default boolean isMain(@NotNull PsiElement psiElement) {
    PsiMethod psiMethod = PsiTreeUtil.getParentOfType(psiElement, PsiMethod.class);
    if (psiMethod == null) return false;

    return "main".equals(psiMethod.getName()) && PsiMethodUtil.isMainMethod(psiMethod);
  }
}
