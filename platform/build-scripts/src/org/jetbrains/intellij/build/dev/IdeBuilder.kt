// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import com.dynatrace.hash4j.hashing.HashFunnel
import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.ijent.community.buildConstants.isMultiRoutingFileSystemEnabledForProduct
import com.intellij.util.lang.PathClassLoader
import com.intellij.util.lang.UrlClassLoader
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.LibcImpl
import org.jetbrains.intellij.build.LinuxDistributionCustomizer
import org.jetbrains.intellij.build.MacDistributionCustomizer
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.ProprietaryBuildTools
import org.jetbrains.intellij.build.ScrambleTool
import org.jetbrains.intellij.build.SearchableOptionSetDescriptor
import org.jetbrains.intellij.build.WindowsDistributionCustomizer
import org.jetbrains.intellij.build.classPath.generateClassPathByLayoutReport
import org.jetbrains.intellij.build.classPath.generateCoreClasspathFromPlugins
import org.jetbrains.intellij.build.classPath.generatePluginClassPath
import org.jetbrains.intellij.build.classPath.generatePluginClassPathFromPrebuiltPluginFiles
import org.jetbrains.intellij.build.classPath.writePluginClassPathHeader
import org.jetbrains.intellij.build.getDevModeOrTestBuildDateInSeconds
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.jetbrains.intellij.build.impl.ModuleOutputPatcher
import org.jetbrains.intellij.build.impl.PLUGIN_CLASSPATH
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.copyDistFiles
import org.jetbrains.intellij.build.impl.createCompilationContext
import org.jetbrains.intellij.build.impl.createIdeaPropertyFile
import org.jetbrains.intellij.build.impl.createPlatformLayout
import org.jetbrains.intellij.build.impl.generateRuntimeModuleRepositoryForDevBuild
import org.jetbrains.intellij.build.impl.getOsDistributionBuilder
import org.jetbrains.intellij.build.impl.layoutPlatformDistribution
import org.jetbrains.intellij.build.impl.normalizeCompilationContextForBuild
import org.jetbrains.intellij.build.impl.productInfo.PRODUCT_INFO_FILE_NAME
import org.jetbrains.intellij.build.impl.projectStructureMapping.ContentReport
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.jarCache.LocalDiskJarCacheManager
import org.jetbrains.intellij.build.normalizeCompiledClassesOptions
import org.jetbrains.intellij.build.productLayout.discovery.ProductConfiguration
import org.jetbrains.intellij.build.readSearchableOptionIndex
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.lang.Long
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.Boolean
import kotlin.Int
import kotlin.OptIn
import kotlin.RuntimeException
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.also
import kotlin.io.path.createDirectories
import kotlin.io.path.moveTo
import kotlin.let
import kotlin.text.StringBuilder
import kotlin.text.buildString
import kotlin.text.removePrefix
import kotlin.text.take
import kotlin.text.takeLast
import kotlin.text.toBoolean
import kotlin.time.Duration.Companion.hours

private const val maxWindowsPathLengthForIDERootToBeAbleToRunRiderBackend: Int = 64

data class BuildRequest(
  @JvmField val platformPrefix: String,
  @JvmField val additionalModules: List<String>,
  @JvmField val projectDir: Path,
  /** For a standalone frontend distribution where `platformPrefix` is "JetBrainsClient", specifies the platform prefix of its base IDE. */
  @JvmField val baseIdePlatformPrefixForFrontend: String? = null,
  @JvmField val devRootDir: Path = System.getProperty("idea.dev.root.dir")?.let { Path.of(it).normalize().toAbsolutePath() } ?: projectDir.resolve("out/dev-run"),
  @JvmField val jarCacheDir: Path = devRootDir.resolve("jar-cache"),
  @JvmField val classesOutputDirectory: Path? = null,
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

  @JvmField val tracer: Tracer? = null,

  @JvmField val os: OsFamily = OsFamily.currentOs,

  @JvmField val isBootClassPathCorrect: Boolean = false,
) {
  override fun toString(): String {
    return buildString {
      append("DevBuildRequest(platformPrefix='$platformPrefix', ")
      if (baseIdePlatformPrefixForFrontend != null) {
        append("baseIdePlatformPrefixForFrontend='$baseIdePlatformPrefixForFrontend', ")
      }
      append("additionalModules=$additionalModules, ")
      if (classesOutputDirectory != null) {
        append("classesOutputDirectory=$classesOutputDirectory, ")
      }
      append("keepHttpClient=$keepHttpClient, ")
      append("generateRuntimeModuleRepository=$generateRuntimeModuleRepository")
    }
  }
}

