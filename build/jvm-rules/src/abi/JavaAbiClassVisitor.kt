// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.abi

import androidx.collection.MutableScatterSet
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.FieldVisitor
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.tree.FieldNode
import org.jetbrains.org.objectweb.asm.tree.MethodNode

internal class JavaAbiClassVisitor(
  classVisitor: ClassVisitor,
  private val classesToBeDeleted: MutableScatterSet<String>,
  private val abiErrorConsumer: (String) -> Unit,
) : ClassVisitor(Opcodes.API_VERSION, classVisitor) {
  private var stripNonPublicMethods = true

  var isApiClass: Boolean = true
    private set

  private val fields = mutableListOf<FieldNode>()
  private val methods = mutableListOf<MethodNode>()

  override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<String>?) {
    isApiClass = (access and (Opcodes.ACC_PUBLIC or Opcodes.ACC_PROTECTED)) != 0
    if (isApiClass) {
      stripNonPublicMethods = !name.contains("android")
      if (superName != null && superName != "java/lang/Object" && classesToBeDeleted.contains(superName)) {
        val subClass = formatClassName(name)
        val parentClass = formatClassName(superName)
        abiErrorConsumer("""
          Class $subClass is public, 
          but it extends $parentClass, which is not public.
          
          A public class must only extend classes that are accessible at the same visibility level.
          
          To fix this, consider either:
            * Reducing the visibility of $subClass, or
            * Making $parentClass public (and annotate it with @ApiStatus.Internal if applicable).
        """.trimIndent())
      }
      super.visit(version, access, name, signature, superName, interfaces)
    }
    else {
      classesToBeDeleted.add(name)
    }
  }

  override fun visitField(
    access: Int,
    name: String?,
    descriptor: String?,
    signature: String?,
    value: Any?,
  ): FieldVisitor? {
    if ((access and (Opcodes.ACC_PUBLIC or Opcodes.ACC_PROTECTED)) == 0) {
      return null
    }

    val fieldNode = FieldNode(api, access, name, descriptor, signature, value)
    fields.add(fieldNode)
    return fieldNode
  }

  override fun visitMethod(
    access: Int,
    name: String,
    descriptor: String,
    signature: String?,
    exceptions: Array<String>?,
  ): MethodVisitor? {
    if ((access and (Opcodes.ACC_PUBLIC or Opcodes.ACC_PROTECTED)) == 0 && stripNonPublicMethods) {
      return null
    }

    val method = MethodNode(Opcodes.API_VERSION, access, name, descriptor, signature, exceptions)
    methods.add(method)
    return method
  }

  override fun visitEnd() {
    fields.sortBy { it.name }
    for (field in fields) {
      field.accept(cv)
    }

    methods.sortBy { it.name }
    for (method in methods) {
      method.accept(cv)
    }

    cv.visitEnd()
  }

  override fun visitSource(source: String?, debug: String?) {
    // skip
  }

  override fun visitNestMember(nestMember: String?) {
    if (nestMember == null || !classesToBeDeleted.contains(nestMember)) {
      super.visitNestMember(nestMember)
    }
  }

  override fun visitPermittedSubclass(permittedSubclass: String?) {
    if (permittedSubclass == null || !classesToBeDeleted.contains(permittedSubclass)) {
      super.visitPermittedSubclass(permittedSubclass)
    }
  }

  override fun visitInnerClass(name: String, outerName: String?, innerName: String?, access: Int) {
    if (innerName == null || !classesToBeDeleted.contains(name)) {
      super.visitInnerClass(name, outerName, innerName, access)
    }
  }
}

private fun formatClassName(s: String): String = s.replace('/', '.')