package org.jetbrains.jps.dependency.impl

import org.jetbrains.jps.dependency.BackDependencyIndex
import org.jetbrains.jps.dependency.Externalizer
import org.jetbrains.jps.dependency.Graph
import org.jetbrains.jps.dependency.GraphDataInput
import org.jetbrains.jps.dependency.GraphDataOutput
import org.jetbrains.jps.dependency.MapletFactory
import org.jetbrains.jps.dependency.MultiMaplet
import org.jetbrains.jps.dependency.Node
import org.jetbrains.jps.dependency.NodeSource
import org.jetbrains.jps.dependency.ReferenceID
import org.jetbrains.jps.dependency.java.JvmNodeReferenceID
import java.io.Closeable

internal val anyGraphElementExternalizer = object : Externalizer<Node<*, *>> {
  override fun load(input: GraphDataInput): Node<*, *> {
    return input.readGraphElement()
  }

  override fun save(output: GraphDataOutput, value: Node<*, *>) {
    output.writeGraphElement(value)
  }
}

internal val sourceExternalizer = Externalizer.forGraphElement<NodeSource> { PathSource(it) }
internal val jvmNodeReferenceIdExternalizer = Externalizer.forGraphElement<ReferenceID> { JvmNodeReferenceID(it) }

abstract class GraphImpl(
  private val containerFactory: MapletFactory
) : Graph {
  // nodeId -> nodes referencing the nodeId
  private val dependencyIndex: BackDependencyIndex
  private val indices = ArrayList<BackDependencyIndex>()
// myNodeToSourcesMap/mySourceToNodesMap are used by DependencyGraphImpl, cannot remove `my` prefix
  @JvmField
  protected val myNodeToSourcesMap: MultiMaplet<ReferenceID, NodeSource>
  @JvmField
  protected val mySourceToNodesMap: MultiMaplet<NodeSource, Node<*, *>>

  init {
    try {
      dependencyIndex = NodeDependenciesIndex(containerFactory).also { addIndex(it) }

      myNodeToSourcesMap = containerFactory.createSetMultiMaplet("node-sources-map", jvmNodeReferenceIdExternalizer, sourceExternalizer)
      mySourceToNodesMap = containerFactory.createSetMultiMaplet("source-nodes-map", sourceExternalizer, anyGraphElementExternalizer)
    }
    catch (e: RuntimeException) {
      closeIgnoreErrors()
      throw e
    }
  }

  protected fun addIndex(index: BackDependencyIndex) {
    indices.add(index)
  }

  final override fun getDependingNodes(id: ReferenceID): Iterable<ReferenceID> {
    return dependencyIndex.getDependencies(id)
  }

  final override fun getIndices(): List<BackDependencyIndex> = indices

  final override fun getIndex(name: String): BackDependencyIndex? {
    for (index in indices) {
      if (index.name == name) {
        return index
      }
    }
    return null
  }

  override fun getSources(id: ReferenceID): Iterable<NodeSource> {
    return myNodeToSourcesMap.get(id)
  }

  override fun getRegisteredNodes(): Iterable<ReferenceID> {
    return myNodeToSourcesMap.keys
  }

  override fun getSources(): Iterable<NodeSource> {
    return mySourceToNodesMap.keys
  }

  override fun getNodes(source: NodeSource): Iterable<Node<*, *>> {
    return mySourceToNodesMap.get(source)
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