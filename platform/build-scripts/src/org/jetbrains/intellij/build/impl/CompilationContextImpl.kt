// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.diagnostic.COROUTINE_DUMP_HEADER
import com.intellij.diagnostic.dumpCoroutines
import com.intellij.diagnostic.enableCoroutineDump
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.PathUtilRt
import com.jetbrains.JBR
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.Obsolete
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.JpsCompilationData
import org.jetbrains.intellij.build.dependencies.DependenciesProperties
import org.jetbrains.intellij.build.dependencies.JdkDownloader
import org.jetbrains.intellij.build.impl.JdkUtils.defineJdk
import org.jetbrains.intellij.build.impl.JdkUtils.readModulesFromReleaseFile
import org.jetbrains.intellij.build.impl.compilation.checkCompilationOptions
import org.jetbrains.intellij.build.impl.compilation.isCompilationRequired
import org.jetbrains.intellij.build.impl.compilation.keepCompilationState
import org.jetbrains.intellij.build.impl.compilation.reuseOrCompile
import org.jetbrains.intellij.build.impl.logging.BuildMessagesHandler
import org.jetbrains.intellij.build.impl.logging.BuildMessagesImpl
import org.jetbrains.intellij.build.impl.moduleBased.OriginalModuleRepositoryImpl
import org.jetbrains.intellij.build.io.ZipEntryProcessorResult
import org.jetbrains.intellij.build.io.logFreeDiskSpace
import org.jetbrains.intellij.build.io.readZipFile
import org.jetbrains.intellij.build.kotlin.KotlinBinaries
import org.jetbrains.intellij.build.moduleBased.OriginalModuleRepository
import org.jetbrains.intellij.build.telemetry.ConsoleSpanExporter
import org.jetbrains.intellij.build.telemetry.JaegerJsonSpanExporterManager
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import org.jetbrains.jps.model.JpsDummyElement
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.JpsGlobal
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.artifact.JpsArtifactService
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
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
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.relativeToOrNull

@Obsolete
fun createCompilationContextBlocking(
  projectHome: Path,
  defaultOutputRoot: Path,
  options: BuildOptions = BuildOptions(),
): CompilationContextImpl = runBlocking(Dispatchers.Default) {
  createCompilationContext(projectHome, defaultOutputRoot, options)
}

suspend fun createCompilationContext(
  projectHome: Path,
  defaultOutputRoot: Path,
  options: BuildOptions = BuildOptions(),
): CompilationContextImpl {
  val logDir = options.logDir ?: (options.outRootDir ?: defaultOutputRoot).resolve("log")
  JaegerJsonSpanExporterManager.setOutput(logDir.toAbsolutePath().normalize().resolve("trace.json"))
  return CompilationContextImpl.createCompilationContext(projectHome, { defaultOutputRoot }, options, setupTracer = false)
}

internal fun computeBuildPaths(options: BuildOptions, buildOut: Path, projectHome: Path, artifactDir: Path? = null): BuildPaths {
  val tempDir = buildOut.resolve("temp")
  val result = BuildPaths(
    communityHomeDirRoot = COMMUNITY_ROOT,
    buildOutputDir = buildOut,
    logDir = options.logDir ?: buildOut.resolve("log"),
    projectHome = projectHome,
    tempDir = tempDir,
    artifactDir = artifactDir ?: buildOut.resolve("artifacts"),
  )
  Files.createDirectories(tempDir)
  return result
}

