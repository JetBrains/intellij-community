package com.intellij.analysis.problemsView.toolWindow

internal class ProblemsNodeCache<T>(private val producer: (T) -> Node) {
  private val nodes = mutableMapOf<T, CacheEntry>()

  fun getNodes(keys: Collection<T>): List<Node> {
    nodes.values.forEach { it.isValid = false }
    val children = keys.mapNotNull { nodes.computeIfAbsent(it) { key -> CacheEntry(producer(key), true) } }
    children.forEach { it.isValid = true }
    nodes.values.removeIf { !it.isValid }
    return children.map { it.node }
  }

  private data class CacheEntry(val node: Node, var isValid: Boolean)
}
