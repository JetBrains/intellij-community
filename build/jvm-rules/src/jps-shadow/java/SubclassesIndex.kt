package org.jetbrains.jps.dependency.java

import org.jetbrains.jps.dependency.MapletFactory
import org.jetbrains.jps.dependency.Node
import org.jetbrains.jps.dependency.ReferenceID
import org.jetbrains.jps.dependency.impl.BackDependencyIndexImpl

class SubclassesIndex(cFactory: MapletFactory) : BackDependencyIndexImpl("direct-subclasses", cFactory) {
  override fun getIndexedDependencies(node: Node<*, *>): Sequence<ReferenceID> {
    if (node !is JvmClass) {
      return emptySequence()
    }
    return node.superTypes().map { JvmNodeReferenceID(it) }
  }
}