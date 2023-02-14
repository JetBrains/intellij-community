package com.intellij.analysis.problemsView.toolWindow

import java.util.WeakHashMap

internal class ProblemsNodeCache<T>(private val producer: (T) -> Node) {
  private val nodesWeak = WeakHashMap<T, Node>()
  private val nodesSync = Any()

  fun getNodes(keys: Collection<T>): List<Node> {
    synchronized(nodesSync) {
      val nodes: List<Node> = keys.mapNotNull {
        return@mapNotNull nodesWeak.computeIfAbsent(it) { k -> producer(k) }
      }
      return nodes
    }
  }
}
