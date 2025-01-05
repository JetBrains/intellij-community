package org.jetbrains.bazel.jvm.jps

import org.jetbrains.org.objectweb.asm.*
import org.jetbrains.org.objectweb.asm.tree.AnnotationNode
import org.jetbrains.org.objectweb.asm.tree.FieldNode
import org.jetbrains.org.objectweb.asm.tree.MethodNode
import kotlin.metadata.*
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.jvm.getterSignature
import kotlin.metadata.jvm.localDelegatedProperties
import kotlin.metadata.jvm.signature

/**
 * ClassVisitor that strips non-public methods/fields and Kotlin `internal` methods.
 */
internal class AbiClassVisitor(
  classVisitor: ClassVisitor,
  private val classesToBeDeleted: MutableSet<String>,
) : ClassVisitor(Opcodes.API_VERSION, classVisitor) {
  // tracks if this class has any public API members
  var isApiClass: Boolean = false
    private set

  private val classAnnotations = mutableListOf<Pair<AnnotationNode, Boolean>>()
  private val fields = mutableListOf<FieldNode>()
  private val methods = mutableListOf<MethodNode>()
  private var kotlinMetadata: Pair<String, KotlinClassMetadata>? = null

  override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
    // parse @Metadata for Kotlin-specific visibility information
    @Suppress("SpellCheckingInspection")
    if (descriptor == "Lkotlin/Metadata;") {
      return KotlinAnnotationVisitor {
        kotlinMetadata = descriptor to KotlinClassMetadata.readStrict(it)
      }
    }
    else {
      val annotationNode = AnnotationNode(api, descriptor)
      classAnnotations.add(annotationNode to visible)
      return annotationNode
    }
  }

  override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<String>?) {
    if (access and Opcodes.ACC_PUBLIC == 0) {
      classesToBeDeleted.add(name)
    }
    else {
      isApiClass = true
      super.visit(version, access, name, signature, superName, interfaces)
    }
  }

  override fun visitField(
    access: Int,
    name: String?,
    descriptor: String?,
    signature: String?,
    value: Any?,
  ): FieldVisitor? {
    if (access and Opcodes.ACC_PUBLIC == 0) {
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
    // retain public methods and exclude Kotlin `internal` methods
    val isPublic = access and Opcodes.ACC_PUBLIC != 0
    val isInternal = isKotlinInternal(name)
    if (!isPublic || isInternal) {
      return null
    }

    val methodNode = MethodNode(api, access, name, descriptor, signature, exceptions)
    methods.add(methodNode)
    return methodNode
  }

  override fun visitEnd() {
    classAnnotations.sortBy { it.first.desc }
    fields.sortBy { it.name }
    methods.sortBy { it.name }

    kotlinMetadata?.let { (descriptor, header) ->
      transformAndWriteKotlinMetadata(
        metadata = header,
        descriptor = descriptor,
        classVisitor = cv,
        classesToBeDeleted = classesToBeDeleted,
      )
    }

    for ((annotation, visible) in classAnnotations) {
      val annotationVisitor = cv.visitAnnotation(annotation.desc, visible)
      annotation.accept(annotationVisitor)
    }

    for (field in fields) {
      field.accept(cv)
    }

    for (method in methods) {
      //val exceptionsArray = if (method.exceptions == null) null else method.exceptions.toTypedArray()
      //val methodVisitor = cv.visitMethod(method.access, method.name, method.desc, method.signature, exceptionsArray)
      //method.accept(methodVisitor)

      val mv = cv.visitMethod(method.access, method.name, method.desc, method.signature, method.exceptions?.toTypedArray())
      val stripper = MethodBodyStripper(mv)
      stripper.visitCode()
      stripper.visitMaxs(0, 0)
      stripper.visitEnd()
    }

    super.visitEnd()
  }

  private fun isKotlinInternal(methodName: String?): Boolean {
    val kClass = (kotlinMetadata?.second as? KotlinClassMetadata.Class)?.kmClass ?: return false
    for (function in kClass.functions) {
      if (function.visibility == Visibility.INTERNAL && function.name == methodName) {
        return true
      }
    }
    return false
  }
}

private class MethodBodyStripper(mv: MethodVisitor) : MethodVisitor(Opcodes.API_VERSION, mv) {
  override fun visitCode() {
    super.visitCode()
    mv.visitInsn(Opcodes.RETURN)
  }

  override fun visitMaxs(maxStack: Int, maxLocals: Int) {
    // indicate no stack and locals required for stripped body
    super.visitMaxs(0, 0)
  }
}

private fun transformAndWriteKotlinMetadata(
  metadata: KotlinClassMetadata,
  descriptor: String,
  classVisitor: ClassVisitor,
  classesToBeDeleted: Set<String>,
) {
  val treatInternalAsPrivate = false
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
  klass.constructors.removeIf { pruneClass || it.visibility.shouldRemove(treatInternalAsPrivate) }
  removePrivateDeclarationsForDeclarationContainer(
    container = klass as KmDeclarationContainer,
    copyFunShouldBeDeleted = klass.copyFunShouldBeDeleted(removeDataClassCopy = removeCopyAlongWithConstructor),
    preserveDeclarationOrder = preserveDeclarationOrder,
    pruneClass = pruneClass,
    treatInternalAsPrivate = treatInternalAsPrivate,
  )
  klass.nestedClasses.removeIf { "${klass.name}$$it" in classesToBeDeleted }
  klass.companionObject = klass.companionObject.takeUnless { "${klass.name}$$it" in classesToBeDeleted }
  klass.localDelegatedProperties.clear()
}

private fun KmClass.copyFunShouldBeDeleted(removeDataClassCopy: Boolean): Boolean {
  return removeDataClassCopy && isData && constructors.none { !it.isSecondary }
}

private fun Visibility.shouldRemove(treatInternalAsPrivate: Boolean): Boolean {
  return this == Visibility.PRIVATE || this == Visibility.PRIVATE_TO_THIS || this == Visibility.LOCAL || (treatInternalAsPrivate && this == Visibility.INTERNAL)
}

private fun removePrivateDeclarationsForDeclarationContainer(
  container: KmDeclarationContainer,
  copyFunShouldBeDeleted: Boolean,
  preserveDeclarationOrder: Boolean,
  pruneClass: Boolean,
  treatInternalAsPrivate: Boolean,
) {
  container.functions.removeIf {
    pruneClass || it.visibility.shouldRemove(treatInternalAsPrivate) || (copyFunShouldBeDeleted && it.name == "copy")
  }
  container.properties.removeIf {
    pruneClass || it.visibility.shouldRemove(treatInternalAsPrivate)
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