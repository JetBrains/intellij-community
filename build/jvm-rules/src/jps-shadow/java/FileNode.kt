@file:Suppress("SSBasedInspection")

package org.jetbrains.jps.dependency.java

import org.jetbrains.jps.dependency.GraphDataInput
import org.jetbrains.jps.dependency.GraphDataOutput
import org.jetbrains.jps.dependency.Node
import org.jetbrains.jps.dependency.Usage
import org.jetbrains.jps.dependency.diff.DiffCapable
import org.jetbrains.jps.dependency.diff.Difference

class FileNode : Node<FileNode, Difference> {
  override val referenceID: JvmNodeReferenceID
  private val usages: Collection<Usage>

  @Suppress("unused")
  constructor(name: String, usages: Iterable<Usage>) {
    this.referenceID = JvmNodeReferenceID(name)
    this.usages = usages as Collection<Usage>
  }

  @Suppress("unused")
  constructor(`in`: GraphDataInput) {
    referenceID = JvmNodeReferenceID(`in`)

    usages = ArrayList<Usage>()
    var groupCount = `in`.readInt()
    while (groupCount-- > 0) {
      `in`.readGraphElementCollection(usages)
    }
  }

  override fun getUsages(): Iterable<Usage> = usages

  override fun write(out: GraphDataOutput) {
    referenceID.write(out)

    out.writeUsages(usages)
  }

  fun getName(): String {
    throw UnsupportedOperationException("Not used")
  }

  override fun isSame(other: DiffCapable<*, *>?): Boolean {
    if (other !is FileNode) {
      return false
    }
    return referenceID == other.referenceID
  }

  override fun diffHashCode(): Int = referenceID.hashCode()

  override fun difference(past: FileNode): Difference = Diff(past, usages)

  private class Diff(private val past: FileNode, private val usages: Collection<Usage>) : Difference {
    private var isUnchanged: Boolean? = null

    override fun unchanged(): Boolean {
      var isUnchanged = isUnchanged
      if (isUnchanged == null) {
        isUnchanged = diff(past.usages, usages).unchanged()
        this.isUnchanged = isUnchanged
      }
      return isUnchanged
    }
  }
}