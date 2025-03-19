// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.lang.jvm.JvmModifier;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.PsiJvmConversionHelper.hasListModifier;

/**
 * Represents a PSI element which has a list of modifiers (public/private/protected/etc.)
 * and annotations.
 */
public interface PsiModifierListOwner extends PsiElement {

  /**
   * Returns the list of modifiers for the element.
   *
   * @return the list of modifiers, or null if the element (for example, an anonymous
   * inner class) does not have the list of modifiers.
   */
  @Nullable
  PsiModifierList getModifierList();

  /**
   * Checks if the element has the specified modifier. Possible modifiers are defined
   * as constants in the {@link PsiModifier} class.
   *
   * @param name the name of the modifier to check.
   * @return true if the element has the modifier, false otherwise
   */
  boolean hasModifierProperty(@PsiModifier.ModifierConstant @NonNls @NotNull String name);

  default PsiAnnotation @NotNull [] getAnnotations() {
    return PsiJvmConversionHelper.getListAnnotations(this);
  }

  default @Nullable PsiAnnotation getAnnotation(@NotNull String fqn) {
    return PsiJvmConversionHelper.getListAnnotation(this, fqn);
  }

  default boolean hasAnnotation(@NotNull String fqn) {
    return PsiJvmConversionHelper.hasListAnnotation(this, fqn);
  }

  default boolean hasModifier(@NotNull JvmModifier modifier) {
    return hasListModifier(this, modifier);
  }
}
