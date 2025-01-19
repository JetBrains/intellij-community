package org.jetbrains.bazel.jvm.abi

import kotlinx.coroutines.channels.Channel
import org.jetbrains.intellij.build.io.writeZipUsingTempFile
import org.jetbrains.org.objectweb.asm.*
import org.jetbrains.org.objectweb.asm.tree.AnnotationNode
import org.jetbrains.org.objectweb.asm.tree.FieldNode
import org.jetbrains.org.objectweb.asm.tree.MethodNode
import java.nio.ByteBuffer
import java.nio.file.Path
import kotlin.metadata.*
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.jvm.KotlinModuleMetadata
import kotlin.metadata.jvm.UnstableMetadataApi
import kotlin.metadata.jvm.getterSignature
import kotlin.metadata.jvm.localDelegatedProperties
import kotlin.metadata.jvm.signature

class JarContentToProcess(
  @JvmField val name: ByteArray,
  @JvmField val data: ByteArray,
  @JvmField val isKotlinModuleMetadata: Boolean,
)

@OptIn(UnstableMetadataApi::class)
suspend fun writeAbi(abiJar: Path, classChannel: Channel<JarContentToProcess>) {
  writeZipUsingTempFile(abiJar, indexWriter = null) { stream ->
    val classesToBeDeleted = HashSet<String>()
    //val deletedFileLevelMethods = HashSet<Pair<String, String>>()
    var kotlinModuleMetadata: JarContentToProcess? = null
    for (item in classChannel) {
      if (item.isKotlinModuleMetadata) {
        kotlinModuleMetadata = item
        continue
      }


      val classWriter = ClassWriter(0)
      val abiClassVisitor = AbiClassVisitor(
        classVisitor = classWriter,
        classesToBeDeleted = classesToBeDeleted,
        treatInternalAsPrivate = false,
      )
      ClassReader(item.data).accept(abiClassVisitor, ClassReader.SKIP_FRAMES)
      if (!abiClassVisitor.isApiClass) {
        continue
      }

      val abiData = classWriter.toByteArray()
      stream.writeDataRawEntryWithoutCrc(ByteBuffer.wrap(abiData), item.name)
    }

    if (kotlinModuleMetadata != null) {
      val parsed = requireNotNull(KotlinModuleMetadata.read(kotlinModuleMetadata.data)) {
        "Unsuccessful parsing of Kotlin module metadata for ABI generation: ${kotlinModuleMetadata.name.decodeToString()}"
      }

      val iterator = parsed.kmModule.packageParts.iterator()
      var isChanged = false
      while (iterator.hasNext()) {
        val (_, kmPackageParts) = iterator.next()

        kmPackageParts.fileFacades.removeIf { it in classesToBeDeleted }
        if (kmPackageParts.fileFacades.isEmpty() && kmPackageParts.multiFileClassParts.isEmpty()) {
          iterator.remove()
          isChanged = true
          continue
        }
      }

      val newData = if (isChanged) { parsed.write() } else kotlinModuleMetadata.data
      stream.writeDataRawEntryWithoutCrc(ByteBuffer.wrap(newData), kotlinModuleMetadata.name)
    }
  }
}

/**
 * ClassVisitor that strips non-public methods/fields and Kotlin `internal` methods.
 */
internal class AbiClassVisitor(
  classVisitor: ClassVisitor,
  private val classesToBeDeleted: MutableSet<String>,
  private val treatInternalAsPrivate: Boolean,
) : ClassVisitor(Opcodes.API_VERSION, classVisitor) {
  // tracks if this class has any public API members
  var isApiClass: Boolean = true
    private set

  private var className: String? = null

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
    className = name
    if (isApiClass) {
      val visibility = getKotlinMetaClass()?.visibility
      if (visibility == null || !isPrivateVisibility(visibility, treatInternalAsPrivate)) {
        super.visit(version, access, name, signature, superName, interfaces)
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
    //return ReplaceWithEmptyBody(method, (Type.getArgumentsAndReturnSizes(method.desc) shr 2) - 1)
    return method
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
        classVisitor = cv,
        classesToBeDeleted = classesToBeDeleted,
        treatInternalAsPrivate = treatInternalAsPrivate,
      )
    }

    for (annotation in visibleAnnotations) {
      annotation.accept(cv.visitAnnotation(annotation.desc, true))
    }
    for (annotation in invisibleAnnotations) {
      annotation.accept(cv.visitAnnotation(annotation.desc, false))
    }

    for (field in fields) {
      field.accept(cv)
    }

    for (method in methods) {
      method.accept(cv)
      //val exceptionsArray = if (method.exceptions == null) null else method.exceptions.toTypedArray()
      //val methodVisitor = cv.visitMethod(method.access, method.name, method.desc, method.signature, exceptionsArray)
      //method.accept(methodVisitor)

      //ReplaceWithEmptyBody(
      //  targetWriter = method,
      //  newMaxLocals = (Type.getArgumentsAndReturnSizes(method.desc) shr 2) - 1,
      //)
      //
      //val mv = cv.visitMethod(method.access, method.name, method.desc, method.signature, method.exceptions?.toTypedArray())
      //val stripper = MethodBodyStripper(mv)
      //stripper.visitCode()
      //stripper.visitMaxs(0, 0)
      //stripper.visitEnd()
    }

    super.visitEnd()
  }

  private fun isMethodKotlinInternal(methodName: String?): Boolean {
    val kClass = getKotlinMetaClass() ?: return false
    for (function in kClass.functions) {
      if (function.visibility == Visibility.INTERNAL && function.name == methodName) {
        return true
      }
    }
    return false
  }

  private fun isFieldKotlinInternal(name: String?): Boolean {
    val kClass = getKotlinMetaClass() ?: return false
    for (property in kClass.properties) {
      if (property.visibility == Visibility.INTERNAL && property.name == name) {
        return true
      }
    }
    return false
  }

  private fun getKotlinMetaClass(): KmClass? = (kotlinMetadata?.second as? KotlinClassMetadata.Class)?.kmClass
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