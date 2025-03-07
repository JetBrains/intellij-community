@file:Suppress("ReplaceGetOrSet", "SSBasedInspection")

package org.jetbrains.jps.dependency.java

import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Ref
import com.intellij.util.SmartList
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import org.jetbrains.annotations.Nullable
import org.jetbrains.bazel.jvm.hashMap
import org.jetbrains.bazel.jvm.hashSet
import org.jetbrains.jps.dependency.NodeBuilder
import org.jetbrains.jps.dependency.Usage
import org.jetbrains.jps.javac.Iterators
import org.jetbrains.org.objectweb.asm.*
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Opcodes.API_VERSION
import org.jetbrains.org.objectweb.asm.signature.SignatureReader
import org.jetbrains.org.objectweb.asm.signature.SignatureVisitor
import org.jetbrains.org.objectweb.asm.util.Textifier
import org.jetbrains.org.objectweb.asm.util.TraceMethodVisitor
import java.lang.annotation.RetentionPolicy
import java.util.*
import java.util.concurrent.CancellationException
import java.util.function.Consumer
import java.util.function.Supplier
import kotlin.metadata.KmDeclarationContainer
import kotlin.metadata.isInline
import kotlin.metadata.jvm.JvmMethodSignature
import kotlin.metadata.jvm.getterSignature
import kotlin.metadata.jvm.setterSignature
import kotlin.metadata.jvm.signature

private const val KOTLIN_LAMBDA_USAGE_CLASS_MARKER = "\$sam$"
private val LOG: Logger = Logger.getInstance(JvmClassNodeBuilder::class.java)
@Suppress("SpellCheckingInspection")
private const val LAMBDA_FACTORY_CLASS: String = "java/lang/invoke/LambdaMetafactory"

