@file:Suppress("SSBasedInspection", "ReplaceGetOrSet")

package org.jetbrains.jps.dependency.impl

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import kotlinx.collections.immutable.PersistentSet
import org.h2.mvstore.MVMap
import org.jetbrains.jps.dependency.BackDependencyIndex
import org.jetbrains.jps.dependency.MultiMaplet
import org.jetbrains.jps.dependency.Node
import org.jetbrains.jps.dependency.ReferenceID
import org.jetbrains.jps.dependency.java.JvmNodeReferenceID
import org.jetbrains.jps.dependency.storage.EnumeratedStringDataType
import org.jetbrains.jps.dependency.storage.EnumeratedStringSetValueDataType
import org.jetbrains.jps.dependency.storage.MvStoreContainerFactory

abstract class BackDependencyIndexImpl protected constructor(
  private val name: String,
  mapletFactory: MvStoreContainerFactory,
  isInMemory: Boolean,
) : BackDependencyIndex {
  private val map: MultiMaplet<ReferenceID, ReferenceID>

  init {
    if (isInMemory) {
      map = mapletFactory.openInMemoryMap()
    }
    else {
      val mapBuilder = MVMap.Builder<JvmNodeReferenceID, PersistentSet<JvmNodeReferenceID>>()
        .keyType(EnumeratedStringDataType(mapletFactory.getStringEnumerator(), JvmNodeReferenceIdEnumeratedStringDataTypeExternalizer))
        .valueType(EnumeratedStringSetValueDataType(mapletFactory.getStringEnumerator(), JvmNodeReferenceIdEnumeratedStringDataTypeExternalizer))
      @Suppress("UNCHECKED_CAST")
      map = mapletFactory.openMap(name, mapBuilder) as MultiMaplet<ReferenceID, ReferenceID>
    }
  }

  /**
   * @param node to be indexed
   * @return direct dependencies for the given node, which should be indexed by this index
   */
  protected abstract fun getIndexedDependencies(node: Node<*, *>): Sequence<ReferenceID>

  final override fun getName(): String = name

  final override fun getKeys(): Iterable<ReferenceID> = throw UnsupportedOperationException("Not used")

  final override fun getDependencies(id: ReferenceID): Iterable<ReferenceID> = map.get(id)

  final override fun indexNode(node: Node<*, *>) {
    val nodeId = node.referenceID
    for (referentId in getIndexedDependencies(node)) {
      map.appendValue(referentId, nodeId)
    }
  }

  final override fun integrate(deletedNodes: Iterable<Node<*, *>>, updatedNodes: Iterable<Node<*, *>>, deltaIndex: BackDependencyIndex) {
    val depsToRemove = Object2ObjectOpenHashMap<ReferenceID, ObjectOpenHashSet<ReferenceID>>()

    for (node in deletedNodes) {
      collectDepsToRemove(node, depsToRemove)
      // corner case, relevant to situations when keys in this index are actually real node IDs
      // if a node gets deleted, the corresponding index key gets deleted to: this allows to ensure there is no outdated information in the index
      // If later a new node with the same ID is added, the previous index data for this ID will not interfere with the new state.
      map.remove(node.referenceID)
    }

    if (deltaIndex !is BackDependencyIndexImpl) {
      // the only another impl is empty (BackDependencyIndex.createEmpty)
      return
    }

    for (node in updatedNodes) {
      collectDepsToRemove(node, depsToRemove)
    }

    val deltaMap = deltaIndex.map
    val iterator = if (depsToRemove.isEmpty()) {
      deltaMap.keys.iterator()
    }
    else {
      ((deltaMap.keys.asSequence() + depsToRemove.keys).distinct()).iterator()
    }
    for (id in iterator) {
      val toRemove = depsToRemove.get(id)
      val toAdd = deltaMap.get(id)
      if (!toRemove.isNullOrEmpty()) {
        toRemove.removeAll(toAdd as Set<*>)
        if (toRemove.isNotEmpty()) {
          map.removeValues(id, toRemove)
        }
      }
      map.appendValues(id, toAdd)
    }
  }

  private fun collectDepsToRemove(node: Node<*, *>, depsToRemove: MutableMap<ReferenceID, ObjectOpenHashSet<ReferenceID>>) {
    val nodeId = node.referenceID
    for (referentId in getIndexedDependencies(node)) {
      depsToRemove.computeIfAbsent(referentId) { ObjectOpenHashSet() }.add(nodeId)
    }
  }
}

class NodeDependenciesIndex(
  mapletFactory: MvStoreContainerFactory,
  isInMemory: Boolean,
) : BackDependencyIndexImpl("node-backward-dependencies", mapletFactory, isInMemory) {
  override fun getIndexedDependencies(node: Node<*, *>): Sequence<ReferenceID> {
    val nodeId = node.referenceID
    return node.usages().filter { nodeId != it.elementOwner }.map { it.elementOwner }.distinct()
  }
}