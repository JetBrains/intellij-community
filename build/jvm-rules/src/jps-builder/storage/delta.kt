@file:Suppress("PackageDirectoryMismatch", "UnstableApiUsage", "SSBasedInspection", "ReplaceGetOrSet")

package org.jetbrains.jps.dependency.impl

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import org.jetbrains.bazel.jvm.hashMap
import org.jetbrains.bazel.jvm.hashSet
import org.jetbrains.jps.dependency.Delta
import org.jetbrains.jps.dependency.MultiMaplet
import org.jetbrains.jps.dependency.Node
import org.jetbrains.jps.dependency.NodeSource
import org.jetbrains.jps.dependency.java.SubclassesIndex
import java.util.function.Supplier

@Suppress("unused")
internal class DeltaImpl internal constructor(baseSources: Iterable<NodeSource>, deletedSources: Iterable<NodeSource>)
  : GraphImpl(Containers.MEMORY_CONTAINER_FACTORY), Delta {
  private val baseSources: Set<NodeSource>
  private val deletedSources: Set<NodeSource>

  init {
    addIndex(SubclassesIndex(Containers.MEMORY_CONTAINER_FACTORY))
    this.baseSources = if (baseSources is Set<*>) baseSources as Set<NodeSource> else baseSources.toCollection(hashSet())
    this.deletedSources = if (deletedSources is Set<*>) deletedSources as Set<NodeSource> else deletedSources.toCollection(hashSet())
  }

  override fun isSourceOnly(): Boolean = false

  override fun getBaseSources(): Set<NodeSource> {
    return baseSources
  }

  override fun getDeletedSources(): Set<NodeSource> {
    return deletedSources
  }

  override fun associate(node: Node<*, *>, sources: Iterable<NodeSource>) {
    myNodeToSourcesMap.appendValues(node.referenceID, sources)
    for (source in sources) {
      mySourceToNodesMap.appendValue(source, node)
    }
    // deduce dependencies
    for (index in indices) {
      index.indexNode(node)
    }
  }

  fun associateNodes(source: NodeSource, nodes: List<Node<*, *>>) {
    for (node in nodes) {
      myNodeToSourcesMap.appendValue(node.referenceID, source)
    }
    mySourceToNodesMap.appendValues(source, nodes)
    // deduce dependencies
    for (index in indices) {
      for (node in nodes) {
        index.indexNode(node)
      }
    }
  }
}

@Suppress("unused")
internal class MemoryMultiMaplet<K : Any, V : Any, C : MutableCollection<V>>(
  collectionFactory: Supplier<C>,
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