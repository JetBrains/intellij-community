// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import org.jetbrains.annotations.Nullable;

/**
 * Represents a {@code requires} directive of a Java module declaration.
 */
public interface PsiRequiresStatement extends PsiModifierListOwner, PsiStatement {
  PsiRequiresStatement[] EMPTY_ARRAY = new PsiRequiresStatement[0];

  @Nullable PsiJavaModuleReferenceElement getReferenceElement();

  @Nullable String getModuleName();
  @Nullable PsiJavaModuleReference getModuleReference();

  default @Nullable PsiJavaModule resolve() {
    PsiJavaModuleReference ref = getModuleReference();
    return ref != null ? ref.resolve() : null;
  }
}