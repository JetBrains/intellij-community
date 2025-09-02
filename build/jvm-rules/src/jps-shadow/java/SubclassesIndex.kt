// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java

import org.jetbrains.jps.dependency.Node
import org.jetbrains.jps.dependency.ReferenceID
import org.jetbrains.jps.dependency.impl.BackDependencyIndexImpl
import org.jetbrains.jps.dependency.storage.MvStoreContainerFactory

class SubclassesIndex(
  mapletFactory: MvStoreContainerFactory,
  isInMemory: Boolean,
) : BackDependencyIndexImpl("direct-subclasses", mapletFactory, isInMemory) {
  companion object {
    @Suppress("unused")
    const val NAME: String = "direct-subclasses"
  }

  override fun processIndexedDependencies(node: Node<*, *>, processor: (ReferenceID) -> Unit) {
    if (node !is JvmClass) {
      return
    }
    for (id in node.superTypes()) {
      processor(JvmNodeReferenceID(id))
    }
  }
}