// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.treeView

/**
 * An interface for tree nodes to control double-click behavior.
 * 
 * By default, if a node can be expanded, it will be expanded on double-click.
 * Sometimes it's not the desired behavior. For example, when a file is shown, it's usually preferable to open it, even if its node has children (file members).
 * In such situations, the node should implement this interface and return `false`.
 */
interface ExpandOnDoubleClickSupport {
  fun expandOnDoubleClick(): Boolean = true
}
