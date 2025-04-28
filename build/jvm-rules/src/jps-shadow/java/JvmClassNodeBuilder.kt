// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "SSBasedInspection", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.jps.dependency.java

import androidx.collection.MutableScatterMap
import androidx.collection.MutableScatterSet
import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.openapi.diagnostic.logger
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
import org.jetbrains.bazel.jvm.util.emptyList
import org.jetbrains.bazel.jvm.util.emptySet
import org.jetbrains.jps.dependency.NodeBuilder
import org.jetbrains.jps.dependency.Usage
import org.jetbrains.jps.dependency.java.nodeBuilder.AnnotationCrawler
import org.jetbrains.jps.dependency.java.nodeBuilder.AnnotationTargetCrawler
import org.jetbrains.jps.dependency.java.nodeBuilder.KotlinMetadataCrawler
import org.jetbrains.jps.dependency.java.nodeBuilder.ModuleCrawler
import org.jetbrains.org.objectweb.asm.AnnotationVisitor
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.FieldVisitor
import org.jetbrains.org.objectweb.asm.Handle
import org.jetbrains.org.objectweb.asm.Label
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.ModuleVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Opcodes.API_VERSION
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.signature.SignatureReader
import org.jetbrains.org.objectweb.asm.signature.SignatureVisitor
import org.jetbrains.org.objectweb.asm.util.Textifier
import org.jetbrains.org.objectweb.asm.util.TraceMethodVisitor
import java.lang.Byte
import java.lang.Double
import java.lang.Float
import java.lang.Short
import java.lang.annotation.RetentionPolicy
import java.util.*
import java.util.concurrent.CancellationException
import kotlin.Any
import kotlin.Array
import kotlin.Boolean
import kotlin.Char
import kotlin.Exception
import kotlin.IllegalArgumentException
import kotlin.Int
import kotlin.Long
import kotlin.RuntimeException
import kotlin.String
import kotlin.Suppress
import kotlin.also
import kotlin.let
import kotlin.metadata.KmDeclarationContainer
import kotlin.metadata.isInline
import kotlin.metadata.jvm.JvmMethodSignature
import kotlin.metadata.jvm.getterSignature
import kotlin.metadata.jvm.setterSignature
import kotlin.metadata.jvm.signature
import kotlin.takeIf

private const val KOTLIN_LAMBDA_USAGE_CLASS_MARKER = "\$sam$"
@Suppress("SpellCheckingInspection")
private const val LAMBDA_FACTORY_CLASS = "java/lang/invoke/LambdaMetafactory"

