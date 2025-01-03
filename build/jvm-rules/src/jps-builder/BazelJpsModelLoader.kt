@file:Suppress("UnstableApiUsage")

package org.jetbrains.bazel.jvm.jps

import com.google.devtools.build.runfiles.Runfiles
import org.jetbrains.bazel.jvm.kotlin.ArgMap
import org.jetbrains.bazel.jvm.kotlin.JvmBuilderFlags
import org.jetbrains.bazel.jvm.kotlin.configureCommonCompilerArgs
import org.jetbrains.jps.model.JpsDummyElement
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.*
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.library.impl.JpsLibraryImpl
import org.jetbrains.jps.model.module.JpsDependenciesList
import org.jetbrains.jps.model.module.impl.JpsModuleImpl
import org.jetbrains.jps.model.module.impl.JpsModuleSourceRootImpl
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.config.KotlinFacetSettings
import org.jetbrains.kotlin.jps.model.JpsKotlinFacetModuleExtension
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.invariantSeparatorsPathString

private val jpsElementFactory = JpsElementFactory.getInstance()

internal val runFiles by lazy {
  Runfiles.preload().unmapped()
}

private fun createClasspath(args: ArgMap<JvmBuilderFlags>, baseDir: Path): Sequence<Path> {
  // REDUCED_CLASSPATH_MODE is not supported for JPS
  return args.mandatory(JvmBuilderFlags.CLASSPATH).asSequence().map { baseDir.resolve(it).normalize() }
}

private val javaHome = Path.of(System.getProperty("java.home")).normalize() ?: error("No java.home system property")

internal fun loadJpsModel(
  sources: List<Path>,
  args: ArgMap<JvmBuilderFlags>,
  classPathRootDir: Path,
  classOutDir: Path
): JpsModel {
  val model = jpsElementFactory.createModel()

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
  jpsJavaModuleExtension.languageLevel = LanguageLevel.valueOf(languageLevelEnumName)

  for (source in sources) {
    // used as a key - immutable instance cannot be used
    val properties = JavaSourceRootProperties("", false)
    module.addSourceRoot(JpsModuleSourceRootImpl(source.toUri().toString(), JavaSourceRootType.SOURCE, properties))
  }

  configureKotlinCompiler(module = module, args = args, classPathRootDir = classPathRootDir)

  val dependencyList = module.dependenciesList
  dependencyList.clear()
  configureJdk(model = model, module = module, dependencyList = dependencyList)
  dependencyList.addModuleSourceDependency()

  configureClasspath(module = module, dependencyList = dependencyList, args = args, baseDir = classPathRootDir)

  val project = model.project
  project.addModule(module)

  configureJavac(project, args)
  return model
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

private fun configureKotlinCompiler(module: JpsModuleImpl<JpsDummyElement>, args: ArgMap<JvmBuilderFlags>, classPathRootDir: Path) {
  val kotlinFacetSettings = KotlinFacetSettings()
  kotlinFacetSettings.useProjectSettings = false
  val kotlinArgs = K2JVMCompilerArguments()
  kotlinFacetSettings.compilerArguments = kotlinArgs
  configureCommonCompilerArgs(kotlinArgs = kotlinArgs, args = args, workingDir = classPathRootDir)

  val plugins = args.optionalList(JvmBuilderFlags.PLUGIN_ID).zip(args.optionalList(JvmBuilderFlags.PLUGIN_CLASSPATH))
  if (plugins.isNotEmpty()) {
    val pluginClassPaths = mutableListOf<String>()
    @Suppress("UnusedVariable")
    for ((id, paths) in plugins) {
      val propertyName = "$id.path"
      val relativePath = System.getProperty(propertyName)
      if (relativePath == null) {
        throw IllegalArgumentException("Missing system property $propertyName")
      }

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
) {
  val lib = JpsLibraryImpl(
    "class-path-lib",
    JpsJavaLibraryType.INSTANCE,
    jpsElementFactory.createDummyElement()
  )
  for (file in createClasspath(args, baseDir)) {
    lib.addRoot(file.toUri().toString(), JpsOrderRootType.COMPILED)
  }
  module.addModuleLibrary(lib)
  dependencyList.addLibraryDependency(lib)
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
