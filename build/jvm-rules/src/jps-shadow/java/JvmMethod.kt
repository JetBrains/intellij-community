// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java

import org.jetbrains.bazel.jvm.util.emptyList
import org.jetbrains.jps.dependency.GraphDataInput
import org.jetbrains.jps.dependency.GraphDataOutput
import org.jetbrains.jps.dependency.Node
import org.jetbrains.jps.dependency.diff.DiffCapable
import org.jetbrains.jps.dependency.diff.Difference
import org.jetbrains.jps.dependency.readList
import org.jetbrains.jps.dependency.writeCollection
import org.jetbrains.jps.javac.Iterators
import org.jetbrains.org.objectweb.asm.Type
import java.util.function.Predicate
import kotlin.jvm.JvmField

class JvmMethod : ProtoMember, DiffCapable<JvmMethod, JvmMethod.Diff> {
  @JvmField
  internal val argTypes: List<TypeRepr>
  @JvmField
  internal val paramAnnotations: Collection<ParamAnnotation>
  @JvmField
  internal val exceptions: List<TypeRepr.ClassType>

  constructor(
    flags: JVMFlags?,
    signature: String?,
    name: String?,
    descriptor: String?,
    annotations: Iterable<ElementAnnotation>,
    parameterAnnotations: Collection<ParamAnnotation>,
    exceptions: List<TypeRepr.ClassType>,
    defaultValue: Any?
  ) : super(flags, signature, name, TypeRepr.getType(Type.getReturnType(descriptor)), annotations, defaultValue) {
    this.paramAnnotations = parameterAnnotations
    this.exceptions = exceptions
    this.argTypes = getTypes(Type.getArgumentTypes(descriptor))
  }

  constructor(input: GraphDataInput) : super(input) {
    argTypes = input.readList { TypeRepr.getType(readUTF()) }
    paramAnnotations = input.readList { ParamAnnotation(this) }
    exceptions = input.readList { TypeRepr.ClassType(readUTF()) }
  }

  @Suppress("unused")
  fun getParamAnnotations(): Iterable<ParamAnnotation> = paramAnnotations

  @Suppress("unused")
  fun getExceptions(): Iterable<TypeRepr.ClassType> = exceptions

  @Suppress("unused")
  fun getArgTypes(): Iterable<TypeRepr> = argTypes

  override fun write(out: GraphDataOutput) {
    super.write(out)
    out.writeCollection(argTypes) { writeUTF(it.descriptor) }
    out.writeCollection(paramAnnotations) { it.write(this) }
    out.writeCollection(exceptions) { writeUTF(it.jvmName) }
  }

  val isConstructor: Boolean
    get() = "<init>" == name

  @Suppress("unused", "SpellCheckingInspection")
  val isStaticInitializer: Boolean
    get() = "<clinit>" == name

  @Suppress("unused")
  val isOverridable: Boolean
    get() = !isFinal && !isStatic && !isPrivate && !this.isConstructor

  override fun createUsage(owner: JvmNodeReferenceID): MethodUsage {
    return MethodUsage(id = owner, name = name, descriptor = getDescriptor())
  }

  @Suppress("unused")
  fun createUsageQuery(owner: JvmNodeReferenceID): Predicate<Node<*, *>> {
    val thisMethodName = name
    return Predicate { node ->
      node.getUsages().any { it is MethodUsage && owner == it.getElementOwner() && it.name == thisMethodName }
    }
  }

  fun isSameByJavaRules(other: JvmMethod): Boolean {
    return name == other.name && argTypes == other.argTypes
  }

  override fun isSame(other: DiffCapable<*, *>?): Boolean {
    return other is JvmMethod && type == other.type && isSameByJavaRules(other)
  }

  override fun diffHashCode(): Int {
    return 31 * (31 * Iterators.hashCode(argTypes) + type.hashCode()) + name.hashCode()
  }

  override fun equals(other: Any?): Boolean = other is JvmMethod && isSame(other)

  override fun hashCode(): Int = diffHashCode()

  override fun difference(past: JvmMethod): Diff = Diff(past)

  inner class Diff(past: JvmMethod) : ProtoMember.Diff<JvmMethod>(past) {
    private val paramAnnotationsDiff by lazy(LazyThreadSafetyMode.NONE) { Difference.deepDiff(myPast!!.paramAnnotations, paramAnnotations) }
    private val exceptionsDiff by lazy(LazyThreadSafetyMode.NONE) { Difference.diff(myPast!!.exceptions, exceptions) }

    override fun unchanged(): Boolean {
      return super.unchanged() && paramAnnotationsDiff.unchanged() && exceptionsDiff.unchanged()
    }

    @Suppress("unused")
    fun paramAnnotations(): Difference.Specifier<ParamAnnotation, ParamAnnotation.Diff> = paramAnnotationsDiff

    @Suppress("unused")
    fun exceptions(): Difference.Specifier<TypeRepr.ClassType?, *> = exceptionsDiff
  }

  fun getDescriptor(): String = getDescriptor(argTypes, type)

  override fun toString(): String = name + getDescriptor()
}

private fun getTypes(types: Array<Type>): List<TypeRepr> {
  return if (types.isEmpty()) emptyList() else types.map { TypeRepr.getType(it) }
}

private fun getDescriptor(argTypes: Iterable<TypeRepr>, returnType: TypeRepr): String {
  val buf = StringBuilder()

  buf.append('(')

  for (t in argTypes) {
    buf.append(t.descriptor)
  }

  buf.append(')')
  buf.append(returnType.descriptor)

  return buf.toString()
}