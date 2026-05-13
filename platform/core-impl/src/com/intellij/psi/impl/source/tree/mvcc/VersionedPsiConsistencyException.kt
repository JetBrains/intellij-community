// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.mvcc

/**
 * This exception indicates that some invariant of versioned PSI is broken.
 */
internal sealed class VersionedPsiConsistencyException(message: String) : RuntimeException(message) {

  /**
   * The invariant in [com.intellij.psi.FileViewProvider] is that physical files always contain versioned trees.
   * Non-physical files can contain any kind of tree.
   */
  class ViewProvider(message: String) : VersionedPsiConsistencyException(message)

  /**
   * At any given moment, either all elements inside a particular PSI tree are versioned, or they all are non-versioned.
   */
  class TreeElement(message: String) : VersionedPsiConsistencyException(message)
}
