// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.core;

import com.intellij.openapi.util.Couple;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Utilities related to Java methods
 */
public final class JavaPsiMethodUtil {
  /**
   * @param aClass class to analyze
   * @param overrideEquivalentSuperMethods collection of override-equivalent super methods
   * @return a couple of unrelated methods from the collection (either both default, or default and abstract),
   * so the absence of a method declaration in a current class leads to ambiguous inheritance. 
   * Returns null if no such a couple is found.
   */
  public static @Nullable Couple<@NotNull PsiMethod> getUnrelatedSuperMethods(
    @NotNull PsiClass aClass, @NotNull Collection<? extends PsiMethod> overrideEquivalentSuperMethods) {
    if (overrideEquivalentSuperMethods.size() <= 1) return null;
    List<PsiMethod> defaults = null;
    PsiMethod abstractMethod = null;
    for (PsiMethod method : overrideEquivalentSuperMethods) {
      boolean isDefault = method.hasModifierProperty(PsiModifier.DEFAULT);
      boolean isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);
      boolean isStatic = method.hasModifierProperty(PsiModifier.STATIC);
      if (!isDefault && !isAbstract && !isStatic) return null;
      if (isDefault) {
        if (defaults == null) defaults = new ArrayList<>(2);
        defaults.add(method);
      }
      if (isAbstract && abstractMethod == null) {
        abstractMethod = method;
      }
    }
    if (defaults == null) return null;
    PsiMethod defaultMethod = defaults.get(0);
    PsiClass defaultMethodContainingClass = defaultMethod.getContainingClass();
    if (defaultMethodContainingClass == null) return null;
    if (abstractMethod == null) {
      return findUnrelatedCouple(defaults);
    }
    PsiClass abstractMethodContainingClass = abstractMethod.getContainingClass();
    if (abstractMethodContainingClass == null) return null;
    if (aClass.isInterface() || abstractMethodContainingClass.isInterface()) {
      Couple<@NotNull PsiMethod> unrelatedCouple = findUnrelatedCouple(defaults);
      if (unrelatedCouple != null) {
        return unrelatedCouple;
      }
      if (hasNotOverriddenAbstract(defaults, abstractMethodContainingClass)) {
        return Couple.of(defaults.get(0), abstractMethod);
      }
    }
    return null;
  }

  /**
   * @param aClass a class to analyze
   * @param overrideEquivalentSuperMethods collection of override-equivalent super methods
   * @return an abstract method from the supplied collection that must be implemented, because an override-equivalent
   * default method is present, and the ambiguity must be resolved.
   */
  public static @Nullable PsiMethod getAbstractMethodToImplementWhenDefaultPresent(
    @NotNull PsiClass aClass, @NotNull Collection<? extends PsiMethod> overrideEquivalentSuperMethods) {
    if (aClass.hasModifierProperty(PsiModifier.ABSTRACT) || aClass instanceof PsiTypeParameter) return null;
    if (overrideEquivalentSuperMethods.size() <= 1) return null;
    PsiMethod abstractMethod = null;
    PsiMethod defaultMethod = null;
    for (PsiMethod method : overrideEquivalentSuperMethods) {
      if (method.hasModifierProperty(PsiModifier.DEFAULT)) {
        if (defaultMethod == null) defaultMethod = method;
      }
      else if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        if (abstractMethod == null) abstractMethod = method;
      }
      else if (!method.hasModifierProperty(PsiModifier.STATIC)) {
        return null;
      }
    }
    if (abstractMethod == null || defaultMethod == null) return null;
    PsiClass abstractMethodContainingClass = abstractMethod.getContainingClass();
    if (abstractMethodContainingClass == null || !abstractMethodContainingClass.isInterface()) return null;
    PsiClass defaultMethodContainingClass = defaultMethod.getContainingClass();
    if (defaultMethodContainingClass == null) return null;
    if (defaultMethodContainingClass.isInheritor(abstractMethodContainingClass, true)) {
      MethodSignature unrelatedMethodSignature = abstractMethod.getSignature(
        TypeConversionUtil.getSuperClassSubstitutor(abstractMethodContainingClass, defaultMethodContainingClass, PsiSubstitutor.EMPTY));
      if (MethodSignatureUtil.isSubsignature(unrelatedMethodSignature, defaultMethod.getSignature(PsiSubstitutor.EMPTY))) {
        return null;
      }
    }
    return abstractMethod;
  }

  private static @Nullable Couple<@NotNull PsiMethod> findUnrelatedCouple(List<PsiMethod> methods) {
    if (methods.size() <= 1) return null;
    List<PsiClass> classes = ContainerUtil.map(methods, method -> method.getContainingClass());
    ArrayList<PsiMethod> resultMethods = new ArrayList<>(methods);
    for (PsiClass aClass1 : classes) {
      resultMethods.removeIf(method -> aClass1.isInheritor(requireNonNull(method.getContainingClass()), true));
    }

    if (resultMethods.size() > 1) {
      return Couple.of(resultMethods.get(0), resultMethods.get(1));
    }
    return null;
  }

  private static boolean belongToOneHierarchy(@Nullable PsiClass class1, @Nullable PsiClass class2) {
    return class1 != null && class2 != null &&
           (class1.isInheritor(class2, true) || class2.isInheritor(class1, true));
  }

  private static boolean hasNotOverriddenAbstract(@NotNull List<PsiMethod> defaults, @NotNull PsiClass abstractMethodContainingClass) {
    return !ContainerUtil.exists(defaults, method -> belongToOneHierarchy(method.getContainingClass(), abstractMethodContainingClass));
  }
}
