// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl

import com.intellij.lang.ASTNode
import org.jetbrains.annotations.ApiStatus

/**
 * The result of the node reparse in [BlockSupportImpl.reparseIncrementally].
 */
@ApiStatus.Internal
sealed interface NodeReparse {

  /** The node reparse replaces [oldNode] with [newNode]. */
  class Replaced internal constructor(
    val oldNode: ASTNode,
    val newNode: ASTNode,
  ) : NodeReparse

  /** The node reparse failed, and the walk stops. It never leaves [BlockSupportImpl.reparseIncrementally]. */
  object Stop : NodeReparse
}
