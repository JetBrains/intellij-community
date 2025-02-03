// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.abi

import org.jetbrains.org.objectweb.asm.AnnotationVisitor
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.FieldVisitor
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.commons.ClassRemapper
import org.jetbrains.org.objectweb.asm.commons.Remapper
import org.jetbrains.org.objectweb.asm.tree.FieldNode
import org.jetbrains.org.objectweb.asm.tree.MethodNode
import kotlin.metadata.KmClass
import kotlin.metadata.KmDeclarationContainer
import kotlin.metadata.KmFunction
import kotlin.metadata.KmPackage
import kotlin.metadata.KmProperty
import kotlin.metadata.Visibility
import kotlin.metadata.hasConstant
import kotlin.metadata.isConst
import kotlin.metadata.isData
import kotlin.metadata.isInline
import kotlin.metadata.isSecondary
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.jvm.getterSignature
import kotlin.metadata.jvm.localDelegatedProperties
import kotlin.metadata.jvm.signature
import kotlin.metadata.visibility

internal fun createAbForKotlin(classesToBeDeleted: HashSet<String>, item: JarContentToProcess): ByteArray? {
  val classWriter = ClassWriter(0)
  val innerClassesToKeep = HashSet<String>()
  val remapper = ClassRemapper(classWriter, object : Remapper() {
    override fun map(internalName: String): String {
      innerClassesToKeep.add(internalName)
      return internalName
    }

    override fun mapInnerClassName(name: String, ownerName: String?, innerName: String?): String? = innerName
  })
  val abiClassVisitor = KotlinAbiClassVisitor(
    classesToBeDeleted = classesToBeDeleted,
    treatInternalAsPrivate = false,
    remapper = remapper,
    innerClassesToKeep = innerClassesToKeep,
  )
  ClassReader(item.data).accept(abiClassVisitor, ClassReader.SKIP_FRAMES)
  if (abiClassVisitor.isApiClass) {
    return classWriter.toByteArray()
  }
  return null
}

private class InnerClassInfo(
  @JvmField val name: String,
  @JvmField val outerName: String?,
  @JvmField val innerName: String?,
  @JvmField val access: Int,
)

