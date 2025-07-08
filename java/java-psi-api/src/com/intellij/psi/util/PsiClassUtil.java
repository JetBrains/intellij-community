// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public final class PsiClassUtil {
  private PsiClassUtil() { }

  public static boolean isRunnableClass(PsiClass aClass, boolean mustBePublic) {
    return isRunnableClass(aClass, mustBePublic, true);
  }

  public static boolean isRunnableClass(PsiClass aClass, boolean mustBePublic, boolean mustNotBeAbstract) {
    if (aClass instanceof PsiAnonymousClass) return false;
    if (mustBePublic && !aClass.hasModifierProperty(PsiModifier.PUBLIC)) {
      if (mustNotBeAbstract || !aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return false;
      }
    }
    if (aClass.hasModifierProperty(PsiModifier.PRIVATE)) return false;
    if (mustNotBeAbstract && aClass.hasModifierProperty(PsiModifier.ABSTRACT)) return false;
    return aClass.getContainingClass() == null ||
           aClass.hasModifierProperty(PsiModifier.STATIC) ||
           aClass.hasModifierProperty(PsiModifier.ABSTRACT);
  }

  public static @NotNull Comparator<PsiClass> createScopeComparator(@NotNull GlobalSearchScope scope) {
    return Comparator.comparing(c -> PsiUtilCore.getVirtualFile(c),
                                Comparator.nullsFirst((file1, file2) -> scope.compare(file2, file1)));
  }

  /**
   * Checks if the given class is a throwable. The behavior is unspecified if the class or any of its superclasses is malformed.
   * 
   * @param psiClass class to test
   * @return true if class is {@code java.lang.Throwable} or legally inherits from it.
   */
  public static boolean isThrowable(@NotNull PsiClass psiClass) {
    if (psiClass.isInterface()) return false;
    if (psiClass.getTypeParameters().length > 0) return false; // Valid throwables are never generic
    if (CommonClassNames.JAVA_LANG_THROWABLE.equals(psiClass.getQualifiedName())) return true;
    PsiClass throwableClass =
      JavaPsiFacade.getInstance(psiClass.getProject()).findClass(CommonClassNames.JAVA_LANG_THROWABLE, psiClass.getResolveScope());
    if (throwableClass == null) return false;
    return psiClass.isInheritor(throwableClass, true);
  }
}