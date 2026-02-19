// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree.ui

import org.jetbrains.annotations.ApiStatus
import java.awt.Rectangle
import javax.swing.JTree
import javax.swing.tree.TreePath

/**
 * Interface for a custom [javax.swing.plaf.TreeUI] which supplies bounds for a tree node painter which differ from [javax.swing.plaf.TreeUI.getPathBounds]
 */
@ApiStatus.Experimental
interface CustomBoundsTreeUI {
  fun getActualPathBounds(tree: JTree, path: TreePath): Rectangle?
}