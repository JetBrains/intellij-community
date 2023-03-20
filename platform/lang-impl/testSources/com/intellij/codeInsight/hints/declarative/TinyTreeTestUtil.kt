// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative

import com.intellij.codeInsight.hints.declarative.impl.util.TinyTree

internal data class TinyTreeDebugNode<T>(val payload: Byte, val data: T, val children: MutableList<TinyTreeDebugNode<T>>) {
  companion object {
    fun <T> buildDebugTree(tree: TinyTree<T>): TinyTreeDebugNode<T> {
      return buildTreeForIndex(tree, 0)
    }

    private fun <T> buildTreeForIndex(tree: TinyTree<T>, index: Byte) : TinyTreeDebugNode<T> {
      val children = ArrayList<TinyTreeDebugNode<T>>()
      tree.processChildren(index) {
        children.add(buildTreeForIndex(tree, it))
        true
      }
      return TinyTreeDebugNode(tree.getBytePayload(index), tree.getDataPayload(index), children)
    }

  }
}