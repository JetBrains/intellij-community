// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.lang.jvm.JvmModifier;
import com.intellij.lang.jvm.JvmModifiersOwner;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.PsiJvmConversionHelper.*;

/**
 * Represents a PSI element which has a list of modifiers (public/private/protected/etc.)
 * and annotations.
 */
public interface PsiModifierListOwner extends PsiElement, JvmModifiersOwner {
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

  @NotNull
  @Override
  default PsiAnnotation[] getAnnotations() {
    return getListAnnotations(this);
  }

  @Nullable
  @Override
  default PsiAnnotation getAnnotation(@NotNull @NonNls String fqn) {
    return getListAnnotation(this, fqn);
  }

  @Override
  default boolean hasAnnotation(@NotNull @NonNls String fqn) {
    return hasListAnnotation(this, fqn);
  }

  @Override
  default boolean hasModifier(@NotNull JvmModifier modifier) {
    return hasListModifier(this, modifier);
  }

  @Nullable
  @Override
  default PsiElement getSourceElement() {
    return this;
  }
}