// inspired by JvmAbiOutputExtension
private class KotlinAbiClassVisitor(
  private val classesToBeDeleted: MutableSet<String>,
  private val treatInternalAsPrivate: Boolean,
  remapper: ClassRemapper,
  private val innerClassesToKeep: MutableSet<String>,
) : ClassVisitor(Opcodes.API_VERSION, remapper) {
  private val innerClassToInfo = mutableMapOf<String, InnerClassInfo>()

  // tracks if this class has any public API members
  var isApiClass: Boolean = true
    private set

  private val fields = mutableListOf<FieldNode>()
  private val methods = mutableListOf<MethodNode>()
  private var kotlinMetadata: Pair<String, KotlinClassMetadata>? = null

  override fun visitSource(source: String?, debug: String?) {
    // debug not important
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

    if (treatInternalAsPrivate && isFieldKotlinInternal(name)) {
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
    if ((access and (Opcodes.ACC_PUBLIC or Opcodes.ACC_PROTECTED)) == 0) {
      return null
    }
    if (treatInternalAsPrivate && isMethodKotlinInternal(name)) {
      return null
    }

    val method = MethodNode(Opcodes.API_VERSION, access, name, descriptor, signature, exceptions)
    methods.add(method)

    if (access and (Opcodes.ACC_NATIVE or Opcodes.ACC_ABSTRACT) != 0) {
      return method
    }

    val functions = kotlinMetadata?.second?.let { getFunctions(it) }
    val isInlineMethod = functions != null && functions.any { it.isInline && it.name == method.name }
    if (isInlineMethod) {
      return method
    }
    else {
      return BodyStrippingMethodVisitor(method)
    }
  }

   override fun visitInnerClass(name: String, outerName: String?, innerName: String?, access: Int) {
     // `visitInnerClass` is called before `visitField`/`visitMethod`, so we don't know
     // which types are referenced by kept methods yet.
     innerClassToInfo[name] = InnerClassInfo(name, outerName, innerName, access)
   }

  override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
    // parse @Metadata for Kotlin-specific visibility information
    @Suppress("SpellCheckingInspection")
    when (descriptor) {
      "Lkotlin/Metadata;" -> {
        return KotlinAnnotationVisitor {
          kotlinMetadata = descriptor to KotlinClassMetadata.readStrict(it)
        }
      }
      "Lkotlin/jvm/internal/SourceDebugExtension;" -> {
        return null
      }
      else -> {
        return super.visitAnnotation(descriptor, visible)
      }
    }
  }

  override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<String>?) {
    isApiClass = (access and (Opcodes.ACC_PUBLIC or Opcodes.ACC_PROTECTED)) != 0
    if (isApiClass) {
      val visibility = (kotlinMetadata?.second as? KotlinClassMetadata.Class)?.kmClass?.visibility
      if (visibility == null || !isPrivateVisibility(visibility, treatInternalAsPrivate)) {
        cv.visit(version, access, name, signature, superName, interfaces)
        return
      }
      else {
        isApiClass = false
      }
    }

    classesToBeDeleted.add(name)
  }

  override fun visitEnd() {
    fields.sortBy { it.name }
    methods.sortBy { it.name }

    kotlinMetadata?.let { (descriptor, header) ->
      transformAndWriteKotlinMetadata(
        metadata = header,
        descriptor = descriptor,
        classVisitor = cv,
        classesToBeDeleted = classesToBeDeleted,
        treatInternalAsPrivate = treatInternalAsPrivate,
      )
    }

    for (field in fields) {
      field.accept(cv)
    }

    for (method in methods) {
      val methodVisitor = cv.visitMethod(method.access, method.name, method.desc, method.signature, method.exceptions?.toTypedArray())
      method.accept(methodVisitor)
    }

    for (name in innerClassesToKeep.sorted()) {
      val innerClassInfo = innerClassToInfo.get(name) ?: continue
      cv.visitInnerClass(innerClassInfo.name, innerClassInfo.outerName, innerClassInfo.innerName, innerClassInfo.access)
    }

    cv.visitEnd()
  }

  private fun isMethodKotlinInternal(methodName: String?): Boolean {
    val functions = kotlinMetadata?.second?.let { getFunctions(it) } ?: return false
    for (function in functions) {
      if (function.visibility == Visibility.INTERNAL && function.name == methodName) {
        return true
      }
    }
    return false
  }

  private fun isFieldKotlinInternal(name: String?): Boolean {
    val properties = kotlinMetadata?.second?.let { getProperties(it) } ?: return false
    for (property in properties) {
      if (property.visibility == Visibility.INTERNAL && property.name == name) {
        return true
      }
    }
    return false
  }
}

private fun getFunctions(km: KotlinClassMetadata?): List<KmFunction>? = when (km) {
  is KotlinClassMetadata.FileFacade -> km.kmPackage.functions
  is KotlinClassMetadata.MultiFileClassPart -> km.kmPackage.functions
  is KotlinClassMetadata.Class -> km.kmClass.functions
  else -> null
}

private fun getProperties(km: KotlinClassMetadata?): List<KmProperty>? = when (km) {
  is KotlinClassMetadata.FileFacade -> km.kmPackage.properties
  is KotlinClassMetadata.MultiFileClassPart -> km.kmPackage.properties
  is KotlinClassMetadata.Class -> km.kmClass.properties
  else -> null
}

private fun transformAndWriteKotlinMetadata(
  metadata: KotlinClassMetadata,
  descriptor: String,
  classVisitor: ClassVisitor,
  classesToBeDeleted: Set<String>,
  treatInternalAsPrivate: Boolean,
) {
  when (metadata) {
    is KotlinClassMetadata.Class -> {
      removePrivateDeclarationsForClass(
        klass = metadata.kmClass,
        removeCopyAlongWithConstructor = false,
        preserveDeclarationOrder = false,
        classesToBeDeleted = classesToBeDeleted,
        // todo try to set to true
        pruneClass = false,
        treatInternalAsPrivate = treatInternalAsPrivate,
      )
    }

    is KotlinClassMetadata.FileFacade -> {
      removePrivateDeclarationsForPackage(
        kmPackage = metadata.kmPackage,
        preserveDeclarationOrder = false,
        pruneClass = false,
        treatInternalAsPrivate = treatInternalAsPrivate,
      )
    }

    is KotlinClassMetadata.MultiFileClassPart -> {
      removePrivateDeclarationsForPackage(
        kmPackage = metadata.kmPackage,
        preserveDeclarationOrder = false,
        pruneClass = false,
        treatInternalAsPrivate = treatInternalAsPrivate,
      )
    }

    else -> {
    }
  }

  val annotationVisitor = classVisitor.visitAnnotation(descriptor, true)
  visitKotlinMetadata(annotationVisitor, metadata.write())
}

