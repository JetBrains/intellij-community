@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.jps.dependency.java.nodeBuilder

import androidx.collection.MutableScatterMap
import androidx.collection.MutableScatterSet
import org.jetbrains.jps.dependency.NodeBuilder
import org.jetbrains.jps.dependency.java.ClassUsage
import org.jetbrains.jps.dependency.java.ContentHashBuilderImpl
import org.jetbrains.jps.dependency.java.ElemType
import org.jetbrains.jps.dependency.java.ElementAnnotation
import org.jetbrains.jps.dependency.java.JvmDifferentiateStrategy
import org.jetbrains.jps.dependency.java.MethodUsage
import org.jetbrains.jps.dependency.java.TypeRepr
import org.jetbrains.org.objectweb.asm.AnnotationVisitor
import org.jetbrains.org.objectweb.asm.Opcodes.API_VERSION
import org.jetbrains.org.objectweb.asm.Type
import java.util.*

private val differentiateStrategies = ServiceLoader.load(JvmDifferentiateStrategy::class.java).toList()

private fun isAnnotationTracked(annotationType: TypeRepr.ClassType): Boolean {
  for (strategy in differentiateStrategies) {
    if (strategy.isAnnotationTracked(annotationType)) {
      return true
    }
  }
  return false
}

internal class AnnotationCrawler(
  private val type: TypeRepr.ClassType,
  private val target: ElemType,
  private val nodeBuilder: NodeBuilder,
  private val annotationTargets: MutableScatterMap<TypeRepr.ClassType, EnumSet<ElemType>>,
  private val annotationArguments: MutableScatterMap<TypeRepr.ClassType, MutableScatterSet<String>>,
  private val resultConsumer: ((ElementAnnotation) -> Unit)?,
) : AnnotationVisitor(API_VERSION) {
  // Do not track changes in the annotation's content if there are no registered annotation trackers that would process these changes.
  // Some technical annotations (e.g., DebugInfo)
  // may contain different content after every compiler run => they will always be considered "changed".
  // Handling such changes may involve additional type-consuming analysis and unnecessary dependency data updates.
  private val hashBuilder = if (isAnnotationTracked(type)) ContentHashBuilderImpl() else null

  private val usedArguments = MutableScatterSet<String>()
  private var arrayName: String? = null

  init {
    val targets = annotationTargets.get(type)
    if (targets == null) {
      annotationTargets.put(type, EnumSet.of(target))
    }
    else {
      targets.add(target)
    }
    nodeBuilder.addUsage(ClassUsage(type.jvmName))
  }

  fun getMethodDescr(value: Any, isArray: Boolean): String {
    val descriptor = StringBuilder()
    descriptor.append("()")
    if (isArray) {
      descriptor.append('[')
    }
    if (value is Type) {
      descriptor.append("Ljava/lang/Class;")
    }
    else {
      val name: String = Type.getType(value.javaClass).internalName
      // only primitive, String, Class, Enum, another Annotation or array of any of these are allowed
      when (name) {
        "java/lang/Integer" -> descriptor.append("I")
        "java/lang/Short" -> descriptor.append("S")
        "java/lang/Long" -> descriptor.append("J")
        "java/lang/Byte" -> descriptor.append("B")
        "java/lang/Char" -> descriptor.append("C")
        "java/lang/Boolean" -> descriptor.append("Z")
        "java/lang/Float" -> descriptor.append("F")
        "java/lang/Double" -> descriptor.append("D")
        else -> descriptor.append("L").append(name).append(";")
      }
    }
    return descriptor.toString()
  }

  override fun visit(name: String?, value: Any) {
    val isArray = name == null && arrayName != null
    val argName: String?
    if (name == null) {
      argName = arrayName
      // not interested in collecting complete array value; need to know just an array type
      arrayName = null
    }
    else {
      argName = name
    }
    registerUsages(argName, value) { getMethodDescr(value, isArray) }
  }

  override fun visitEnum(name: String?, desc: String?, value: String) {
    val isArray = name == null && arrayName != null
    val argName: String?
    if (name != null) {
      argName = name
    }
    else {
      argName = arrayName
      // not interested in collecting complete array value; need to know just an array type
      arrayName = null
    }
    registerUsages(argName, value) { (if (isArray) "()[" else "()") + desc }
  }

  override fun visitAnnotation(name: String?, desc: String): AnnotationVisitor {
    return AnnotationCrawler(
      type = TypeRepr.getType(desc) as TypeRepr.ClassType,
      target = target,
      annotationTargets = annotationTargets,
      annotationArguments = annotationArguments,
      nodeBuilder = nodeBuilder,
      resultConsumer = if (hashBuilder == null) null else { { hashBuilder.update(it.contentHash) } },
    )
  }

  override fun visitArray(name: String?): AnnotationVisitor? {
    arrayName = name
    return this
  }

  private inline fun registerUsages(methodName: String?, value: Any, methodDescr: () -> String) {
    if (value is Type) {
      val className = value.className.replace('.', '/')
      nodeBuilder.addUsage(ClassUsage(className))
      hashBuilder?.putString(className)
    }
    else {
      hashBuilder?.update(value)
    }

    if (methodName != null) {
      nodeBuilder.addUsage(MethodUsage(type.jvmName, methodName, methodDescr()))
      usedArguments.add(methodName)
      hashBuilder?.putString(methodName)
    }
  }

  override fun visitEnd() {
    try {
      val s = annotationArguments.get(type)
      if (s == null) {
        annotationArguments.put(type, usedArguments)
      }
      else {
        s.retainAll(usedArguments)
      }
    }
    finally {
      resultConsumer?.invoke(ElementAnnotation(type, hashBuilder?.getResult()))
    }
  }
}

internal class AnnotationTargetCrawler(private val targets: EnumSet<ElemType>) : AnnotationVisitor(API_VERSION) {
  override fun visit(name: String?, value: Any?) {
  }

  override fun visitEnum(name: String?, desc: String?, value: String) {
    targets.add(ElemType.valueOf(value))
  }

  override fun visitAnnotation(name: String?, desc: String?): AnnotationVisitor? = this

  override fun visitArray(name: String?): AnnotationVisitor? = this

  override fun visitEnd() {
  }
}