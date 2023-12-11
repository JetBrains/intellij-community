// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplaceNegatedIsEmptyWithIsNotEmpty")
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import com.intellij.util.PathUtilRt
import com.intellij.util.SystemProperties
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.Obsolete
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.DependenciesProperties
import org.jetbrains.intellij.build.dependencies.JdkDownloader
import org.jetbrains.intellij.build.impl.JdkUtils.defineJdk
import org.jetbrains.intellij.build.impl.JdkUtils.readModulesFromReleaseFile
import org.jetbrains.intellij.build.impl.compilation.CompiledClasses
import org.jetbrains.intellij.build.impl.logging.BuildMessagesHandler
import org.jetbrains.intellij.build.impl.logging.BuildMessagesImpl
import org.jetbrains.intellij.build.io.logFreeDiskSpace
import org.jetbrains.intellij.build.kotlin.KotlinBinaries
import org.jetbrains.jps.model.*
import org.jetbrains.jps.model.artifact.JpsArtifactService
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.JpsJavaSdkType
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.library.sdk.JpsSdkReference
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import org.jetbrains.jps.model.serialization.JpsPathMapper
import org.jetbrains.jps.model.serialization.JpsProjectLoader.loadProject
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

@Obsolete
fun createCompilationContextBlocking(communityHome: BuildDependenciesCommunityRoot,
                                     projectHome: Path,
                                     defaultOutputRoot: Path,
                                     options: BuildOptions = BuildOptions()): CompilationContextImpl {
  return runBlocking(Dispatchers.Default) {
    createCompilationContext(communityHome = communityHome,
                             projectHome = projectHome,
                             defaultOutputRoot = defaultOutputRoot,
                             options = options)
  }
}

suspend fun createCompilationContext(communityHome: BuildDependenciesCommunityRoot,
                                     projectHome: Path,
                                     defaultOutputRoot: Path,
                                     options: BuildOptions = BuildOptions()): CompilationContextImpl {
  val logDir = options.logPath?.let { Path.of(it) }
               ?: (options.outputRootPath ?: defaultOutputRoot).resolve("log")
  JaegerJsonSpanExporterManager.setOutput(logDir.toAbsolutePath().normalize().resolve("trace.json"))
  return CompilationContextImpl.createCompilationContext(communityHome = communityHome,
                                                         projectHome = projectHome,
                                                         setupTracer = false,
                                                         buildOutputRootEvaluator = { defaultOutputRoot },
                                                         options = options)
}

private fun computeBuildPaths(options: BuildOptions,
                              project: JpsProject,
                              communityHome: BuildDependenciesCommunityRoot,
                              buildOutputRootEvaluator: (JpsProject) -> Path,
                              projectHome: Path): BuildPaths {
  val buildOut = options.outputRootPath ?: buildOutputRootEvaluator(project)
  val logDir = options.logPath?.let { Path.of(it).toAbsolutePath().normalize() } ?: buildOut.resolve("log")
  return BuildPathsImpl(communityHome = communityHome, projectHome = projectHome, buildOut = buildOut, logDir = logDir)
}