class JvmClassNodeBuilder private constructor(
  private val fileName: String,
  private val isGenerated: Boolean,
  private val issLibraryMode: Boolean
) : ClassVisitor(API_VERSION), NodeBuilder {
  companion object {
    @JvmStatic
    fun create(filePath: String, classReader: ClassReader, isGenerated: Boolean): JvmClassNodeBuilder {
      val builder = JvmClassNodeBuilder(fileName = filePath, isGenerated = isGenerated, issLibraryMode = false)
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

    @JvmStatic
    fun createForLibrary(filePath: String, classReader: ClassReader): JvmClassNodeBuilder {
      val builder = JvmClassNodeBuilder(fileName = filePath, isGenerated = false, issLibraryMode = true)
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

  private inner class AnnotationTargetCrawler : AnnotationVisitor(API_VERSION) {
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

  private inner class AnnotationCrawler(
    private val type: TypeRepr.ClassType,
    private val target: ElemType,
    private val resultConsumer: (ElementAnnotation) -> Unit,
  ) : AnnotationVisitor(API_VERSION) {
    // Do not track changes in the annotation's content if there are no registered annotation trackers that would process these changes.
    // Some technical annotations (e.g., DebugInfo) may contain different content after every compiler run => they will always be considered "changed".
    // Handling such changes may involve additional type-consuming analysis and unnecessary dependency data updates.
    private val hashBuilder = if (isAnnotationTracked(type)) ContentHashBuilderImpl() else ContentHashBuilder.NULL_CONTENT_BUILDER

    private val usedArguments = hashSet<String>()
    private var arrayName: String? = null

    init {
      val targets = annotationTargets.get(type)
      if (targets == null) {
        annotationTargets.put(type, EnumSet.of(target))
      }
      else {
        targets.add(target)
      }
      addUsage(ClassUsage(type.jvmName))
    }

    fun getMethodDescr(value: Any, isArray: Boolean): String {
      val descriptor = StringBuilder()
      descriptor.append("()")
      if (isArray) {
        descriptor.append('[')
      }
      if (value is Type) {
        @Suppress("SpellCheckingInspection")
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
      if (name != null) {
        argName = name
      }
      else {
        argName = arrayName
        // not interested in collecting complete array value; need to know just an array type
        arrayName = null
      }
      registerUsages(argName, Supplier { getMethodDescr(value, isArray) }, value)
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
      registerUsages(argName, Supplier { (if (isArray) "()[" else "()") + desc }, value)
    }

    override fun visitAnnotation(name: String?, desc: String): AnnotationVisitor {
      return AnnotationCrawler(type = TypeRepr.getType(desc) as TypeRepr.ClassType, target = target) { hashBuilder.update(it.contentHash) }
    }

    override fun visitArray(name: String?): AnnotationVisitor? {
      arrayName = name
      return this
    }

    fun registerUsages(@Nullable methodName: String?, methodDescr: Supplier<String?>, value: Any) {
      if (value is Type) {
        val className = value.className.replace('.', '/')
        addUsage(ClassUsage(className))
      }
      if (methodName != null) {
        addUsage(MethodUsage(type.jvmName, methodName, methodDescr.get()))
        usedArguments.add(methodName)
        hashBuilder.update(methodName)
      }
      hashBuilder.update(value)
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
        resultConsumer(ElementAnnotation(type, hashBuilder.getResult()))
      }
    }
  }

  private class KotlinMetadataCrawler(
    private val resultConsumer: Consumer<KotlinMeta>,
  ) : AnnotationVisitor(API_VERSION) {
    private val data = hashMap<String, Any>()

    private class DataField<T>(private val name: String, private val type: Class<T>) {
      fun get(data: Map<String, Any>): T? {
        val value = data.get(name)
        return if (type.isInstance(value)) type.cast(value) else null
      }
    }

    override fun visit(name: String, value: Any) {
      data.put(name, value)
    }

    override fun visitArray(name: String?): AnnotationVisitor {
      return object : AnnotationVisitor(API_VERSION) {
        private val values = ArrayList<Any>()

        override fun visit(name: String, value: Any?) {
          if (value != null) {
            values.add(value)
          }
        }

        override fun visitEnd() {
          if (!values.isEmpty()) {
            data.put(name, values.toArray<Any>(java.lang.reflect.Array.newInstance(values.first().javaClass, values.size) as Array<out Any?>))
          }
        }
      }
    }

    override fun visitEnd() {
      val kind = KIND.get(data)
      if (kind != null) {
        val extraInt = EXTRA_INT.get(data)
        resultConsumer.accept(KotlinMeta(
          kind,
          VERSION.get(data),
          DATA1.get(data),
          DATA2.get(data),
          EXTRA_STRING.get(data),
          PACKAGE_NAME.get(data),
          extraInt ?: 0
        ))
      }
    }

    @Suppress("RemoveRedundantQualifierName")
    companion object {
      private val KIND = DataField<Int>("k", java.lang.Integer.TYPE)
      private val VERSION = DataField<IntArray>("mv", IntArray::class.java)
      private val DATA1 = DataField<Array<String>>("d1", Array<String>::class.java)
      private val DATA2 = DataField<Array<String>>("d2", Array<String>::class.java)
      private val EXTRA_STRING = DataField<String>("xs", String::class.java)
      private val PACKAGE_NAME = DataField<String>("pn", String::class.java)
      private val EXTRA_INT = DataField<Int>("xi", java.lang.Integer.TYPE)
    }
  }

  private inner class ModuleCrawler : ModuleVisitor(API_VERSION) {
    override fun visitMainClass(mainClass: String) {
      addUsage(ClassUsage(mainClass))
    }

    override fun visitRequire(module: String?, access: Int, version: String?) {
      if (isExplicit(access)) {
        // collect non-synthetic dependencies only
        myModuleRequires.add(ModuleRequires(JVMFlags(access), module, version))
      }
    }

    override fun visitExport(packaze: String?, access: Int, vararg modules: String?) {
      if (isExplicit(access)) {
        // collect non-synthetic dependencies only
        @Suppress("UNNECESSARY_SAFE_CALL")
        myModuleExports.add(ModulePackage(packaze, modules?.asList() ?: emptyList()))
      }
    }

    override fun visitUse(service: String) {
      addUsage(ClassUsage(service))
    }

    override fun visitProvide(service: String, vararg providers: String) {
      addUsage(ClassUsage(service))
      @Suppress("SENSELESS_COMPARISON")
      if (providers != null) {
        for (provider in providers) {
          addUsage(ClassUsage(provider))
        }
      }
    }

    fun isExplicit(access: Int): Boolean {
      return (access and (Opcodes.ACC_SYNTHETIC or Opcodes.ACC_MANDATED)) == 0
    }
  }

  private fun processSignature(sig: String?) {
    if (sig != null) {
      try {
        SignatureReader(sig).accept(mySignatureCrawler)
      }
      catch (e: Exception) {
        LOG.info("Problems parsing signature \"$sig\" in $fileName", e)
      }
    }
  }

  private val mySignatureCrawler: SignatureVisitor = object : BaseSignatureVisitor() {
    override fun visitClassBound(): SignatureVisitor {
      return mySignatureWithGenericBoundUsageCrawler
    }

    override fun visitInterfaceBound(): SignatureVisitor {
      return mySignatureWithGenericBoundUsageCrawler
    }

    override fun visitTypeArgument(wildcard: Char): SignatureVisitor? {
      return if (wildcard == '+' || wildcard == '-') mySignatureWithGenericBoundUsageCrawler else super.visitTypeArgument(wildcard)
    }
  }

  private val mySignatureWithGenericBoundUsageCrawler: SignatureVisitor = object : BaseSignatureVisitor() {
    override fun visitClassType(name: String) {
      super.visitClassType(name)
      addUsage(ClassAsGenericBoundUsage(name))
    }
  }

  private var isModule = false
  private var access = 0
  private var name: String? = null
  private var myVersion: String? = null // for class contains a class bytecode version, for module contains a module version
  private var superClass: String? = null
  private var interfaces: Array<String>? = null
  private var signature: String? = null

  private val classNameHolder: Ref<String?> = Ref.create()
  private var outerClassName: String? = null
  private var localClassFlag = false
  private var anonymousClassFlag = false
  private var sealedClassFlag = false

  private val myMethods = ObjectOpenHashSet<JvmMethod>()
  private val myFields = ObjectOpenHashSet<JvmField>()
  private val myUsages = ObjectOpenHashSet<Usage>()
  private val targets = EnumSet.noneOf<ElemType>(ElemType::class.java)
  private var retentionPolicy: RetentionPolicy? = null

  private val annotationArguments = Object2ObjectOpenHashMap<TypeRepr.ClassType, MutableSet<String>>()
  private val annotationTargets = Object2ObjectOpenHashMap<TypeRepr.ClassType, MutableSet<ElemType>>()
  private val myAnnotations = ObjectOpenHashSet<ElementAnnotation>()

  private val myModuleRequires = ObjectOpenHashSet<ModuleRequires>()
  private val myModuleExports = ObjectOpenHashSet<ModulePackage>()

  private val metadata = ArrayList<JvmMetadata<*, *>>()

  override fun getReferenceID(): JvmNodeReferenceID {
    return JvmNodeReferenceID(name!!)
  }

  override fun addUsage(usage: Usage) {
    val owner = usage.elementOwner
    if (owner !is JvmNodeReferenceID || JvmClass.OBJECT_CLASS_NAME != owner.nodeName) {
      myUsages.add(usage)
    }
  }

  override fun getResult(): JVMClassNode<*, out Proto.Diff<out JVMClassNode<*, *>?>> {
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
    if (issLibraryMode) {
      flags = flags.deriveIsLibrary()
    }

    if (isModule) {
      if (!issLibraryMode) {
        for (moduleRequire in myModuleRequires) {
          if (name == moduleRequire.name) {
            addUsage(ModuleUsage(moduleRequire.name))
          }
        }
      }
      return JvmModule(flags, name, fileName, myVersion, myModuleRequires, myModuleExports, if (issLibraryMode) mutableSetOf<Usage?>() else myUsages, metadata)
    }

    if (!issLibraryMode) {
      superClass?.let {
        addUsage(ClassUsage(it))
      }
      interfaces?.let { interfaces ->
        for (anInterface in interfaces) {
          addUsage(ClassUsage(anInterface))
        }
      }
      for (field in myFields) {
        for (usage in field.type.usages) {
          addUsage(usage)
        }
      }
      for (jvmMethod in myMethods) {
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

    val fields = if (issLibraryMode) myFields.filter { !it.isPrivate } else myFields
    val methods = if (issLibraryMode) myMethods.filter { !it.isPrivate } else myMethods
    val usages = if (issLibraryMode) emptySet() else myUsages
    return JvmClass(
      flags = flags,
      signature = signature,
      fqName = name!!,
      outFilePath = fileName,
      superFqName = superClass,
      outerFqName = outerClassName,
      interfaces = interfaces?.asList() ?: emptyList(),
      fields = fields,
      methods = methods,
      annotations = myAnnotations,
      annotationTargets = targets,
      retentionPolicy = retentionPolicy,
      usages = usages,
      metadata = metadata,
    )
  }

  override fun visit(version: Int, access: Int, name: String, sig: String?, superName: String?, interfaces: Array<String>?) {
    this@JvmClassNodeBuilder.access = access
    this@JvmClassNodeBuilder.name = name
    myUsages.add(ImportPackageOnDemandUsage(JvmClass.getPackageName(name))) // implicit 'import' of the package to which the node belongs to
    myVersion = version.toString()
    signature = sig
    superClass = superName
    this.interfaces = interfaces

    classNameHolder.set(name)
    processSignature(sig)
  }

  override fun visitEnd() {
    for (entry in annotationTargets.entries) {
      val type: TypeRepr.ClassType = entry.key!!
      val targets = entry.value
      val usedArguments = annotationArguments.get(type)
      addUsage(AnnotationUsage(type, usedArguments ?: emptyList(), targets))
    }
  }

  override fun visitModule(name: String, access: Int, version: String?): ModuleVisitor {
    isModule = true
    this@JvmClassNodeBuilder.access = access
    this.name = name
    myVersion = version
    return ModuleCrawler()
  }

  override fun visitAnnotation(desc: String, visible: Boolean): AnnotationVisitor {
    @Suppress("SpellCheckingInspection")
    return when (desc) {
      "Ljava/lang/annotation/Target;" -> {
        AnnotationTargetCrawler()
      }
      "Ljava/lang/annotation/Retention;" -> {
        AnnotationRetentionPolicyCrawler()
      }
      "Lkotlin/Metadata;" -> {
        KotlinMetadataCrawler(metadata::add)
      }
      else -> AnnotationCrawler(
        type = TypeRepr.getType(desc) as TypeRepr.ClassType,
        target = if ((access and Opcodes.ACC_ANNOTATION) > 0) ElemType.ANNOTATION_TYPE else ElemType.TYPE) { myAnnotations.add(it) }
    }
  }

  override fun visitSource(source: String?, debug: String?) {
  }

  override fun visitField(access: Int, name: String?, desc: String, signature: String?, value: Any?): FieldVisitor {
    processSignature(signature)

    return object : FieldVisitor(API_VERSION) {
      val annotations: MutableSet<ElementAnnotation?> = HashSet<ElementAnnotation?>()

      override fun visitAnnotation(desc: String, visible: Boolean): AnnotationVisitor? {
        return AnnotationCrawler(TypeRepr.getType(desc) as TypeRepr.ClassType, ElemType.FIELD) { annotations.add(it) }
      }

      override fun visitEnd() {
        try {
          super.visitEnd()
        }
        finally {
          if ((access and Opcodes.ACC_SYNTHETIC) == 0 || (access and Opcodes.ACC_PRIVATE) == 0) {
            myFields.add(JvmField(JVMFlags(access), signature, name, desc, annotations, value))
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
    val defaultValue: Ref<Any?> = Ref.create()
    val annotations: MutableSet<ElementAnnotation?> = HashSet<ElementAnnotation?>()
    val paramAnnotations: MutableSet<ParamAnnotation?> = HashSet<ParamAnnotation?>()
    processSignature(signature)

    val isInlined = isInlined(name, descriptor)
    val printer = Textifier()

    val visitor: MethodVisitor = object : MethodVisitor(API_VERSION) {
      override fun visitEnd() {
        if ((access and Opcodes.ACC_SYNTHETIC) == 0 || (access and Opcodes.ACC_BRIDGE) > 0 || (access and Opcodes.ACC_PRIVATE) == 0) {
          if (isInlined) {
            // use 'defaultValue' attribute to store the hash of the function body to track changes in inline method implementation
            defaultValue.set(buildContentHash(ContentHashBuilderImpl(), printer.getText()).getResult())
          }
          myMethods.add(JvmMethod(JVMFlags(access), signature, name, descriptor, annotations, paramAnnotations, Iterators.asIterable(exceptions), defaultValue.get()))
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
          resultConsumer = annotations::add,
        )
      }

      override fun visitAnnotationDefault(): AnnotationVisitor {
        return object : AnnotationVisitor(API_VERSION) {
          private var myAcc: MutableList<Any?>? = null

          override fun visit(name: String?, value: Any?) {
            collectValue(value)
          }

          override fun visitEnum(name: String?, desc: String?, value: String?) {
            collectValue(value)
          }

          override fun visitArray(name: String?): AnnotationVisitor? {
            myAcc = SmartList<Any?>()
            return this
          }

          override fun visitEnd() {
            if (myAcc != null) {
              if (!myAcc!!.isEmpty()) {
                var elem: Any? = null
                for (o in myAcc) {
                  if (o != null) {
                    elem = o
                    break
                  }
                }
                if (elem != null) {
                  defaultValue.set(myAcc!!.toTypedArray())
                }
              }

              if (defaultValue.get() == null) {
                val declaredType: Type = Type.getReturnType(descriptor)
                // spec does not allow array of array
                defaultValue.set(java.lang.reflect.Array.newInstance(getTypeClass(if (declaredType.sort == Type.ARRAY) declaredType.getElementType() else declaredType), myAcc!!.size))
              }
            }
          }

          fun collectValue(value: Any?) {
            if (myAcc != null) {
              myAcc!!.add(value)
            }
            else {
              defaultValue.set(value)
            }
          }
        }
      }

      override fun visitParameterAnnotation(parameter: Int, desc: String, visible: Boolean): AnnotationVisitor {
        return AnnotationCrawler(TypeRepr.getType(desc) as TypeRepr.ClassType, ElemType.PARAMETER) {
          paramAnnotations.add(ParamAnnotation(parameter, it.annotationClass, it.contentHash))
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
        Iterators.collect(typ.usages, myUsages)

        val element = typ.deepElementType
        if (element is TypeRepr.ClassType) {
          addUsage(ClassNewUsage(element.jvmName))
        }

        super.visitMultiANewArrayInsn(desc, dims)
      }

      override fun visitLocalVariable(n: String?, desc: String, signature: String?, start: Label?, end: Label?, index: Int) {
        if ("this" != n) {
          processSignature(signature)
          myUsages.addAll(TypeRepr.getType(desc).usages)
        }
        super.visitLocalVariable(n, desc, signature, start, end, index)
      }

      override fun visitTryCatchBlock(start: Label?, end: Label?, handler: Label?, type: String?) {
        if (type != null) {
          myUsages.addAll(TypeRepr.ClassType(type).usages)
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

        Iterators.collect(typ.usages, myUsages)

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
        myUsages.addAll(TypeRepr.getType(returnType).usages)

        // common args processing
        for (arg in bsmArgs) {
          if (arg is Type) {
            if (arg.sort == Type.METHOD) {
              for (argType in arg.argumentTypes) {
                Iterators.collect(TypeRepr.getType(argType).usages, myUsages)
              }
              Iterators.collect(TypeRepr.getType(arg.returnType).usages, myUsages)
            }
            else {
              Iterators.collect(TypeRepr.getType(arg).usages, myUsages)
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
        val memberOwner: String? = handle.owner
        if (memberOwner != null && memberOwner != classNameHolder.get()) {
          // do not register access to own class members
          val memberName: String? = handle.name
          val memberDescriptor: String = handle.desc
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
          Iterators.collect(TypeRepr.getType(desc).usages, myUsages)
        }
        addUsage(FieldUsage(owner, fName, desc))
      }

      fun registerMethodUsage(owner: String?, name: String?, @Nullable desc: String?) {
        if (desc == null) {
          // todo: verify for which methods null descriptor is passed
          addUsage(MethodUsage(owner, name, ""))
        }
        else {
          addUsage(MethodUsage(owner, name, desc))
          myUsages.addAll(TypeRepr.getType(Type.getReturnType(desc)).usages)
        }
      }
    }

    return if (isInlined) TraceMethodVisitor(visitor, printer) else visitor
  }

  override fun visitInnerClass(name: String?, outerName: String?, innerName: String?, access: Int) {
    if (name != null && name == classNameHolder.get()) {
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
  companion object {
    val NULL_CONTENT_BUILDER: ContentHashBuilder = object : ContentHashBuilder {
      override fun update(data: Any?) {
      }

      override fun getResult(): Any? = null
    }
  }

  fun update(data: Any?)

  fun getResult(): Any?
}

private class ContentHashBuilderImpl : ContentHashBuilder {
  private val digest = Hashing.xxh3_64().hashStream()
  private var hasData = false

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

private val differentiateStrategies = ServiceLoader.load(JvmDifferentiateStrategy::class.java).toList()

private fun isAnnotationTracked(annotationType: TypeRepr.ClassType): Boolean {
  for (strategy in differentiateStrategies) {
    if (strategy.isAnnotationTracked(annotationType)) {
      return true
    }
  }
  return false
}

private fun getTypeClass(t: Type): Class<*> {
  @Suppress("RemoveRedundantQualifierName", "RedundantSuppression")
  return when (t.sort) {
    Type.BOOLEAN -> java.lang.Boolean.TYPE
    Type.CHAR -> java.lang.Character.TYPE
    Type.BYTE -> java.lang.Byte.TYPE
    Type.SHORT -> java.lang.Short.TYPE
    Type.INT -> java.lang.Integer.TYPE
    Type.FLOAT -> java.lang.Float.TYPE
    Type.LONG -> java.lang.Long.TYPE
    Type.DOUBLE -> java.lang.Double.TYPE
    Type.OBJECT -> if ("java.lang.String" == t.className) java.lang.String::class.java else Type::class.java
    else -> Type::class.java
  }
}