@Suppress("SameParameterValue")
private fun removePrivateDeclarationsForPackage(
  kmPackage: KmPackage,
  preserveDeclarationOrder: Boolean,
  pruneClass: Boolean,
  treatInternalAsPrivate: Boolean,
) {
  removePrivateDeclarationsForDeclarationContainer(
    container = kmPackage as KmDeclarationContainer,
    copyFunShouldBeDeleted = false,
    preserveDeclarationOrder = preserveDeclarationOrder,
    pruneClass = pruneClass,
    treatInternalAsPrivate = treatInternalAsPrivate,
  )
  kmPackage.localDelegatedProperties.clear()
}

@Suppress("SameParameterValue")
private fun removePrivateDeclarationsForClass(
  klass: KmClass,
  removeCopyAlongWithConstructor: Boolean,
  preserveDeclarationOrder: Boolean,
  classesToBeDeleted: Set<String>,
  pruneClass: Boolean,
  treatInternalAsPrivate: Boolean,
) {
  klass.constructors.removeIf { pruneClass || isPrivateVisibility(it.visibility, treatInternalAsPrivate) }
  removePrivateDeclarationsForDeclarationContainer(
    container = klass as KmDeclarationContainer,
    copyFunShouldBeDeleted = copyFunShouldBeDeleted(klass = klass, removeDataClassCopy = removeCopyAlongWithConstructor),
    preserveDeclarationOrder = preserveDeclarationOrder,
    pruneClass = pruneClass,
    treatInternalAsPrivate = treatInternalAsPrivate,
  )
  klass.nestedClasses.removeIf { "${klass.name}$$it" in classesToBeDeleted }
  klass.companionObject = klass.companionObject.takeUnless { "${klass.name}$$it" in classesToBeDeleted }
  klass.localDelegatedProperties.clear()
}

private fun copyFunShouldBeDeleted(klass: KmClass, removeDataClassCopy: Boolean): Boolean {
  return removeDataClassCopy && klass.isData && klass.constructors.none { !it.isSecondary }
}

private fun isPrivateVisibility(visibility: Visibility, treatInternalAsPrivate: Boolean): Boolean {
  return visibility == Visibility.PRIVATE ||
    visibility == Visibility.PRIVATE_TO_THIS ||
    visibility == Visibility.LOCAL ||
    (treatInternalAsPrivate && visibility == Visibility.INTERNAL)
}

private fun removePrivateDeclarationsForDeclarationContainer(
  container: KmDeclarationContainer,
  copyFunShouldBeDeleted: Boolean,
  preserveDeclarationOrder: Boolean,
  pruneClass: Boolean,
  treatInternalAsPrivate: Boolean,
) {
  container.functions.removeIf {
    pruneClass || isPrivateVisibility(it.visibility, treatInternalAsPrivate) || (copyFunShouldBeDeleted && it.name == "copy")
  }
  container.properties.removeIf {
    pruneClass || isPrivateVisibility(it.visibility, treatInternalAsPrivate)
  }

  if (!preserveDeclarationOrder) {
    container.functions.sortWith(compareBy(KmFunction::name, { it.signature.toString() }))
    container.properties.sortWith(compareBy(KmProperty::name, { it.getterSignature.toString() }))
  }

  for (property in container.properties) {
    // whether the *non-const* property is initialized by a compile-time constant is not a part of the ABI.
    if (!property.isConst) {
      property.hasConstant = false
    }
  }
}

private class BodyStrippingMethodVisitor(visitor: MethodVisitor) : MethodVisitor(Opcodes.API_VERSION, visitor) {
  override fun visitCode() {
    mv.visitCode()
    mv.visitInsn(Opcodes.ACONST_NULL)
    mv.visitInsn(Opcodes.ATHROW)
    mv.visitMaxs(0, 0)
    mv.visitEnd()
    mv = null
  }
}