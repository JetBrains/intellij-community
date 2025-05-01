// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  private val fields: Collection<JvmField>
  private val methods: Collection<JvmMethod>
  val annotationTargets: Collection<ElemType>

  private val retentionPolicy: RetentionPolicy?

  constructor(
    flags: JVMFlags,
    signature: String?,
    fqName: String,
    outFilePathHash: Long,
    superFqName: String?,
    outerFqName: String?,
    interfaces: Collection<String>,
    fields: Collection<JvmField>,
    methods: Collection<JvmMethod>,
    annotations: Iterable<ElementAnnotation>,
    annotationTargets: Collection<ElemType>,
    retentionPolicy: RetentionPolicy?,
    usages: Collection<Usage>,
    metadata: Collection<JvmMetadata<*, *>>
  ) : super(
    flags = flags,
    signature = signature,
    name = fqName,
    outFilePathHash = outFilePathHash,
    annotations = annotations,
    usages = usages,
    metadata = metadata,
  ) {
    this.superFqName = if (superFqName == null || OBJECT_CLASS_NAME == superFqName) "" else superFqName
    this.outerFqName = outerFqName ?: ""
    this.interfaces = interfaces
    this.fields = fields
    this.methods = methods
    this.annotationTargets = annotationTargets
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

  companion object {
    const val OBJECT_CLASS_NAME: String = "java/lang/Object"

    @JvmStatic
    fun getPackageName(jvmClassName: String): String {
      val index = jvmClassName.lastIndexOf('/')
      return if (index >= 0) jvmClassName.take(index) else ""
    }
  }

  @Suppress("unused")
  fun getMethods(): Iterable<JvmMethod> = methods

  @Suppress("unused")
  fun getFields(): Iterable<JvmField> = fields

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
      return if (interfaces.isEmpty()) emptySequence() else interfaces.asSequence()
    }
    else {
      return (sequenceOf(superFqName) + interfaces)
    }
  }

  @Suppress("unused")
  fun getRetentionPolicy(): RetentionPolicy? = retentionPolicy

  override fun difference(past: JvmClass): Diff {
    check(this !== past) {
      "Diff must not be called for identical class"
    }
    return Diff(
      past = past,
      interfacesDiff = lazy(LazyThreadSafetyMode.NONE) { diff(past.interfaces, interfaces) },
      methodsDiff = lazy(LazyThreadSafetyMode.NONE) { deepDiff(past.methods, methods) },
      fieldsDiff = lazy(LazyThreadSafetyMode.NONE) { deepDiff(past.fields, fields) },
      annotationTargetsDiff = lazy(LazyThreadSafetyMode.NONE) { diff(past.annotationTargets, annotationTargets) }
    )
  }

  inner class Diff internal constructor(
    past: JvmClass,
    private val interfacesDiff: Lazy<Difference.Specifier<String, Difference>>,
    private val methodsDiff: Lazy<Difference.Specifier<JvmMethod, JvmMethod.Diff>>,
    private val fieldsDiff: Lazy<Difference.Specifier<JvmField, JvmField.Diff>>,
    private val annotationTargetsDiff: Lazy<Difference.Specifier<ElemType, Difference>>,
  ) : JVMClassNode<JvmClass, Diff>.Diff(past) {
    override fun unchanged(): Boolean {
      return super.unchanged() &&
        !superClassChanged() &&
        !outerClassChanged() &&
        interfacesDiff.value.unchanged() &&
        methodsDiff.value.unchanged() &&
        fieldsDiff.value.unchanged() &&
        !retentionPolicyChanged() &&
        annotationTargetsDiff.value.unchanged()
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

    fun interfaces(): Difference.Specifier<String, *> = interfacesDiff.value

    @Suppress("unused")
    fun methods(): Difference.Specifier<JvmMethod, JvmMethod.Diff> = methodsDiff.value

    @Suppress("unused")
    fun fields(): Difference.Specifier<JvmField, JvmField.Diff> = fieldsDiff.value

    fun retentionPolicyChanged(): Boolean = myPast.retentionPolicy != retentionPolicy

    @Suppress("unused")
    fun annotationTargets(): Difference.Specifier<ElemType, *> = annotationTargetsDiff.value

    @Suppress("unused")
    fun targetAttributeCategoryMightChange(): Boolean {
      val targetsDiff = annotationTargetsDiff
      if (!targetsDiff.value.unchanged()) {
        for (elementType in arrayOf(ElemType.TYPE_USE, ElemType.RECORD_COMPONENT)) {
          if (targetsDiff.value.added().contains(elementType) ||
            targetsDiff.value.removed().contains(elementType) ||
            myPast.annotationTargets.contains(elementType)) {
            return true
          }
        }
      }
      return false
    }
  }
}