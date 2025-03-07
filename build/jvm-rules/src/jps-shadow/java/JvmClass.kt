@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.jps.dependency.java

import org.jetbrains.jps.dependency.GraphDataInput
import org.jetbrains.jps.dependency.GraphDataOutput
import org.jetbrains.jps.dependency.Usage
import org.jetbrains.jps.dependency.diff.Difference
import org.jetbrains.jps.dependency.readList
import org.jetbrains.jps.dependency.writeCollection

import java.lang.annotation.RetentionPolicy

class JvmClass : JVMClassNode<JvmClass, JvmClass.Diff> {
  val outerFqName: String?
  val superFqName: String
  val interfaces: Collection<String>
  val fields: Collection<JvmField>
  private val methods: Collection<JvmMethod>
  val annotationTargets: Collection<ElemType>

  companion object {
    const val OBJECT_CLASS_NAME: String = "java/lang/Object"

    @JvmStatic
    fun getPackageName(jvmClassName: String): String {
      val index = jvmClassName.lastIndexOf('/')
      return if (index >= 0) jvmClassName.substring(0, index) else ""
    }
  }

  private val retentionPolicy: RetentionPolicy?

  constructor(
    flags: JVMFlags,
    signature: String?,
    fqName: String,
    outFilePath: String,
    superFqName: String?,
    outerFqName: String?,
    interfaces: Iterable<String>,
    fields: Iterable<JvmField>?,
    methods: Iterable<JvmMethod>?,
    annotations: Iterable<ElementAnnotation>,
    annotationTargets: Iterable<ElemType>?,
    retentionPolicy: RetentionPolicy?,
    usages: Iterable<Usage>,
    metadata: Iterable<JvmMetadata<*, *>>
  ) : super(
    flags = flags,
    signature = signature,
    name = fqName,
    outFilePath = outFilePath,
    annotations = annotations,
    usages = usages,
    metadata = metadata,
  ) {
    this.superFqName = if (superFqName == null || OBJECT_CLASS_NAME == superFqName) "" else superFqName
    this.outerFqName = outerFqName ?: ""
    this.interfaces = interfaces as Collection<String>
    this.fields = fields as Collection<JvmField>
    this.methods = methods as Collection<JvmMethod>
    this.annotationTargets = annotationTargets as Collection<ElemType>
    this.retentionPolicy = retentionPolicy
  }

  @Suppress("unused")
  constructor(input: GraphDataInput) : super(input) {
    outerFqName = input.readUTF()
    superFqName = input.readUTF()
    interfaces = input.readList { readUTF() }
    fields = input.readList { JvmField(this) }
    methods = input.readList { JvmMethod(input) }
    annotationTargets = input.readList { ElemType.fromOrdinal(readUnsignedByte()) }

    val policyOrdinal = input.readByte().toInt()
    retentionPolicy = if (policyOrdinal == -1) null else RetentionPolicy.entries.getOrNull(policyOrdinal)
  }

  @Suppress("unused")
  fun getMethods(): Iterable<JvmMethod> = methods

  override fun write(out: GraphDataOutput) {
    super.write(out)
    out.writeUTF(outerFqName)
    out.writeUTF(superFqName)
    out.writeCollection(interfaces) { writeUTF(it) }
    out.writeCollection(fields) { it.write(this) }
    out.writeCollection(methods) { it.write(this) }
    out.writeCollection(annotationTargets) { writeByte(it.ordinal) }
    out.writeByte(retentionPolicy?.ordinal ?: -1)
  }

  @Suppress("unused")
  fun getPackageName(): String = getPackageName(name)

  @Suppress("unused")
  fun getShortName(): String {
    val fqName = name
    if (isInnerClass() && fqName.startsWith(outerFqName!!) && fqName.length > outerFqName.length) {
      // for inner classes use 'real' class short name as it appears in source code
      return fqName.substring(outerFqName.length + 1)
    }
    val index = fqName.lastIndexOf('/')
    return if (index >= 0) fqName.substring(index + 1) else fqName
  }

