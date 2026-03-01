package fleet.buildtool.codecache

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.lang.module.ModuleDescriptor
import java.util.Base64

/**
 * Serialises this module descriptor into the class file format.
 *
 * Class file version will be inferred from the [jdkVersionFeature] parameter.
 *
 * If the module is "automatic" it will be converted into an "open" module.
 *
 * See: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html
 */
fun ModuleDescriptor.serialize(jdkVersionFeature: Int): String = let { descriptor ->
  // As defined by https://www.oracle.com/java/technologies/javase/10-relnote-issues.html#Remaining
  val CLASS_FILE_VERSION_MAJOR = 44 + jdkVersionFeature
  val CLASS_FILE_VERSION_MINOR = 0

  assert(CLASS_FILE_VERSION_MAJOR >= 53) {
    "Modules are not supported by this class file version ($CLASS_FILE_VERSION_MAJOR.$CLASS_FILE_VERSION_MINOR)"
  }

  return byteArrayBuilder { dos ->
    dos.writeInt(CLASS_FILE_MAGIC)

    dos.writeShort(CLASS_FILE_VERSION_MINOR)
    dos.writeShort(CLASS_FILE_VERSION_MAJOR)

    // Constant pool is filled in by its users (e.g. writeModule).
    val pool = ConstantPool()
    val module = byteArrayBuilder { it.writeModule(descriptor.automaticToOpen(), pool) }

    // Write constant pool.
    dos.writeShort(pool.count)
    for ((constant, _) in pool.constants.toList().sortedBy { it.second }) {
      dos.writeConstant(constant)
    }

    // Write serialised module.
    dos.write(module)
  }.let {
    Base64.getEncoder().encodeToString(it)
  }
}

/**
 * Converts an automatic module to an open module.
 *
 * Automatic modules are not serializable as by definition they are not represented by a `module-info.class`. We need them to be serialized
 * for loading optimization at runtime.
 *
 * This function must be aligned with its runtime equivalent [fleet.util.modules.convertOpenToAutomatic]
 */
private fun ModuleDescriptor.automaticToOpen(): ModuleDescriptor {
  val moduleDescriptor = this
  if (!moduleDescriptor.isAutomatic) return moduleDescriptor
  return ModuleDescriptor.newOpenModule(moduleDescriptor.name()).apply {
    if (moduleDescriptor.version().isPresent) {
      version(moduleDescriptor.version().get())
    }
    moduleDescriptor.requires().forEach {
      requires(it)
    }
    moduleDescriptor.provides().forEach {
      provides(it)
    }
    packages(moduleDescriptor.packages())
  }.build()
}

/**
 * Serialises the part of the class file that pertains to a particular module (its flags, attributes, main class, etc.)
 */
private fun DataOutputStream.writeModule(descriptor: ModuleDescriptor, pool: ConstantPool) {
  // Access flags. ACC_MODULE means that this class file is a module.
  writeShort(ACC_MODULE)
  // this_class item must be "module-info" class in a module class file.
  writeShort(pool.getOrCreateClass("module-info"))

  // super_class item must be 0 in module class files.
  writeShort(0)
  // interfaces_count item must be 0 in module class files.
  writeShort(0)
  // fields_count item must be 0 in module class files.
  writeShort(0)
  // methods_count item must be 0 in module class files.
  writeShort(0)


  // We will write Module attribute, SourceFile attribute and, optionally, ModulePackages and ModuleMainClass
  // attributes if they are present.
  //
  // This means the attributes count will be in the range 2 to 4.
  val attributesCount = when {
    descriptor.mainClass().isPresent && descriptor.packages().isNotEmpty() -> 4
    descriptor.mainClass().isPresent || descriptor.packages().isNotEmpty() -> 3
    else -> 2
  }

  writeShort(attributesCount)

  // Write SourceFile attribute.
  writeAttribute("SourceFile", pool) {
    it.writeSourceFileAttributeInfo("module-info.java", pool)
  }

  // Write Module attribute.
  writeAttribute("Module", pool) {
    it.writeModuleAttributeInfo(descriptor, pool)
  }

  // Write ModuleMainClass attribute if present.
  descriptor.mainClass().ifPresent { mainClass ->
    writeAttribute("ModuleMainClass", pool) {
      it.writeModuleMainClassAttributeInfo(mainClass, pool)
    }
  }

  // Write ModulePackages attribute if any are present.
  descriptor.packages().takeIf { it.isNotEmpty() }?.let { packages ->
    writeAttribute("ModulePackages", pool) {
      it.writeModulePackagesAttributeInfo(packages, pool)
    }
  }
}

