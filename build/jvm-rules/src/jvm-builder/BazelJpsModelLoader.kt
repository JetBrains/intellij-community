// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.worker

import androidx.collection.MutableObjectList
import androidx.collection.ObjectList
import androidx.collection.ScatterMap
import com.dynatrace.hash4j.hashing.HashFunnel
import com.dynatrace.hash4j.hashing.HashStream64
import com.dynatrace.hash4j.hashing.Hashing
import org.jetbrains.annotations.Unmodifiable
import org.jetbrains.bazel.jvm.kotlin.JvmBuilderFlags
import org.jetbrains.bazel.jvm.kotlin.configureCommonCompilerArgs
import org.jetbrains.bazel.jvm.kotlin.getJvmTargetLevel
import org.jetbrains.bazel.jvm.util.ArgMap
import org.jetbrains.bazel.jvm.worker.core.BazelConfigurationHolder
import org.jetbrains.bazel.jvm.worker.state.TargetConfigurationDigestContainer
import org.jetbrains.bazel.jvm.worker.state.TargetConfigurationDigestProperty
import org.jetbrains.jps.model.JpsCompositeElement
import org.jetbrains.jps.model.JpsDummyElement
import org.jetbrains.jps.model.JpsElement
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.JpsElementReference
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.JpsReferenceableElement
import org.jetbrains.jps.model.ex.JpsNamedCompositeElementBase
import org.jetbrains.jps.model.impl.JpsElementCollectionImpl
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.JpsJavaLibraryType
import org.jetbrains.jps.model.java.JpsJavaModuleType
import org.jetbrains.jps.model.java.JpsJavaSdkType
import org.jetbrains.jps.model.java.LanguageLevel
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsLibraryReference
import org.jetbrains.jps.model.library.JpsLibraryRoot
import org.jetbrains.jps.model.library.JpsLibraryRoot.InclusionOptions
import org.jetbrains.jps.model.library.JpsLibraryType
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.library.JpsTypedLibrary
import org.jetbrains.jps.model.library.impl.JpsLibraryReferenceImpl
import org.jetbrains.jps.model.module.JpsDependenciesList
import org.jetbrains.jps.model.module.impl.JpsModuleImpl
import org.jetbrains.jps.model.module.impl.JpsModuleSourceRootImpl
import org.jetbrains.jps.model.serialization.impl.JpsProjectSerializationDataExtensionImpl
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.config.KotlinFacetSettings
import org.jetbrains.kotlin.jps.model.JpsKotlinFacetModuleExtension
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.invariantSeparatorsPathString

private val jpsElementFactory = JpsElementFactory.getInstance()
private val javaHome = Path.of(System.getProperty("java.home")).normalize() ?: error("No java.home system property")

private val JAVA_VERSION_HASH = Hashing.xxh3_64().hashBytesToLong(System.getProperty("java.version")?.toByteArray() ?: error("No java.version system property"))
private val KOTLINC_VERSION_HASH = Hashing.xxh3_64().hashBytesToLong((KotlinCompilerVersion.getVersion() ?: "@snapshot@").toByteArray())

