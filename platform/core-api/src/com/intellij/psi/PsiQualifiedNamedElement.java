// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import org.jetbrains.annotations.Nullable;

/**
 * A variation of a named PSI element.
 *
 * @author Konstantin Bulenkov
 * @since 9.0
 */
public interface PsiQualifiedNamedElement extends PsiNamedElement {
  /**
   * Returns the fully qualified name of the element, or {@code null}.
   */
  @Nullable String getQualifiedName();

  /**
   * Returns the name of the element, or {@code null}.
   */
  @Override
  @Nullable String getName();
}