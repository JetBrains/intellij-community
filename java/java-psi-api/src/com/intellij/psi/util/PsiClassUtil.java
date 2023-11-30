// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
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

  @NotNull
  public static Comparator<PsiClass> createScopeComparator(@NotNull GlobalSearchScope scope) {
    return Comparator.comparing(c -> PsiUtilCore.getVirtualFile(c),
                                Comparator.nullsFirst((file1, file2) -> scope.compare(file2, file1)));
  }
}