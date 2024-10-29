// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree

import org.jetbrains.annotations.ApiStatus
import javax.swing.tree.TreePath

@ApiStatus.Experimental
interface SuspendingTreeVisitor {
  suspend fun visit(path: TreePath): TreeVisitor.Action
}
