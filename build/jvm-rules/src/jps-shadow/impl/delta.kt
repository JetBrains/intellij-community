@file:Suppress("PackageDirectoryMismatch", "UnstableApiUsage", "SSBasedInspection", "ReplaceGetOrSet", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.jps.dependency.impl

import it.unimi.dsi.fastutil.objects.ObjectArraySet
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import org.h2.mvstore.MVMap
import org.jetbrains.bazel.jvm.emptySet
import org.jetbrains.bazel.jvm.hashMap
import org.jetbrains.bazel.jvm.hashSet
import org.jetbrains.jps.dependency.BackDependencyIndex
import org.jetbrains.jps.dependency.Delta
import org.jetbrains.jps.dependency.Graph
import org.jetbrains.jps.dependency.MultiMaplet
import org.jetbrains.jps.dependency.Node
import org.jetbrains.jps.dependency.NodeSource
import org.jetbrains.jps.dependency.ReferenceID
import org.jetbrains.jps.dependency.java.SubclassesIndex
import java.util.function.Supplier

private val memoryFactory = object : MvStoreContainerFactory {
  override fun <K : Any, V : Any> openMap(mapName: String, mapBuilder: MVMap.Builder<K, Set<V>>): MultiMaplet<K, V> {
    throw UnsupportedOperationException("Not used")
  }

  override fun <K : Any, V : Any> openInMemoryMap(): MultiMaplet<K, V> {
    return MemoryMultiMaplet(null)
  }

  override fun getStringEnumerator() = throw UnsupportedOperationException("Not used")

  override fun getElementInterner() = throw UnsupportedOperationException("Not used")
}

@Suppress("unused")
class DeltaImpl(baseSources: Iterable<NodeSource>, deletedSources: Iterable<NodeSource>) : Graph, Delta {
  private val baseSources: Set<NodeSource> = toSet(baseSources)
  private val deletedSources: Set<NodeSource> = toSet(deletedSources)

  // nodeId -> nodes referencing the nodeId
  private val dependencyIndex: BackDependencyIndex
  private val indices: List<BackDependencyIndex>

  private val nodeToSourcesMap = hashMap<ReferenceID, MutableSet<NodeSource>>()
  private val sourceToNodesMap = hashMap<NodeSource, MutableSet<Node<*, *>>>()

  init {
    val subclassesIndex = SubclassesIndex(memoryFactory, true)
    dependencyIndex = NodeDependenciesIndex(memoryFactory, true)
    indices = java.util.List.of(dependencyIndex, subclassesIndex)
  }

  override fun getDependingNodes(id: ReferenceID): Iterable<ReferenceID> {
    return dependencyIndex.getDependencies(id)
  }

  override fun getIndices(): List<BackDependencyIndex> = indices

  override fun getIndex(name: String): BackDependencyIndex? {
    for (index in indices) {
      if (index.name == name) {
        return index
      }
    }
    return null
  }

  override fun getSources(id: ReferenceID): Iterable<NodeSource> {
    return nodeToSourcesMap.get(id) ?: emptySet()
  }

  override fun getRegisteredNodes(): Iterable<ReferenceID> {
    return nodeToSourcesMap.keys
  }

  override fun getSources(): Iterable<NodeSource> {
    return sourceToNodesMap.keys
  }

  override fun getNodes(source: NodeSource): Iterable<Node<*, *>> {
    return sourceToNodesMap.get(source) ?: emptySet()
  }

  override fun isSourceOnly(): Boolean = false

  override fun getBaseSources(): Set<NodeSource> {
    return baseSources
  }

  override fun getDeletedSources(): Set<NodeSource> {
    return deletedSources
  }

  override fun associate(node: Node<*, *>, sources: Iterable<NodeSource>) {
    nodeToSourcesMap.computeIfAbsent(node.referenceID) { ObjectOpenHashSet() }.addAll(sources)
    for (source in sources) {
      sourceToNodesMap.computeIfAbsent(source) { ObjectOpenHashSet() }.add(node)
    }
    // deduce dependencies
    for (index in indices) {
      index.indexNode(node)
    }
  }

  fun associateNodes(source: NodeSource, nodes: List<Node<*, *>>) {
    for (node in nodes) {
      nodeToSourcesMap.computeIfAbsent(node.referenceID) { ObjectArraySet() }.add(source)
    }
    sourceToNodesMap.computeIfAbsent(source) { ObjectOpenHashSet() }.addAll(nodes)
    // deduce dependencies
    for (index in indices) {
      for (node in nodes) {
        index.indexNode(node)
      }
    }
  }
}

private fun toSet(baseSources: Iterable<NodeSource>): Set<NodeSource> {
  return when (baseSources) {
    is Set<*> -> baseSources as Set<NodeSource>
    is Collection<*> -> if (baseSources.isEmpty()) emptySet() else baseSources.toCollection(hashSet(baseSources.size))
    else -> baseSources.toCollection(hashSet())
  }
}

class MemoryMultiMaplet<K : Any, V : Any, C : MutableCollection<V>>(
  @Suppress("unused") collectionFactory: Supplier<C>?,
) : MultiMaplet<K, V> {
  private val map = hashMap<K, MutableCollection<V>>()

  override fun containsKey(key: K): Boolean {
    return map.containsKey(key)
  }

  override fun get(key: K): Iterable<V> {
    return map.get(key) ?: emptySet()
  }

  override fun put(key: K, values: Iterable<V>) {
    val data: MutableCollection<V> = when (values) {
      is Collection<*> -> {
        if (values.isEmpty()) {
          map.remove(key)
          return
        }
        else {
          ObjectOpenHashSet(values as Collection<V>)
        }
      }

      else -> ObjectOpenHashSet(values.iterator())
    }

    if (data.isEmpty()) {
      map.remove(key)
    }
    else {
      map.put(key, data)
    }
  }

  override fun removeValues(key: K, values: Iterable<V>) {
    val set = map.get(key) ?: return
    for (value in values) {
      set.remove(value)
    }
  }

  override fun remove(key: K) {
    map.remove(key)
  }

  override fun appendValues(key: K, values: Iterable<V>) {
    map.computeIfAbsent(key) { ObjectOpenHashSet() }.addAll(values)
  }

  override fun appendValue(key: K, value: V) {
    map.computeIfAbsent(key) { ObjectOpenHashSet() }.add(value)
  }

  override fun removeValue(key: K, value: V) {
    map.get(key)?.remove(value)
  }

  override fun getKeys(): Iterable<K> = map.keys

  override fun close() {
  }

  override fun flush() {
  }
}