// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.jps

import com.dynatrace.hash4j.hashing.HashFunnel
import com.dynatrace.hash4j.hashing.HashStream64
import com.dynatrace.hash4j.hashing.Hashing
import com.google.devtools.build.runfiles.Runfiles
import org.jetbrains.annotations.Unmodifiable
import org.jetbrains.bazel.jvm.jps.state.TargetConfigurationDigestContainer
import org.jetbrains.bazel.jvm.jps.state.TargetConfigurationDigestProperty
import org.jetbrains.bazel.jvm.kotlin.ArgMap
import org.jetbrains.bazel.jvm.kotlin.JvmBuilderFlags
import org.jetbrains.bazel.jvm.kotlin.configureCommonCompilerArgs
import org.jetbrains.jps.model.*
import org.jetbrains.jps.model.ex.JpsNamedCompositeElementBase
import org.jetbrains.jps.model.impl.JpsElementCollectionImpl
import org.jetbrains.jps.model.java.*
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions
import org.jetbrains.jps.model.library.*
import org.jetbrains.jps.model.library.JpsLibraryRoot.InclusionOptions
import org.jetbrains.jps.model.library.impl.JpsLibraryReferenceImpl
import org.jetbrains.jps.model.module.JpsDependenciesList
import org.jetbrains.jps.model.module.impl.JpsModuleImpl
import org.jetbrains.jps.model.module.impl.JpsModuleSourceRootImpl
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.config.KotlinFacetSettings
import org.jetbrains.kotlin.jps.model.JpsKotlinFacetModuleExtension
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.invariantSeparatorsPathString

private val jpsElementFactory = JpsElementFactory.getInstance()

internal val runFiles by lazy {
  Runfiles.preload().unmapped()
}

private val javaHome = Path.of(System.getProperty("java.home")).normalize() ?: error("No java.home system property")

internal fun loadJpsModel(
  sources: List<Path>,
  args: ArgMap<JvmBuilderFlags>,
  classPathRootDir: Path,
  classOutDir: Path,
  dependencyFileToDigest: Map<Path, ByteArray>,
): Pair<JpsModel, TargetConfigurationDigestContainer> {
  val model = jpsElementFactory.createModel()

  val digests = TargetConfigurationDigestContainer()

  val configHash = Hashing.xxh3_64().hashStream()
  // version
  configHash.putInt(1)

  // properties not needed for us (not implemented for java)
  // extension.loadModuleOptions not needed for us (not implemented for java)
  val module = JpsModuleImpl(
    JpsJavaModuleType.INSTANCE,
    args.mandatorySingle(JvmBuilderFlags.TARGET_LABEL),
    jpsElementFactory.createDummyElement(),
  )
  val jpsJavaModuleExtension = JpsJavaExtensionService.getInstance().getOrCreateModuleExtension(module)
  jpsJavaModuleExtension.outputUrl = classOutDir.toUri().toString()

  val languageLevelEnumName = "JDK_" + args.mandatorySingle(JvmBuilderFlags.JVM_TARGET).let { if (it == "8") "1_8" else it }
  val langLevel = LanguageLevel.valueOf(languageLevelEnumName)
  jpsJavaModuleExtension.languageLevel = langLevel

  configHash.putInt(langLevel.ordinal)

  for (source in sources) {
    // used as a key - immutable instance cannot be used
    val properties = JavaSourceRootProperties("", false)
    module.addSourceRoot(JpsModuleSourceRootImpl(source.toUri().toString(), JavaSourceRootType.SOURCE, properties))
  }

  configureKotlinCompiler(module = module, args = args, classPathRootDir = classPathRootDir, configHash = configHash)

  val dependencyList = module.dependenciesList
  dependencyList.clear()
  configureJdk(model = model, module = module, dependencyList = dependencyList)
  dependencyList.addModuleSourceDependency()

  configureClasspath(
    module = module,
    dependencyList = dependencyList,
    args = args,
    baseDir = classPathRootDir,
    dependencyFileToDigest = dependencyFileToDigest,
    digests = digests,
  )

  val project = model.project
  project.addModule(module)

  configHash.putUnorderedIterable(args.optionalList(JvmBuilderFlags.ADD_EXPORT), HashFunnel.forString(), Hashing.xxh3_64())
  configureJavac(project, args)
  digests.set(TargetConfigurationDigestProperty.COMPILER, configHash.asLong)

  return model to digests
}