private inline fun DataOutputStream.writeAttribute(attributeName: String, pool: ConstantPool, body: (DataOutputStream) -> Unit) {
  // attribute_info {
  //   u2 attribute_name_index;
  //   u4 attribute_length;
  //   u1 info[attribute_length];
  // }

  val bytes = byteArrayBuilder { body(it) }

  writeShort(pool.getOrCreateUtf8(attributeName))
  writeInt(bytes.size)
  write(bytes)
}

private fun DataOutputStream.writeModuleAttributeInfo(descriptor: ModuleDescriptor, pool: ConstantPool) {
  // Write module name.
  writeShort(pool.getOrCreateModule(descriptor.name()))

  // Write module flags.
  var flags = 0x0000
  for (modifier in descriptor.modifiers()) {
    when (modifier) {
      ModuleDescriptor.Modifier.OPEN -> flags = flags or MODULE_ACC_OPEN
      ModuleDescriptor.Modifier.SYNTHETIC -> flags = flags or MODULE_ACC_SYNTHETIC
      ModuleDescriptor.Modifier.MANDATED -> flags = flags or MODULE_ACC_MANDATED
      ModuleDescriptor.Modifier.AUTOMATIC, null -> Unit
    }
  }
  writeShort(flags)

  // Write module version.
  writeShort(descriptor.rawVersion().map { pool.getOrCreateUtf8(it) }.orElse(0))

  // Write requires.
  writeShort(descriptor.requires().size)
  for (require in descriptor.requires().sorted()) {
    writeShort(pool.getOrCreateModule(require.name()))

    flags = 0x0000
    for (modifier in require.modifiers()) {
      when (modifier) {
        ModuleDescriptor.Requires.Modifier.TRANSITIVE -> flags = flags or REQ_ACC_TRANSITIVE
        ModuleDescriptor.Requires.Modifier.STATIC -> flags = flags or REQ_ACC_STATIC_PHASE
        ModuleDescriptor.Requires.Modifier.SYNTHETIC -> flags = flags or REQ_ACC_SYNTHETIC
        ModuleDescriptor.Requires.Modifier.MANDATED -> flags = flags or REQ_ACC_MANDATED
        null -> Unit
      }
    }
    writeShort(flags)

    writeShort(require.rawCompiledVersion().map { pool.getOrCreateUtf8(it) }.orElse(0))
  }

  // Write exports.
  writeShort(descriptor.exports().size)
  for (export in descriptor.exports().sorted()) {
    writeShort(pool.getOrCreatePackage(export.source()))

    flags = 0x0000
    for (modifier in export.modifiers()) {
      when (modifier) {
        ModuleDescriptor.Exports.Modifier.SYNTHETIC -> flags = flags or EXP_ACC_SYNTHETIC
        ModuleDescriptor.Exports.Modifier.MANDATED -> flags = flags or EXP_ACC_MANDATED
        null -> Unit
      }
    }
    writeShort(flags)

    writeShort(export.targets().size)
    for (target in export.targets().sorted()) {
      writeShort(pool.getOrCreateModule(target))
    }
  }

  // Write opens.
  writeShort(descriptor.opens().size)
  for (open in descriptor.opens().sorted()) {
    writeShort(pool.getOrCreatePackage(open.source()))

    flags = 0x0000
    for (modifier in open.modifiers()) {
      when (modifier) {
        ModuleDescriptor.Opens.Modifier.SYNTHETIC -> flags = flags or OP_ACC_SYNTHETIC
        ModuleDescriptor.Opens.Modifier.MANDATED -> flags = flags or OP_ACC_MANDATED
        null -> Unit
      }
    }
    writeShort(flags)

    writeShort(open.targets().size)
    for (target in open.targets().sorted()) {
      writeShort(pool.getOrCreateModule(target))
    }
  }

  // Write uses.
  writeShort(descriptor.uses().size)
  for (use in descriptor.uses().sorted()) {
    writeShort(pool.getOrCreateClass(use))
  }

  // Write provides.
  writeShort(descriptor.provides().size)
  for (provide in descriptor.provides().sorted()) {
    writeShort(pool.getOrCreateClass(provide.service()))
    writeShort(provide.providers().size)
    for (provider in provide.providers().sorted()) {
      writeShort(pool.getOrCreateClass(provider))
    }
  }
}

