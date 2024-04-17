// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 */
public final class JShellPsiAugmentProvider extends PsiAugmentProvider{
  private static final Set<String> JSHELL_FIELD_MODIFIERS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(PsiModifier.PUBLIC, PsiModifier.STATIC)));
  @Override
  protected @NotNull Set<String> transformModifiers(@NotNull PsiModifierList modifierList, @NotNull Set<String> modifiers) {
    // enforce permanent field modifiers for all variables declared at top-level
    return isInsideJShellField(modifierList)? JSHELL_FIELD_MODIFIERS : modifiers;
  }

  private static boolean isInsideJShellField(PsiElement element) {
    PsiElement parent = element.getParent();
    return parent instanceof PsiField && parent.getParent() instanceof PsiJShellRootClass;
  }
}
