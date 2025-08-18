// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import com.dynatrace.hash4j.hashing.HashFunnel
import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.PathUtilRt
import com.intellij.util.lang.PathClassLoader
import com.intellij.util.lang.UrlClassLoader
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.protobuf.ProtoBuf
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildOptions.Companion.PROJECT_CLASSES_OUTPUT_DIRECTORY_PROPERTY
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.LibcImpl
import org.jetbrains.intellij.build.LinuxDistributionCustomizer
import org.jetbrains.intellij.build.MacDistributionCustomizer
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.PluginBuildDescriptor
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.ProprietaryBuildTools
import org.jetbrains.intellij.build.ScrambleTool
import org.jetbrains.intellij.build.SearchableOptionSetDescriptor
import org.jetbrains.intellij.build.WindowsDistributionCustomizer
import org.jetbrains.intellij.build.computeAppClassPath
import org.jetbrains.intellij.build.excludedLibJars
import org.jetbrains.intellij.build.generatePluginClassPath
import org.jetbrains.intellij.build.generatePluginClassPathFromPrebuiltPluginFiles
import org.jetbrains.intellij.build.getDevModeOrTestBuildDateInSeconds
import org.jetbrains.intellij.build.impl.ArchivedCompilationContext
import org.jetbrains.intellij.build.impl.BazelCompilationContext
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.jetbrains.intellij.build.impl.CompilationContextImpl
import org.jetbrains.intellij.build.impl.ModuleOutputPatcher
import org.jetbrains.intellij.build.impl.PLUGIN_CLASSPATH
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.asArchivedIfNeeded
import org.jetbrains.intellij.build.impl.collectIncludedPluginModules
import org.jetbrains.intellij.build.impl.collectPlatformModules
import org.jetbrains.intellij.build.impl.copyDistFiles
import org.jetbrains.intellij.build.impl.createIdeaPropertyFile
import org.jetbrains.intellij.build.impl.createPlatformLayout
import org.jetbrains.intellij.build.impl.generateRuntimeModuleRepositoryForDevBuild
import org.jetbrains.intellij.build.impl.getOsDistributionBuilder
import org.jetbrains.intellij.build.impl.getToolModules
import org.jetbrains.intellij.build.impl.isRunningFromBazelOut
import org.jetbrains.intellij.build.impl.layoutPlatformDistribution
import org.jetbrains.intellij.build.impl.productInfo.PRODUCT_INFO_FILE_NAME
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleOutputEntry
import org.jetbrains.intellij.build.jarCache.LocalDiskJarCacheManager
import org.jetbrains.intellij.build.postData
import org.jetbrains.intellij.build.readSearchableOptionIndex
import org.jetbrains.intellij.build.telemetry.TraceManager
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import org.jetbrains.intellij.build.writePluginClassPathHeader
import org.jetbrains.jps.model.artifact.JpsArtifactService
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.moveTo
import kotlin.time.Duration.Companion.seconds

