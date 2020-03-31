// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.lang.jvm.JvmModifier;
import com.intellij.lang.jvm.JvmModifiersOwner;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Not all PsiModifierListOwner inheritors are JvmModifiersOwners, e.g. {@link PsiLocalVariable} or {@link PsiRequiresStatement}.
 * This is a bridge interface between them.
 * <p>
 * Known PsiModifierListOwners which are also JvmModifiersOwners:
 * {@link PsiJvmMember} inheritors, {@link PsiParameter} and {@link PsiPackage}.
 */
public interface PsiJvmModifiersOwner extends PsiModifierListOwner, JvmModifiersOwner {

  @Override
  default PsiAnnotation @NotNull [] getAnnotations() {
    return PsiModifierListOwner.super.getAnnotations();
  }

  @Nullable
  @Override
  default PsiAnnotation getAnnotation(@NotNull @NonNls String fqn) {
    return PsiModifierListOwner.super.getAnnotation(fqn);
  }

  @Override
  default boolean hasAnnotation(@NotNull @NonNls String fqn) {
    return PsiModifierListOwner.super.hasAnnotation(fqn);
  }

  @Override
  default boolean hasModifier(@NotNull JvmModifier modifier) {
    return PsiModifierListOwner.super.hasModifier(modifier);
  }

  @Nullable
  @Override
  default PsiElement getSourceElement() {
    return this;
  }
}
