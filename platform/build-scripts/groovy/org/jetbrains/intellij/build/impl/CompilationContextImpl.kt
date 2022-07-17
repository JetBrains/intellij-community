// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

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
import org.jetbrains.intellij.build.dependencies.Jdk11Downloader
import org.jetbrains.intellij.build.impl.JdkUtils.defineJdk
import org.jetbrains.intellij.build.impl.JdkUtils.readModulesFromReleaseFile
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
    checkCompilationOptions()
    NioFiles.deleteRecursively(paths.logDir)
    Files.createDirectories(paths.logDir)
    compilationData = JpsCompilationData(
      dataStorageRoot = paths.buildOutputDir.resolve(".jps-build-data"),
      buildLogFile = paths.logDir.resolve("compilation.log"),
      categoriesWithDebugLevelNullable = System.getProperty("intellij.build.debug.logging.categories", "")
    )
    val projectArtifactsDirName = "project-artifacts"
    overrideProjectOutputDirectory()
    val baseArtifactsOutput = "${paths.buildOutputRoot}/$projectArtifactsDirName"
    JpsArtifactService.getInstance().getArtifacts(project).forEach {
      setOutputPath(it, "$baseArtifactsOutput/${PathUtilRt.getFileName(it.outputPath)}")
    }
    if (!options.useCompiledClassesFromProjectOutput) {
      messages.info("Incremental compilation: ${options.incrementalCompilation}")
    }
    if (options.incrementalCompilation) {
      System.setProperty("kotlin.incremental.compilation", "true")
    }
    suppressWarnings(project)
    flush()
    setPathRoot(paths.buildOutputDir)
    /**
     * FIXME should be called lazily, yet it breaks [TestingTasks.runTests], needs investigation
     */
    CompilationTasks.create(this).reuseCompiledClassesIfProvided()
  }

  private fun overrideProjectOutputDirectory() {
    if (options.projectClassesOutputDirectory != null && !options.projectClassesOutputDirectory.isNullOrEmpty()) {
      setProjectOutputDirectory0(this, options.projectClassesOutputDirectory!!)
    }
    else if (options.useCompiledClassesFromProjectOutput) {
      val outputDir = projectOutputDirectory
      if (!outputDir.exists()) {
        throw RuntimeException("${BuildOptions.USE_COMPILED_CLASSES_PROPERTY} is enabled," +
                               " but the project output directory $outputDir doesn\'t exist")
      }
    }
    else {
      setProjectOutputDirectory0(this, "${paths.buildOutputRoot}/classes")
    }
  }

  override val projectOutputDirectory: Path
    get() = JpsPathUtil.urlToFile(JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(project).outputUrl).toPath()

  fun setProjectOutputDirectory(outputDirectory: String) {
    val url = "file://${FileUtilRt.toSystemIndependentName(outputDirectory)}"
    JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(project).outputUrl = url
  }

  private fun checkCompilationOptions() {
    if (options.useCompiledClassesFromProjectOutput && options.incrementalCompilation) {
      messages.warning(
        "\'" + BuildOptions.USE_COMPILED_CLASSES_PROPERTY + "\' is specified, so \'incremental compilation\' option will be ignored")
      options.incrementalCompilation = false
    }
    if (options.pathToCompiledClassesArchive != null && options.incrementalCompilation) {
      messages.warning("Paths to the compiled project output is specified, so 'incremental compilation' option will be ignored")
      options.incrementalCompilation = false
    }
    if (options.pathToCompiledClassesArchive != null && options.useCompiledClassesFromProjectOutput) {
      messages.warning(
        "\'" + BuildOptions.USE_COMPILED_CLASSES_PROPERTY + "\' is specified, so the archive with compiled project output won\'t be used")
      options.pathToCompiledClassesArchive = null
    }
    if (options.pathToCompiledClassesArchivesMetadata != null && options.incrementalCompilation) {
      messages.warning("Paths to the compiled project output metadata is specified, so 'incremental compilation' option will be ignored")
      options.incrementalCompilation = false
    }
    if (options.pathToCompiledClassesArchivesMetadata != null && options.useCompiledClassesFromProjectOutput) {
      messages.warning("\'" +
                       BuildOptions.USE_COMPILED_CLASSES_PROPERTY +
                       "\' is specified, so the archive with the compiled project output metadata won\'t be used to fetch compile output")
      options.pathToCompiledClassesArchivesMetadata = null
    }
    if (options.incrementalCompilation && "false" == System.getProperty("teamcity.build.branch.is_default")) {
      messages.warning(
        "Incremental builds for feature branches have no sense because JPS caches are out of date, so 'incremental compilation' option will be ignored")
      options.incrementalCompilation = false
    }
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
    @JvmStatic
    fun printEnvironmentDebugInfo() {
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
      printEnvironmentDebugInfo()
      logFreeDiskSpace(dir = projectHome, phase = "before downloading dependencies")
      val kotlinBinaries = KotlinBinaries(communityHome, options, messages)
      val model = loadProject(projectHome, kotlinBinaries)
      val oldToNewModuleName = loadModuleRenamingHistory(projectHome, messages) + loadModuleRenamingHistory(communityHome.communityRoot, messages)
      val context = CompilationContextImpl(model = model,
                                           communityHome = communityHome,
                                           projectHome = projectHome,
                                           messages = messages,
                                           oldToNewModuleName = oldToNewModuleName,
                                           buildOutputRootEvaluator = buildOutputRootEvaluator,
                                           options = options)
      defineJavaSdk(context)
      context.prepareForBuild()

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
    dependenciesProperties = DependenciesProperties(this)
    bundledRuntime = BundledRuntimeImpl(this)
    stableJdkHome = Jdk11Downloader.getJdkHome(paths.communityHomeDir)
    stableJavaExecutable = Jdk11Downloader.getJavaExecutable(stableJdkHome)
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

private fun setProjectOutputDirectory0(propOwner: CompilationContextImpl, outputDirectory: String): String {
  propOwner.setProjectOutputDirectory(outputDirectory)
  return outputDirectory
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
  val homePath = Jdk11Downloader.getJdkHome(context.paths.communityHomeDir)
  val jbrHome = toCanonicalPath(homePath.toString())
  val jbrVersionName = "11"
  defineJdk(context.projectModel.global, jbrVersionName, jbrHome, context.messages)
  readModulesFromReleaseFile(context.projectModel, jbrVersionName, jbrHome)
  context.projectModel.project.modules
    .asSequence()
    .mapNotNull { it.getSdkReference(JpsJavaSdkType.INSTANCE)?.sdkName }
    .distinct()
    .forEach { sdkName ->
      val vendorPrefixEnd = sdkName.indexOf('-')
      val sdkNameWithoutVendor = if (vendorPrefixEnd == -1) sdkName else sdkName.substring(vendorPrefixEnd + 1)
      check(sdkNameWithoutVendor == "11") {
        "Project model at ${context.paths.projectHome} requested SDK $sdkNameWithoutVendor, " +
        "but only '11' is supported as SDK in intellij project"
      }

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

private fun loadModuleRenamingHistory(projectHome: Path, messages: BuildMessages): Map<String, String> {
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