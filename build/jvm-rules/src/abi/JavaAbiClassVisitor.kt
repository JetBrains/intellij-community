// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.abi

import org.jetbrains.org.objectweb.asm.AnnotationVisitor
import org.jetbrains.org.objectweb.asm.Attribute
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.FieldVisitor
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.ModuleVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.RecordComponentVisitor
import org.jetbrains.org.objectweb.asm.TypePath
import org.jetbrains.org.objectweb.asm.tree.AnnotationNode
import org.jetbrains.org.objectweb.asm.tree.FieldNode
import org.jetbrains.org.objectweb.asm.tree.MethodNode

internal class JavaAbiClassVisitor(
  private val classVisitor: ClassVisitor,
  private val classesToBeDeleted: MutableSet<String>,
) : ClassVisitor(Opcodes.API_VERSION) {
  // tracks if this class has any public API members
  var isApiClass: Boolean = true
    private set

  private val visibleAnnotations = ArrayList<AnnotationNode>()
  private val invisibleAnnotations = ArrayList<AnnotationNode>()

  private val fields = mutableListOf<FieldNode>()
  private val methods = mutableListOf<MethodNode>()

  override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
    val annotationNode = AnnotationNode(api, descriptor)
    (if (visible) visibleAnnotations else invisibleAnnotations).add(annotationNode)
    return annotationNode
  }

  override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<String>?) {
    //isApiClass = (access and (Opcodes.ACC_PUBLIC or Opcodes.ACC_PROTECTED)) != 0
    // we have a lot of violations for now - allow any, even package-local class
    isApiClass = (access and (Opcodes.ACC_PUBLIC or Opcodes.ACC_PROTECTED)) != 0
    if (isApiClass) {
      classVisitor.visit(version, access, name, signature, superName, interfaces)
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
    descriptor: String?,
    signature: String?,
    exceptions: Array<String>?,
  ): MethodVisitor? {
    //if ((access and (Opcodes.ACC_PUBLIC or Opcodes.ACC_PROTECTED)) == 0) {
    //  return null
    //}

    val method = MethodNode(Opcodes.API_VERSION, access, name, descriptor, signature, exceptions)
    methods.add(method)
    return method
  }

  override fun visitEnd() {
    visibleAnnotations.sortBy { it.desc }
    invisibleAnnotations.sortBy { it.desc }

    fields.sortBy { it.name }
    methods.sortBy { it.name }

    for (annotation in visibleAnnotations) {
      annotation.accept(classVisitor.visitAnnotation(annotation.desc, true))
    }
    for (annotation in invisibleAnnotations) {
      annotation.accept(classVisitor.visitAnnotation(annotation.desc, false))
    }

    for (field in fields) {
      field.accept(classVisitor)
    }

    for (method in methods) {
      method.accept(classVisitor)
    }

    classVisitor.visitEnd()
  }

  override fun visitSource(source: String?, debug: String?) {
    classVisitor.visitSource(source, debug)
  }

  override fun visitModule(name: String?, access: Int, version: String?): ModuleVisitor? {
    return classVisitor.visitModule(name, access, version)
  }

  override fun visitAttribute(attribute: Attribute?) {
    classVisitor.visitAttribute(attribute)
  }

  override fun visitOuterClass(owner: String?, name: String?, descriptor: String?) {
    classVisitor.visitOuterClass(owner, name, descriptor)
  }

  override fun visitRecordComponent(name: String?, descriptor: String?, signature: String?): RecordComponentVisitor? {
    return classVisitor.visitRecordComponent(name, descriptor, signature)
  }

  override fun visitTypeAnnotation(typeRef: Int, typePath: TypePath?, descriptor: String?, visible: Boolean): AnnotationVisitor? {
    return classVisitor.visitTypeAnnotation(typeRef, typePath, descriptor, visible)
  }

  override fun visitNestMember(nestMember: String?) {
    if (nestMember == null || !classesToBeDeleted.contains(nestMember)) {
      classVisitor.visitNestMember(nestMember)
    }
  }

  override fun visitPermittedSubclass(permittedSubclass: String?) {
    if (permittedSubclass == null || !classesToBeDeleted.contains(permittedSubclass)) {
      classVisitor.visitPermittedSubclass(permittedSubclass)
    }
  }

  override fun visitInnerClass(name: String?, outerName: String?, innerName: String?, access: Int) {
    if (innerName == null || !classesToBeDeleted.contains(name)) {
      classVisitor.visitInnerClass(name, outerName, innerName, access)
    }
  }

  override fun visitNestHost(nestHost: String?) {
    classVisitor.visitNestHost(nestHost)
  }
}
