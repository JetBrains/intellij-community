// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree

import com.intellij.ui.tree.TreeVisitor.Action
import org.jetbrains.annotations.ApiStatus
import javax.swing.tree.TreePath

/**
 * A complicated visitor for mixing EDT/BGT logic.
 *
 * It's intended to be used this way: call [preVisitEDT] on the EDT first,
 * and if it returns a non-null value, consider it to be the end result
 * of visiting the node. Otherwise, call [visit] on either the EDT or a BGT
 * (depending on what [visitThread] returns) and then pass its
 * result to [postVisitEDT] on the EDT and consider its return value to be
 * the end result.
 */
@ApiStatus.Internal
interface EdtBgtTreeVisitor : TreeVisitor {

  fun preVisitEDT(path: TreePath): Action?

  override fun visit(path: TreePath): Action // overridden for readability

  fun postVisitEDT(path: TreePath, visitResult: Action): Action

}