private fun defaultClassesOutputDirectory(projectDir: Path): Path {
  return System.getenv("CLASSES_DIR")?.let { Path.of(it).normalize().toAbsolutePath().parent } ?: projectDir.resolve("out/classes")
}

internal fun resolveProjectClassesOutputDirectory(request: BuildRequest, buildOptionsTemplate: BuildOptions): Path {
  return request.classesOutputDirectory ?: buildOptionsTemplate.classOutDir?.let { Path.of(it) } ?: defaultClassesOutputDirectory(request.projectDir)
}

@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun buildProductFromProject(
  request: BuildRequest,
  productConfiguration: ProductConfiguration,
  buildOptionsTemplate: BuildOptions,
): Path {
  return buildProduct(request = request) { buildDir ->
    createBuildContextFromProject(
      productConfiguration = productConfiguration,
      request = request,
      buildDir = buildDir,
      buildOptionsTemplate = buildOptionsTemplate,
      scope = this,
    )
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun buildProduct(request: BuildRequest, createBuildContext: suspend CoroutineScope.(buildDir: Path) -> BuildContext): Path {
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
  val productDirSuffix = when {
    System.getProperty("intellij.build.minimal").toBoolean() -> "-ij-void"
    request.scrambleTool != null -> "-scrambled"
    else -> ""
  }
  val productDirName = (productDirNameWithoutClassifier + productDirSuffix + classifier).takeLast(maxWindowsPathLengthForIDERootToBeAbleToRunRiderBackend)
  
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
  var contextToClose: BuildContext? = null
  try {
    coroutineScope {
      val context = createBuildContext(buildDir)
      contextToClose = context
      launch(Dispatchers.IO + CoroutineName("cleanup jar cache")) {
        context.cleanupJarCache()
      }
      if (request.os != OsFamily.currentOs) {
        context.options.targetOs = persistentListOf(request.os)
        context.options.targetArch = JvmArchitecture.currentJvmArch
      }

      val moduleOutputPatcher = ModuleOutputPatcher()

      val platformLayout = async(CoroutineName("create platform layout")) {
        spanBuilder("create platform layout").use {
          createPlatformLayout(context)
        }
      }

      val searchableOptionSetDeferred = async(CoroutineName("read searchable options")) {
        getSearchableOptionSet(context)
      }
      val platformLayoutResultDeferred = async(CoroutineName("platform distribution entries")) {
        val searchableOptionSet = searchableOptionSetDeferred.await()
        launch(Dispatchers.IO) {
          // PathManager.getBinPath() is used as a working dir for maven
          val binDir = Files.createDirectories(runDir.resolve("bin"))
          val oldFiles = Files.newDirectoryStream(binDir).use { it.toCollection(HashSet()) }

          val libcImpl = LibcImpl.current(request.os)

          val osDistributionBuilder = getOsDistributionBuilder(os = request.os, libcImpl = libcImpl, context = context)
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
        spanBuilder("layout platform").use {
          layoutPlatform(
            runDir = runDir,
            platformLayout = platformLayoutAwaited,
            searchableOptionSet = searchableOptionSet,
            context = context,
            moduleOutputPatcher = moduleOutputPatcher,
            request = request,
          )
        }
      }

      val pluginDistributionEntriesDeferred = async(CoroutineName("build plugins")) {
        buildPluginsForDevMode(
          request = request,
          context = context,
          runDir = runDir,
          platformLayout = platformLayout,
          searchableOptionSet = searchableOptionSetDeferred.await(),
          platformEntriesProvider = { platformLayoutResultDeferred.await().distributionEntries },
        )
      }

      // write and update core classpath from platform and plugins distribution
      launch(CoroutineName("compute core classpath")) {
        val platformClasspath = platformLayoutResultDeferred.await().coreClassPath
        val pluginDistributionEntities = pluginDistributionEntriesDeferred.await().pluginEntries
        val platformLayoutAwaited = platformLayout.await()
        val coreClasspathFromPlugins = generateCoreClasspathFromPlugins(
          platformLayout = platformLayoutAwaited,
          pluginEntities = pluginDistributionEntities,
          context = context
        )
        val classPath = platformClasspath + coreClasspathFromPlugins

        if (request.writeCoreClasspath) {
          val classPathString = classPath.joinToString(separator = "\n")
          launch(Dispatchers.IO) {
            Files.writeString(runDir.resolve("core-classpath.txt"), classPathString)
          }
        }

        request.platformClassPathConsumer?.invoke(context.ideMainClassName, classPath, runDir)
      }

      launch(CoroutineName("compute IDE fingerprint")) {
        computeIdeFingerprint(
          platformDistributionEntriesDeferred = platformLayoutResultDeferred,
          pluginDistributionEntriesDeferred = pluginDistributionEntriesDeferred,
          runDir = runDir,
          homePath = request.projectDir,
        )
      }

      launch(CoroutineName("post-process distribution")) {
        // ensure platform dist files added to the list
        val platformFileEntries = platformLayoutResultDeferred.await().distributionEntries
        // ensure plugin dist files added to the list
        val pluginDistributionEntries = pluginDistributionEntriesDeferred.await()
        val platformLayout = platformLayout.await()

        // must be before generatePluginClassPath, because we modify plugin descriptors (e.g., rename classes)
        request.scrambleTool?.let { scrambleTool ->
          spanBuilder("scramble platform").use {
            scrambleTool.scramble(platformLayout = platformLayout, platformContent = platformFileEntries, context = context)
          }
        }

        val pluginClasspathJob = launch {
          val (pluginEntries, additionalEntries) = pluginDistributionEntries
          val cachedDescriptorContainer = platformLayout.descriptorCacheContainer
          spanBuilder("generate plugin classpath").use(Dispatchers.IO) {
            val mainData = generatePluginClassPath(
              pluginEntries = pluginEntries,
              descriptorFileProvider = cachedDescriptorContainer,
              platformLayout = platformLayout,
              context = context,
            )
            val additionalData = additionalEntries?.let { generatePluginClassPathFromPrebuiltPluginFiles(it) }

            val byteOut = ByteArrayOutputStream()
            val out = DataOutputStream(byteOut)
            val pluginCount = pluginEntries.size + (additionalEntries?.size ?: 0)
            platformLayoutResultDeferred.join()
            writePluginClassPathHeader(
              out = out,
              isJarOnly = !request.isUnpackedDist,
              pluginCount = pluginCount,
              platformLayout = platformLayout,
              descriptorCacheContainer = cachedDescriptorContainer,
              context = context
            )
            out.write(mainData)
            additionalData?.let { out.write(it) }
            out.close()
            Files.write(runDir.resolve(PLUGIN_CLASSPATH), byteOut.toByteArray())
          }
        }
        if (context.generateRuntimeModuleRepository) {
          launch(CoroutineName("generate runtime repository")) {
            val contentReport = ContentReport(
              platform = platformFileEntries,
              bundledPlugins = pluginDistributionEntries.pluginEntries,
              nonBundledPlugins = emptyList()
            )
            pluginClasspathJob.join() //this is necessary to have full data in DescriptorCacheContainer

            spanBuilder("generate runtime repository").use(Dispatchers.IO) {
              generateRuntimeModuleRepositoryForDevBuild(
                contentReport = contentReport,
                targetDirectory = runDir,
                context = context,
                platformLayout = platformLayout,
              )
            }
          }
        }

        withContext(Dispatchers.IO) {
          context.productProperties.copyAdditionalOsSpecificFiles(
            runDir = runDir,
            os = request.os,
            arch = JvmArchitecture.currentJvmArch,
            context = context
          )
          copyDistFiles(
            newDir = runDir,
            os = request.os,
            arch = JvmArchitecture.currentJvmArch,
            libcImpl = LibcImpl.current(request.os),
            context = context,
          )
        }
      }
    }
  }
  finally {
    // close debug logging to prevent locking of the output directory on Windows
    contextToClose?.messages?.close()
  }
  return runDir
}

private suspend fun getSearchableOptionSet(context: CompilationContext): SearchableOptionSetDescriptor? {
  return withContext(Dispatchers.IO) {
    try {
      readSearchableOptionIndex(context.paths.searchableOptionDir)
    }
    catch (_: NoSuchFileException) {
      null
    }
  }
}

private suspend fun computeIdeFingerprint(
  platformDistributionEntriesDeferred: Deferred<PlatformLayoutResult>,
  pluginDistributionEntriesDeferred: Deferred<PluginsLayoutResult>,
  runDir: Path,
  homePath: Path,
) {
  val hasher = Hashing.xxh3_64().hashStream()
  val debug = if (System.getProperty("intellij.build.fingerprint.debug").toBoolean()) StringBuilder() else null

  fun relativePath(path: Path): Path = when {
    path.startsWith(runDir) -> runDir.relativize(path)
    path.startsWith(homePath) -> homePath.relativize(path)
    else -> path
  }

  val distributionFileEntries = platformDistributionEntriesDeferred.await().distributionEntries
  hasher.putInt(distributionFileEntries.size)
  debug?.append(distributionFileEntries.size)?.append('\n')
  for (entry in distributionFileEntries) {
    hasher.putLong(entry.hash)
    debug?.append(Long.toUnsignedString(entry.hash, Character.MAX_RADIX))?.append(" ")?.append(relativePath(entry.path))?.append('\n')
  }

  val pluginDistributionEntries = pluginDistributionEntriesDeferred.await().pluginEntries
  hasher.putInt(pluginDistributionEntries.size)
  for (plugin in pluginDistributionEntries) {
    hasher.putInt(plugin.distribution.size)

    debug?.append('\n')?.append(plugin.layout.mainModule)?.append('\n')
    for (entry in plugin.distribution) {
      hasher.putLong(entry.hash)
      debug?.append("  ")?.append(Long.toUnsignedString(entry.hash, Character.MAX_RADIX))?.append(" ")?.append(relativePath(entry.path))?.append('\n')
    }
  }

  val fingerprint = Long.toUnsignedString(hasher.asLong, Character.MAX_RADIX)
  withContext(Dispatchers.IO) {
    Files.writeString(runDir.resolve("fingerprint.txt"), fingerprint)
    debug?.let { Files.writeString(runDir.resolve("fingerprint-debug.txt"), it) }
  }
  Span.current().addEvent("IDE fingerprint: $fingerprint")
}

private suspend fun createBuildContextFromProject(
  productConfiguration: ProductConfiguration,
  request: BuildRequest,
  buildDir: Path,
  buildOptionsTemplate: BuildOptions,
  scope: CoroutineScope,
): BuildContext {
  val options = createProjectDevBuildOptions(request = request, buildDir = buildDir, buildOptionsTemplate = buildOptionsTemplate)

  val buildPaths = createDevBuildPaths(projectDir = request.projectDir, buildDir = buildDir, logDir = options.logDir!!)
  val compilationContext = normalizeCompilationContextForBuild(
    context = createCompilationContext(
      projectHome = request.projectDir,
      buildOutputRootEvaluator = { _ -> buildDir },
      setupTracer = false,
      // will be enabled later in [com.intellij.platform.ide.bootstrap.enableJstack] instead
      enableCoroutinesDump = false,
      options = options,
      customBuildPaths = buildPaths,
    ),
    scope = scope,
  )
  val productProperties = createProductProperties(
    productConfiguration = productConfiguration,
    outputProvider = compilationContext.outputProvider,
    projectDir = request.projectDir,
    platformPrefix = request.platformPrefix,
  )
  return createDevBuildContext(
    compilationContext = compilationContext,
    productProperties = productProperties,
    request = request,
  )
}

@VisibleForTesting
internal fun createProjectDevBuildOptions(request: BuildRequest, buildDir: Path, buildOptionsTemplate: BuildOptions): BuildOptions {
  val classesOutputDirectory = resolveProjectClassesOutputDirectory(request, buildOptionsTemplate)
  val options = buildOptionsTemplate.copy(
    classOutDir = classesOutputDirectory.toString(),
  ).normalizeCompiledClassesOptions(
    defaultClassesOutputDirectory = classesOutputDirectory,
  ).copy(
    jarCacheDir = request.jarCacheDir,
    buildDateInSeconds = getDevModeOrTestBuildDateInSeconds(),
    printFreeSpace = false,
    validateImplicitPlatformModule = false,
    skipDependencySetup = true,
    skipCheckOutputOfPluginModules = true,
    validateModuleStructure = false,
    cleanOutDir = false,
    outRootDir = buildDir,
    compilationLogEnabled = false,
    logDir = buildDir.resolve("log"),
    isUnpackedDist = request.isUnpackedDist,
  )
  configureDevModeBuildOptions(options = options, request = request, buildOptionsTemplate = buildOptionsTemplate)
  return options
}

internal fun configureDevModeBuildOptions(options: BuildOptions, request: BuildRequest, buildOptionsTemplate: BuildOptions) {
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
  options.buildNumber = buildOptionsTemplate.buildNumber
  options.isInDevelopmentMode = buildOptionsTemplate.isInDevelopmentMode
  options.isTestBuild = buildOptionsTemplate.isTestBuild
}

internal fun createDevBuildPaths(projectDir: Path, buildDir: Path, logDir: Path): BuildPaths {
  val tempDir = buildDir.resolve("temp")
  Files.createDirectories(tempDir)

  return BuildPaths(
    communityHomeDirRoot = BuildPaths.COMMUNITY_ROOT,
    buildOutputDir = buildDir,
    logDir = logDir,
    projectHome = projectDir,
    tempDir = tempDir,
    artifactDir = buildDir.resolve("artifacts"),
    searchableOptionDir = projectDir.resolve("out/dev-data/searchable-options"),
  ).also {
    it.distAllDir = buildDir
  }
}

internal fun createDevBuildContext(
  compilationContext: CompilationContext,
  productProperties: ProductProperties,
  request: BuildRequest,
): BuildContextImpl {
  return BuildContextImpl(
    compilationContext = compilationContext,
    productProperties = productProperties,
    windowsDistributionCustomizer = object : WindowsDistributionCustomizer() {},
    linuxDistributionCustomizer = LinuxDistributionCustomizer(),
    macDistributionCustomizer = MacDistributionCustomizer(),
    proprietaryBuildTools = if (request.scrambleTool == null) {
      ProprietaryBuildTools.DUMMY
    }
    else {
      ProprietaryBuildTools(
        signTool = ProprietaryBuildTools.DUMMY_SIGN_TOOL,
        scrambleTool = request.scrambleTool,
        featureUsageStatisticsProperties = null,
        artifactsServer = null,
        licenseServerHost = null,
      )
    },
    jarCacheManager = LocalDiskJarCacheManager(
      cacheDir = request.jarCacheDir,
      classesOutputDirectory = compilationContext.classesOutputDirectory,
      maxAccessTimeAge = compilationContext.options.jarCacheMaxAccessAge,
      cleanupInterval = 1.hours,
    ),
  )
}

internal suspend fun createProductProperties(
  productConfiguration: ProductConfiguration,
  outputProvider: ModuleOutputProvider,
  projectDir: Path,
  platformPrefix: String?,
): ProductProperties {
  val classPathFiles = getBuildModules(productConfiguration)
    .flatMap { outputProvider.getModuleOutputRoots(outputProvider.findRequiredModule(it)) }
    .toList()

  @Suppress("SimpleRedundantLet")
  (ProductConfiguration::class.java.classLoader as? PathClassLoader)?.let {
    it.getClassPath().addFiles(classPathFiles)
  }

  val classLoader = spanBuilder("create product properties classloader").use {
    PathClassLoader(UrlClassLoader.build().files(classPathFiles).parent(BuildRequest::class.java.classLoader))
  }

  val className = if (System.getProperty("intellij.build.minimal").toBoolean()) {
    "org.jetbrains.intellij.build.IjVoidProperties"
  }
  else {
    productConfiguration.className
  }
  return spanBuilder("create product properties").setAttribute("className", className).use {
    doCreateProductProperties(classLoader = classLoader, className = className, classPathFiles = classPathFiles, projectDir = projectDir, platformPrefix = platformPrefix)
  }
}

private val lookup = MethodHandles.lookup()

private fun doCreateProductProperties(
  classLoader: PathClassLoader,
  className: String,
  classPathFiles: List<Path>,
  projectDir: Path,
  platformPrefix: String?,
): ProductProperties {
  val productPropertiesClass = try {
    classLoader.loadClass(className)
  }
  catch (e: ClassNotFoundException) {
    val classPathString = classPathFiles.joinToString(separator = "\n") { file ->
      "$file (" + (if (Files.isDirectory(file)) "dir" else if (Files.exists(file)) "exists" else "doesn't exist") + ")"
    }
    val projectPropertiesPath = getProductPropertiesPath(projectDir)
    throw RuntimeException("cannot create product properties, className=$className, projectPropertiesPath=$projectPropertiesPath, classPath=$classPathString, ", e)
  }

  return try {
    lookup.findConstructor(productPropertiesClass, MethodType.methodType(Void.TYPE)).invoke()
  }
  catch (_: NoSuchMethodException) {
    lookup
      .findConstructor(productPropertiesClass, MethodType.methodType(Void.TYPE, Path::class.java))
      .invoke(if (platformPrefix == "Idea") getCommunityHomePath(projectDir) else projectDir)
  } as ProductProperties
}

private fun getBuildModules(productConfiguration: ProductConfiguration): Sequence<String> = sequenceOf("intellij.idea.community.build") + productConfiguration.modules.asSequence()

private data class PlatformLayoutResult(
  @JvmField val distributionEntries: List<DistributionFileEntry>,
  @JvmField val coreClassPath: Set<Path>,
)

private suspend fun layoutPlatform(
  runDir: Path,
  platformLayout: PlatformLayout,
  searchableOptionSet: SearchableOptionSetDescriptor?,
  context: BuildContext,
  moduleOutputPatcher: ModuleOutputPatcher,
  request: BuildRequest,
): PlatformLayoutResult {
  // cannot be in parallel
  val entries = layoutPlatformDistribution(
    moduleOutputPatcher = moduleOutputPatcher,
    targetDir = runDir,
    platform = platformLayout,
    searchableOptionSet = searchableOptionSet,
    copyFiles = true,
    context = context,
  )
  val coreClassPath = coroutineScope {
    launch(Dispatchers.IO) {
      Files.writeString(runDir.resolve("build.txt"), context.fullBuildNumber)
    }

    // todo - we cannot for now skip nio-fs.jar, probably `-Xbootclasspath/a` is not correctly set for dev-mode-based tests
    generateClassPathByLayoutReport(
      libDir = runDir.resolve("lib"),
      entries = entries,
      skipNioFs = if (request.isBootClassPathCorrect) isMultiRoutingFileSystemEnabledForProduct(context.productProperties.platformPrefix) else false,
    )
  }

  return PlatformLayoutResult(entries, coreClassPath)
}

private fun computeAdditionalModulesFingerprint(additionalModules: List<String>): String {
  if (additionalModules.isEmpty()) {
    return ""
  }
  else {
    val hash = Hashing.xxh3_64().hashStream()
    hash.putUnorderedIterable(additionalModules, HashFunnel.forString(), Hashing.xxh3_64())
    return "-" + additionalModules.joinToString(separator = "-") { it.removePrefix("intellij.").take(4) } + "-" +
           Long.toUnsignedString(hash.asLong, Character.MAX_RADIX)
  }
}

private fun getCommunityHomePath(homePath: Path): Path {
  return if (Files.isDirectory(homePath.resolve("community"))) homePath.resolve("community") else homePath
}
