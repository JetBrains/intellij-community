package org.jetbrains.jps.dependency.java

import org.jetbrains.jps.dependency.GraphDataInput
import org.jetbrains.jps.dependency.GraphDataOutput
import org.jetbrains.jps.dependency.writeCollection
import java.util.EnumSet

internal class AnnotationUsage : JvmElementUsage {
  val classType: TypeRepr.ClassType
  private val usedArgNames: Collection<String>
  private val targets: EnumSet<ElemType>

  constructor(
    classType: TypeRepr.ClassType,
    usedArgNames: Collection<String>,
    targets: EnumSet<ElemType>
  ) : super(JvmNodeReferenceID(classType.jvmName)) {
    this.classType = classType
    this.usedArgNames = usedArgNames
    this.targets = targets
  }

  @Suppress("unused")
  constructor(input: GraphDataInput) : super(input) {
    classType = TypeRepr.ClassType(getElementOwner().nodeName)
    usedArgNames = input.readStringList()

    targets = EnumSet.noneOf(ElemType::class.java)
    repeat(input.readInt()) {
      targets.add(ElemType.entries[input.readUnsignedByte()])
    }
  }

  @Suppress("unused")
  fun getUsedArgNames(): Iterable<String> = usedArgNames

  @Suppress("unused")
  fun getTargets(): Iterable<ElemType> = targets

  override fun write(out: GraphDataOutput) {
    super.write(out)
    out.writeCollection(usedArgNames) { out.writeUTF(it) }
    out.writeCollection(targets) { out.writeByte(it.ordinal) }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    if (other == null || javaClass != other.javaClass) {
      return false
    }

    other as AnnotationUsage
    return classType == other.classType && usedArgNames == other.usedArgNames && targets == other.targets
  }

  override fun hashCode(): Int {
    var result = classType.hashCode()
    result = 31 * result + usedArgNames.hashCode()
    result = 31 * result + targets.hashCode()
    return result
  }
}