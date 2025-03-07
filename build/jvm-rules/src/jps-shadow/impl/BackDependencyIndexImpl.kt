@file:Suppress("SSBasedInspection", "ReplaceGetOrSet")

package org.jetbrains.jps.dependency.impl

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import org.jetbrains.jps.dependency.BackDependencyIndex
import org.jetbrains.jps.dependency.Externalizer
import org.jetbrains.jps.dependency.GraphDataInput
import org.jetbrains.jps.dependency.GraphDataOutput
import org.jetbrains.jps.dependency.MapletFactory
import org.jetbrains.jps.dependency.MultiMaplet
import org.jetbrains.jps.dependency.Node
import org.jetbrains.jps.dependency.ReferenceID
import org.jetbrains.jps.dependency.java.JvmNodeReferenceID

abstract class BackDependencyIndexImpl protected constructor(
  private val name: String,
  mapletFactory: MapletFactory
) : BackDependencyIndex {
  private val map: MultiMaplet<ReferenceID, ReferenceID>

  init {
    // important: if multiple implementations of ReferenceID are available, change to createMultitypeExternalizer
    val ext = object : Externalizer<ReferenceID> {
      override fun load(input: GraphDataInput): ReferenceID {
        return JvmNodeReferenceID(input.readUTF())
      }

      override fun save(out: GraphDataOutput, value: ReferenceID) {
        value.write(out)
      }
    }
    map = mapletFactory.createSetMultiMaplet(name, ext, ext)
  }

  /**
   * @param node to be indexed
   * @return direct dependencies for the given node, which should be indexed by this index
   */
  protected abstract fun getIndexedDependencies(node: Node<*, *>): Sequence<ReferenceID>

  final override fun getName(): String = name

  final override fun getKeys(): Iterable<ReferenceID> = map.getKeys()

  final override fun getDependencies(id: ReferenceID): Iterable<ReferenceID> = map.get(id)

  final override fun indexNode(node: Node<*, *>) {
    val nodeId = node.referenceID
    for (referentId in getIndexedDependencies(node)) {
      map.appendValue(referentId, nodeId)
    }
  }

  final override fun integrate(deletedNodes: Iterable<Node<*, *>>, updatedNodes: Iterable<Node<*, *>>, deltaIndex: BackDependencyIndex) {
    val depsToRemove = Object2ObjectOpenHashMap<ReferenceID, MutableSet<ReferenceID>>()

    for (node in deletedNodes) {
      cleanupDependencies(node, depsToRemove)
      // corner case, relevant to situations when keys in this index are actually real node IDs
      // if a node gets deleted, the corresponding index key gets deleted to: this allows to ensure there is no outdated information in the index
      // If later a new node with the same ID is added, the previous index data for this ID will not interfere with the new state.
      map.remove(node.referenceID)
    }

    for (node in updatedNodes) {
      cleanupDependencies(node, depsToRemove)
    }

    for (id in (deltaIndex.keys.asSequence() + depsToRemove.keys).distinct()) {
      val toRemove = depsToRemove.get(id)
      val toAdd = deltaIndex.getDependencies(id)
      if (!toRemove.isNullOrEmpty()) {
        if (toAdd is Set<*>) {
          toRemove.removeAll(toAdd as Set<*>)
        }
        else {
          for (refId in toAdd) {
            toRemove.remove(refId)
          }
        }
        map.removeValues(id, toRemove)
      }
      map.appendValues(id, toAdd)
    }
  }

  private fun cleanupDependencies(node: Node<*, *>, depsToRemove: MutableMap<ReferenceID, MutableSet<ReferenceID>>) {
    val nodeId = node.referenceID
    for (referentId in getIndexedDependencies(node)) {
      depsToRemove.computeIfAbsent(referentId) { ObjectOpenHashSet() }.add(nodeId)
    }
  }
}

internal class NodeDependenciesIndex(mapletFactory: MapletFactory) : BackDependencyIndexImpl("node-backward-dependencies", mapletFactory) {
  override fun getIndexedDependencies(node: Node<*, *>): Sequence<ReferenceID> {
    val nodeID = node.referenceID
    return node.usages().filter { nodeID != it.elementOwner }.map { it.elementOwner }.distinct()
  }
}