@Internal
class CompilationContextImpl private constructor(
  model: JpsModel,
  private val communityHome: BuildDependenciesCommunityRoot,
  override val messages: BuildMessages,
  override val paths: BuildPaths,
  override val options: BuildOptions,
) : CompilationContext {
  val global: JpsGlobal = model.global
  private val nameToModule: Map<String?, JpsModule>

  override var classesOutputDirectory: Path
    get() {
      val url = JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(project).outputUrl
      return Path.of(JpsPathUtil.urlToOsPath(url))
    }
    set(outputDirectory) {
      val url = "file://" + FileUtilRt.toSystemIndependentName(outputDirectory.toString())
      JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(project).outputUrl = url
    }

  override val project: JpsProject = model.project
  override val projectModel: JpsModel = model
  override val dependenciesProperties: DependenciesProperties
  override val bundledRuntime: BundledRuntime
  override lateinit var compilationData: JpsCompilationData

  @Volatile
  private var cachedJdkHome: Path? = null

  override suspend fun getStableJdkHome(): Path {
    var jdkHome = cachedJdkHome
    if (jdkHome == null) {
      jdkHome = JdkDownloader.getJdkHome(communityHome, Span.current()::addEvent)
      cachedJdkHome = jdkHome
    }
    return jdkHome
  }

  override val stableJavaExecutable: Path by lazy {
    var jdkHome = cachedJdkHome
    if (jdkHome == null) {
      // blocking doesn't matter, getStableJdkHome is mostly always called before
      jdkHome = JdkDownloader.blockingGetJdkHome(communityHome, Span.current()::addEvent)
      cachedJdkHome = jdkHome
    }
    JdkDownloader.getJavaExecutable(jdkHome)
  }

  init {
    val modules = project.modules
    nameToModule = modules.associateByTo(HashMap(modules.size)) { it.name }
    dependenciesProperties = DependenciesProperties(paths.communityHomeDirRoot)
    bundledRuntime = BundledRuntimeImpl(context = this)
  }

  companion object {
    suspend fun createCompilationContext(communityHome: BuildDependenciesCommunityRoot,
                                         projectHome: Path,
                                         buildOutputRootEvaluator: (JpsProject) -> Path,
                                         options: BuildOptions,
                                         setupTracer: Boolean): CompilationContextImpl {
      check(sequenceOf("platform/build-scripts", "bin/idea.properties", "build.txt").all {
        Files.exists(communityHome.communityRoot.resolve(it))
      }) {
        "communityHome ($communityHome) doesn\'t point to a directory containing IntelliJ Community sources"
      }

      val messages = BuildMessagesImpl.create()
      if (options.printEnvironmentInfo) {
        Span.current().addEvent("environment info", Attributes.of(
          AttributeKey.stringKey("community home"), communityHome.communityRoot.toString(),
          AttributeKey.stringKey("project home"), projectHome.toString(),
        ))
        printEnvironmentDebugInfo()
      }

      if (options.printFreeSpace) {
        logFreeDiskSpace(dir = projectHome, phase = "before downloading dependencies")
      }

      val isCompilationRequired = CompiledClasses.isCompilationRequired(options)

      val model = coroutineScope {
        loadProject(projectHome = projectHome, kotlinBinaries = KotlinBinaries(communityHome, messages), isCompilationRequired)
      }

      val buildPaths = computeBuildPaths(project = model.project,
                                         communityHome = communityHome,
                                         options = options,
                                         buildOutputRootEvaluator = buildOutputRootEvaluator,
                                         projectHome = projectHome)

      // not as part of prepareForBuild because prepareForBuild may be called several times per each product or another flavor
      // (see createCopyForProduct)
      if (setupTracer) {
        JaegerJsonSpanExporterManager.setOutput(buildPaths.logDir.resolve("trace.json"))
      }

      val context = CompilationContextImpl(model = model,
                                           communityHome = communityHome,
                                           messages = messages,
                                           paths = buildPaths,
                                           options = options)
      /**
       * [defineJavaSdk] may be skipped using [CompiledClasses.isCompilationRequired]
       * after removing workaround from [JpsCompilationRunner.compileMissingArtifactsModules].
       */
      spanBuilder("define JDK").useWithScope {
        defineJavaSdk(context)
      }
      spanBuilder("prepare for build").useWithScope {
        context.prepareForBuild()
      }

      messages.setDebugLogPath(context.paths.logDir.resolve("debug.log"))
      // this is not a proper place to initialize logging, but this is the only place called in most build scripts
      BuildMessagesHandler.initLogging(messages)
      return context
    }
  }

  fun createCopy(messages: BuildMessages,
                 options: BuildOptions,
                 buildOutputRootEvaluator: (JpsProject) -> Path): CompilationContextImpl {
    val copy = CompilationContextImpl(model = projectModel,
                                      communityHome = paths.communityHomeDirRoot,
                                      messages = messages,
                                      paths = computeBuildPaths(options = options,
                                                                project = project,
                                                                communityHome = communityHome,
                                                                buildOutputRootEvaluator = buildOutputRootEvaluator,
                                                                projectHome = paths.projectHome),
                                      options = options)
    copy.compilationData = compilationData
    return copy
  }

  internal fun prepareForBuild() {
    CompiledClasses.checkOptions(this)

    val logDir = paths.logDir
    if (Files.exists(logDir)) {
      Files.newDirectoryStream(logDir).use { stream ->
        for (file in stream) {
          if (!file.endsWith("trace.json")) {
            NioFiles.deleteRecursively(file)
          }
        }
      }
    }
    else {
      Files.createDirectories(logDir)
    }
    overrideClassesOutputDirectory()
    if (!this::compilationData.isInitialized) {
      compilationData = JpsCompilationData(
        dataStorageRoot = paths.buildOutputDir.resolve(".jps-build-data"),
        classesOutputDirectory = classesOutputDirectory,
        buildLogFile = logDir.resolve("compilation.log"),
        categoriesWithDebugLevelNullable = System.getProperty("intellij.build.debug.logging.categories", "")
      )
    }
    for (artifact in JpsArtifactService.getInstance().getArtifacts(project)) {
      artifact.outputPath = "${paths.jpsArtifacts.resolve(PathUtilRt.getFileName(artifact.outputPath))}"
    }
    suppressWarnings(project)
    ConsoleSpanExporter.setPathRoot(paths.buildOutputDir)
    cleanOutput(keepCompilationState = CompiledClasses.keepCompilationState(options))
  }

  private fun overrideClassesOutputDirectory() {
    val override = options.classesOutputDirectory
    when {
      !override.isNullOrEmpty() -> classesOutputDirectory = Path.of(override)
      options.useCompiledClassesFromProjectOutput -> check(Files.exists(classesOutputDirectory)) {
        "${BuildOptions.USE_COMPILED_CLASSES_PROPERTY} is enabled but the classes output directory $classesOutputDirectory doesn't exist"
      }
      else -> classesOutputDirectory = paths.buildOutputDir.resolve("classes")
    }
    Span.current().addEvent("set class output directory",
                            Attributes.of(AttributeKey.stringKey("classOutputDirectory"), classesOutputDirectory.toString()))
  }

  override fun findRequiredModule(name: String): JpsModule {
    val module = findModule(name)
    checkNotNull(module) {
      "Cannot find required module \'$name\' in the project"
    }
    return module
  }

  override fun findModule(name: String): JpsModule? = nameToModule.get(name)

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
      // if a project requires different SDKs, they all shouldn't be added to test classpath
      .also { if (forTests) it.withoutSdk() }
      .includedIn(JpsJavaClasspathKind.runtime(forTests))
    return enumerator.classes().roots.map { it.absolutePath }
  }

  override fun notifyArtifactBuilt(artifactPath: Path) {
    if (options.buildStepsToSkip.contains(BuildOptions.TEAMCITY_ARTIFACTS_PUBLICATION_STEP)) {
      return
    }

    val isRegularFile = Files.isRegularFile(artifactPath)
    var targetDirectoryPath = ""
    if (artifactPath.parent.startsWith(paths.artifactDir)) {
      targetDirectoryPath = FileUtilRt.toSystemIndependentName(paths.artifactDir.relativize(artifactPath.parent).toString())
    }
    if (!isRegularFile) {
      targetDirectoryPath = (if (targetDirectoryPath.isEmpty()) "" else "$targetDirectoryPath/") + artifactPath.fileName
    }
    var pathToReport = artifactPath.toString()
    if (targetDirectoryPath.isNotEmpty()) {
      pathToReport += "=>$targetDirectoryPath"
    }
    messages.artifactBuilt(pathToReport)
  }
}

