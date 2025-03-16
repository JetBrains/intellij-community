package org.jetbrains.jps.dependency.impl

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
import kotlinx.collections.immutable.PersistentSet
import org.h2.mvstore.MVMap
import org.jetbrains.bazel.jvm.jps.storage.IntLong
import org.jetbrains.bazel.jvm.jps.storage.IntLongPairKeyDataType
import org.jetbrains.jps.dependency.BackDependencyIndex
import org.jetbrains.jps.dependency.Graph
import org.jetbrains.jps.dependency.Node
import org.jetbrains.jps.dependency.NodeSource
import org.jetbrains.jps.dependency.ReferenceID
import org.jetbrains.jps.dependency.java.JvmNodeReferenceID
import org.jetbrains.jps.dependency.storage.AnyGraphElementDataType
import org.jetbrains.jps.dependency.storage.EnumeratedStringDataType
import org.jetbrains.jps.dependency.storage.EnumeratedStringDataTypeExternalizer
import org.jetbrains.jps.dependency.storage.EnumeratedStringSetValueDataType
import org.jetbrains.jps.dependency.storage.MultiMapletEx
import org.jetbrains.jps.dependency.storage.MvStoreContainerFactory
import org.jetbrains.jps.dependency.storage.StringEnumerator

abstract class GraphImpl(
  containerFactory: MvStoreContainerFactory,
  extraIndex: BackDependencyIndex,
) : Graph {
  // nodeId -> nodes referencing the nodeId
  private val dependencyIndex = NodeDependenciesIndex(mapletFactory = containerFactory, isInMemory = false)
  private val indices = arrayOf(dependencyIndex, extraIndex).asList()

  @JvmField
  protected val nodeToSourcesMap: MultiMapletEx<ReferenceID, NodeSource>
  @JvmField
  protected val sourceToNodesMap: MultiMapletEx<IntLong, Node<*, *>>

  @JvmField
  protected val registeredIndices: Set<String> = indices.mapTo(ObjectLinkedOpenHashSet(indices.size)) { it.name }

  init {
    @Suppress("UNCHECKED_CAST")
    nodeToSourcesMap = createNodeIdToSourcesMap(containerFactory, containerFactory.getStringEnumerator())
    @Suppress("UNCHECKED_CAST")
    sourceToNodesMap = createSourceToNodesMap(containerFactory, containerFactory.getStringEnumerator())
  }

  @Synchronized
  final override fun getDependingNodes(id: ReferenceID): Iterable<ReferenceID> {
    return dependencyIndex.getDependencies(id)
  }

  final override fun getIndices(): List<BackDependencyIndex> = indices

  final override fun getIndex(name: String): BackDependencyIndex? {
    return indices.firstOrNull { it.name == name }
  }

  @Synchronized
  override fun getSources(id: ReferenceID): Iterable<NodeSource> {
    return nodeToSourcesMap.get(id)
  }

  @Synchronized
  override fun getRegisteredNodes(): Iterable<ReferenceID> {
    return nodeToSourcesMap.keys
  }

  @Synchronized
  override fun getSources(): Iterable<NodeSource> {
    throw UnsupportedOperationException("Supported only for delta graphs")
  }

  @Synchronized
  override fun getNodes(source: NodeSource): Iterable<Node<*, *>> {
    return sourceToNodesMap.get((source as PathSource).pathHash)
  }
}

private fun createSourceToNodesMap(
  containerFactory: MvStoreContainerFactory,
  stringEnumerator: StringEnumerator,
): MultiMapletEx<IntLong, Node<*, *>> {
  val builder = MVMap.Builder<IntLong, PersistentSet<Node<*, *>>>()
  builder.keyType(IntLongPairKeyDataType)
  builder.valueType(AnyGraphElementDataType(stringEnumerator, containerFactory.getElementInterner()))
  @Suppress("UNCHECKED_CAST")
  return containerFactory.openMap("source-to-nodes", builder)
}

private fun createNodeIdToSourcesMap(
  containerFactory: MvStoreContainerFactory,
  stringEnumerator: StringEnumerator,
): MultiMapletEx<ReferenceID, NodeSource> {
  val builder = MVMap.Builder<JvmNodeReferenceID, PersistentSet<PathSource>>()
  builder.keyType(EnumeratedStringDataType(stringEnumerator, JvmNodeReferenceIdEnumeratedStringDataTypeExternalizer))
  builder.valueType(EnumeratedStringSetValueDataType(stringEnumerator, PathSourceEnumeratedStringDataTypeExternalizer))
  @Suppress("UNCHECKED_CAST")
  return containerFactory.openMap("node-id-to-sources", builder) as MultiMapletEx<ReferenceID, NodeSource>
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