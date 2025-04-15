package org.jetbrains.jps.dependency.java

import org.jetbrains.jps.dependency.GraphDataInput
import org.jetbrains.jps.dependency.GraphDataOutput
import org.jetbrains.jps.dependency.ReferenceID

class JvmNodeReferenceID : ReferenceID {
  /**
   * @return either JVM class name (FQ-name) or JVM module name
   */
  val nodeName: String

  constructor(name: String) {
    this.nodeName = name
  }

  constructor(`in`: GraphDataInput) {
    nodeName = `in`.readUTF()
  }

  override fun write(out: GraphDataOutput) {
    out.writeUTF(nodeName)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    return other is JvmNodeReferenceID && nodeName == other.nodeName
  }

  override fun hashCode(): Int = nodeName.hashCode()

  override fun toString(): String = "JVM_NODE_ID:" + this.nodeName
}