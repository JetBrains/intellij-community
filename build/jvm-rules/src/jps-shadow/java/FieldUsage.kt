package org.jetbrains.jps.dependency.java

import org.jetbrains.jps.dependency.GraphDataInput
import org.jetbrains.jps.dependency.GraphDataOutput

open class FieldUsage : MemberUsage {
  private val descriptorHash: Long

  constructor(className: String?, name: String?, descriptor: String) : super(className, name) {
    descriptorHash = descriptorToHash(descriptor)
  }

  @Suppress("unused")
  constructor(id: JvmNodeReferenceID, name: String, descriptor: String) : super(id, name) {
    descriptorHash = descriptorToHash(descriptor)
  }

  @Suppress("unused")
  constructor(owner: JvmNodeReferenceID, input: GraphDataInput) : super(owner, input) {
    descriptorHash = input.readRawLong()
  }

  override fun write(out: GraphDataOutput) {
    super.write(out)

    out.writeRawLong(descriptorHash)
  }

  override fun equals(o: Any?): Boolean {
    if (!super.equals(o)) {
      return false
    }

    o as FieldUsage
    return descriptorHash == o.descriptorHash
  }

  override fun hashCode(): Int {
    return 31 * super.hashCode() + descriptorHash.toInt()
  }
}