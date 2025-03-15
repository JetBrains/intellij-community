package org.jetbrains.jps.dependency

import org.jetbrains.jps.dependency.diff.DiffCapable
import org.jetbrains.jps.dependency.diff.Difference

interface Node<T : Node<T, D>, D : Difference> : DiffCapable<T, D>, ExternalizableGraphElement {
  val referenceID: ReferenceID

  fun getUsages(): Iterable<Usage>
}