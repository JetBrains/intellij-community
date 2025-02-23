// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.jps

import com.dynatrace.hash4j.hashing.HashFunnel
import com.dynatrace.hash4j.hashing.HashStream64
import com.dynatrace.hash4j.hashing.Hashing
import org.jetbrains.annotations.Unmodifiable
import org.jetbrains.bazel.jvm.ArgMap
import org.jetbrains.bazel.jvm.jps.state.TargetConfigurationDigestContainer
import org.jetbrains.bazel.jvm.jps.state.TargetConfigurationDigestProperty
import org.jetbrains.bazel.jvm.kotlin.JvmBuilderFlags
import org.jetbrains.bazel.jvm.kotlin.configureCommonCompilerArgs
import org.jetbrains.jps.api.GlobalOptions
import org.jetbrains.jps.model.JpsCompositeElement
import org.jetbrains.jps.model.JpsDummyElement
import org.jetbrains.jps.model.JpsElement
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.JpsElementReference
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.JpsReferenceableElement
import org.jetbrains.jps.model.ex.JpsElementBase
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase
import org.jetbrains.jps.model.ex.JpsNamedCompositeElementBase
import org.jetbrains.jps.model.impl.JpsElementCollectionImpl
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.JpsJavaLibraryType
import org.jetbrains.jps.model.java.JpsJavaModuleType
import org.jetbrains.jps.model.java.JpsJavaSdkType
import org.jetbrains.jps.model.java.LanguageLevel
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions
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

private val KOTLINC_VERSION_HASH = Hashing.xxh3_64().hashBytesToLong((KotlinCompilerVersion.getVersion() ?: "@snapshot@").toByteArray())

internal val isLibTracked = System.getProperty(GlobalOptions.TRACK_LIBRARY_DEPENDENCIES_ENABLED).toBoolean()

