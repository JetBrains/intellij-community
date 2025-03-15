@file:Suppress("PackageDirectoryMismatch", "UnstableApiUsage", "SSBasedInspection", "ReplaceGetOrSet", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.jps.dependency.impl

import androidx.collection.MutableScatterMap
import androidx.collection.MutableScatterSet
import androidx.collection.ScatterSet
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.minus
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.collections.immutable.plus
import kotlinx.collections.immutable.toPersistentHashSet
import org.h2.mvstore.MVMap
import org.jetbrains.bazel.jvm.emptySet
import org.jetbrains.bazel.jvm.hashSet
import org.jetbrains.bazel.jvm.removeAll
import org.jetbrains.bazel.jvm.toPersistentHashSet
import org.jetbrains.jps.dependency.BackDependencyIndex
import org.jetbrains.jps.dependency.Delta
import org.jetbrains.jps.dependency.Graph
import org.jetbrains.jps.dependency.Node
import org.jetbrains.jps.dependency.NodeSource
import org.jetbrains.jps.dependency.ReferenceID
import org.jetbrains.jps.dependency.java.SubclassesIndex
import org.jetbrains.jps.dependency.storage.MultiMapletEx
import org.jetbrains.jps.dependency.storage.MvStoreContainerFactory
import java.util.function.Supplier

private val memoryFactory = object : MvStoreContainerFactory {
  override fun <K : Any, V : Any> openMap(mapName: String, mapBuilder: MVMap.Builder<K, PersistentSet<V>>): MultiMapletEx<K, V> {
    throw UnsupportedOperationException("Not used")
  }

  override fun <K : Any, V : Any> openInMemoryMap(): MultiMapletEx<K, V> {
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

  private val nodeToSourcesMap = MutableScatterMap<ReferenceID, MutableScatterSet<NodeSource>>()
  @JvmField
  internal val sourceToNodesMap = MutableScatterMap<NodeSource, MutableScatterSet<Node<*, *>>>()

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
    return nodeToSourcesMap.get(id)?.asSet() ?: emptySet()
  }

  override fun getRegisteredNodes(): Iterable<ReferenceID> {
    return nodeToSourcesMap.asMap().keys
  }

  override fun getSources(): Iterable<NodeSource> {
    return sourceToNodesMap.asMap().keys
  }

  override fun getNodes(source: NodeSource): Iterable<Node<*, *>> {
    return sourceToNodesMap.get(source)?.asSet() ?: emptySet()
  }

  override fun isSourceOnly(): Boolean = false

  override fun getBaseSources(): Set<NodeSource> = baseSources

  override fun getDeletedSources(): Set<NodeSource> = deletedSources

  override fun associate(node: Node<*, *>, sources: Iterable<NodeSource>) {
    nodeToSourcesMap.compute(node.referenceID) { k, v ->
      (v ?: MutableScatterSet()).also { it.addAll(sources) }
    }
    for (source in sources) {
      sourceToNodesMap.compute(source) { k, v ->
        (v ?: MutableScatterSet()).also { it.add(node) }
      }
    }
    // deduce dependencies
    for (index in indices) {
      index.indexNode(node)
    }
  }

  fun associateNodes(source: NodeSource, nodes: List<Node<*, *>>) {
    for (node in nodes) {
      nodeToSourcesMap.compute(node.referenceID) { _, v -> (v ?: MutableScatterSet()).also { it.add(source) } }
    }
    sourceToNodesMap.compute(source) { _, v -> (v ?: MutableScatterSet()).also { it.addAll(nodes) } }
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
) : MultiMapletEx<K, V> {
  private val map = MutableScatterMap<K, PersistentSet<V>>()

  override fun containsKey(key: K): Boolean {
    return map.containsKey(key)
  }

  override fun get(key: K): Iterable<V> {
    return map.get(key) ?: persistentHashSetOf()
  }

  override fun put(key: K, values: ScatterSet<V>) {
    if (values.isEmpty()) {
      map.remove(key)
    }
    else {
      map.put(key, values.toPersistentHashSet())
    }
  }

  override fun put(key: K, values: Iterable<V>) {
    if (values.none()) {
      map.remove(key)
    }
    else {
      map.put(key, values.toPersistentHashSet())
    }
  }

  override fun removeValue(key: K, value: V) {
    map.get(key)?.remove(value)
  }

  override fun removeValues(key: K, values: Iterable<V>) {
    val set = map.get(key) ?: return
    val result = set.minus(values)
    if (result.isEmpty()) {
      map.remove(key)
    }
    else {
      map.put(key, result)
    }
  }

  override fun removeValues(key: K, values: ScatterSet<V>) {
    val set = map.get(key) ?: return
    val result = set.removeAll(values)
    if (result.isEmpty()) {
      map.remove(key)
    }
    else {
      map.put(key, result)
    }
  }

  override fun remove(key: K) {
    map.remove(key)
  }

  override fun appendValues(key: K, values: Iterable<V>) {
    map.compute(key) { _, set ->
      (set ?: persistentHashSetOf()).plus(values)
    }
  }

  override fun appendValue(key: K, value: V) {
    map.compute(key) { _, set ->
      (set ?: persistentHashSetOf()).add(value)
    }
  }

  override fun getKeys(): Iterable<K> = map.asMap().keys

  override fun close() {
  }

  override fun flush() {
  }
}