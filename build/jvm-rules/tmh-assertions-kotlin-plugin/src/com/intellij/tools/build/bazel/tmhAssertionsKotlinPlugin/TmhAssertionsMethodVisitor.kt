// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.tmhAssertionsKotlinPlugin

import com.intellij.tools.build.bazel.jvmIncBuilder.tmh.TMHAssertionGenerator2
import com.intellij.tools.build.bazel.jvmIncBuilder.tmh.TMHMethodInstrumenter
import org.jetbrains.org.objectweb.asm.AnnotationVisitor
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.tree.LineNumberNode
import org.jetbrains.org.objectweb.asm.tree.MethodNode

/**
 * Watches the annotations of one method and adds the assertion calls when one of them asks for an assertion. The
 * annotations arrive before the code. A method without a threading annotation passes through unchanged. A method with
 * one is recorded from the start of its code into a [MethodNode]. At the end of the method the recording is replayed
 * through the [TMHMethodInstrumenter] of instrumentation-core, which writes the calls before the first instruction. The
 * replay keeps every label and every instruction in the recorded order.
 *
 * The line number of the calls is the first line number of the method, as in the worker, or none when the option
 * `generateLineNumbers` is false.
 *
 * @param target the method visitor of the compiler
 * @param generators the assertion generators whose assertion the method can take
 */
internal class TmhAssertionsMethodVisitor(
  private val target: MethodVisitor,
  private val generators: List<TMHAssertionGenerator2>,
  private val generateLineNumbers: Boolean,
) : MethodVisitor(Opcodes.API_VERSION, target) {
  /**
   * The generators whose annotation asked for an assertion, in the order of the annotations.
   */
  private val requested = ArrayList<TMHAssertionGenerator2>()
  private var recording: MethodNode? = null

  override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
    val visitor = super.visitAnnotation(descriptor, visible)
    val generator = generators.firstOrNull { it.isMyAnnotation(descriptor) } ?: return visitor
    val checker = generator.getAnnotationChecker(Opcodes.API_VERSION) { requested.add(generator) }
    return TeeAnnotationVisitor(visitor, checker)
  }

  override fun visitCode() {
    if (requested.isNotEmpty()) {
      val node = MethodNode(Opcodes.API_VERSION)
      recording = node
      mv = node
    }
    super.visitCode()
  }

  override fun visitEnd() {
    val node = recording
    if (node == null) {
      super.visitEnd()
      return
    }
    val lineNumber = if (generateLineNumbers) firstLineNumber(node) else -1
    node.accept(TMHMethodInstrumenter(Opcodes.API_VERSION, target, requested, lineNumber))
  }

  private fun firstLineNumber(node: MethodNode): Int {
    for (instruction in node.instructions) {
      if (instruction is LineNumberNode) {
        return instruction.line
      }
    }
    return -1
  }
}

/**
 * Gives each call to two annotation visitors: the visitor of the compiler, which writes the annotation, and the checker
 * of an assertion generator, which reads it.
 */
private class TeeAnnotationVisitor(
  first: AnnotationVisitor?,
  private val second: AnnotationVisitor?,
) : AnnotationVisitor(Opcodes.API_VERSION, first) {
  override fun visit(name: String?, value: Any?) {
    super.visit(name, value)
    second?.visit(name, value)
  }

  override fun visitEnum(name: String?, descriptor: String?, value: String?) {
    super.visitEnum(name, descriptor, value)
    second?.visitEnum(name, descriptor, value)
  }

  override fun visitAnnotation(name: String?, descriptor: String?): AnnotationVisitor? {
    return tee(super.visitAnnotation(name, descriptor), second?.visitAnnotation(name, descriptor))
  }

  override fun visitArray(name: String?): AnnotationVisitor? {
    return tee(super.visitArray(name), second?.visitArray(name))
  }

  override fun visitEnd() {
    super.visitEnd()
    second?.visitEnd()
  }

  private fun tee(first: AnnotationVisitor?, second: AnnotationVisitor?): AnnotationVisitor? {
    if (second == null) {
      return first
    }
    if (first == null) {
      return second
    }
    return TeeAnnotationVisitor(first, second)
  }
}
