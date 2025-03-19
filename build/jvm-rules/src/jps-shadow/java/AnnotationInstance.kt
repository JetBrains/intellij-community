package org.jetbrains.jps.dependency.java

import org.jetbrains.jps.dependency.ExternalizableGraphElement
import org.jetbrains.jps.dependency.GraphDataInput
import org.jetbrains.jps.dependency.GraphDataOutput
import org.jetbrains.jps.dependency.diff.Difference

abstract class AnnotationInstance : ExternalizableGraphElement {
  val annotationClass: TypeRepr.ClassType
  val contentHash: Long

  @Suppress("unused")
  protected constructor(annotationClass: TypeRepr.ClassType, contentHash: Any?) {
    this.annotationClass = annotationClass
    this.contentHash = if (contentHash == null) 0 else contentHash as Long
  }

  @Suppress("unused")
  protected constructor(input: GraphDataInput) {
    annotationClass = TypeRepr.ClassType(input.readUTF())
    contentHash = input.readRawLong()
  }

  override fun write(out: GraphDataOutput) {
    out.writeUTF(annotationClass.jvmName)
    out.writeRawLong(contentHash)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    if (other == null || javaClass != other.javaClass) {
      return false
    }

    other as AnnotationInstance
    return contentHash == other.contentHash && annotationClass == other.annotationClass
  }

  override fun hashCode(): Int {
    return 31 * annotationClass.hashCode() + contentHash.toInt()
  }

  @Suppress("unused")
  abstract inner class Diff<V : AnnotationInstance>(private val past: V) : Difference {
    override fun unchanged(): Boolean = past.contentHash == contentHash
  }
}