@Internal
class CompilationContextImpl private constructor(
  private val model: JpsModel,
  override val messages: BuildMessages,
  override val paths: BuildPaths,
  override val options: BuildOptions,
) : CompilationContext {
  val global: JpsGlobal
    get() = model.global

  private val nameToModule: Map<String?, JpsModule>

  override var classesOutputDirectory: Path
    get() = Path.of(JpsPathUtil.urlToPath(JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(project).outputUrl))
    set(outputDirectory) {
      val url = "file://" + outputDirectory.invariantSeparatorsPathString
      JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(project).outputUrl = url
    }

  override val project: JpsProject
    get() = model.project

  override val projectModel: JpsModel
    get() = model

  override val dependenciesProperties: DependenciesProperties = DependenciesProperties(paths.communityHomeDirRoot)

  override val bundledRuntime: BundledRuntime = BundledRuntimeImpl(this)

  override lateinit var compilationData: JpsCompilationData

  @Volatile
  private var cachedJdkHome: Path? = null

  init {
    val modules = project.modules
    nameToModule = modules.associateByTo(HashMap(modules.size)) { it.name }
  }

  override val stableJavaExecutable: Path by lazy {
    var jdkHome = cachedJdkHome
    if (jdkHome == null) {
      // blocking doesn't matter, getStableJdkHome is mostly always called before
      jdkHome = JdkDownloader.blockingGetJdkHome(COMMUNITY_ROOT, infoLog = Span.current()::addEvent)
      cachedJdkHome = jdkHome
    }
    JdkDownloader.getJavaExecutable(jdkHome)
  }

  companion object {
    init {
      // https://github.com/netty/netty/issues/11532
      if (System.getProperty("io.netty.tryReflectionSetAccessible") == null) {
        System.setProperty("io.netty.tryReflectionSetAccessible", "true")
      }
    }

    suspend fun createCompilationContext(
      projectHome: Path,
      buildOutputRootEvaluator: (JpsProject) -> Path,
      options: BuildOptions,
      setupTracer: Boolean,
      enableCoroutinesDump: Boolean = true,
      customBuildPaths: BuildPaths? = null,
    ): CompilationContextImpl {
      if (!options.useCompiledClassesFromProjectOutput) {
        // disable compression - otherwise, our zstd/zip cannot compress efficiently
        System.setProperty("jps.storage.do.compression", "false")
        System.setProperty("jps.new.storage.cache.size.mb", "96")
      }

      check(sequenceOf("platform/build-scripts", "bin/idea.properties", "build.txt").all {
        Files.exists(COMMUNITY_ROOT.communityRoot.resolve(it))
      }) {
        "communityHome ($COMMUNITY_ROOT) doesn't point to a directory containing IntelliJ Community sources"
      }

      val messages = BuildMessagesImpl.create()
      if (options.printEnvironmentInfo) {
        Span.current().addEvent("environment info", Attributes.of(
          AttributeKey.stringKey("community home"), COMMUNITY_ROOT.communityRoot.toString(),
          AttributeKey.stringKey("project home"), projectHome.toString(),
        ))
        printEnvironmentDebugInfo()
      }

      if (options.printFreeSpace) {
        logFreeDiskSpace(dir = projectHome, phase = "before downloading dependencies")
      }

      val model = loadProject(projectHome, KotlinBinaries(COMMUNITY_ROOT), isCompilationRequired(options))

      val buildPaths = customBuildPaths ?: computeBuildPaths(options, options.outRootDir ?: buildOutputRootEvaluator(model.project), projectHome)

      // not as part of prepareForBuild because prepareForBuild may be called several times per each product or another flavor
      // (see createCopyForProduct)
      if (setupTracer) {
        JaegerJsonSpanExporterManager.setOutput(buildPaths.logDir.resolve("trace.json"))
      }

      val context = CompilationContextImpl(model = model, messages = messages, paths = buildPaths, options = options)
      /**
       * [defineJavaSdk] may be skipped using [isCompilationRequired]
       * after removing workaround from [JpsCompilationRunner.compileMissingArtifactsModules].
       */
      spanBuilder("define JDK").use {
        defineJavaSdk(context)
      }
      if (enableCoroutinesDump) {
        spanBuilder("enable coroutines dump").use {
          context.enableCoroutinesDump(it)
        }
      }

      spanBuilder("prepare for build").use {
        context.prepareForBuild()
      }

      messages.setDebugLogPath(context.paths.logDir.resolve("debug.log"))
      // this is not a proper place to initialize logging, but this is the only place called in most build scripts
      BuildMessagesHandler.initLogging(messages)
      return context
    }
  }

  override suspend fun getStableJdkHome(): Path {
    var jdkHome = cachedJdkHome
    if (jdkHome == null) {
      jdkHome = JdkDownloader.getJdkHome(COMMUNITY_ROOT, infoLog = Span.current()::addEvent)
      cachedJdkHome = jdkHome
    }
    return jdkHome
  }

  override suspend fun getOriginalModuleRepository(): OriginalModuleRepository {
    generateRuntimeModuleRepository(this)
    return OriginalModuleRepositoryImpl(this)
  }

  override fun createCopy(messages: BuildMessages, options: BuildOptions, paths: BuildPaths): CompilationContext {
    val copy = CompilationContextImpl(projectModel, messages, paths, options)
    copy.compilationData = compilationData
    return copy
  }

  override suspend fun prepareForBuild() {
    checkCompilationOptions(this)

    val logDir = paths.logDir
    if (options.compilationLogEnabled) {
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
    }

    overrideClassesOutputDirectory()
    if (!this::compilationData.isInitialized) {
      compilationData = JpsCompilationData(
        dataStorageRoot = paths.buildOutputDir.resolve("jps-build-data"),
        classesOutputDirectory = classesOutputDirectory,
        buildLogFile = logDir.resolve("compilation.log"),
        categoriesWithDebugLevel = System.getProperty("intellij.build.debug.logging.categories", "") ?: "",
      )
    }
    for (artifact in JpsArtifactService.getInstance().getArtifacts(project)) {
      artifact.outputPath = "${paths.jpsArtifacts.resolve(PathUtilRt.getFileName(artifact.outputPath))}"
    }
    suppressWarnings(project)
    ConsoleSpanExporter.setPathRoot(paths.buildOutputDir)
    if (options.cleanOutDir || options.forceRebuild) {
      cleanOutput(context = this@CompilationContextImpl, keepCompilationState(options))
    }
    else {
      Span.current().addEvent("skip output cleaning", Attributes.of(
        AttributeKey.stringKey("dir"), paths.buildOutputDir.toString(),
      ))
    }
  }

  private val compileMutex = Mutex()

  override suspend fun withCompilationLock(block: suspend () -> Unit) {
    compileMutex.withReentrantLock(block)
  }

  override suspend fun compileModules(moduleNames: Collection<String>?, includingTestsInModules: List<String>?) {
    spanBuilder("resolve dependencies and compile modules").use { span ->
      compileMutex.withReentrantLock {
        resolveProjectDependencies(this@CompilationContextImpl)
        reuseOrCompile(context = this@CompilationContextImpl, moduleNames, includingTestsInModules, span)
      }
    }
  }

  private fun overrideClassesOutputDirectory() {
    val override = options.classOutDir
    when {
      !override.isNullOrEmpty() -> classesOutputDirectory = Path.of(override)
      options.useCompiledClassesFromProjectOutput -> check(Files.exists(classesOutputDirectory)) {
        "${BuildOptions.USE_COMPILED_CLASSES_PROPERTY} is enabled but the classes output directory $classesOutputDirectory doesn't exist"
      }
      else -> classesOutputDirectory = paths.buildOutputDir.resolve("classes")
    }
    Span.current().addEvent("set class output directory", Attributes.of(AttributeKey.stringKey("classOutputDirectory"), classesOutputDirectory.toString()))
  }

  override fun findRequiredModule(name: String): JpsModule {
    val module = findModule(name)
    checkNotNull(module) {
      "Cannot find required module '$name' in the project"
    }
    return module
  }

  override fun findModule(name: String): JpsModule? = nameToModule[name.removeSuffix("._test")]

  override suspend fun getModuleOutputDir(module: JpsModule, forTests: Boolean): Path {
    val url = JpsJavaExtensionService.getInstance().getOutputUrl(/* module = */ module, /* forTests = */ forTests)
    requireNotNull(url) {
      "Output directory for ${module.name} isn't set"
    }
    return Path.of(JpsPathUtil.urlToPath(url))
  }

  override suspend fun getModuleTestsOutputDir(module: JpsModule): Path {
    val url = JpsJavaExtensionService.getInstance().getOutputUrl(module, true)
    requireNotNull(url) {
      "Output directory for ${module.name} isn't set"
    }
    return Path.of(JpsPathUtil.urlToPath(url))
  }

  override suspend fun getModuleRuntimeClasspath(module: JpsModule, forTests: Boolean): List<String> {
    val enumerator = JpsJavaExtensionService.dependencies(module).recursively()
      // if a project requires different SDKs, they all shouldn't be added to the test classpath
      .also { if (forTests) it.withoutSdk() }
      .includedIn(JpsJavaClasspathKind.runtime(forTests))
    return enumerator.classes().paths.map { it.toString() }
  }

  override fun findFileInModuleSources(moduleName: String, relativePath: String, forTests: Boolean): Path? {
    return org.jetbrains.intellij.build.impl.findFileInModuleSources(module = findRequiredModule(moduleName), relativePath = relativePath)
  }

  override fun findFileInModuleSources(module: JpsModule, relativePath: String, forTests: Boolean): Path? {
    return org.jetbrains.intellij.build.impl.findFileInModuleSources(module, relativePath)
  }

  override suspend fun readFileContentFromModuleOutput(module: JpsModule, relativePath: String): ByteArray? {
    val file = getModuleOutputDir(module).resolve(relativePath)
    try {
      return Files.readAllBytes(file)
    }
    catch (_: NoSuchFileException) {
      return null
    }
  }

  override fun notifyArtifactBuilt(artifactPath: Path) {
    if (options.buildStepsToSkip.contains(BuildOptions.TEAMCITY_ARTIFACTS_PUBLICATION_STEP)) {
      return
    }

    val isRegularFile = Files.isRegularFile(artifactPath)
    var targetDirectoryPath = ""
    if (artifactPath.parent.startsWith(paths.artifactDir)) {
      targetDirectoryPath = paths.artifactDir.relativize(artifactPath.parent).invariantSeparatorsPathString
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

  private fun enableCoroutinesDump(span: Span) {
    try {
      enableCoroutineDump()
      JBR.getJstack()?.includeInfoFrom { """
$COROUTINE_DUMP_HEADER
${dumpCoroutines()}
""" // dumpCoroutines is multiline, trimIndent won't work
      }
    }
    catch (e: NoClassDefFoundError) {
      span.addEvent("Cannot enable coroutines dump, JetBrains Runtime is required: ${e.message}")
    }
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

  spanBuilder("load project").use(Dispatchers.IO) { span ->
    pathVariablesConfiguration.addPathVariable("MAVEN_REPOSITORY", Path.of(System.getProperty("user.home"), ".m2/repository").invariantSeparatorsPathString)
    val pathVariables = JpsModelSerializationDataService.computeAllPathVariables(model.global)
    loadProject(model.project, pathVariables, JpsPathMapper.IDENTITY, projectHome, null, { it: Runnable -> launch(CoroutineName("loading project")) { it.run() } }, false)
    span.setAllAttributes(
      Attributes.of(
        AttributeKey.stringKey("project"), projectHome.toString(),
        AttributeKey.longKey("moduleCount"), model.project.modules.size.toLong(),
        AttributeKey.longKey("libraryCount"), model.project.libraryCollection.libraries.size.toLong(),
      )
    )
  }
  return model as JpsModel
}

private fun suppressWarnings(project: JpsProject) {
  val compilerOptions = JpsJavaExtensionService.getInstance().getCompilerConfiguration(project).currentCompilerOptions
  compilerOptions.GENERATE_NO_WARNINGS = true
  compilerOptions.DEPRECATION = false
  compilerOptions.ADDITIONAL_OPTIONS_STRING = compilerOptions.ADDITIONAL_OPTIONS_STRING.replace("-Xlint:unchecked", "")
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
    val sdkNameWithoutVendor = (if (vendorPrefixEnd == -1) sdkName else sdkName.substring(vendorPrefixEnd + 1)).removeSuffix(" (WSL)")
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

internal suspend fun cleanOutput(context: CompilationContext, keepCompilationState: Boolean) {
  val compilationState = setOf(
    context.compilationData.dataStorageRoot,
    context.classesOutputDirectory,
    context.paths.jpsArtifacts,
  )
  val outputDirectoriesToKeep = buildSet {
    add(context.paths.logDir)
    if (keepCompilationState) {
      addAll(compilationState)
    }
  }
  spanBuilder("clean output").use { span ->
    val outDir = context.paths.buildOutputDir
    for (dir in outputDirectoriesToKeep) {
      val path = dir.relativeToOrNull(outDir) ?: dir
      span.addEvent("skip cleaning", Attributes.of(AttributeKey.stringKey("dir"), path.toString()))
    }
    Files.newDirectoryStream(outDir).use { dirStream ->
      var pathsToBeCleanedStream = dirStream - outputDirectoriesToKeep
      if (!keepCompilationState) {
        pathsToBeCleanedStream = pathsToBeCleanedStream + compilationState
      }
      for (path in pathsToBeCleanedStream) {
        val pathToBeCleaned = outDir.relativize(path)
        span.addEvent("delete", Attributes.of(AttributeKey.stringKey("dir"), "$pathToBeCleaned"))
        for (outputDirToKeep in outputDirectoriesToKeep) {
          check(!outputDirToKeep.startsWith(path)) {
            val outputDirectoryToKeep = outDir.relativize(outputDirToKeep)
            "'$outputDirectoryToKeep' is going to be cleaned together with '$pathToBeCleaned'. " +
            "Please configure a different location for '$outputDirectoryToKeep'"
          }
        }
        NioFiles.deleteRecursively(path)
      }
    }
    Files.createDirectories(context.paths.tempDir)
  }
}

private fun printEnvironmentDebugInfo() {
  // print it to the stdout since TeamCity will remove any sensitive fields from the build log automatically
  // don't write it to a debug log file!
  val env = System.getenv()
  for (key in env.keys.sorted()) {
    println("ENV $key = ${env[key]}")
  }

  val properties = System.getProperties()
  for (propertyName in properties.keys.sortedBy { it as String }) {
    println("PROPERTY $propertyName = ${properties[propertyName].toString()}")
  }
}

private val rootTypeOrder = arrayOf(JavaResourceRootType.RESOURCE, JavaSourceRootType.SOURCE, JavaResourceRootType.TEST_RESOURCE, JavaSourceRootType.TEST_SOURCE)

internal fun findFileInModuleSources(module: JpsModule, relativePath: String, onlyProductionSources: Boolean = false): Path? {
  for (type in rootTypeOrder) {
    for (root in module.sourceRoots) {
      if (type != root.rootType || (onlyProductionSources && !(root.rootType == JavaResourceRootType.RESOURCE || root.rootType == JavaSourceRootType.SOURCE))) {
        continue
      }
      val sourceFile = JpsJavaExtensionService.getInstance().findSourceFile(root, relativePath)
      if (sourceFile != null) {
        return sourceFile
      }
    }
  }
  return null
}

internal suspend fun resolveProjectDependencies(context: CompilationContext) {
  if (context.compilationData.projectDependenciesResolved) {
    Span.current().addEvent("project dependencies are already resolved")
  }
  else {
    spanBuilder("resolve project dependencies").use {
      JpsCompilationRunner(context).resolveProjectDependencies()
    }
  }
}

@Internal
suspend fun CompilationContext.hasModuleOutputPath(module: JpsModule, relativePath: String): Boolean {
  val output = getModuleOutputDir(module)

  val attributes = try {
    Files.readAttributes(output, BasicFileAttributes::class.java)
  }
  catch (_: FileSystemException) {
    return false
  }

  if (attributes.isDirectory) {
    return Files.exists(output.resolve(relativePath))
  }
  else if (attributes.isRegularFile && output.toString().endsWith(".jar")) {
    var found = false
    readZipFile(output) { name, _ ->
      if (name == relativePath) {
        found = true
        ZipEntryProcessorResult.STOP
      }
      else {
        ZipEntryProcessorResult.CONTINUE
      }
    }
    return found
  }
  else {
    throw IllegalStateException("Module '${module.name}' output is neither directory, nor jar $output")
  }
}

@Internal
suspend fun CompilationContext.getModuleOutputFileContent(module: JpsModule, relativePath: String, forTests: Boolean = false): ByteArray? {
  val output = getModuleOutputDir(module = module, forTests = forTests)
  val attributes = try {
    Files.readAttributes(output, BasicFileAttributes::class.java)
  }
  catch (_: FileSystemException) {
    return null
  }

  if (attributes.isDirectory) {
    val file = output.resolve(relativePath)
    try {
      return Files.readAllBytes(file)
    }
    catch (_: NoSuchFileException) {
      return null
    }
  }
  else if (attributes.isRegularFile && output.toString().endsWith("jar")) {
    var content: ByteArray? = null
    readZipFile(output) { name, dataSupplier ->
      if (name == relativePath) {
        val buffer = dataSupplier()
        val array = ByteArray(buffer.remaining())
        buffer.get(array)
        content = array
        ZipEntryProcessorResult.STOP
      }
      else {
        ZipEntryProcessorResult.CONTINUE
      }
    }
    return content
  }
  else {
    throw IllegalStateException("Module '${module.name}' output is neither directory, nor jar $output")
  }
}