internal fun loadJpsModel(
  sources: List<Path>,
  args: ArgMap<JvmBuilderFlags>,
  classPathRootDir: Path,
  dependencyFileToDigest: ScatterMap<Path, ByteArray>,
): Pair<JpsModel, TargetConfigurationDigestContainer> {
  val model = jpsElementFactory.createModel()

  val digests = TargetConfigurationDigestContainer()
  digests.set(TargetConfigurationDigestProperty.TOOL_JVM_VERSION, JAVA_VERSION_HASH)
  digests.set(TargetConfigurationDigestProperty.KOTLIN_VERSION, KOTLINC_VERSION_HASH)

  // properties not needed for us (not implemented for java)
  // extension.loadModuleOptions not needed for us (not implemented for java)
  val module = JpsModuleImpl(
    JpsJavaModuleType.INSTANCE,
    args.mandatorySingle(JvmBuilderFlags.KOTLIN_MODULE_NAME),
    jpsElementFactory.createDummyElement(),
  )
  val jpsJavaModuleExtension = JpsJavaExtensionService.getInstance().getOrCreateModuleExtension(module)

  val langLevel = LanguageLevel.valueOf("JDK_" + getJvmTargetLevel(args).replace('.', '_'))
  jpsJavaModuleExtension.languageLevel = langLevel

  for (source in sources) {
    // used as a key - immutable instance cannot be used
    val properties = JavaSourceRootProperties("", false)
    module.addSourceRoot(JpsModuleSourceRootImpl(source.toUri().toString(), JavaSourceRootType.SOURCE, properties))
  }

  val configHash = Hashing.xxh3_64().hashStream()
  // version
  configHash.putInt(1)
  configHash.putInt(langLevel.ordinal)

  val dependencyList = module.dependenciesList
  dependencyList.clear()
  configureJdk(model = model, module = module, dependencyList = dependencyList)
  // must be after configureJdk; otherwise, for some reason, the module output dir is not included in the classpath
  dependencyList.addModuleSourceDependency()

  // no classpath if no source file (jvm_test without own sources)
  val classPathRaw = args.optionalList(JvmBuilderFlags.CP)
  val classPathFiles = Array<Path>(classPathRaw.size) {
    classPathRootDir.resolve(classPathRaw[it]).normalize()
  }

  val trackableDependencyFiles = configureClasspath(
    module = module,
    dependencyList = dependencyList,
    classPathRaw = classPathRaw,
    files = classPathFiles,
    dependencyFileToDigest = dependencyFileToDigest,
    digests = digests,
  )

  val kotlinArgs = configureKotlinCompiler(
    module = module,
    args = args,
    classPathRootDir = classPathRootDir,
    configHash = configHash,
    classPathFiles = classPathFiles,
    sources = sources,
  )
  val javaExports = args.optionalList(JvmBuilderFlags.ADD_EXPORT)
  configHash.putUnorderedIterable(javaExports, HashFunnel.forString(), Hashing.xxh3_64())
  digests.set(TargetConfigurationDigestProperty.COMPILER, configHash.asLong)

  val project = model.project
  project.addModule(module)
  project.container.setChild(JpsProjectSerializationDataExtensionImpl.ROLE, JpsProjectSerializationDataExtensionImpl(classPathRootDir.parent))

  module.container.setChild(BazelConfigurationHolder.KIND, BazelConfigurationHolder(
    classPath = classPathFiles,
    trackableDependencyFiles = trackableDependencyFiles,
    args = args,
    kotlinArgs = kotlinArgs,
    classPathRootDir = classPathRootDir,
    sources = sources,
    javaExports = javaExports,
  ))

  return model to digests
}

private fun configureKotlinCompiler(
  module: JpsModuleImpl<JpsDummyElement>,
  args: ArgMap<JvmBuilderFlags>,
  classPathRootDir: Path,
  configHash: HashStream64,
  classPathFiles: Array<Path>,
  sources: List<Path>,
): K2JVMCompilerArguments {
  val kotlinFacetSettings = KotlinFacetSettings()
  kotlinFacetSettings.useProjectSettings = false

  val kotlinArgs = K2JVMCompilerArguments()

  kotlinArgs.moduleName = args.mandatorySingle(JvmBuilderFlags.KOTLIN_MODULE_NAME)
  configHash.putString(kotlinArgs.moduleName)

  kotlinFacetSettings.compilerArguments = kotlinArgs
  configureCommonCompilerArgs(kotlinArgs = kotlinArgs, args = args, workingDir = classPathRootDir, configHash = configHash)

  val plugins = args.optionalList(JvmBuilderFlags.PLUGIN_ID).zip(args.optionalList(JvmBuilderFlags.PLUGIN_CLASSPATH))
  configHash.putInt(plugins.size)
  if (plugins.isNotEmpty()) {
    @Suppress("UnusedVariable")
    for ((id, paths) in plugins) {
      configHash.putString(id)
    }
  }

  kotlinArgs.classpath = classPathFiles.joinToString(separator = File.pathSeparator, transform = { it.toString() })
  kotlinArgs.freeArgs = sources.map { it.toString() }

  module.container.setChild(JpsKotlinFacetModuleExtension.KIND, JpsKotlinFacetModuleExtension(kotlinFacetSettings))
  return kotlinArgs
}

private fun configureJdk(
  model: JpsModel,
  module: JpsModuleImpl<JpsDummyElement>,
  dependencyList: JpsDependenciesList,
) {
  val jdkName = "default-jdk"
  // do not use JpsJavaExtensionService.getInstance().addJavaSdk - we don't need to detect version and collect roots for non-modular JDK
  val jdkLib = model.global.addSdk(jdkName, javaHome.invariantSeparatorsPathString, null, JpsJavaSdkType.INSTANCE)
  for (moduleUrl in readModulesFromJdkReleaseFile(javaHome)) {
    jdkLib.addRoot(moduleUrl, JpsOrderRootType.COMPILED)
  }

  val sdk = jdkLib.properties
  val sdkType = sdk.getSdkType()
  module.sdkReferencesTable.setSdkReference(sdkType, sdk.createReference())
  dependencyList.addSdkDependency(sdkType)
}