private suspend fun loadProject(projectHome: Path, kotlinBinaries: KotlinBinaries, isCompilationRequired: Boolean): JpsModel {
  val model = JpsElementFactory.getInstance().createModel()
  val pathVariablesConfiguration = JpsModelSerializationDataService.getOrCreatePathVariablesConfiguration(model.global)
  if (isCompilationRequired) {
    kotlinBinaries.loadKotlinJpsPluginToClassPath()

    val kotlinCompilerHome = kotlinBinaries.kotlinCompilerHome
    System.setProperty("jps.kotlin.home", kotlinCompilerHome.toString())
    pathVariablesConfiguration.addPathVariable("KOTLIN_BUNDLED", kotlinCompilerHome.toString())
  }

  withContext(Dispatchers.IO) {
    spanBuilder("load project").useWithScope { span ->
      pathVariablesConfiguration.addPathVariable("MAVEN_REPOSITORY", FileUtilRt.toSystemIndependentName(
        Path.of(SystemProperties.getUserHome(), ".m2/repository").toString()))
      val pathVariables = JpsModelSerializationDataService.computeAllPathVariables(model.global)
      loadProject(model.project, pathVariables, JpsPathMapper.IDENTITY, projectHome, { launch { it.run() } }, false)
      span.setAllAttributes(Attributes.of(
        AttributeKey.stringKey("project"), projectHome.toString(),
        AttributeKey.longKey("moduleCount"), model.project.modules.size.toLong(),
        AttributeKey.longKey("libraryCount"), model.project.libraryCollection.libraries.size.toLong(),
      ))
    }
  }
  return model as JpsModel
}