data class BuildRequest(
  @JvmField val platformPrefix: String,
  @JvmField val additionalModules: List<String>,
  @JvmField val projectDir: Path,
  /** For a standalone frontend distribution where `platformPrefix` is "JetBrainsClient", specifies the platform prefix of its base IDE. */
  @JvmField val baseIdePlatformPrefixForFrontend: String? = null,
  @JvmField val devRootDir: Path = System.getProperty("idea.dev.root.dir")?.let { Path.of(it).normalize().toAbsolutePath() } ?: projectDir.resolve("out/dev-run"),
  @JvmField val jarCacheDir: Path = devRootDir.resolve("jar-cache"),
  @JvmField val productionClassOutput: Path = System.getenv("CLASSES_DIR")?.let { Path.of(it).normalize().toAbsolutePath() } ?: projectDir.resolve("out/classes/production"),
  @JvmField val keepHttpClient: Boolean = true,
  @JvmField val platformClassPathConsumer: ((mainClass: String, classPath: Set<Path>, runDir: Path) -> Unit)? = null,
  /**
   * If `true`, the dev build will include a [runtime module repository](psi_element://com.intellij.platform.runtime.repository). 
   * It is currently used only to run an instance of JetBrains Client from IDE's installation,
   * and its generation makes the project build a little longer, so it should be enabled only if needed.
   */
  @JvmField val generateRuntimeModuleRepository: Boolean = false,

  @JvmField val isUnpackedDist: Boolean = System.getProperty("idea.dev.build.unpacked").toBoolean(),
  @JvmField val scrambleTool: ScrambleTool? = null,

  @JvmField val writeCoreClasspath: Boolean = true,

  @JvmField val buildOptionsTemplate: BuildOptions? = null,

  @JvmField val tracer: Tracer? = null,

  @JvmField val os: OsFamily = OsFamily.currentOs
) {
  override fun toString(): String =
    buildString {
      append("BuildRequest(platformPrefix='$platformPrefix', ")
      if (baseIdePlatformPrefixForFrontend != null) {
        append("baseIdePlatformPrefixForFrontend='$baseIdePlatformPrefixForFrontend', ")
      }
      append("additionalModules=$additionalModules, ")
      append("productionClassOutput=$productionClassOutput, ")
      append("keepHttpClient=$keepHttpClient, ")
      append("generateRuntimeModuleRepository=$generateRuntimeModuleRepository")
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun buildProduct(request: BuildRequest, createProductProperties: suspend (CompilationContext) -> ProductProperties): Path {
  val rootDir = withContext(Dispatchers.IO) {
    val rootDir = request.devRootDir
    // if symlinked to RAM disk, use a real path for performance reasons and avoid any issues in ant/other code
    if (Files.exists(rootDir)) {
      // toRealPath must be called only on an existing file
      rootDir.toRealPath()
    }
    else {
      rootDir
    }
  }

  val classifier = computeAdditionalModulesFingerprint(request.additionalModules)
  val productDirNameWithoutClassifier = when (request.platformPrefix) {
    "Idea" -> "idea-community"
    "JetBrainsClient" -> "${request.baseIdePlatformPrefixForFrontend ?: ""}${request.platformPrefix}"
    else -> request.platformPrefix
  }
  val productDirName = (productDirNameWithoutClassifier + (if (System.getProperty("intellij.build.minimal").toBoolean()) "-ij-void" else "") + classifier).takeLast(255)

  val buildDir = withContext(Dispatchers.IO.limitedParallelism(4)) {
    val buildDir = rootDir.resolve(productDirName)
    // on start, delete everything to avoid stale data
    val files = try {
      Files.newDirectoryStream(buildDir).toList()
    }
    catch (_: NoSuchFileException) {
      Files.createDirectories(buildDir)
      return@withContext buildDir
    }

    for (child in files) {
      val fileName = child.fileName.toString()
      if (fileName != "log" && fileName != "bin") {
        launch {
          NioFiles.deleteRecursively(child)
        }
      }
    }
    buildDir
  }

  val runDir = buildDir
  val context = createBuildContext(createProductProperties, request, runDir, request.jarCacheDir, buildDir)
  if (request.os != OsFamily.currentOs) {
    context.options.targetOs = persistentListOf(request.os)
    context.options.targetArch = JvmArchitecture.currentJvmArch
  }
  compileIfNeeded(context)

  coroutineScope {
    val moduleOutputPatcher = ModuleOutputPatcher()

    val platformLayout = async(CoroutineName("create platform layout")) {
      spanBuilder("create platform layout").use {
        createPlatformLayout(context)
      }
    }

    val searchableOptionSet = getSearchableOptionSet(context)

    val platformDistributionEntriesDeferred = async(CoroutineName("platform distribution entries")) {
      launch(Dispatchers.IO) {
        // PathManager.getBinPath() is used as a working dir for maven
        val binDir = Files.createDirectories(runDir.resolve("bin"))
        val oldFiles = Files.newDirectoryStream(binDir).use { it.toCollection(HashSet()) }

        val libcImpl = LibcImpl.current(request.os)

        val osDistributionBuilder = getOsDistributionBuilder(request.os, libcImpl, context)
        if (osDistributionBuilder != null) {
          oldFiles.remove(osDistributionBuilder.writeVmOptions(binDir))
          // the file cannot be placed right into the distribution as it throws off home dir detection in `PathManager#getHomeDirFor`
          val productInfoDir = context.paths.tempDir.resolve("product-info").createDirectories()
          val productInfoFile = osDistributionBuilder.writeProductInfoFile(productInfoDir, JvmArchitecture.currentJvmArch)
          oldFiles.remove(productInfoFile.moveTo(binDir.resolve(PRODUCT_INFO_FILE_NAME), overwrite = true))
          NioFiles.deleteRecursively(productInfoDir)
        }

        val ideaPropertyFile = binDir.resolve(PathManager.PROPERTIES_FILE_NAME)
        Files.writeString(ideaPropertyFile, createIdeaPropertyFile(context))
        oldFiles.remove(ideaPropertyFile)

        for (oldFile in oldFiles) {
          NioFiles.deleteRecursively(oldFile)
        }
      }

      val platformLayoutAwaited = platformLayout.await()
      val (platformDistributionEntries, classPath) = spanBuilder("layout platform").use {
        layoutPlatform(runDir, platformLayoutAwaited, searchableOptionSet, context, moduleOutputPatcher)
      }

      if (request.writeCoreClasspath) {
        val excluded = excludedLibJars(context)
        val classPathString = classPath
          .asSequence()
          .filter { !excluded.contains(it.fileName.toString()) }
          .joinToString(separator = "\n")
        launch(Dispatchers.IO) {
          Files.writeString(runDir.resolve("core-classpath.txt"), classPathString)
        }
      }

      request.platformClassPathConsumer?.invoke(context.ideMainClassName, classPath, runDir)
      platformDistributionEntries
    }

    val artifactTask = launch {
      val artifactOutDir = request.projectDir.resolve("out/classes/artifacts").toString()
      for (artifact in JpsArtifactService.getInstance().getArtifacts(context.project)) {
        artifact.outputPath = "$artifactOutDir/${PathUtilRt.getFileName(artifact.outputPath)}"
      }
    }

    val pluginDistributionEntriesDeferred = async(CoroutineName("build plugins")) {
      buildPlugins(request, context, runDir, platformLayout, artifactTask, searchableOptionSet, platformDistributionEntriesDeferred, moduleOutputPatcher)
    }

    launch {
      val (pluginEntries, additionalEntries) = pluginDistributionEntriesDeferred.await()
      spanBuilder("generate plugin classpath").use(Dispatchers.IO) {
        val mainData = generatePluginClassPath(pluginEntries, moduleOutputPatcher)
        val additionalData = additionalEntries?.let { generatePluginClassPathFromPrebuiltPluginFiles(it) }

        val byteOut = ByteArrayOutputStream()
        val out = DataOutputStream(byteOut)
        val pluginCount = pluginEntries.size + (additionalEntries?.size ?: 0)
        platformDistributionEntriesDeferred.join()
        writePluginClassPathHeader(out, isJarOnly = !request.isUnpackedDist, pluginCount, moduleOutputPatcher, context)
        out.write(mainData)
        additionalData?.let { out.write(it) }
        out.close()
        Files.write(runDir.resolve(PLUGIN_CLASSPATH), byteOut.toByteArray())
      }
    }

    if (context.generateRuntimeModuleRepository) {
      launch {
        val allDistributionEntries =
          platformDistributionEntriesDeferred.await().asSequence() +
          pluginDistributionEntriesDeferred.await().first.asSequence().flatMap { it.second }
        spanBuilder("generate runtime repository").use(Dispatchers.IO) {
          generateRuntimeModuleRepositoryForDevBuild(allDistributionEntries, runDir, context)
        }
      }
    }

    launch {
      computeIdeFingerprint(platformDistributionEntriesDeferred, pluginDistributionEntriesDeferred, runDir, homePath = request.projectDir)
    }

    launch(Dispatchers.IO) {
      // ensure platform dist files added to the list
      platformDistributionEntriesDeferred.await()
      // ensure plugin dist files added to the list
      pluginDistributionEntriesDeferred.await()

      spanBuilder("scramble platform").use{
        request.scrambleTool?.scramble(platformLayout.await(), context)
      }
      copyDistFiles(context, runDir, request.os, JvmArchitecture.currentJvmArch, LibcImpl.current(OsFamily.currentOs))
    }
  }.invokeOnCompletion {
    // close debug logging to prevent locking of the output directory on Windows
    context.messages.close()
  }
  return runDir
}

private suspend fun getSearchableOptionSet(context: BuildContext): SearchableOptionSetDescriptor? = withContext(Dispatchers.IO) {
  try {
    readSearchableOptionIndex(context.paths.searchableOptionDir)
  }
  catch (_: NoSuchFileException) {
    null
  }
}

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalSerializationApi::class)
private suspend fun compileIfNeeded(context: BuildContext) {
  val port = System.getProperty("compile.server.port")?.toIntOrNull()
  val project = System.getProperty("compile.server.project")
  val token = System.getProperty("compile.server.token")
  if (port == null || project == null || token == null) {
    return
  }

  val modulesToCompile = spanBuilder("collect modules to compile").use {
    val result = collectModulesToCompileForDistribution(context)
    JpsJavaExtensionService.getInstance().enumerateDependencies(listOf(context.findRequiredModule("intellij.platform.bootstrap.dev")))
      .recursively()
      .productionOnly()
      .withoutLibraries()
      .processModules { result.remove(it.name) }
    result
  }

  val url = "http://127.0.0.1:$port/devkit/make?project-hash=$project&token=$token"
  TraceManager.flush()
  spanBuilder("compile modules").setAttribute("url", url).use {
    coroutineScope {
      val task = launch {
        postData(url, ProtoBuf.encodeToByteArray(SetSerializer(String.serializer()), modulesToCompile))
      }

      var count = 0
      while (task.isActive) {
        select<Unit> {
          task.onJoin {
            return@onJoin
          }
          onTimeout((if (count != 0 && count < 100) 2 else 6).seconds) {
            if (count == 0) {
              println("compiling")
            }
            else {
              print('.')
            }
            count++
          }
        }
      }
    }
  }
}

private suspend fun collectModulesToCompileForDistribution(context: BuildContext): MutableSet<String> {
  val result = java.util.LinkedHashSet<String>()
  val productLayout = context.productProperties.productLayout
  collectIncludedPluginModules(context.getBundledPluginModules(), result, context)
  collectPlatformModules(to = result)
  result.addAll(productLayout.productApiModules)
  result.addAll(productLayout.productImplementationModules)
  result.addAll(getToolModules())
  if (context.isEmbeddedFrontendEnabled) {
    result.add(context.productProperties.embeddedFrontendRootModule!!)
  }
  result.add("intellij.idea.community.build.tasks")
  result.add("intellij.platform.images.build")
  result.removeAll(productLayout.excludedModuleNames)

  context.proprietaryBuildTools.scrambleTool?.let {
    result.addAll(it.additionalModulesToCompile)
  }

  val productProperties = context.productProperties
  result.add(productProperties.applicationInfoModule)

  val mavenArtifacts = productProperties.mavenArtifacts
  result.addAll(mavenArtifacts.additionalModules)
  result.addAll(mavenArtifacts.squashedModules)
  result.addAll(mavenArtifacts.proprietaryModules)

  result.addAll(productProperties.modulesToCompileTests)
  result.add("intellij.tools.launcherGenerator")
  return result
}

private suspend fun computeIdeFingerprint(
  platformDistributionEntriesDeferred: Deferred<List<DistributionFileEntry>>,
  pluginDistributionEntriesDeferred: Deferred<Pair<List<Pair<PluginBuildDescriptor, List<DistributionFileEntry>>>, List<Pair<Path, List<Path>>>?>>,
  runDir: Path,
  homePath: Path,
) {
  val hasher = Hashing.xxh3_64().hashStream()
  val debug = StringBuilder()

  val distributionFileEntries = platformDistributionEntriesDeferred.await()
  hasher.putInt(distributionFileEntries.size)
  debug.append(distributionFileEntries.size).append('\n')
  for (entry in distributionFileEntries) {
    hasher.putLong(entry.hash)

    var path = entry.path
    if (path.startsWith(homePath)) {
      path = homePath.relativize(path)
    }
    debug.append(java.lang.Long.toUnsignedString(entry.hash, Character.MAX_RADIX)).append(" ").append(path).append('\n')
  }

  val (pluginDistributionEntries, _) = pluginDistributionEntriesDeferred.await()
  hasher.putInt(pluginDistributionEntries.size)
  for ((plugin, entries) in pluginDistributionEntries) {
    hasher.putInt(entries.size)

    debug.append('\n').append(plugin.layout.mainModule).append('\n')
    for (entry in entries) {
      hasher.putLong(entry.hash)
      debug.append("  ").append(java.lang.Long.toUnsignedString(entry.hash, Character.MAX_RADIX)).append(" ").append(entry.path).append('\n')
    }
  }

  val fingerprint = java.lang.Long.toUnsignedString(hasher.asLong, Character.MAX_RADIX)
  withContext(Dispatchers.IO) {
    Files.writeString(runDir.resolve("fingerprint.txt"), fingerprint)
    Files.writeString(runDir.resolve("fingerprint-debug.txt"), debug)
  }
  Span.current().addEvent("IDE fingerprint: $fingerprint")
}

private suspend fun createBuildContext(
  createProductProperties: suspend (CompilationContext) -> ProductProperties,
  request: BuildRequest,
  runDir: Path,
  jarCacheDir: Path,
  buildDir: Path,
): BuildContext {
  return coroutineScope {
    val buildOptionsTemplate = request.buildOptionsTemplate
    val useCompiledClassesFromProjectOutput =
      buildOptionsTemplate == null ||
      (buildOptionsTemplate.useCompiledClassesFromProjectOutput && buildOptionsTemplate.unpackCompiledClassesArchives)
    val classOutDir = if (useCompiledClassesFromProjectOutput) {
      request.productionClassOutput.parent
    }
    else {
      buildOptionsTemplate.classOutDir?.let { Path.of(it) }
      ?: System.getProperty(PROJECT_CLASSES_OUTPUT_DIRECTORY_PROPERTY)?.let { Path.of(it) }
      ?: request.productionClassOutput.parent
    }

    // load project is executed as part of compilation context creation - ~1 second
    val compilationContextDeferred = async(CoroutineName("create build context")) {
      spanBuilder("create build context").use {
        // we cannot inject a proper build time as it is a part of resources, so, set to the first day of the current month
        val options = BuildOptions(
          jarCacheDir,
          buildDateInSeconds = getDevModeOrTestBuildDateInSeconds(),
          printFreeSpace = false,
          validateImplicitPlatformModule = false,
          skipDependencySetup = true,

          useCompiledClassesFromProjectOutput = useCompiledClassesFromProjectOutput,
          pathToCompiledClassesArchivesMetadata = buildOptionsTemplate?.pathToCompiledClassesArchivesMetadata?.takeIf { !useCompiledClassesFromProjectOutput },
          pathToCompiledClassesArchive = buildOptionsTemplate?.pathToCompiledClassesArchive?.takeIf { !useCompiledClassesFromProjectOutput },
          unpackCompiledClassesArchives = buildOptionsTemplate?.unpackCompiledClassesArchives?.takeIf { !useCompiledClassesFromProjectOutput } ?: true,
          classOutDir = classOutDir.toString(),

          validateModuleStructure = false,
          cleanOutDir = false,
          outRootDir = runDir,
          compilationLogEnabled = false,
          logDir = buildDir.resolve("log"),

          isUnpackedDist = request.isUnpackedDist,
        )
        options.setTargetOsAndArchToCurrent()
        options.buildStepsToSkip += listOf(
          BuildOptions.PREBUILD_SHARED_INDEXES,
          BuildOptions.FUS_METADATA_BUNDLE_STEP,
          BuildOptions.PROVIDED_MODULES_LIST_STEP,
        )

        if (request.isUnpackedDist && options.enableEmbeddedFrontend) {
          options.enableEmbeddedFrontend = false
        }

        options.generateRuntimeModuleRepository = options.generateRuntimeModuleRepository && request.generateRuntimeModuleRepository

        buildOptionsTemplate?.let { template ->
          options.buildNumber = template.buildNumber
          options.isInDevelopmentMode = template.isInDevelopmentMode
          options.isTestBuild = template.isTestBuild
        }

        val tempDir = buildDir.resolve("temp")
        val result = BuildPaths(
          communityHomeDirRoot = COMMUNITY_ROOT,
          buildOutputDir = runDir,
          logDir = options.logDir!!,
          projectHome = request.projectDir,
          tempDir = tempDir,
          artifactDir = buildDir.resolve("artifacts"),
          searchableOptionDir = request.projectDir.resolve("out/dev-data/searchable-options"),
        )
        result.distAllDir = runDir
        Files.createDirectories(tempDir)

        CompilationContextImpl.createCompilationContext(
          projectHome = request.projectDir,
          buildOutputRootEvaluator = { _ -> runDir },
          setupTracer = false,
          enableCoroutinesDump = false,  // will be enabled later in [com.intellij.platform.ide.bootstrap.enableJstack] instead
          options = options,
          customBuildPaths = result,
        )
        .let { if (options.unpackCompiledClassesArchives) it else ArchivedCompilationContext(it) }
        .let { if (!isRunningFromBazelOut()) it else BazelCompilationContext(it) }
      }
    }

    val jarCacheManager = LocalDiskJarCacheManager(cacheDir = request.jarCacheDir, productionClassOutDir = classOutDir.resolve("production"))
    launch {
      jarCacheManager.cleanup()
    }

    val compilationContext = compilationContextDeferred.await().asArchivedIfNeeded

    val productProperties = async(CoroutineName("create product properties")) {
      createProductProperties(compilationContext)
    }

    BuildContextImpl(
      compilationContext, productProperties.await(), WindowsDistributionCustomizer(), LinuxDistributionCustomizer(), MacDistributionCustomizer(),
      proprietaryBuildTools = if (request.scrambleTool == null) ProprietaryBuildTools.DUMMY else ProprietaryBuildTools(
        ProprietaryBuildTools.DUMMY_SIGN_TOOL, request.scrambleTool, featureUsageStatisticsProperties = null, artifactsServer = null, licenseServerHost = null
      ),
      jarCacheManager = jarCacheManager,
    )
  }
}

internal suspend fun createProductProperties(productConfiguration: ProductConfiguration, compilationContext: CompilationContext, request: BuildRequest): ProductProperties {
  val classPathFiles = buildList {
    for (moduleName in getBuildModules(productConfiguration)) {
      addAll(compilationContext.getModuleOutputRoots(compilationContext.findRequiredModule(moduleName)))
    }
  }

  val classLoader = spanBuilder("create product properties classloader").use {
    PathClassLoader(UrlClassLoader.build().files(classPathFiles).parent(BuildRequest::class.java.classLoader))
  }

  return spanBuilder("create product properties").use {
    val className = if (System.getProperty("intellij.build.minimal").toBoolean()) {
      "org.jetbrains.intellij.build.IjVoidProperties"
    }
    else {
      productConfiguration.className
    }
    val productPropertiesClass = try {
      classLoader.loadClass(className)
    }
    catch (e: ClassNotFoundException) {
      val classPathString = classPathFiles.joinToString(separator = "\n") { file ->
        "$file (" + (if (Files.isDirectory(file)) "dir" else if (Files.exists(file)) "exists" else "doesn't exist") + ")"
      }
      val projectPropertiesPath = getProductPropertiesPath(request.projectDir)
      throw RuntimeException("cannot create product properties, className=$className, projectPropertiesPath=$projectPropertiesPath, classPath=$classPathString, ", e)
    }

    val lookup = MethodHandles.lookup()
    try {
      lookup.findConstructor(productPropertiesClass, MethodType.methodType(Void.TYPE)).invoke()
    }
    catch (_: NoSuchMethodException) {
      lookup
        .findConstructor(productPropertiesClass, MethodType.methodType(Void.TYPE, Path::class.java))
        .invoke(if (request.platformPrefix == "Idea") getCommunityHomePath(request.projectDir) else request.projectDir)
    } as ProductProperties
  }
}

private fun getBuildModules(productConfiguration: ProductConfiguration): Sequence<String> = sequenceOf("intellij.idea.community.build") + productConfiguration.modules.asSequence()

private suspend fun layoutPlatform(
  runDir: Path,
  platformLayout: PlatformLayout,
  searchableOptionSet: SearchableOptionSetDescriptor?,
  context: BuildContext,
  moduleOutputPatcher: ModuleOutputPatcher,
): Pair<List<DistributionFileEntry>, Set<Path>> {
  val entries = layoutPlatformDistribution(moduleOutputPatcher, runDir, platformLayout, searchableOptionSet, copyFiles = true, context)
  lateinit var sortedClassPath: Set<Path>
  coroutineScope {
    launch {
      val classPath = LinkedHashSet<Path>()
      val libDir = runDir.resolve("lib")
      for (entry in entries) {
        val file = entry.path
        // exclude files like ext/platform-main.jar - if a file in lib, take only direct children in an account
        if ((entry.relativeOutputFile ?: "").contains('/')) {
          continue
        }
        if (entry is ModuleOutputEntry &&
            (entry.moduleName == "intellij.platform.testFramework" || entry.moduleName.startsWith("intellij.platform.unitTestMode"))) {
          continue
        }

        classPath.add(file)
      }
      sortedClassPath = computeAppClassPath(libDir = libDir, existing = classPath)
    }

    launch(Dispatchers.IO) {
      Files.writeString(runDir.resolve("build.txt"), context.fullBuildNumber)
    }
  }
  return entries to sortedClassPath
}

private fun computeAdditionalModulesFingerprint(additionalModules: List<String>): String {
  if (additionalModules.isEmpty()) {
    return ""
  }
  else {
    val hash = Hashing.xxh3_64().hashStream()
    hash.putUnorderedIterable(additionalModules, HashFunnel.forString(), Hashing.xxh3_64())
    return "-" + additionalModules.joinToString(separator = "-") { it.removePrefix("intellij.").take(4) } + "-" +
           java.lang.Long.toUnsignedString(hash.asLong, Character.MAX_RADIX)
  }
}

private fun getCommunityHomePath(homePath: Path): Path =
  if (Files.isDirectory(homePath.resolve("community"))) homePath.resolve("community") else homePath
