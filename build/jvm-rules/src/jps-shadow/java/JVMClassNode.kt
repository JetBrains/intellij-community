@file:Suppress("SSBasedInspection", "ReplaceGetOrSet")

package org.jetbrains.jps.dependency.java

import com.dynatrace.hash4j.hashing.Hashing
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet
import org.jetbrains.bazel.jvm.emptySet
import org.jetbrains.jps.dependency.GraphDataInput
import org.jetbrains.jps.dependency.GraphDataOutput
import org.jetbrains.jps.dependency.Node
import org.jetbrains.jps.dependency.Usage
import org.jetbrains.jps.dependency.diff.DiffCapable
import org.jetbrains.jps.dependency.diff.Difference
import org.jetbrains.jps.dependency.diff.Difference.Specifier
import org.jetbrains.jps.dependency.impl.GraphDataInputImpl
import org.jetbrains.jps.dependency.impl.GraphDataOutputImpl

private val emptyMetadata = emptyArray<JvmMetadata<*, *>>()

abstract class JVMClassNode<T : JVMClassNode<T, D>, D : Difference> : Proto, Node<T, D> {
  private val id: JvmNodeReferenceID
  private val outFilePathHash: Long
  private val usages: Collection<Usage>
  private val metadata: Array<JvmMetadata<*, *>>

  constructor(
    flags: JVMFlags,
    signature: String?,
    name: String,
    outFilePath: String,
    annotations: Iterable<ElementAnnotation>,
    usages: Iterable<Usage>,
    metadata: Iterable<JvmMetadata<*, *>>
  ) : super(flags, signature, name, annotations) {
    id = JvmNodeReferenceID(name)
    outFilePathHash = Hashing.xxh3_64().hashBytesToLong(outFilePath.toByteArray())
    this.usages = usages as Collection<Usage>
    this.metadata = (metadata as java.util.Collection<JvmMetadata<*, *>>).toArray(emptyMetadata)
  }

  constructor(`in`: GraphDataInput) : super(`in`) {
    val input = `in` as GraphDataInputImpl
    id = JvmNodeReferenceID(name)
    outFilePathHash = input.readRawLong()

    val usages = ArrayList<Usage>()
    var groupCount = `in`.readInt()
    while (groupCount-- > 0) {
      `in`.readGraphElementCollection(usages)
    }
    this.usages = usages
    metadata = Array(`in`.readInt()) {
      `in`.readGraphElement()
    }
  }

  override fun write(out: GraphDataOutput) {
    super.write(out)
    (out as GraphDataOutputImpl).writeRawLong(outFilePathHash)

    val classToUsageList = Object2ObjectOpenHashMap<Class<out Usage>, MutableList<Usage>>()
    for (usage in usages) {
      classToUsageList.computeIfAbsent(usage.javaClass) { ArrayList() }.add(usage)
    }

    out.writeInt(classToUsageList.size)
    for (entry in classToUsageList.object2ObjectEntrySet().fastIterator()) {
      out.writeGraphElementCollection(entry.key, entry.value)
    }

    out.writeInt(metadata.size)
    for (t in metadata) {
      out.writeGraphElement(t)
    }
  }

  final override fun getReferenceID(): JvmNodeReferenceID = id

  final override fun getUsages(): Iterable<Usage> = usages

  @Suppress("unused")
  fun getMetadata(): Iterable<JvmMetadata<*, *>> = Iterable { metadata.iterator() }

  @Suppress("unused")
  fun <MT : JvmMetadata<MT, *>> getMetadata(metaClass: Class<MT>): Iterable<MT> {
    return metadata
      .asSequence()
      .mapNotNull { if (metaClass.isInstance(it)) metaClass.cast(it) else null }
      .asIterable()
  }

  internal fun <MT : JvmMetadata<MT, *>> filterMetadata(metaClass: Class<MT>): Set<MT> {
    val size = metadata.size
    if (size == 0) {
      return emptySet()
    }
    else if (size == 1) {
      val m = metadata[0]
      if (metaClass.isInstance(metadata)) {
        val result = ObjectOpenCustomHashSet<MT>(1, DiffCapableHashStrategy)
        @Suppress("UNCHECKED_CAST")
        result.add(m as MT)
        return result
      }
      else {
        return emptySet()
      }
    }
    else {
      var result: ObjectOpenCustomHashSet<MT>? = null
      for (m in metadata) {
        if (metaClass.isInstance(m)) {
          if (result == null) {
            result = ObjectOpenCustomHashSet<MT>(DiffCapableHashStrategy)
          }
          @Suppress("UNCHECKED_CAST")
          result.add(m as MT)
        }
      }
      return result ?: emptySet()
    }
  }

  final override fun isSame(other: DiffCapable<*, *>?): Boolean {
    if (other !is JVMClassNode<*, *>) {
      return false
    }

    if (this.javaClass != other.javaClass) {
      return false
    }
    val that = other
    return outFilePathHash == that.outFilePathHash && id == that.id
  }

  final override fun diffHashCode(): Int {
    return 31 * outFilePathHash.toInt() + id.hashCode()
  }

  @Suppress("unused")
  open inner class Diff(past: T) : Proto.Diff<T>(past) {
    private val past: T
      get() = myPast!!

    private val metadataDiffCache = Object2ObjectOpenHashMap<Class<out JvmMetadata<*, *>>, Specifier<*, *>>()
    private val usageDiff by lazy(LazyThreadSafetyMode.NONE) { diff(past.usages, usages) }

    override fun unchanged(): Boolean {
      return super.unchanged() && usages().unchanged() && !metadataChanged()
    }

    fun usages(): Specifier<Usage, *> = usageDiff

    fun metadataChanged(): Boolean {
      @Suppress("UNCHECKED_CAST")
      return isMetadataChanged(past.metadata as Array<out JvmMetadata<*, Difference>>) ||
        isMetadataChanged(metadata as Array<out JvmMetadata<*, Difference>>)
    }

    private fun <MT : JvmMetadata<MT, MD>, MD : Difference> isMetadataChanged(metadata: Array<MT>): Boolean {
      for (m in metadata) {
        if (!metadataDiffCache.computeIfAbsent(m.javaClass) {
            @Suppress("UNCHECKED_CAST")
            val metaClass = it as Class<MT>
            deepDiff(past.filterMetadata(metaClass), filterMetadata(metaClass))
          }.unchanged()) {
          return true
        }
      }
      return false
    }

    fun <MT : JvmMetadata<MT, MD>, MD : Difference> metadata(metaClass: Class<MT>): Specifier<MT, MD> {
      @Suppress("UNCHECKED_CAST")
      return metadataDiffCache.computeIfAbsent(metaClass) {
        deepDiff(past.filterMetadata(it as Class<MT>), filterMetadata(it))
      } as Specifier<MT, MD>
    }
  }
}