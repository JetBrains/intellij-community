// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.reference;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * A node in the reference graph corresponding to entities that may contain derived references, e.g. RefMethod.
 */
public interface RefOverridable extends RefJavaElement {
  /**
   * @return the collection of derived references, e.g. RefMethod may contain at least RefMethod, RefFunctionalExpression as derived reference,
   * RefFunctionalExpression contains nothing.
   */
  @NotNull Collection<? extends RefOverridable> getDerivedReferences();

  /**
   * Adds derived reference to collection of derived references.
   */
  void addDerivedReference(@NotNull RefOverridable reference);
}
