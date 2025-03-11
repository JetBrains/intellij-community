package org.jetbrains.jps.dependency.impl

import org.h2.mvstore.MVMap
import org.jetbrains.jps.dependency.BackDependencyIndex
import org.jetbrains.jps.dependency.ExternalizableGraphElement
import org.jetbrains.jps.dependency.Graph
import org.jetbrains.jps.dependency.MapletFactory
import org.jetbrains.jps.dependency.MultiMaplet
import org.jetbrains.jps.dependency.Node
import org.jetbrains.jps.dependency.NodeSource
import org.jetbrains.jps.dependency.ReferenceID
import org.jetbrains.jps.dependency.java.JvmNodeReferenceID
import org.jetbrains.jps.dependency.storage.AnyGraphElementDataType
import org.jetbrains.jps.dependency.storage.EnumeratedStringDataType
import org.jetbrains.jps.dependency.storage.EnumeratedStringDataTypeExternalizer
import org.jetbrains.jps.dependency.storage.EnumeratedStringSetValueDataType
import org.jetbrains.jps.dependency.storage.StringEnumerator
import java.io.Closeable
import kotlin.arrayOf

interface MvStoreContainerFactory {
  fun <K : Any, V : Any> openMap(mapName: String, mapBuilder: MVMap.Builder<K, Set<V>>): MultiMaplet<K, V>

  fun <K : Any, V : Any> openInMemoryMap(): MultiMaplet<K, V>

  fun getStringEnumerator(): StringEnumerator

  fun getElementInterner(): (ExternalizableGraphElement) -> ExternalizableGraphElement
}

abstract class GraphImpl(
  private val containerFactory: MapletFactory,
  extraIndex: BackDependencyIndex,
) : Graph {
  // nodeId -> nodes referencing the nodeId
  private val dependencyIndex: BackDependencyIndex
  private val indices: List<BackDependencyIndex>
  @JvmField
  protected val nodeToSourcesMap: MultiMaplet<ReferenceID, NodeSource>
  @JvmField
  protected val sourceToNodesMap: MultiMaplet<NodeSource, Node<*, *>>

  init {
    try {
      containerFactory as MvStoreContainerFactory
      val indices = arrayOfNulls<BackDependencyIndex>(2)
      dependencyIndex = NodeDependenciesIndex(containerFactory, false).also {
        indices[0] = it
      }

      indices[1] = extraIndex
      @Suppress("UNCHECKED_CAST")
      this.indices = indices.asList() as List<BackDependencyIndex>

      @Suppress("UNCHECKED_CAST")
      nodeToSourcesMap = createNodeIdToSourcesMap(containerFactory, containerFactory.getStringEnumerator())
      @Suppress("UNCHECKED_CAST")
      sourceToNodesMap = createSourceToNodesMap(containerFactory, containerFactory.getStringEnumerator())
    }
    catch (e: RuntimeException) {
      closeIgnoreErrors()
      throw e
    }
  }

  final override fun getDependingNodes(id: ReferenceID): Iterable<ReferenceID> {
    return dependencyIndex.getDependencies(id)
  }

  final override fun getIndices(): List<BackDependencyIndex> = indices

  final override fun getIndex(name: String): BackDependencyIndex? {
    return indices.firstOrNull { it.name == name }
  }

  override fun getSources(id: ReferenceID): Iterable<NodeSource> {
    return nodeToSourcesMap.get(id)
  }

  override fun getRegisteredNodes(): Iterable<ReferenceID> {
    return nodeToSourcesMap.keys
  }

  override fun getSources(): Iterable<NodeSource> {
    return sourceToNodesMap.keys
  }

  override fun getNodes(source: NodeSource): Iterable<Node<*, *>> {
    return sourceToNodesMap.get(source)
  }

  protected fun closeIgnoreErrors() {
    try {
      close()
    }
    catch (_: Throwable) {
    }
  }

  open fun close() {
    if (containerFactory is Closeable) {
      containerFactory.close()
    }
  }
}

private fun createSourceToNodesMap(
  containerFactory: MvStoreContainerFactory,
  stringEnumerator: StringEnumerator,
): MultiMaplet<NodeSource, Node<*, *>> {
  val builder = MVMap.Builder<PathSource, Set<Node<*, *>>>()
  builder.keyType(EnumeratedStringDataType(stringEnumerator, PathSourceEnumeratedStringDataTypeExternalizer))
  builder.valueType(AnyGraphElementDataType(stringEnumerator, containerFactory.getElementInterner()))
  @Suppress("UNCHECKED_CAST")
  return containerFactory.openMap("source-to-nodes", builder) as MultiMaplet<NodeSource, Node<*, *>>
}

private fun createNodeIdToSourcesMap(
  containerFactory: MvStoreContainerFactory,
  stringEnumerator: StringEnumerator,
): MultiMaplet<ReferenceID, NodeSource> {
  val builder = MVMap.Builder<JvmNodeReferenceID, Set<PathSource>>()
  builder.keyType(EnumeratedStringDataType(stringEnumerator, JvmNodeReferenceIdEnumeratedStringDataTypeExternalizer))
  builder.valueType(EnumeratedStringSetValueDataType(stringEnumerator, PathSourceEnumeratedStringDataTypeExternalizer))
  @Suppress("UNCHECKED_CAST")
  return containerFactory.openMap("node-id-to-sources", builder) as MultiMaplet<ReferenceID, NodeSource>
}

internal object JvmNodeReferenceIdEnumeratedStringDataTypeExternalizer : EnumeratedStringDataTypeExternalizer<JvmNodeReferenceID> {
  private val emptyIds = arrayOfNulls<JvmNodeReferenceID>(0)

  override fun createStorage(size: Int) = if (size == 0) emptyIds else arrayOfNulls(size)

  override fun create(id: String): JvmNodeReferenceID = JvmNodeReferenceID(id)

  override fun getStringId(obj: JvmNodeReferenceID): String = obj.nodeName
}

internal object PathSourceEnumeratedStringDataTypeExternalizer : EnumeratedStringDataTypeExternalizer<PathSource> {
  private val emptyPaths = arrayOfNulls<PathSource>(0)

  override fun createStorage(size: Int) = if (size == 0) emptyPaths else arrayOfNulls(size)

  override fun create(id: String): PathSource = PathSource(id)

  override fun getStringId(obj: PathSource): String = obj.toString()
}