// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.tree.util

import com.intellij.lang.ASTNode

fun ASTNode.children(): Sequence<ASTNode> {
  return generateSequence(firstChildNode) { it.treeNext }
}

fun ASTNode.parents(withSelf: Boolean): Sequence<ASTNode> {
  return generateSequence(if (withSelf) this else treeParent) { it.treeParent }
}

fun ASTNode.siblings(forward: Boolean, withSelf: Boolean): Sequence<ASTNode> {
  return when {
    forward -> generateSequence(if (withSelf) this else treeNext) { it.treeNext }
    else -> generateSequence(if (withSelf) this else treePrev) { it.treePrev }
  }
}
