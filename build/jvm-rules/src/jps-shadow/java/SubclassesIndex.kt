package org.jetbrains.jps.dependency.java

import org.jetbrains.jps.dependency.MapletFactory
import org.jetbrains.jps.dependency.Node
import org.jetbrains.jps.dependency.ReferenceID
import org.jetbrains.jps.dependency.impl.BackDependencyIndexImpl
import org.jetbrains.jps.dependency.impl.MvStoreContainerFactory

internal class SubclassesIndex(
  cFactory: MvStoreContainerFactory,
  isInMemory: Boolean,
) : BackDependencyIndexImpl("direct-subclasses", cFactory, isInMemory) {
  override fun getIndexedDependencies(node: Node<*, *>): Sequence<ReferenceID> {
    if (node !is JvmClass) {
      return emptySequence()
    }
    return node.superTypes().map { JvmNodeReferenceID(it) }
  }
}