private fun suppressWarnings(project: JpsProject) {
  val compilerOptions = JpsJavaExtensionService.getInstance().getCompilerConfiguration(project).currentCompilerOptions
  compilerOptions.GENERATE_NO_WARNINGS = true
  compilerOptions.DEPRECATION = false
  @Suppress("SpellCheckingInspection")
  compilerOptions.ADDITIONAL_OPTIONS_STRING = compilerOptions.ADDITIONAL_OPTIONS_STRING.replace("-Xlint:unchecked", "")
}

private class BuildPathsImpl(communityHome: BuildDependenciesCommunityRoot, projectHome: Path, buildOut: Path, logDir: Path)
  : BuildPaths(communityHomeDirRoot = communityHome,
               buildOutputDir = buildOut,
               logDir = logDir,
               projectHome = projectHome) {
  init {
    artifactDir = buildOutputDir.resolve("artifacts")
    artifacts = FileUtilRt.toSystemIndependentName(artifactDir.toString())
  }
}

private suspend fun defineJavaSdk(context: CompilationContext) {
  val homePath = context.getStableJdkHome()
  val jbrVersionName = "jbr-17"
  defineJdk(global = context.projectModel.global, jdkName = jbrVersionName, homeDir = homePath)
  readModulesFromReleaseFile(model = context.projectModel, sdkName = jbrVersionName, sdkHome = homePath)

  val sdkReferenceToFirstModule = HashMap<JpsSdkReference<JpsDummyElement>, JpsModule>()
  for (module in context.projectModel.project.modules) {
    val sdkReference = module.getSdkReference(JpsJavaSdkType.INSTANCE) ?: continue
    sdkReferenceToFirstModule.putIfAbsent(sdkReference, module)
  }

  // validate all modules have proper SDK reference
  for ((sdkRef, module) in sdkReferenceToFirstModule) {
    val sdkName = sdkRef.sdkName
    val vendorPrefixEnd = sdkName.indexOf('-')
    val sdkNameWithoutVendor = if (vendorPrefixEnd == -1) sdkName else sdkName.substring(vendorPrefixEnd + 1)
    check(sdkNameWithoutVendor == "17") {
      "Project model at ${context.paths.projectHome} [module ${module.name}] requested SDK $sdkNameWithoutVendor, " +
      "but only '17' is supported as SDK in intellij project"
    }

    if (context.projectModel.global.libraryCollection.findLibrary(sdkName) == null) {
      defineJdk(global = context.projectModel.global, jdkName = sdkName, homeDir = homePath)
      readModulesFromReleaseFile(model = context.projectModel, sdkName = sdkName, sdkHome = homePath)
    }
  }
}

private fun readModulesFromReleaseFile(model: JpsModel, sdkName: String, sdkHome: Path) {
  val additionalSdk = model.global.libraryCollection.findLibrary(sdkName) ?: error("Sdk '$sdkName' is not found")
  val urls = additionalSdk.getRoots(JpsOrderRootType.COMPILED).mapTo(HashSet()) { it.url }
  for (it in readModulesFromReleaseFile(sdkHome)) {
    if (!urls.contains(it)) {
      additionalSdk.addRoot(it, JpsOrderRootType.COMPILED)
    }
  }
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
    outputDirectoriesToKeep.add(compilationData.dataStorageRoot.name)
    outputDirectoriesToKeep.add("classes")
    outputDirectoriesToKeep.add(paths.jpsArtifacts.name)
  }
  spanBuilder("clean output")
    .setAttribute("path", outDir.toString())
    .setAttribute(AttributeKey.stringArrayKey("outputDirectoriesToKeep"), java.util.List.copyOf(outputDirectoriesToKeep))
    .use { span ->
      Files.newDirectoryStream(outDir).use { dirStream ->
        for (file in dirStream) {
          val attributes = Attributes.of(AttributeKey.stringKey("dir"), outDir.relativize(file).toString())
          if (outputDirectoriesToKeep.contains(file.name)) {
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
