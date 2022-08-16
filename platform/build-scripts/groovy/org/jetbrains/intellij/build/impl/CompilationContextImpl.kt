// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import com.intellij.diagnostic.telemetry.use
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.PathUtilRt
import com.intellij.util.SystemProperties
import com.intellij.util.xml.dom.readXmlAsModel
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.ConsoleSpanExporter.Companion.setPathRoot
import org.jetbrains.intellij.build.TracerProviderManager.flush
import org.jetbrains.intellij.build.TracerProviderManager.setOutput
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import org.jetbrains.intellij.build.dependencies.DependenciesProperties
import org.jetbrains.intellij.build.dependencies.JdkDownloader
import org.jetbrains.intellij.build.impl.JdkUtils.defineJdk
import org.jetbrains.intellij.build.impl.JdkUtils.readModulesFromReleaseFile
import org.jetbrains.intellij.build.impl.compilation.CompiledClasses
import org.jetbrains.intellij.build.impl.logging.BuildMessagesHandler
import org.jetbrains.intellij.build.impl.logging.BuildMessagesImpl
import org.jetbrains.intellij.build.kotlin.KotlinBinaries
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.JpsGlobal
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.artifact.JpsArtifact
import org.jetbrains.jps.model.artifact.JpsArtifactService
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.JpsJavaSdkType
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import org.jetbrains.jps.model.serialization.JpsProjectLoader
import org.jetbrains.jps.util.JpsPathUtil
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

@JvmOverloads
fun createCompilationContext(communityHome: BuildDependenciesCommunityRoot,
                             projectHome: Path,
                             defaultOutputRoot: Path,
                             options: BuildOptions = BuildOptions()): CompilationContextImpl {
  return CompilationContextImpl.create(communityHome = communityHome,
                                       projectHome = projectHome,
                                       buildOutputRootEvaluator = { defaultOutputRoot },
                                       options = options)
}

