package org.jetbrains.jps.dependency.java

import org.jetbrains.jps.dependency.Node
import org.jetbrains.jps.dependency.ReferenceID
import org.jetbrains.jps.dependency.impl.BackDependencyIndexImpl
import org.jetbrains.jps.dependency.storage.MvStoreContainerFactory

internal class SubclassesIndex(
  cFactory: MvStoreContainerFactory,
  isInMemory: Boolean,
) : BackDependencyIndexImpl("direct-subclasses", cFactory, isInMemory) {
  override fun processIndexedDependencies(node: Node<*, *>, processor: (ReferenceID) -> Unit) {
    if (node !is JvmClass) {
      return
    }
    for (id in node.superTypes()) {
      processor(JvmNodeReferenceID(id))
    }
  }
}