class JvmClassNodeBuilder private constructor(
  private val fileName: String,
  private val isGenerated: Boolean,
) : ClassVisitor(API_VERSION), NodeBuilder {
  companion object {
    @JvmStatic
    fun create(filePath: String, classReader: ClassReader, isGenerated: Boolean): JvmClassNodeBuilder {
      val builder = JvmClassNodeBuilder(fileName = filePath, isGenerated = isGenerated)
      try {
        classReader.accept(builder, ClassReader.SKIP_FRAMES)
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: RuntimeException) {
        throw RuntimeException("Corrupted .class file: $filePath", e)
      }
      return builder
    }

    fun createForLibrary(filePath: String, classReader: ClassReader): JvmClassNodeBuilder {
      val builder = JvmClassNodeBuilder(fileName = filePath, isGenerated = false)
      try {
        classReader.accept(builder, ClassReader.SKIP_FRAMES or ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG)
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: RuntimeException) {
        throw RuntimeException("Corrupted .class file: $filePath", e)
      }
      return builder
    }
  }

  private inner class AnnotationRetentionPolicyCrawler : AnnotationVisitor(API_VERSION) {
    override fun visit(name: String?, value: Any?) {
    }

    override fun visitEnum(name: String?, desc: String?, value: String) {
      retentionPolicy = RetentionPolicy.valueOf(value)
    }

    override fun visitAnnotation(name: String?, desc: String?): AnnotationVisitor? = null

    override fun visitArray(name: String?): AnnotationVisitor? = null

    override fun visitEnd() {
    }
  }

  private fun processSignature(sig: String?) {
    if (sig != null) {
      try {
        SignatureReader(sig).accept(signatureCrawler)
      }
      catch (e: Exception) {
        logger<JvmClassNodeBuilder>().warn("Problems parsing signature \"$sig\" in $fileName", e)
      }
    }
  }

  private val signatureCrawler: SignatureVisitor = object : BaseSignatureVisitor() {
    override fun visitClassBound() = signatureWithGenericBoundUsageCrawler

    override fun visitInterfaceBound() = signatureWithGenericBoundUsageCrawler

    override fun visitTypeArgument(wildcard: Char): SignatureVisitor? {
      return if (wildcard == '+' || wildcard == '-') signatureWithGenericBoundUsageCrawler else super.visitTypeArgument(wildcard)
    }
  }

  private val signatureWithGenericBoundUsageCrawler = object : BaseSignatureVisitor() {
    override fun visitClassType(name: String) {
      super.visitClassType(name)
      addUsage(ClassAsGenericBoundUsage(name))
    }
  }

  private var access = 0
  private var name: String? = null
  // for class contains a class bytecode version, for module contains a module version
  private var version: String? = null
  private var superClass: String? = null
  private var interfaces: Array<String>? = null
  private var signature: String? = null

  private var className: String? = null
  private var outerClassName: String? = null
  private var localClassFlag = false
  private var anonymousClassFlag = false
  private var sealedClassFlag = false

  private val methods = ArrayList<JvmMethod>()
  private val fields = ArrayList<JvmField>()
  // MutableOrderedScatterSet is broken (infinite forEach in some cases, the bug not yet reported,
  // but the class was added only in 2024 and looks like not battle-tested)
  private val usages = ObjectLinkedOpenHashSet<Usage>()
  private val targets: EnumSet<ElemType> = EnumSet.noneOf<ElemType>(ElemType::class.java)
  private var retentionPolicy: RetentionPolicy? = null

  private val annotationArguments = MutableScatterMap<TypeRepr.ClassType, MutableScatterSet<String>>()
  private val annotationTargets = MutableScatterMap<TypeRepr.ClassType, EnumSet<ElemType>>()
  private val annotations = MutableScatterSet<ElementAnnotation>()

  private var moduleRequires: ArrayList<ModuleRequires>? = null
  private var moduleExports: ArrayList<ModulePackage>? = null

  private val metadata = ArrayList<JvmMetadata<*, *>>()

  override fun getReferenceID() = JvmNodeReferenceID(name!!)

  override fun addUsage(usage: Usage) {
    val owner = usage.elementOwner
    if (owner !is JvmNodeReferenceID || JvmClass.OBJECT_CLASS_NAME != owner.nodeName) {
      usages.add(usage)
    }
  }

  override fun getResult(): JVMClassNode<*, out Proto.Diff<out JVMClassNode<*, *>?>> {
    return build(outFilePathHash = Hashing.xxh3_64().hashBytesToLong(fileName.toByteArray()), isLibraryMode = false)
  }

  fun build(
    isLibraryMode: Boolean,
    outFilePathHash: Long,
    skipPrivateMethodsAndFields: Boolean = isLibraryMode,
  ): JVMClassNode<*, out Proto.Diff<out JVMClassNode<*, *>>> {
    var flags = JVMFlags(access)
    if (localClassFlag) {
      flags = flags.deriveIsLocal()
    }
    if (anonymousClassFlag) {
      flags = flags.deriveIsAnonymous()
    }
    if (sealedClassFlag) {
      flags = flags.deriveIsSealed()
    }
    if (isGenerated) {
      flags = flags.deriveIsGenerated()
    }
    if (isLibraryMode) {
      flags = flags.deriveIsLibrary()
    }

    val moduleRequires = moduleRequires
    val moduleExports = moduleExports
    if (moduleRequires != null && moduleExports != null) {
      if (!isLibraryMode) {
        moduleRequires.forEach {
          if (name == it.name) {
            addUsage(ModuleUsage(it.name))
          }
        }
      }
      return JvmModule(
        /* flags = */ flags,
        /* name = */ name!!,
        /* outFilePath = */ outFilePathHash,
        /* version = */ version,
        /* requires = */ moduleRequires.ifEmpty { emptyList() },
        /* exports = */ moduleExports.ifEmpty { emptyList() },
        /* usages = */ if (isLibraryMode) emptySet() else usages.ifEmpty { emptySet() },
        /* metadata = */ metadata,
      )
    }

    if (!isLibraryMode) {
      superClass?.let {
        addUsage(ClassUsage(it))
      }
      interfaces?.let { interfaces ->
        for (anInterface in interfaces) {
          addUsage(ClassUsage(anInterface))
        }
      }
      fields.forEach { field ->
        for (usage in field.type.usages) {
          addUsage(usage)
        }
      }
      methods.forEach { jvmMethod ->
        for (usage in jvmMethod.type.usages) {
          addUsage(usage)
        }
        for (argType in jvmMethod.argTypes) {
          for (usage in argType.usages) {
            addUsage(usage)
          }
        }
        for (exception in jvmMethod.exceptions) {
          for (usage in exception.usages) {
            addUsage(usage)
          }
        }
      }
    }

    val fields = if (skipPrivateMethodsAndFields) fields.filter { !it.isPrivate } else fields
    val methods = if (skipPrivateMethodsAndFields) methods.filter { !it.isPrivate } else methods
    val usages = if (isLibraryMode) emptySet() else usages.ifEmpty { emptySet() }

    return JvmClass(
      flags = flags,
      signature = signature,
      fqName = name!!,
      outFilePathHash = outFilePathHash,
      superFqName = superClass,
      outerFqName = outerClassName,
      interfaces = interfaces?.takeIf { it.isNotEmpty() }?.asList() ?: emptyList(),
      fields = fields.ifEmpty { emptyList() },
      methods = methods.ifEmpty { emptyList() },
      annotations = toSet(annotations),
      annotationTargets = targets,
      retentionPolicy = retentionPolicy,
      usages = usages,
      metadata = metadata.ifEmpty { emptyList() },
    )
  }

  override fun visit(version: Int, access: Int, name: String, sig: String?, superName: String?, interfaces: Array<String>?) {
    this.access = access
    this.name = name
    // implicit 'import' of the package to which the node belongs to
    usages.add(ImportPackageOnDemandUsage(JvmClass.getPackageName(name)))
    this.version = version.toString()
    signature = sig
    superClass = superName
    this.interfaces = interfaces

    className = name
    processSignature(sig)
  }

  override fun visitEnd() {
    annotationTargets.forEach { type, targets ->
      val usedArguments = annotationArguments.get(type)
      addUsage(AnnotationUsage(type, usedArguments?.takeIf { it.isNotEmpty() }?.asSet() ?: emptyList(), targets))
    }
  }

  override fun visitModule(name: String, access: Int, version: String?): ModuleVisitor {
    this.access = access
    this.name = name
    this.version = version
    val moduleRequires = ArrayList<ModuleRequires>().also { this.moduleRequires = it }
    val moduleExports = ArrayList<ModulePackage>().also { this.moduleExports = it }
    return ModuleCrawler(moduleRequires, moduleExports, this)
  }

  override fun visitAnnotation(desc: String, visible: Boolean): AnnotationVisitor {
    @Suppress("SpellCheckingInspection")
    return when (desc) {
      "Ljava/lang/annotation/Target;" -> AnnotationTargetCrawler(targets)
      "Ljava/lang/annotation/Retention;" -> AnnotationRetentionPolicyCrawler()
      "Lkotlin/Metadata;" -> KotlinMetadataCrawler(metadata::add)
      else -> AnnotationCrawler(
        type = TypeRepr.getType(desc) as TypeRepr.ClassType,
        target = if ((access and Opcodes.ACC_ANNOTATION) > 0) ElemType.ANNOTATION_TYPE else ElemType.TYPE,
        annotationArguments = annotationArguments,
        annotationTargets = annotationTargets,
        nodeBuilder = this,
      ) { annotations.add(it) }
    }
  }

  override fun visitSource(source: String?, debug: String?) {
  }

  override fun visitField(access: Int, name: String?, desc: String, signature: String?, value: Any?): FieldVisitor {
    processSignature(signature)

    return object : FieldVisitor(API_VERSION) {
      private var annotations: ArrayList<ElementAnnotation>? = null

      override fun visitAnnotation(desc: String, visible: Boolean): AnnotationVisitor? {
        return AnnotationCrawler(
          type = TypeRepr.getType(desc) as TypeRepr.ClassType,
          target = ElemType.FIELD,
          annotationArguments = annotationArguments,
          annotationTargets = annotationTargets,
          nodeBuilder = this@JvmClassNodeBuilder,
        ) {
          var annotations = annotations
          if (annotations == null) {
            annotations = ArrayList()
            this.annotations = annotations
          }

          // set is expected very small - do not use a hash set
          if (!annotations.contains(it)) {
            annotations.add(it)
          }
        }
      }

      override fun visitEnd() {
        try {
          super.visitEnd()
        }
        finally {
          if ((access and Opcodes.ACC_SYNTHETIC) == 0 || (access and Opcodes.ACC_PRIVATE) == 0) {
            fields.add(JvmField(JVMFlags(access), signature, name, desc, annotations?.takeIf { it.isNotEmpty() } ?: emptyList(), value))
          }
        }
      }
    }
  }

  private fun isInlined(methodName: String, methodDescriptor: String): Boolean {
    val container = findKotlinDeclarationContainer() ?: return false
    val sig = JvmMethodSignature(methodName, methodDescriptor)
    for (f in container.functions) {
      if (f.isInline && sig == f.signature) {
        return true
      }
    }
    for (p in container.properties) {
      if (p.getter.isInline && sig == p.getterSignature) {
        return true
      }
      val setter = p.setter
      if (setter != null && setter.isInline && sig == p.setterSignature) {
        return true
      }
    }
    return false
  }

  private fun findKotlinDeclarationContainer(): KmDeclarationContainer? {
    return (metadata.firstOrNull { it is KotlinMeta } as KotlinMeta?)?.declarationContainer
  }

  override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String?>?): MethodVisitor? {
    var defaultValue: Any? = null
    val annotations = ArrayList<ElementAnnotation>()
    val paramAnnotations = ArrayList<ParamAnnotation>()
    processSignature(signature)

    val isInlined = isInlined(name, descriptor)
    val printer = Textifier()

    val visitor = object : MethodVisitor(API_VERSION) {
      override fun visitEnd() {
        if ((access and Opcodes.ACC_SYNTHETIC) == 0 || (access and Opcodes.ACC_BRIDGE) > 0 || (access and Opcodes.ACC_PRIVATE) == 0) {
          if (isInlined) {
            // use 'defaultValue' attribute to store the hash of the function body to track changes in inline method implementation
            defaultValue = buildContentHash(ContentHashBuilderImpl(), printer.getText()).getResult()
          }
          methods.add(JvmMethod(
            flags = JVMFlags(access),
            signature = signature,
            name = name,
            descriptor = descriptor,
            annotations = annotations.ifEmpty { emptyList() },
            parameterAnnotations = paramAnnotations.ifEmpty { emptyList() },
            exceptions = if (exceptions.isNullOrEmpty()) emptyList() else exceptions.map { TypeRepr.ClassType(it) },
            defaultValue = defaultValue,
          ))
        }
      }

      fun buildContentHash(builder: ContentHashBuilder, dataSequence: Iterable<*>): ContentHashBuilder {
        for (o in dataSequence) {
          if (o is Iterable<*>) {
            buildContentHash(builder, o)
          }
          else {
            builder.update(o)
          }
        }
        return builder
      }

      override fun visitAnnotation(desc: String, visible: Boolean): AnnotationVisitor {
        return AnnotationCrawler(
          type = TypeRepr.getType(desc) as TypeRepr.ClassType,
          target = if ("<init>" == name) ElemType.CONSTRUCTOR else ElemType.METHOD,
          annotationArguments = annotationArguments,
          annotationTargets = annotationTargets,
          nodeBuilder = this@JvmClassNodeBuilder,
          resultConsumer = {
            if (!annotations.contains(it)) {
              annotations.add(it)
            }
          },
        )
      }

      override fun visitAnnotationDefault(): AnnotationVisitor {
        return object : AnnotationVisitor(API_VERSION) {
          private var result: MutableList<Any?>? = null

          override fun visit(name: String?, value: Any?) {
            collectValue(value)
          }

          override fun visitEnum(name: String?, desc: String?, value: String?) {
            collectValue(value)
          }

          override fun visitArray(name: String?): AnnotationVisitor? {
            this.result = ArrayList<Any?>()
            return this
          }

          override fun visitEnd() {
            val result = result ?: return
            if (!result.isEmpty()) {
              var elem: Any? = null
              for (o in result) {
                if (o != null) {
                  elem = o
                  break
                }
              }
              if (elem != null) {
                defaultValue = result.toTypedArray()
              }
            }

            if (defaultValue == null) {
              val declaredType = Type.getReturnType(descriptor)
              // spec does not allow array of array
              val typeClass = getTypeClass(if (declaredType.sort == Type.ARRAY) declaredType.getElementType() else declaredType)
              defaultValue = java.lang.reflect.Array.newInstance(typeClass, result.size)
            }
          }

          fun collectValue(value: Any?) {
            val result = result
            if (result == null) {
              defaultValue = value
            }
            else {
              result.add(value)
            }
          }
        }
      }

      override fun visitParameterAnnotation(parameter: Int, desc: String, visible: Boolean): AnnotationVisitor {
        return AnnotationCrawler(
          type = TypeRepr.getType(desc) as TypeRepr.ClassType,
          target = ElemType.PARAMETER,
          annotationArguments = annotationArguments,
          annotationTargets = annotationTargets,
          nodeBuilder = this@JvmClassNodeBuilder,
        ) {
          val paramAnnotation = ParamAnnotation(parameter, it.annotationClass, it.contentHash)
          if (!paramAnnotations.contains(paramAnnotation)) {
            paramAnnotations.add(paramAnnotation)
          }
        }
      }

      override fun visitLdcInsn(cst: Any) {
        if (cst is Type) {
          addUsage(ClassUsage(cst.internalName))
        }

        super.visitLdcInsn(cst)
      }

      override fun visitMultiANewArrayInsn(desc: String, dims: Int) {
        val typ = TypeRepr.getType(desc) as TypeRepr.ArrayType
        usages.addAll(typ.usages)

        val element = typ.deepElementType
        if (element is TypeRepr.ClassType) {
          addUsage(ClassNewUsage(element.jvmName))
        }

        super.visitMultiANewArrayInsn(desc, dims)
      }

      override fun visitLocalVariable(n: String?, desc: String, signature: String?, start: Label?, end: Label?, index: Int) {
        if ("this" != n) {
          processSignature(signature)
          usages.addAll(TypeRepr.getType(desc).usages)
        }
        super.visitLocalVariable(n, desc, signature, start, end, index)
      }

      override fun visitTryCatchBlock(start: Label?, end: Label?, handler: Label?, type: String?) {
        if (type != null) {
          usages.addAll(TypeRepr.ClassType(type).usages)
        }
        super.visitTryCatchBlock(start, end, handler, type)
      }

      override fun visitTypeInsn(opcode: Int, type: String) {
        val typ = if (type.startsWith('[')) TypeRepr.getType(type) else TypeRepr.ClassType(type)

        if (opcode == Opcodes.NEW) {
          addUsage(ClassUsage((typ as TypeRepr.ClassType).jvmName))
          addUsage(ClassNewUsage(typ.jvmName))
          val ktLambdaMarker = type.indexOf(KOTLIN_LAMBDA_USAGE_CLASS_MARKER)
          if (ktLambdaMarker > 0) {
            val ifNameStart = ktLambdaMarker + KOTLIN_LAMBDA_USAGE_CLASS_MARKER.length
            val ifNameEnd = type.indexOf("$", ifNameStart)
            if (ifNameEnd > ifNameStart) {
              addUsage(ClassNewUsage(type.substring(ifNameStart, ifNameEnd).replace('_', '/')))
            }
          }
        }
        else if (opcode == Opcodes.ANEWARRAY) {
          if (typ is TypeRepr.ClassType) {
            addUsage(ClassUsage(typ.jvmName))
            addUsage(ClassNewUsage(typ.jvmName))
          }
        }

        usages.addAll(typ.usages)

        super.visitTypeInsn(opcode, type)
      }

      override fun visitFieldInsn(opcode: Int, owner: String?, name: String?, desc: String) {
        registerFieldUsage(opcode, owner, name, desc)
        super.visitFieldInsn(opcode, owner, name, desc)
      }

      override fun visitMethodInsn(opcode: Int, owner: String?, name: String?, desc: String?, itf: Boolean) {
        registerMethodUsage(owner, name, desc)
        super.visitMethodInsn(opcode, owner, name, desc, itf)
      }

      override fun visitInvokeDynamicInsn(methodName: String?, desc: String?, bsm: Handle, vararg bsmArgs: Any?) {
        val returnType = Type.getReturnType(desc)
        usages.addAll(TypeRepr.getType(returnType).usages)

        // common args processing
        for (arg in bsmArgs) {
          if (arg is Type) {
            if (arg.sort == Type.METHOD) {
              for (argType in arg.argumentTypes) {
                usages.addAll(TypeRepr.getType(argType).usages)
              }
              usages.addAll(TypeRepr.getType(arg.returnType).usages)
            }
            else {
              usages.addAll(TypeRepr.getType(arg).usages)
            }
          }
          else if (arg is Handle) {
            processMethodHandle(arg)
          }
        }

        if (LAMBDA_FACTORY_CLASS == bsm.owner) {
          // This invokeDynamic implements a lambda or method reference usage.
          // Need to register method usage for the corresponding SAM type.
          // The first three arguments to the bootstrap methods are provided automatically by VM.
          // Arguments in an args array are expected to be as following:
          // [0]: Type: Signature and return type of method to be implemented by the function object.
          // [1]: Handle: implementation method handle
          // [2]: Type: The signature and return type that should be enforced dynamically at invocation time.
          // Maybe the same as samMethodType, or may be a specialization of it
          // [...]: optional additional arguments

          if (returnType.sort == Type.OBJECT && bsmArgs.size >= 3) {
            if (bsmArgs[0] is Type) {
              val samMethodType: Type = bsmArgs[0] as Type
              if (samMethodType.sort == Type.METHOD) {
                registerMethodUsage(returnType.internalName, methodName, samMethodType.descriptor)
                // reflect dynamic proxy instantiation with NewClassUsage
                addUsage(ClassNewUsage(returnType.internalName))
              }
            }
          }
        }

        super.visitInvokeDynamicInsn(methodName, desc, bsm, bsmArgs)
      }

      fun processMethodHandle(handle: Handle) {
        val memberOwner = handle.owner
        if (memberOwner != null && memberOwner != className) {
          // do not register access to own class members
          val memberName = handle.name
          val memberDescriptor = handle.desc
          val opCode = getFieldAccessOpcode(handle)
          if (opCode > 0) {
            registerFieldUsage(opCode, memberOwner, memberName, memberDescriptor)
          }
          else {
            registerMethodUsage(memberOwner, memberName, memberDescriptor)
          }
        }
      }

      fun registerFieldUsage(opcode: Int, owner: String?, fName: String?, desc: String) {
        if (opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC) {
          addUsage(FieldAssignUsage(owner, fName, desc))
        }
        if (opcode == Opcodes.GETFIELD || opcode == Opcodes.GETSTATIC) {
          usages.addAll(TypeRepr.getType(desc).usages)
        }
        addUsage(FieldUsage(owner, fName, desc))
      }

      fun registerMethodUsage(owner: String?, name: String?, desc: String?) {
        if (desc == null) {
          // todo: verify for which methods null descriptor is passed
          addUsage(MethodUsage(owner, name, ""))
        }
        else {
          addUsage(MethodUsage(owner, name, desc))
          usages.addAll(TypeRepr.getType(Type.getReturnType(desc)).usages)
        }
      }
    }

    return if (isInlined) TraceMethodVisitor(visitor, printer) else visitor
  }

  override fun visitInnerClass(name: String?, outerName: String?, innerName: String?, access: Int) {
    if (name != null && name == className) {
      // Set outer class name only if we are parsing the real inner class and
      // not the reference to inner class inside some top-level class.
      // Information about some access flags for the inner class is missing from the mask passed to 'visit' method.
      this.access = this.access or access
      if (outerName != null) {
        outerClassName = outerName
      }
      if (innerName == null) {
        anonymousClassFlag = true
      }
    }
  }

  override fun visitOuterClass(owner: String?, name: String?, desc: String?) {
    outerClassName = owner

    if (name != null) {
      localClassFlag = true
    }
  }

  override fun visitPermittedSubclass(permittedSubclass: String) {
    sealedClassFlag = true
    addUsage(ClassUsage(permittedSubclass))
    addUsage(ClassPermitsUsage(permittedSubclass))
  }

  private open inner class BaseSignatureVisitor : SignatureVisitor(API_VERSION) {
    override fun visitClassType(name: String) {
      addUsage(ClassUsage(name))
    }
  }
}

