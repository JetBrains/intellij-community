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

internal class KotlinAbiClassVisitor(
  private val classVisitor: ClassVisitor,
  private val classesToBeDeleted: MutableSet<String>,
  private val treatInternalAsPrivate: Boolean,
) : ClassVisitor(Opcodes.API_VERSION) {
  // tracks if this class has any public API members
  var isApiClass: Boolean = true
    private set

  private val visibleAnnotations = ArrayList<AnnotationNode>()
  private val invisibleAnnotations = ArrayList<AnnotationNode>()

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
      (if (visible) visibleAnnotations else invisibleAnnotations).add(annotationNode)
      return annotationNode
    }
  }

  override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<String>?) {
    isApiClass = (access and (Opcodes.ACC_PUBLIC or Opcodes.ACC_PROTECTED)) != 0
    if (isApiClass) {
      val visibility = (kotlinMetadata?.second as? KotlinClassMetadata.Class)?.kmClass?.visibility
      if (visibility == null || !isPrivateVisibility(visibility, treatInternalAsPrivate)) {
        classVisitor.visit(version, access, name, signature, superName, interfaces)
        return
      }
      else {
        isApiClass = false
      }
    }

    classesToBeDeleted.add(name)
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

    val functions = kotlinMetadata?.second?.let { getFunctions(it) }
    val isInlineMethod = functions != null && functions.any { it.isInline && it.name == method.name }
    if (isInlineMethod) {
      return method
    }
    else {
      return ReplaceWithEmptyBody(method)
    }
  }

  override fun visitEnd() {
    visibleAnnotations.sortBy { it.desc }
    invisibleAnnotations.sortBy { it.desc }

    fields.sortBy { it.name }
    methods.sortBy { it.name }

    kotlinMetadata?.let { (descriptor, header) ->
      transformAndWriteKotlinMetadata(
        metadata = header,
        descriptor = descriptor,
        classVisitor = classVisitor,
        classesToBeDeleted = classesToBeDeleted,
        treatInternalAsPrivate = treatInternalAsPrivate,
      )
    }

    for (annotation in visibleAnnotations) {
      annotation.accept(classVisitor.visitAnnotation(annotation.desc, true))
    }
    for (annotation in invisibleAnnotations) {
      annotation.accept(classVisitor.visitAnnotation(annotation.desc, false))
    }

    for (field in fields) {
      field.accept(classVisitor)
    }

    //val km = kotlinMetadata?.second
    //val functions = getFunctions(km)
    for (method in methods) {
      //if (isInlineMethod || !isKotlin) {
      method.accept(classVisitor)
      //}
      //else {
      //  val methodVisitor = cv.visitMethod(method.access, method.name, method.desc, method.signature, method.exceptions?.toTypedArray())
      //  methodVisitor.visitCode()
      //  methodVisitor.visitInsn(Opcodes.RETURN)
      //  methodVisitor.visitMaxs(0, 0)
      //  methodVisitor.visitEnd()
      //}
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

private class ReplaceWithEmptyBody(
  private val targetWriter: MethodVisitor,
) : MethodVisitor(Opcodes.API_VERSION) {
  override fun visitCode() {
    targetWriter.visitCode()
    targetWriter.visitInsn(Opcodes.RETURN)
  }

  override fun visitParameter(name: String?, access: Int) {
    targetWriter.visitParameter(name, access)
  }

  override fun visitParameterAnnotation(parameter: Int, descriptor: String?, visible: Boolean): AnnotationVisitor? {
    return targetWriter.visitParameterAnnotation(parameter, descriptor, visible)
  }

  override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
    return targetWriter.visitAnnotation(descriptor, visible)
  }

  override fun visitEnd() {
    targetWriter.visitEnd()
  }
}