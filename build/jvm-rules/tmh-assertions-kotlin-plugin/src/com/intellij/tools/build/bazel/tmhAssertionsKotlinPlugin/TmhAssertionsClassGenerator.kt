// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.tmhAssertionsKotlinPlugin

import com.intellij.tools.build.bazel.jvmIncBuilder.tmh.TMHAssertionGenerator2
import org.jetbrains.kotlin.backend.jvm.extensions.ClassGenerator
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes

/**
 * The class generator of one class with the threading model assertions. It gives every call to the generator of the
 * compiler, so the class keeps the code, the debug information and the constant pool order of a compile without the
 * plugin. A method that can take an assertion gets a [TmhAssertionsMethodVisitor] in front of the visitor of the
 * compiler.
 *
 * @param generators the assertion generators of the compile; a method takes the ones whose annotation it carries
 */
internal class TmhAssertionsClassGenerator(
  private val original: ClassGenerator,
  private val generators: Set<TMHAssertionGenerator2>,
  private val generateLineNumbers: Boolean,
) : ClassGenerator by original {
  override fun newMethod(
    declaration: IrFunction?,
    access: Int,
    name: String,
    desc: String,
    signature: String?,
    exceptions: Array<out String>?,
  ): MethodVisitor {
    val visitor = original.newMethod(declaration, access, name, desc, signature, exceptions)
    if (access and (Opcodes.ACC_ABSTRACT or Opcodes.ACC_NATIVE) != 0) {
      return visitor
    }
    val applicable = generators.filter { it.isApplicableMethod(desc) }
    if (applicable.isEmpty()) {
      return visitor
    }
    return TmhAssertionsMethodVisitor(visitor, applicable, generateLineNumbers)
  }
}