class CompilationContextImpl private constructor(model: JpsModel,
                                                 communityHome: BuildDependenciesCommunityRoot,
                                                 projectHome: Path,
                                                 override val messages: BuildMessages,
                                                 private val oldToNewModuleName: Map<String, String>,
                                                 buildOutputRootEvaluator: (JpsProject) -> Path,
                                                 override val options: BuildOptions) : CompilationContext {

  fun createCopy(messages: BuildMessages,
                 options: BuildOptions,
                 buildOutputRootEvaluator: (JpsProject) -> Path): CompilationContextImpl {
    val copy = CompilationContextImpl(model = projectModel,
                                      communityHome = paths.communityHomeDir,
                                      projectHome = paths.projectHome,
                                      messages = messages,
                                      oldToNewModuleName = oldToNewModuleName,
                                      buildOutputRootEvaluator = buildOutputRootEvaluator,
                                      options = options)
    copy.compilationData = compilationData
    return copy
  }

  fun prepareForBuild() {
    CompiledClasses.checkOptions(this)
    NioFiles.deleteRecursively(paths.logDir)
    Files.createDirectories(paths.logDir)
    if (!this::compilationData.isInitialized) {
      compilationData = JpsCompilationData(
        dataStorageRoot = paths.buildOutputDir.resolve(".jps-build-data"),
        buildLogFile = paths.logDir.resolve("compilation.log"),
        categoriesWithDebugLevelNullable = System.getProperty("intellij.build.debug.logging.categories", "")
      )
    }
    overrideProjectOutputDirectory()
    val baseArtifactsOutput = paths.buildOutputDir.resolve("project-artifacts")
    JpsArtifactService.getInstance().getArtifacts(project).forEach {
      setOutputPath(it, "$baseArtifactsOutput/${PathUtilRt.getFileName(it.outputPath)}")
    }
    suppressWarnings(project)
    flush()
    setPathRoot(paths.buildOutputDir)
    cleanOutput(keepCompilationState = CompiledClasses.keepCompilationState(options))
  }

  private fun overrideProjectOutputDirectory() {
    val override = options.projectClassesOutputDirectory
    when {
      !override.isNullOrEmpty() -> projectOutputDirectory = Path.of(override)
      options.useCompiledClassesFromProjectOutput -> require(projectOutputDirectory.exists()) {
        "${BuildOptions.USE_COMPILED_CLASSES_PROPERTY} is enabled but the project output directory $projectOutputDirectory doesn't exist"
      }
      else -> projectOutputDirectory = paths.buildOutputDir.resolve("classes")
    }
    messages.info("Project output directory is $projectOutputDirectory")
  }

  override var projectOutputDirectory: Path
    get() {
      val url = JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(project).outputUrl
      return JpsPathUtil.urlToFile(url).toPath()
    }
    set(outputDirectory) {
      val url = "file://" + FileUtilRt.toSystemIndependentName("$outputDirectory")
      JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(project).outputUrl = url
    }

  override fun findRequiredModule(name: String): JpsModule {
    val module = findModule(name)
    check(module != null) {
      "Cannot find required module \'$name\' in the project"
    }
    return module
  }

  override fun findModule(name: String): JpsModule? {
    val actualName: String?
    if (oldToNewModuleName.containsKey(name)) {
      actualName = oldToNewModuleName[name]
      messages.warning("Old module name \'$name\' is used in the build scripts; use the new name \'$actualName\' instead")
    }
    else {
      actualName = name
    }
    return nameToModule.get(actualName)
  }

  override fun getOldModuleName(newName: String) = newToOldModuleName.get(newName)

  override fun getModuleOutputDir(module: JpsModule): Path {
    val url = JpsJavaExtensionService.getInstance().getOutputUrl(module, false)
    check(url != null) {
      "Output directory for ${module.name} isn\'t set"
    }
    return Path.of(JpsPathUtil.urlToPath(url))
  }

  override fun getModuleTestsOutputPath(module: JpsModule): String {
    val outputDirectory = JpsJavaExtensionService.getInstance().getOutputDirectory(module, true)
    check(outputDirectory != null) {
      "Output directory for ${module.name} isn\'t set"
    }
    return outputDirectory.absolutePath
  }

  override fun getModuleRuntimeClasspath(module: JpsModule, forTests: Boolean): List<String> {
    val enumerator = JpsJavaExtensionService.dependencies(module).recursively()
      // if project requires different SDKs they all shouldn't be added to test classpath
      .also { if (forTests) it.withoutSdk() }
      .includedIn(JpsJavaClasspathKind.runtime(forTests))
    return enumerator.classes().roots.map { it.absolutePath }
  }

  override fun notifyArtifactWasBuilt(artifactPath: Path) {
    if (options.buildStepsToSkip.contains(BuildOptions.TEAMCITY_ARTIFACTS_PUBLICATION_STEP)) {
      return
    }

    val isRegularFile = Files.isRegularFile(artifactPath)
    var targetDirectoryPath = ""
    if (artifactPath.parent.startsWith(paths.artifactDir)) {
      targetDirectoryPath = FileUtilRt.toSystemIndependentName(paths.artifactDir.relativize(artifactPath.parent).toString())
    }
    if (!isRegularFile) {
      targetDirectoryPath = (if (!targetDirectoryPath.isEmpty()) "$targetDirectoryPath/" else "") + artifactPath.fileName
    }
    var pathToReport = artifactPath.toString()
    if (targetDirectoryPath.isNotEmpty()) {
      pathToReport += "=>$targetDirectoryPath"
    }
    messages.artifactBuilt(pathToReport)
  }

  override val paths: BuildPaths
  override val project: JpsProject
  val global: JpsGlobal
  override val projectModel: JpsModel = model
  private val newToOldModuleName: Map<String, String>
  private val nameToModule: Map<String?, JpsModule>
  override val dependenciesProperties: DependenciesProperties
  override val bundledRuntime: BundledRuntime
  override lateinit var compilationData: JpsCompilationData
  override val stableJavaExecutable: Path
  override val stableJdkHome: Path

  companion object {
    private fun printEnvironmentDebugInfo() {
      // print it to the stdout since TeamCity will remove any sensitive fields from build log automatically
      // don't write it to debug log file!
      val env = System.getenv()
      for (key in env.keys.sorted()) {
        println("ENV $key = ${env[key]}")
      }

      val properties = System.getProperties()
      for (propertyName in properties.keys.sortedBy { it as String }) {
        println("PROPERTY $propertyName = ${properties[propertyName].toString()}")
      }
    }

    internal fun create(communityHome: BuildDependenciesCommunityRoot,
                        projectHome: Path,
                        buildOutputRootEvaluator: (JpsProject) -> Path,
                        options: BuildOptions = BuildOptions()): CompilationContextImpl {
      // This is not a proper place to initialize tracker for downloader
      // but this is the only place which is called in most build scripts
      BuildDependenciesDownloader.TRACER = BuildDependenciesOpenTelemetryTracer.INSTANCE
      val messages = BuildMessagesImpl.create()
      if (sequenceOf("platform/build-scripts", "bin/idea.properties", "build.txt").any { !Files.exists(communityHome.communityRoot.resolve(it)) }) {
        messages.error("communityHome ($communityHome) doesn\'t point to a directory containing IntelliJ Community sources")
      }
      if (options.printEnvironmentInfo) {
        messages.block("Environment info") {
          messages.info("Community home: ${communityHome.communityRoot}")
          messages.info("Project home: $projectHome")
          printEnvironmentDebugInfo()
        }
      }
      logFreeDiskSpace(dir = projectHome, phase = "before downloading dependencies")
      val kotlinBinaries = KotlinBinaries(communityHome, options, messages)
      val model = loadProject(projectHome, kotlinBinaries)
      val oldToNewModuleName = loadModuleRenamingHistory(projectHome) + loadModuleRenamingHistory(communityHome.communityRoot)
      val context = CompilationContextImpl(model = model,
                                           communityHome = communityHome,
                                           projectHome = projectHome,
                                           messages = messages,
                                           oldToNewModuleName = oldToNewModuleName,
                                           buildOutputRootEvaluator = buildOutputRootEvaluator,
                                           options = options)
      defineJavaSdk(context)
      messages.block("Preparing for build") {
        context.prepareForBuild()
      }

      // not as part of prepareForBuild because prepareForBuild may be called several times per each product or another flavor
      // (see createCopyForProduct)
      setOutput(context.paths.logDir.resolve("trace.json"))
      messages.setDebugLogPath(context.paths.logDir.resolve("debug.log"))

      // This is not a proper place to initialize logging
      // but this is the only place which is called in most build scripts
      BuildMessagesHandler.initLogging(messages)
      return context
    }
  }

  init {
    project = model.project
    global = model.global
    newToOldModuleName = oldToNewModuleName.entries.associate { it.value to it.key }
    val modules = model.project.modules
    this.nameToModule = modules.associateBy { it.name }
    val buildOut = options.outputRootPath?.let { Path.of(it) } ?: buildOutputRootEvaluator(project)
    val logDir = options.logPath?.let { Path.of(it).toAbsolutePath().normalize() } ?: buildOut.resolve("log")
    paths = BuildPathsImpl(communityHome, projectHome, buildOut, logDir)
    dependenciesProperties = DependenciesProperties(paths.communityHomeDir)
    bundledRuntime = BundledRuntimeImpl(options, paths, dependenciesProperties, messages::error, messages::info)
    stableJdkHome = JdkDownloader.getJdkHome(paths.communityHomeDir)
    stableJavaExecutable = JdkDownloader.getJavaExecutable(stableJdkHome)
  }
}