internal fun loadJpsModel(
  sources: List<Path>,
  args: ArgMap<JvmBuilderFlags>,
  classPathRootDir: Path,
  dependencyFileToDigest: Map<Path, ByteArray>?,
): Pair<JpsModel, TargetConfigurationDigestContainer> {
  val model = jpsElementFactory.createModel()

  val digests = TargetConfigurationDigestContainer()
  digests.set(TargetConfigurationDigestProperty.KOTLIN_VERSION, KOTLINC_VERSION_HASH)
  digests.set(TargetConfigurationDigestProperty.JPS_TRACK_LIB_DEPS, if (isLibTracked) 1 else 0)
  digests.set(TargetConfigurationDigestProperty.TOOL_VERSION, 1)

  // properties not needed for us (not implemented for java)
  // extension.loadModuleOptions not needed for us (not implemented for java)
  val module = JpsModuleImpl(
    JpsJavaModuleType.INSTANCE,
    args.mandatorySingle(JvmBuilderFlags.KOTLIN_MODULE_NAME),
    jpsElementFactory.createDummyElement(),
  )
  val jpsJavaModuleExtension = JpsJavaExtensionService.getInstance().getOrCreateModuleExtension(module)

  val languageLevelEnumName = "JDK_" + args.mandatorySingle(JvmBuilderFlags.JVM_TARGET).let { if (it == "8") "1_8" else it }
  val langLevel = LanguageLevel.valueOf(languageLevelEnumName)
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
  // must be after configureJdk; otherwise, for some reason, module output dir is not included in classpath
  dependencyList.addModuleSourceDependency()

  val classPathFiles = configureClasspath(
    module = module,
    dependencyList = dependencyList,
    args = args,
    baseDir = classPathRootDir,
    dependencyFileToDigest = dependencyFileToDigest.takeIf { !isLibTracked },
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
  configHash.putUnorderedIterable(args.optionalList(JvmBuilderFlags.ADD_EXPORT), HashFunnel.forString(), Hashing.xxh3_64())
  digests.set(TargetConfigurationDigestProperty.COMPILER, configHash.asLong)

  val project = model.project
  project.addModule(module)

  configureJavac(project, args)

  module.container.setChild(BazelConfigurationHolder.KIND, BazelConfigurationHolder(
    classPath = classPathFiles,
    args = args,
    kotlinArgs = kotlinArgs,
    classPathRootDir = classPathRootDir,
    sources = sources,
  ))

  return model to digests
}

private fun configureJavac(project: JpsProject, args: ArgMap<JvmBuilderFlags>) {
  val configuration = JpsJavaExtensionService.getInstance().getCompilerConfiguration(project)
  val compilerOptions = JpsJavaCompilerOptions()
  compilerOptions.PREFER_TARGET_JDK_COMPILER = false
  compilerOptions.DEPRECATION = false
  compilerOptions.GENERATE_NO_WARNINGS = true
  compilerOptions.MAXIMUM_HEAP_SIZE = 512
  compilerOptions.ADDITIONAL_OPTIONS_STRING = args.optionalList(JvmBuilderFlags.ADD_EXPORT).asSequence()
    .filter { !it.isBlank() }
    .joinToString(separator = " ") {
      "--add-exports $it"
    }
  configuration.setCompilerOptions("Javac", compilerOptions)
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
  val moduleName = args.mandatorySingle(JvmBuilderFlags.KOTLIN_MODULE_NAME)
  kotlinArgs.moduleName = moduleName
  kotlinFacetSettings.compilerArguments = kotlinArgs
  configureCommonCompilerArgs(kotlinArgs = kotlinArgs, args = args, workingDir = classPathRootDir)

  configHash.putString(kotlinArgs.apiVersion ?: "")
  configHash.putString(kotlinArgs.languageVersion ?: "")
  configHash.putString(kotlinArgs.jvmTarget ?: "")
  configHash.putString(kotlinArgs.lambdas ?: "")
  configHash.putString(kotlinArgs.jvmDefault)
  configHash.putBoolean(kotlinArgs.inlineClasses)
  configHash.putBoolean(kotlinArgs.allowKotlinPackage)
  configHash.putString(moduleName)

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

internal class BazelConfigurationHolder(
  @JvmField val classPath: Array<Path>,
  @JvmField val args: ArgMap<JvmBuilderFlags>,
  @JvmField val kotlinArgs: K2JVMCompilerArguments,
  @JvmField val classPathRootDir: Path,
  @JvmField val sources: List<Path>,
) : JpsElementBase<BazelConfigurationHolder>() {
  companion object {
    @JvmField val KIND: JpsElementChildRoleBase<BazelConfigurationHolder> = JpsElementChildRoleBase.create<BazelConfigurationHolder>("kotlin facet extension")
  }
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
  args: ArgMap<JvmBuilderFlags>,
  baseDir: Path,
  dependencyFileToDigest: Map<Path, ByteArray>?,
  digests: TargetConfigurationDigestContainer,
): Array<Path> {
  // no classpath if no source file (jvm_test without own sources)
  val classPathRaw = args.optionalList(JvmBuilderFlags.CP)
  val files = Array<Path>(classPathRaw.size) {
    baseDir.resolve(classPathRaw[it]).normalize()
  }
  val lib = BazelJpsLibrary("class-path-lib", files.asList())
  module.addModuleLibrary(lib)
  dependencyList.addLibraryDependency(lib)

  val hash = Hashing.xxh3_64().hashStream()
  hash.putOrderedIterable(classPathRaw, HashFunnel.forString())
  digests.set(TargetConfigurationDigestProperty.DEPENDENCY_PATH_LIST, hash.asLong)
  hash.reset()

  if (dependencyFileToDigest != null) {
    for (file in files) {
      val digest = requireNotNull(dependencyFileToDigest.get(file)) {
        "Missing digest for $file.\nAvailable digests: ${dependencyFileToDigest.keys.joinToString(separator = ",\n") { it.invariantSeparatorsPathString }}"
      }
      hash.putBytes(digest)
    }
  }
  hash.putInt(files.size)

  digests.set(TargetConfigurationDigestProperty.DEPENDENCY_DIGEST_LIST, hash.asLong)
  hash.reset()

  return files
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
    // kotlin uses this API
    //return files.map { "jar://" + it.invariantSeparatorsPathString + "!/" }
    throw UnsupportedOperationException()
  }

  override fun toString(): String = "BazelJpsLibrary(files=$files)"
}