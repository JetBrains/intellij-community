package org.jetbrains.jps.dependency.java

import org.jetbrains.jps.dependency.GraphDataInput
import org.jetbrains.jps.dependency.GraphDataOutput
import org.jetbrains.jps.dependency.Usage

abstract class JvmElementUsage : Usage {
  private val owner: JvmNodeReferenceID

  internal constructor(owner: JvmNodeReferenceID) {
    this.owner = owner
  }

  internal constructor(`in`: GraphDataInput) {
    owner = JvmNodeReferenceID(`in`)
  }

  override fun write(out: GraphDataOutput) {
    owner.write(out)
  }

  override fun getElementOwner(): JvmNodeReferenceID = owner

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    if (other == null || javaClass != other.javaClass) {
      return false
    }

    other as JvmElementUsage
    return owner == other.owner
  }

  override fun hashCode(): Int = owner.hashCode()
}