private fun configureClasspath(
  module: JpsModuleImpl<JpsDummyElement>,
  dependencyList: JpsDependenciesList,
  files: Array<Path>,
  dependencyFileToDigest: ScatterMap<Path, ByteArray>,
  digests: TargetConfigurationDigestContainer,
  classPathRaw: List<String>,
): ObjectList<Path> {
  val lib = BazelJpsLibrary("class-path-lib", files.asList())
  module.addModuleLibrary(lib)
  dependencyList.addLibraryDependency(lib)

  val hash = Hashing.xxh3_64().hashStream()
  for (path in classPathRaw) {
    hash.putByteArray(path.toByteArray())
  }
  hash.putInt(classPathRaw.size)
  digests.set(TargetConfigurationDigestProperty.DEPENDENCY_PATH_LIST, hash.asLong)
  hash.reset()

  var untrackedCount = 0
  val trackableDependencyFiles = MutableObjectList<Path>(files.size)
  for (file in files) {
    if (isDependencyTracked(file.toString())) {
      trackableDependencyFiles.add(file)
      continue
    }

    untrackedCount++

    val digest = requireNotNull(dependencyFileToDigest.get(file)) {
      "Missing digest for $file.\n" +
        "Available digests: ${dependencyFileToDigest.asMap().keys.joinToString(separator = ",\n") { it.invariantSeparatorsPathString }}"
    }
    hash.putByteArray(digest)
  }
  hash.putInt(untrackedCount)
  digests.set(TargetConfigurationDigestProperty.UNTRACKED_DEPENDENCY_DIGEST_LIST, hash.asLong)
  hash.reset()

  return trackableDependencyFiles
}

@Suppress("SameParameterValue")
private fun readModulesFromJdkReleaseFile(javaHome: Path): Sequence<String> {
  val p = Files.newInputStream(javaHome.resolve("release")).use { stream ->
    Properties().also { it.load(stream) }
  }
  val jbrBaseUrl = "jrt://${javaHome.invariantSeparatorsPathString}!/"
  val modules = p.getProperty("MODULES") ?: return emptySequence()
  return modules.removeSurrounding("\"").removeSurrounding("'").splitToSequence(' ').map { jbrBaseUrl + it }
}

private class BazelJpsLibrary(
  name: String,
  private val files: List<Path>,
) : JpsNamedCompositeElementBase<BazelJpsLibrary>(name), JpsTypedLibrary<JpsDummyElement> {
  private val properties = JpsElementFactory.getInstance().createDummyElement()

  override fun getType(): JpsJavaLibraryType = JpsJavaLibraryType.INSTANCE

  override fun <T : JpsElement> asTyped(type: JpsLibraryType<T>): JpsTypedLibrary<T>? {
    @Suppress("UNCHECKED_CAST")
    return if (getType() == type) this as JpsTypedLibrary<T> else null
  }

  override fun getProperties(): JpsDummyElement = properties

  override fun getRoots(rootType: JpsOrderRootType): List<JpsLibraryRoot> {
    throw UnsupportedOperationException()
  }

  override fun addRoot(url: String, rootType: JpsOrderRootType) {
    throw UnsupportedOperationException()
  }

  override fun addRoot(file: File, rootType: JpsOrderRootType) {
    throw UnsupportedOperationException()
  }

  override fun addRoot(url: String, rootType: JpsOrderRootType, options: InclusionOptions) {
    throw UnsupportedOperationException()
  }

  override fun removeUrl(url: String, rootType: JpsOrderRootType) {
    throw UnsupportedOperationException()
  }

  override fun delete() {
    throw UnsupportedOperationException()
  }

  override fun getParent(): JpsElementCollectionImpl<JpsLibrary>? {
    @Suppress("UNCHECKED_CAST")
    return myParent as JpsElementCollectionImpl<JpsLibrary>?
  }

  @Suppress("OVERRIDE_DEPRECATION", "removal")
  override fun createCopy(): BazelJpsLibrary {
    throw UnsupportedOperationException()
  }

  override fun createReference(): JpsLibraryReference {
    return JpsLibraryReferenceImpl(name, createParentReference())
  }

  private fun createParentReference(): JpsElementReference<JpsCompositeElement> {
    @Suppress("UNCHECKED_CAST")
    return (parent!!.parent as JpsReferenceableElement<JpsCompositeElement>).createReference()
  }

  override fun getFiles(rootType: JpsOrderRootType): @Unmodifiable List<File> {
    return files.map { it.toFile() }
  }

  override fun getPaths(rootType: JpsOrderRootType): List<Path> = files

  override fun getRootUrls(rootType: JpsOrderRootType): List<String> {
    throw UnsupportedOperationException()
  }

  override fun toString(): String = "BazelJpsLibrary(files=$files)"
}

private fun isDependencyTracked(path: String): Boolean = path.endsWith(".abi.jar")