private interface ContentHashBuilder {
  fun update(data: Any?)

  fun putString(data: String)

  fun putLong(data: Long)

  fun getResult(): Any?
}

internal class ContentHashBuilderImpl : ContentHashBuilder {
  private val digest = Hashing.xxh3_64().hashStream()
  private var hasData = false

  override fun putString(data: String) {
    hasData = true
    digest.putByteArray(data.toByteArray())
  }

  override fun putLong(data: Long) {
    hasData = true
    digest.putLong(data)
  }

  override fun update(data: Any?) {
    hasData = true
    when {
      data == null -> {
        digest.putByte(0)
      }

      data.javaClass.isArray -> {
        var index = 0
        val length = java.lang.reflect.Array.getLength(data)
        while (index < length) {
          update(java.lang.reflect.Array.get(data, index))
          index++
        }
        digest.putInt(length)
      }

      data is Long -> {
        digest.putLong(data)
      }

      data is String -> {
        digest.putByteArray(data.toByteArray())
      }

      else -> {
        throw IllegalArgumentException("Unsupported data type: ${data.javaClass}")
      }
    }
  }

  override fun getResult(): Any? {
    return if (hasData) digest.asLong else null
  }
}

/**
 * @return corresponding field access opcode or -1 if the handle does not represent a field access handle
 */
