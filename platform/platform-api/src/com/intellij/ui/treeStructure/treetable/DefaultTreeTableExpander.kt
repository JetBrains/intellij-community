// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.treeStructure.treetable

import com.intellij.ide.DefaultTreeExpander
import javax.swing.JTree

open class DefaultTreeTableExpander(table: TreeTable) : DefaultTreeExpander({ table.tree }) {
  override fun isShowing(tree: JTree) = (tree as? TreeTableTree)?.treeTable?.isShowing ?: false
}