private fun DataOutputStream.writeSourceFileAttributeInfo(fileName: String, pool: ConstantPool) {
  writeShort(pool.getOrCreateUtf8(fileName))
}

private fun DataOutputStream.writeModuleMainClassAttributeInfo(mainClass: String, pool: ConstantPool) {
  writeShort(pool.getOrCreateClass(mainClass))
}

private fun DataOutputStream.writeModulePackagesAttributeInfo(packages: Set<String>, pool: ConstantPool) {
  writeShort(packages.size)
  for (packageName in packages.sorted()) {
    writeShort(pool.getOrCreatePackage(packageName))
  }
}

private inline fun byteArrayBuilder(body: (DataOutputStream) -> Unit): ByteArray {
  return ByteArrayOutputStream().use { baos ->
    DataOutputStream(baos).use { dos -> body(dos) }

    baos.toByteArray()
  }
}

private fun DataOutputStream.writeConstant(constant: Constant) {
  when (constant) {
    is Constant.Utf8 -> {
      writeByte(ConstantPoolTag.Utf8.value)
      writeUTF(constant.value)
    }

    is Constant.Class -> {
      writeByte(ConstantPoolTag.Class.value)
      writeShort(constant.classNameIndex)
    }

    is Constant.Module -> {
      writeByte(ConstantPoolTag.Module.value)
      writeShort(constant.moduleNameIndex)
    }

    is Constant.Package -> {
      writeByte(ConstantPoolTag.Package.value)
      writeShort(constant.packageNameIndex)
    }
  }
}

private class ConstantPool {
  private var index = 1

  val constants = mutableMapOf<Constant, Int>()

  // Specification defines constant pool count to be count of entries in the constant pool table plus one.
  val count get() = constants.size + 1

  fun getOrCreateUtf8(string: String): Int {
    return constants.computeIfAbsent(Constant.Utf8(string)) { index++ }
  }

  fun getOrCreateClass(className: String): Int {
    val classNameIndex = getOrCreateUtf8(internalFormName(className))
    return constants.computeIfAbsent(Constant.Class(classNameIndex)) { index++ }
  }

  fun getOrCreateModule(moduleName: String): Int {
    // Module names are NOT encoded in the "internal form".
    val moduleNameIndex = getOrCreateUtf8(moduleName)
    return constants.computeIfAbsent(Constant.Module(moduleNameIndex)) { index++ }
  }

  fun getOrCreatePackage(packageName: String): Int {
    val packageNameIndex = getOrCreateUtf8(internalFormName(packageName))
    return constants.computeIfAbsent(Constant.Package(packageNameIndex)) { index++ }
  }

  private fun internalFormName(name: String): String {
    return name.replace(".", "/")
  }
}

private sealed interface Constant {
  data class Utf8(val value: String) : Constant
  data class Class(val classNameIndex: Int) : Constant
  data class Module(val moduleNameIndex: Int) : Constant
  data class Package(val packageNameIndex: Int) : Constant
}

private enum class ConstantPoolTag(val value: Int) {
  Class(7),
  Fieldref(9),
  Methodref(10),
  InterfaceMethodref(11),
  String(8),
  Integer(3),
  Float(4),
  Long(5),
  Double(6),
  NameAndType(12),
  Utf8(1),
  MethodHandle(15),
  MethodType(16),
  InvokeDynamic(18),
  Module(19),
  Package(20),
}

private const val CLASS_FILE_MAGIC = 0xcafebabe.toInt()

// Access flag denoting that this class file is a module.
private const val ACC_MODULE = 0x8000

// Module flags.
private const val MODULE_ACC_OPEN = 0x0020
private const val MODULE_ACC_SYNTHETIC = 0x1000
private const val MODULE_ACC_MANDATED = 0x8000

// Requires flags.
private const val REQ_ACC_TRANSITIVE = 0x0020
private const val REQ_ACC_STATIC_PHASE = 0x0040
private const val REQ_ACC_SYNTHETIC = 0x1000
private const val REQ_ACC_MANDATED = 0x8000

// Exports flags.
private const val EXP_ACC_SYNTHETIC = 0x1000
private const val EXP_ACC_MANDATED = 0x8000

// Opens flags.
private const val OP_ACC_SYNTHETIC = 0x1000
private const val OP_ACC_MANDATED = 0x8000
