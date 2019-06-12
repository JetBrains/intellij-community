// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import org.jetbrains.annotations.Nullable;

/**
 * Represents a member of a Java class (for example, a field or a method).
 */
public interface PsiMember extends PsiModifierListOwner, NavigatablePsiElement {
  /**
   * The empty array of PSI members which can be reused to avoid unnecessary allocations.
   */
  PsiMember[] EMPTY_ARRAY = new PsiMember[0];

  /**
   * Returns the class containing the member.
   *
   * @return the containing class.
   */
  @Nullable
  PsiClass getContainingClass();
}
