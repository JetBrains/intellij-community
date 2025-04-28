// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SSBasedInspection", "ReplaceGetOrSet")

package org.jetbrains.jps.dependency.impl

import androidx.collection.MutableLongObjectMap
import androidx.collection.MutableScatterSet
import kotlinx.collections.immutable.PersistentSet
import org.h2.mvstore.MVMap
import org.jetbrains.bazel.jvm.mvStore.EnumeratedStringSetValueDataType
import org.jetbrains.bazel.jvm.mvStore.LongDataType
import org.jetbrains.jps.dependency.BackDependencyIndex
import org.jetbrains.jps.dependency.Node
import org.jetbrains.jps.dependency.ReferenceID
import org.jetbrains.jps.dependency.java.JvmNodeReferenceID
import org.jetbrains.jps.dependency.storage.MultiMapletEx
import org.jetbrains.jps.dependency.storage.MvStoreContainerFactory

abstract class BackDependencyIndexImpl protected constructor(
  private val name: String,
  mapletFactory: MvStoreContainerFactory,
  isInMemory: Boolean,
) : BackDependencyIndex {
  private val map: MultiMapletEx<Long, ReferenceID>

  init {
    if (isInMemory) {
      map = mapletFactory.openInMemoryMap()
    }
    else {
      val mapBuilder = MVMap.Builder<Long, PersistentSet<JvmNodeReferenceID>>()
        .keyType(LongDataType)
        .valueType(EnumeratedStringSetValueDataType(mapletFactory.getStringEnumerator(), JvmNodeReferenceIdEnumeratedStringDataTypeExternalizer))
      @Suppress("UNCHECKED_CAST")
      map = mapletFactory.openMap(name, mapBuilder) as MultiMapletEx<Long, ReferenceID>
    }
  }

  /**
   * @param node to be indexed
   * @return direct dependencies for the given node, which should be indexed by this index
   */
  protected abstract fun processIndexedDependencies(node: Node<*, *>, processor: (node: ReferenceID) -> Unit)

  final override fun getName(): String = name

  final override fun getKeys(): Iterable<ReferenceID> = throw UnsupportedOperationException("Not used")

  final override fun getDependencies(id: ReferenceID): Iterable<ReferenceID> = map.get(refToMapKey(id))

  final override fun indexNode(node: Node<*, *>) {
    val nodeId = node.referenceID
    processIndexedDependencies(node) {
      map.appendValue(refToMapKey(it), nodeId)
    }
  }

  final override fun integrate(
    deletedNodes: Iterable<Node<*, *>>,
    updatedNodes: Iterable<Node<*, *>>,
    deltaIndex: BackDependencyIndex?,
  ) {
    val depsToRemove = MutableLongObjectMap<MutableScatterSet<ReferenceID>>()

    for (node in deletedNodes) {
      collectDepsToRemove(node, depsToRemove)
      // corner case, relevant to situations when keys in this index are actually real node IDs
      // if a node gets deleted, the corresponding index key gets deleted too: this allows ensure there is no outdated information in the index
      // If later a new node with the same ID is added, the previous index data for this ID will not interfere with the new state.
      map.remove(refToMapKey(node.referenceID))
    }

    if (deltaIndex == null || deltaIndex !is BackDependencyIndexImpl) {
      // only another impl is empty (BackDependencyIndex.createEmpty)
      return
    }

    for (node in updatedNodes) {
      collectDepsToRemove(node, depsToRemove)
    }

    val deltaMap = deltaIndex.map
    val sequence: Sequence<Long> = if (depsToRemove.isEmpty()) {
      deltaMap.keys.asSequence()
    }
    else {
      sequence {
        yieldAll(deltaMap.keys)
        depsToRemove.forEachKey {
          yield(it)
        }
      }
        .distinct()
    }
    for (idHash in sequence) {
      val toRemove = depsToRemove.get(idHash)
      val toAdd = deltaMap.get(idHash)
      if (toRemove != null && !toRemove.isEmpty()) {
        toRemove.removeAll(toAdd)
        if (toRemove.isNotEmpty()) {
          map.removeValues(idHash, toRemove)
        }
      }
      map.appendValues(idHash, toAdd)
    }
  }

  private fun collectDepsToRemove(node: Node<*, *>, depsToRemove: MutableLongObjectMap<MutableScatterSet<ReferenceID>>) {
    val nodeId = node.referenceID
    processIndexedDependencies(node) { id ->
      depsToRemove.getOrPut(refToMapKey(id)) {
        MutableScatterSet()
      }.add(nodeId)
    }
  }
}

private fun refToMapKey(id: ReferenceID): Long = (id as JvmNodeReferenceID).hashId

class NodeDependenciesIndex(
  mapletFactory: MvStoreContainerFactory,
  isInMemory: Boolean,
) : BackDependencyIndexImpl("node-backward-dependencies", mapletFactory, isInMemory) {
  override fun processIndexedDependencies(node: Node<*, *>, processor: (ReferenceID) -> Unit) {
    val referentId = node.referenceID
    val visited = MutableScatterSet<ReferenceID>()
    for (usage in node.getUsages()) {
      val id = usage.elementOwner
      if (id != referentId && visited.add(id)) {
        processor(id)
      }
    }
  }
}