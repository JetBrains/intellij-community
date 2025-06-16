@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.jps.dependency.impl

import kotlinx.collections.immutable.persistentHashSetOf
import org.jetbrains.jps.dependency.BackDependencyIndex
import org.jetbrains.jps.dependency.Delta
import org.jetbrains.jps.dependency.Node
import org.jetbrains.jps.dependency.NodeSource
import org.jetbrains.jps.dependency.ReferenceID

internal class SourceOnlyDelta(
  private val baseSources: Set<NodeSource>,
  private val deletedSources: Set<NodeSource>,
) : Delta {
  override fun isSourceOnly(): Boolean = true

  override fun getBaseSources() = baseSources

  override fun getDeletedSources() = deletedSources

  override fun associate(node: Node<*, *>, sources: Iterable<NodeSource>) = throw UnsupportedOperationException("Not used")

  override fun getIndices(): Iterable<BackDependencyIndex> = throw UnsupportedOperationException("Not used")

  override fun getIndex(name: String?): BackDependencyIndex? = null

  override fun getDependingNodes(id: ReferenceID) = persistentHashSetOf<ReferenceID>()

  override fun getSources(id: ReferenceID) = persistentHashSetOf<NodeSource>()

  override fun getRegisteredNodes() = persistentHashSetOf<ReferenceID>()

  override fun getSources() = persistentHashSetOf<NodeSource>()

  override fun getNodes(source: NodeSource) = persistentHashSetOf<Node<*, *>>()
}