private fun loadProject(projectHome: Path, kotlinBinaries: KotlinBinaries): JpsModel {
  val model = JpsElementFactory.getInstance().createModel()
  val pathVariablesConfiguration = JpsModelSerializationDataService.getOrCreatePathVariablesConfiguration(model.global)
  if (kotlinBinaries.isCompilerRequired) {
    kotlinBinaries.loadKotlinJpsPluginToClassPath()

    val kotlinCompilerHome = kotlinBinaries.kotlinCompilerHome
    System.setProperty("jps.kotlin.home", kotlinCompilerHome.toFile().absolutePath)
    pathVariablesConfiguration.addPathVariable("KOTLIN_BUNDLED", kotlinCompilerHome.toString())
  }
  pathVariablesConfiguration.addPathVariable("MAVEN_REPOSITORY", FileUtilRt.toSystemIndependentName(
    File(SystemProperties.getUserHome(), ".m2/repository").absolutePath))
  val pathVariables = JpsModelSerializationDataService.computeAllPathVariables(model.global)
  JpsProjectLoader.loadProject(model.project, pathVariables, projectHome)
  Span.current().addEvent("project loaded", Attributes.of(
    AttributeKey.stringKey("project"), projectHome.toString(),
    AttributeKey.longKey("moduleCount"), model.project.modules.size.toLong(),
    AttributeKey.longKey("libraryCount"), model.project.libraryCollection.libraries.size.toLong(),
  ))
  return model as JpsModel
}

private fun suppressWarnings(project: JpsProject) {
  val compilerOptions = JpsJavaExtensionService.getInstance().getCompilerConfiguration(project).currentCompilerOptions
  compilerOptions.GENERATE_NO_WARNINGS = true
  compilerOptions.DEPRECATION = false
  @Suppress("SpellCheckingInspection")
  compilerOptions.ADDITIONAL_OPTIONS_STRING = compilerOptions.ADDITIONAL_OPTIONS_STRING.replace("-Xlint:unchecked", "")
}

private fun toCanonicalPath(path: String): String {
  return FileUtilRt.toSystemIndependentName(File(path).canonicalPath)
}

private fun <Value : String?> setOutputPath(propOwner: JpsArtifact, outputPath: Value): Value {
  propOwner.outputPath = outputPath
  return outputPath
}

