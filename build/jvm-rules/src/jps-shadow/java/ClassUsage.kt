package org.jetbrains.jps.dependency.java

import org.jetbrains.jps.dependency.GraphDataInput

open class ClassUsage : JvmElementUsage {
  constructor(className: String) : this(JvmNodeReferenceID(className))

  constructor(id: JvmNodeReferenceID) : super(id)

  @Suppress("unused")
  constructor(`in`: GraphDataInput) : super(`in`)

  override fun hashCode(): Int {
    return super.hashCode() + 10
  }
}