private fun getFieldAccessOpcode(handle: Handle): Int {
  return when (handle.tag) {
    Opcodes.H_GETFIELD -> Opcodes.GETFIELD
    Opcodes.H_GETSTATIC -> Opcodes.GETSTATIC
    Opcodes.H_PUTFIELD -> Opcodes.PUTFIELD
    Opcodes.H_PUTSTATIC -> Opcodes.PUTSTATIC
    else -> -1
  }
}

private fun getTypeClass(t: Type): Class<*> {
  @Suppress("RemoveRedundantQualifierName", "RedundantSuppression")
  return when (t.sort) {
    Type.BOOLEAN -> java.lang.Boolean.TYPE
    Type.CHAR -> Character.TYPE
    Type.BYTE -> Byte.TYPE
    Type.SHORT -> Short.TYPE
    Type.INT -> Integer.TYPE
    Type.FLOAT -> Float.TYPE
    Type.LONG -> java.lang.Long.TYPE
    Type.DOUBLE -> Double.TYPE
    Type.OBJECT -> if ("java.lang.String" == t.className) java.lang.String::class.java else Type::class.java
    else -> Type::class.java
  }
}

private fun <T> toSet(set: MutableScatterSet<T>): Set<T> = if (set.isEmpty()) emptySet() else set.asSet()