  @Suppress("unused")
  fun isInterface(): Boolean = flags.isInterface

  @Suppress("unused")
  fun isAnonymous(): Boolean = flags.isAnonymous

  @Suppress("unused")
  fun isSealed(): Boolean = flags.isSealed

  @Suppress("unused")
  fun isLocal(): Boolean = flags.isLocal

  @Suppress("unused")
  fun isInnerClass(): Boolean = !outerFqName.isNullOrEmpty()

  @Suppress("unused")
  fun getSuperTypes(): Iterable<String> {
    if (superFqName.isEmpty() || OBJECT_CLASS_NAME == superFqName) {
      return interfaces
    }
    else {
      return (sequenceOf(superFqName) + interfaces).asIterable()
    }
  }

  fun superTypes(): Sequence<String> {
    if (superFqName.isEmpty() || OBJECT_CLASS_NAME == superFqName) {
      return interfaces.asSequence()
    }
    else {
      return (sequenceOf(superFqName) + interfaces)
    }
  }

  @Suppress("unused")
  fun getRetentionPolicy(): RetentionPolicy? = retentionPolicy

  override fun difference(past: JvmClass): Diff = Diff(past)

  inner class Diff(past: JvmClass) : JVMClassNode<JvmClass, Diff>.Diff(past) {
    private val interfacesDiff by lazy(LazyThreadSafetyMode.NONE) { diff(myPast.interfaces, interfaces) }
    private val methodsDiff by lazy(LazyThreadSafetyMode.NONE) { deepDiff(myPast.methods, methods) }
    private val fieldsDiff by lazy(LazyThreadSafetyMode.NONE) { deepDiff(myPast.fields, fields) }
    private val annotationTargetsDiff by lazy(LazyThreadSafetyMode.NONE) { diff(myPast.annotationTargets, annotationTargets) }

    override fun unchanged(): Boolean {
      return super.unchanged() &&
        !superClassChanged() &&
        !outerClassChanged() &&
        interfacesDiff.unchanged() &&
        methodsDiff.unchanged() &&
        fieldsDiff.unchanged() &&
        !retentionPolicyChanged() &&
        annotationTargets().unchanged()
    }

    fun superClassChanged(): Boolean = myPast.superFqName != superFqName

    @Suppress("unused")
    fun extendsAdded(): Boolean {
      val pastSuper = myPast.superFqName
      return (pastSuper.isEmpty() || OBJECT_CLASS_NAME == pastSuper) && superClassChanged()
    }

    @Suppress("unused")
    fun extendsRemoved(): Boolean {
      val currentSuper = superFqName
      return (currentSuper.isEmpty() || OBJECT_CLASS_NAME == currentSuper) && superClassChanged()
    }

    fun outerClassChanged(): Boolean = myPast.outerFqName != outerFqName

    fun interfaces(): Difference.Specifier<String, *> = interfacesDiff

    @Suppress("unused")
    fun methods(): Difference.Specifier<JvmMethod, JvmMethod.Diff> = methodsDiff

    @Suppress("unused")
    fun fields(): Difference.Specifier<JvmField, JvmField.Diff> = fieldsDiff

    fun retentionPolicyChanged(): Boolean = myPast.retentionPolicy != retentionPolicy

    fun annotationTargets(): Difference.Specifier<ElemType, *> = annotationTargetsDiff

    @Suppress("unused")
    fun targetAttributeCategoryMightChange(): Boolean {
      val targetsDiff = annotationTargetsDiff
      if (!targetsDiff.unchanged()) {
        for (elementType in arrayOf(ElemType.TYPE_USE, ElemType.RECORD_COMPONENT)) {
          if (targetsDiff.added().contains(elementType) ||
            targetsDiff.removed().contains(elementType) ||
            myPast.annotationTargets.contains(elementType)) {
            return true
          }
        }
      }
      return false
    }
  }
}