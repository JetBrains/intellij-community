/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diagnostic.hprof.util

import com.intellij.diagnostic.hprof.analysis.AnalysisConfig
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import java.util.*

class TreeVisualizer {

  private class IndentStore {
    private val map = Int2ObjectOpenHashMap<String>()

    fun getIndentForDepth(size: Int): String {
      if (map.containsKey(size)) {
        return map[size]
      }
      val indent = "  ".repeat(size)
      map[size] = indent
      return indent
    }
  }

  fun visualizeTree(root: TreeNode, buffer: TruncatingPrintBuffer, options: AnalysisConfig.DisposerTreeSummaryOptions) {
    data class StackItem(val node: TreeNode, val depth: Int)

    val nodeStack = Stack<StackItem>().apply { push(StackItem(root, 0)) }
    val indentStore = IndentStore()
    while (!nodeStack.empty()) {
      val (node, depth) = nodeStack.pop()
      val indent = indentStore.getIndentForDepth(depth)
      buffer.println(indent + node.description())
      val children = node.children().reversed()

      if (depth >= options.maxDepth && children.isNotEmpty()) {
        buffer.println(indentStore.getIndentForDepth(depth + 1) + "[...]")
      }
      else {
        children.forEach { child -> nodeStack.push(StackItem(child, depth + 1)) }
      }
    }
  }
}