private fun configureJavac(project: JpsProject, args: ArgMap<JvmBuilderFlags>) {
  val configuration = JpsJavaExtensionService.getInstance().getCompilerConfiguration(project)
  val compilerOptions = JpsJavaCompilerOptions()
  compilerOptions.PREFER_TARGET_JDK_COMPILER = false
  compilerOptions.DEPRECATION = false
  compilerOptions.GENERATE_NO_WARNINGS = true
  compilerOptions.MAXIMUM_HEAP_SIZE = 512
  compilerOptions.ADDITIONAL_OPTIONS_STRING = args.optionalList(JvmBuilderFlags.ADD_EXPORT).joinToString(separator = " ") {
    "--add-exports $it"
  }
  configuration.setCompilerOptions("Javac", compilerOptions)
}

private fun configureKotlinCompiler(
  module: JpsModuleImpl<JpsDummyElement>,
  args: ArgMap<JvmBuilderFlags>,
  classPathRootDir: Path,
  configHash: HashStream64,
) {
  val kotlinFacetSettings = KotlinFacetSettings()
  kotlinFacetSettings.useProjectSettings = false
  val kotlinArgs = K2JVMCompilerArguments()
  kotlinFacetSettings.compilerArguments = kotlinArgs
  configureCommonCompilerArgs(kotlinArgs = kotlinArgs, args = args, workingDir = classPathRootDir)

  configHash.putString(kotlinArgs.apiVersion ?: "")
  configHash.putString(kotlinArgs.languageVersion ?: "")
  configHash.putString(kotlinArgs.jvmTarget ?: "")
  configHash.putString(kotlinArgs.lambdas ?: "")
  configHash.putString(kotlinArgs.jvmDefault)
  configHash.putBoolean(kotlinArgs.inlineClasses)
  configHash.putBoolean(kotlinArgs.allowKotlinPackage)

  val plugins = args.optionalList(JvmBuilderFlags.PLUGIN_ID).zip(args.optionalList(JvmBuilderFlags.PLUGIN_CLASSPATH))
  configHash.putInt(plugins.size)
  if (plugins.isNotEmpty()) {
    val pluginClassPaths = mutableListOf<String>()
    @Suppress("UnusedVariable")
    for ((id, paths) in plugins) {
      val propertyName = "$id.path"
      val relativePath = System.getProperty(propertyName)
      if (relativePath == null) {
        throw IllegalArgumentException("Missing system property $propertyName")
      }

      configHash.putString(id)
      pluginClassPaths.add(runFiles.rlocation(relativePath))
    }
    kotlinArgs.pluginClasspaths = pluginClassPaths.toTypedArray()
  }

  module.container.setChild(JpsKotlinFacetModuleExtension.KIND, JpsKotlinFacetModuleExtension(kotlinFacetSettings))
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
  dependencyFileToDigest: Map<Path, ByteArray>,
  digests: TargetConfigurationDigestContainer,
) {
  // REDUCED_CLASSPATH_MODE is not supported for JPS
  val classPathRaw = args.mandatory(JvmBuilderFlags.CLASSPATH)
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

  // todo JPS should support dependency as JARs, but for now we do include digest into hash
  for (file in files) {
    val digest = requireNotNull(dependencyFileToDigest.get(file)) {
      "Missing digest for $file.\nAvailable digests: ${dependencyFileToDigest.keys.joinToString(separator = ",\n") { it.invariantSeparatorsPathString }}"
    }
    hash.putBytes(digest)
  }
  hash.putInt(files.size)

  digests.set(TargetConfigurationDigestProperty.DEPENDENCY_DIGEST_LIST, hash.asLong)
  hash.reset()
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
  //private val roots = files.map {
  //  JpsLibraryRootImpl("jar://" + it.invariantSeparatorsPathString + "!/", JpsOrderRootType.COMPILED, InclusionOptions.ROOT_ITSELF)
  //}

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