private class BuildPathsImpl(communityHome: BuildDependenciesCommunityRoot, projectHome: Path, buildOut: Path, logDir: Path)
  : BuildPaths(communityHomeDir = communityHome,
               buildOutputDir = buildOut,
               logDir = logDir,
               projectHome = projectHome) {
  init {
    artifactDir = buildOutputDir.resolve("artifacts")
    artifacts = FileUtilRt.toSystemIndependentName(artifactDir.toString())
  }
}

private fun defineJavaSdk(context: CompilationContext) {
  val homePath = JdkDownloader.getJdkHome(context.paths.communityHomeDir)
  val jbrHome = toCanonicalPath(homePath.toString())
  val jbrVersionName = "11"
  defineJdk(context.projectModel.global, jbrVersionName, jbrHome, context.messages)
  readModulesFromReleaseFile(context.projectModel, jbrVersionName, jbrHome)

  // Validate all modules have proper SDK reference
  context.projectModel.project.modules
    .asSequence()
    .forEach { module ->
      val sdkName = module.getSdkReference(JpsJavaSdkType.INSTANCE)?.sdkName ?: return@forEach
      val vendorPrefixEnd = sdkName.indexOf('-')
      val sdkNameWithoutVendor = if (vendorPrefixEnd == -1) sdkName else sdkName.substring(vendorPrefixEnd + 1)
      check(sdkNameWithoutVendor == "17") {
        "Project model at ${context.paths.projectHome} [module ${module.name}] requested SDK $sdkNameWithoutVendor, " +
        "but only '17' is supported as SDK in intellij project"
      }
    }

  context.projectModel.project.modules
    .asSequence()
    .mapNotNull { it.getSdkReference(JpsJavaSdkType.INSTANCE)?.sdkName }
    .distinct()
    .forEach { sdkName ->
      if (context.projectModel.global.libraryCollection.findLibrary(sdkName) == null) {
        defineJdk(context.projectModel.global, sdkName, jbrHome, context.messages)
        readModulesFromReleaseFile(context.projectModel, sdkName, jbrHome)
      }
    }
}

private fun readModulesFromReleaseFile(model: JpsModel, sdkName: String, sdkHome: String) {
  val additionalSdk = model.global.libraryCollection.findLibrary(sdkName) ?: throw IllegalStateException("Sdk '$sdkName' is not found")
  val urls = additionalSdk.getRoots(JpsOrderRootType.COMPILED).map { it.url }
  for (it in readModulesFromReleaseFile(Path.of(sdkHome))) {
    if (!urls.contains(it)) {
      additionalSdk.addRoot(it, JpsOrderRootType.COMPILED)
    }
  }
}

private fun loadModuleRenamingHistory(projectHome: Path): Map<String, String> {
  val modulesXml = projectHome.resolve(".idea/modules.xml")
  check(Files.exists(modulesXml)) {
    "Incorrect project home: $modulesXml doesn\'t exist"
  }
  return Files.newInputStream(modulesXml).use { readXmlAsModel(it) }.children("component")
           .find { it.getAttributeValue("name") == "ModuleRenamingHistory" }
           ?.children("module")
           ?.associate { it.getAttributeValue("old-name")!! to it.getAttributeValue("new-name")!! }
         ?: emptyMap()
}

private fun CompilationContext.cleanOutput(keepCompilationState: Boolean) {
  val outDir = paths.buildOutputDir
  if (!options.cleanOutputFolder) {
    Span.current().addEvent("skip output cleaning", Attributes.of(
      AttributeKey.stringKey("dir"), "$outDir",
    ))
    return
  }
  val outputDirectoriesToKeep = HashSet<String>(4)
  outputDirectoriesToKeep.add("log")
  if (keepCompilationState) {
    outputDirectoriesToKeep.add(compilationData.dataStorageRoot.fileName.toString())
    outputDirectoriesToKeep.add("classes")
    outputDirectoriesToKeep.add("project-artifacts")
  }
  TraceManager.spanBuilder("clean output")
    .setAttribute("path", outDir.toString())
    .setAttribute(AttributeKey.stringArrayKey("outputDirectoriesToKeep"), java.util.List.copyOf(outputDirectoriesToKeep))
    .use { span ->
      Files.newDirectoryStream(outDir).use { dirStream ->
        for (file in dirStream) {
          val attributes = Attributes.of(AttributeKey.stringKey("dir"), outDir.relativize(file).toString())
          if (outputDirectoriesToKeep.contains(file.fileName.toString())) {
            span.addEvent("skip cleaning", attributes)
          }
          else {
            span.addEvent("delete", attributes)
            NioFiles.deleteRecursively(file)
          }
        }
      }
      null
    }
}
