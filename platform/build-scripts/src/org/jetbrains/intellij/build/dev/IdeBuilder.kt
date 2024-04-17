// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import com.dynatrace.hash4j.hashing.HashFunnel
import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import com.intellij.util.PathUtilRt
import com.intellij.util.lang.PathClassLoader
import com.intellij.util.lang.UrlClassLoader
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.protobuf.ProtoBuf
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.BuildOptions.Companion.PROJECT_CLASSES_OUTPUT_DIRECTORY_PROPERTY
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.impl.*
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleOutputEntry
import org.jetbrains.jps.model.artifact.JpsArtifactService
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.DayOfWeek
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.String
import kotlin.time.Duration.Companion.seconds

data class BuildRequest(
  @JvmField val platformPrefix: String,
  @JvmField val additionalModules: List<String>,
  @JvmField val projectDir: Path,
  @JvmField val devRootDir: Path = projectDir.normalize().toAbsolutePath().resolve("out/dev-run"),
  @JvmField val jarCacheDir: Path = devRootDir.resolve("jar-cache"),
  @JvmField val productionClassOutput: Path = Path.of(System.getenv("CLASSES_DIR")
                                                      ?: projectDir.resolve("out/classes/production").toString()).toAbsolutePath(),
  @JvmField val keepHttpClient: Boolean = true,
  @JvmField val platformClassPathConsumer: ((classPath: Set<Path>, runDir: Path) -> Unit)? = null,
  /**
   * If `true`, the dev build will include a [runtime module repository](psi_element://com.intellij.platform.runtime.repository). 
   * It's currently used only to run an instance of JetBrains Client from IDE's installation, 
   * and its generation makes build a little longer, so it should be enabled only if needed.
   */
  @JvmField val generateRuntimeModuleRepository: Boolean = false,

  @JvmField val isUnpackedDist: Boolean = System.getProperty("idea.dev.build.unpacked").toBoolean(),

  @JvmField val buildOptionsTemplate: BuildOptions? = null,
) {
  override fun toString(): String =
    "BuildRequest(platformPrefix='$platformPrefix', " +
    "additionalModules=$additionalModules, " +
    "productionClassOutput=$productionClassOutput, " +
    "keepHttpClient=$keepHttpClient, " +
    "generateRuntimeModuleRepository=$generateRuntimeModuleRepository"
}

@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun buildProduct(request: BuildRequest, createProductProperties: suspend () -> ProductProperties): Path {
  val rootDir = withContext(Dispatchers.IO) {
    val rootDir = request.devRootDir
    // if symlinked to ram disk, use a real path for performance reasons and avoid any issues in ant/other code
    if (Files.exists(rootDir)) {
      // toRealPath must be called only on existing file
      rootDir.toRealPath()
    }
    else {
      rootDir
    }
  }

  val classifier = computeAdditionalModulesFingerprint(request.additionalModules)
  val productDirNameWithoutClassifier = if (request.platformPrefix == "Idea") "idea-community" else request.platformPrefix
  val productDirName = (productDirNameWithoutClassifier + classifier).takeLast(255)

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
      if (child.fileName.toString() != "log") {
        launch {
          NioFiles.deleteRecursively(child)
        }
      }
    }
    buildDir
  }

  val runDir = buildDir.resolve(productDirNameWithoutClassifier)
  val context = createBuildContext(createProductProperties, request, runDir, request.jarCacheDir, buildDir)
  compileIfNeeded(context)

  coroutineScope {
    val platformLayout = async {
      createPlatformLayout(pluginsToPublish = emptySet(), context = context)
    }

    val platformDistributionEntriesDeferred = async {
      launch(Dispatchers.IO) {
        // PathManager.getBinPath() is used as a working dir for maven
        val binDir = Files.createDirectories(runDir.resolve("bin"))
        val osDistributionBuilder = getOsDistributionBuilder(os = OsFamily.currentOs, context = context)!!
        val vmOptionsFile = osDistributionBuilder.writeVmOptions(binDir)
        // copying outside the installation directory is necessary to specify system property "jb.vmOptionsFile"
        Files.copy(vmOptionsFile, binDir.parent.parent.resolve(vmOptionsFile.fileName), StandardCopyOption.REPLACE_EXISTING)

        val ideaPropertyFile = binDir.resolve(PathManager.PROPERTIES_FILE_NAME)
        Files.writeString(ideaPropertyFile, createIdeaPropertyFile(context))
      }

      val (platformDistributionEntries, classPath) = spanBuilder("layout platform").useWithScope {
        layoutPlatform(runDir = runDir, platformLayout = platformLayout.await(), context = context)
      }

      launch(Dispatchers.IO) {
        val cp = classPath
          .asSequence()
          .filter { !excludedLibJars.contains(it.fileName.toString()) }
          .joinToString(separator = "\n")
        Files.writeString(runDir.resolve("core-classpath.txt"), cp)
      }

      request.platformClassPathConsumer?.invoke(classPath, runDir)
      platformDistributionEntries
    }

    val artifactTask = launch {
      val artifactOutDir = request.projectDir.resolve("out/classes/artifacts").toString()
      for (artifact in JpsArtifactService.getInstance().getArtifacts(context.project)) {
        artifact.outputPath = "$artifactOutDir/${PathUtilRt.getFileName(artifact.outputPath)}"
      }
    }

    val pluginDistributionEntriesDeferred = async {
      buildPlugins(request, context, runDir, platformLayout, artifactTask)
    }

    launch {
      val (pluginEntries, additionalEntries) = pluginDistributionEntriesDeferred.await()
      spanBuilder("generate plugin classpath").useWithScope(Dispatchers.IO) {
        val mainData = generatePluginClassPath(pluginEntries, writeDescriptor = !request.isUnpackedDist)
        val additionalData = additionalEntries?.let { generatePluginClassPathFromFiles(it, writeDescriptor = !request.isUnpackedDist) }

        val byteOut = ByteArrayOutputStream()
        val out = DataOutputStream(byteOut)
        val pluginCount = pluginEntries.size + (additionalEntries?.size ?: 0)
        writePluginClassPathHeader(out = out, isJarOnly = !request.isUnpackedDist, pluginCount = pluginCount)
        out.write(mainData)
        additionalData?.let { out.write(it) }
        out.close()
        Files.write(runDir.resolve(PLUGIN_CLASSPATH), byteOut.toByteArray())
      }
    }

    if (context.generateRuntimeModuleRepository) {
      launch {
        val allDistributionEntries = platformDistributionEntriesDeferred.await().asSequence() +
                                     pluginDistributionEntriesDeferred.await().first.asSequence().flatMap { it.second }
        spanBuilder("generate runtime repository").useWithScope(Dispatchers.IO) {
          generateRuntimeModuleRepositoryForDevBuild(entries = allDistributionEntries, targetDirectory = runDir, context)
        }
      }
    }

    launch {
      computeIdeFingerprint(
        platformDistributionEntriesDeferred = platformDistributionEntriesDeferred,
        pluginDistributionEntriesDeferred = pluginDistributionEntriesDeferred,
        runDir = runDir,
        homePath = request.projectDir,
      )
    }
  }
    .invokeOnCompletion {
      // close debug logging to prevent locking of output directory on Windows
      context.messages.close()
    }
  return runDir
}

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalSerializationApi::class)
private suspend fun compileIfNeeded(context: BuildContext) {
  val port = System.getProperty("compile.server.port")?.toIntOrNull()
  val project = System.getProperty("compile.server.project")
  val token = System.getProperty("compile.server.token")
  if (port == null || project == null || token == null) {
    return
  }

  val modulesToCompile = spanBuilder("collect modules to compile").useWithScope {
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
  spanBuilder("compile modules").setAttribute("url", url).useWithScope {
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

private suspend fun computeIdeFingerprint(
  platformDistributionEntriesDeferred: Deferred<List<DistributionFileEntry>>,
  pluginDistributionEntriesDeferred: Deferred<Pair<List<Pair<PluginBuildDescriptor, List<DistributionFileEntry>>>, List<Pair<Path, List<Path>>>?>>,
  runDir: Path,
  homePath: Path,
) {
  val hasher = Hashing.komihash5_0().hashStream()
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
    //Files.writeString(runDir.resolve("fingerprint-debug.txt"), debug)
  }
  Span.current().addEvent("IDE fingerprint: $fingerprint")
}

private suspend fun buildPlugins(
  request: BuildRequest,
  context: BuildContext,
  runDir: Path,
  platformLayout: Deferred<PlatformLayout>,
  artifactTask: Job
): Pair<List<Pair<PluginBuildDescriptor, List<DistributionFileEntry>>>, List<Pair<Path, List<Path>>>?> {
  val bundledMainModuleNames = getBundledMainModuleNames(context, request.additionalModules)

  val pluginRootDir = runDir.resolve("plugins")

  val moduleNameToPluginBuildDescriptor = HashMap<String, PluginBuildDescriptor>()
  val pluginBuildDescriptors = mutableListOf<PluginBuildDescriptor>()
  for (plugin in getPluginLayoutsByJpsModuleNames(bundledMainModuleNames, context.productProperties.productLayout)) {
    if (!isPluginApplicable(bundledMainModuleNames = bundledMainModuleNames, plugin = plugin, context = context)) {
      continue
    }

    // remove all modules without a content root
    val modules = plugin.includedModules.asSequence()
      .map { it.moduleName }
      .distinct()
      .filter { it == plugin.mainModule || !context.findRequiredModule(it).contentRootsList.urls.isEmpty() }
      .toList()
    val pluginBuildDescriptor = PluginBuildDescriptor(
      dir = pluginRootDir.resolve(plugin.directoryName),
      layout = plugin,
      moduleNames = modules,
    )
    for (name in modules) {
      moduleNameToPluginBuildDescriptor.put(name, pluginBuildDescriptor)
    }
    pluginBuildDescriptors.add(pluginBuildDescriptor)
  }

  withContext(Dispatchers.IO) {
    Files.createDirectories(pluginRootDir)
  }

  artifactTask.join()

  val pluginEntries = buildPlugins(pluginBuildDescriptors, platformLayout.await(), context)
  val additionalPlugins = copyAdditionalPlugins(context, pluginRootDir)
  return pluginEntries to additionalPlugins
}

internal suspend fun createBuildContext(
  createProductProperties: suspend () -> ProductProperties,
  request: BuildRequest,
  runDir: Path,
  jarCacheDir: Path,
  buildDir: Path,
): BuildContext {
  return coroutineScope {
    // ~1 second
    val productProperties = async {
      createProductProperties()
    }

    // load project is executed as part of compilation context creation - ~1 second
    val compilationContext = async {
      spanBuilder("create build context").useWithScope {
        // we cannot inject a proper build time as it is a part of resources, so, set to the first day of the current month
        val buildOptionsTemplate = request.buildOptionsTemplate
        val useCompiledClassesFromProjectOutput = buildOptionsTemplate == null || buildOptionsTemplate.useCompiledClassesFromProjectOutput
        val classOutDir = if (useCompiledClassesFromProjectOutput) {
          request.productionClassOutput.parent.toString()
        }
        else {
          buildOptionsTemplate?.classOutDir
          ?: System.getProperty(PROJECT_CLASSES_OUTPUT_DIRECTORY_PROPERTY)
          ?: request.productionClassOutput.parent.toString()
        }
        val options = BuildOptions(
          jarCacheDir = jarCacheDir,
          buildDateInSeconds = getBuildDateInSeconds(),
          printFreeSpace = false,
          validateImplicitPlatformModule = false,
          skipDependencySetup = true,

          useCompiledClassesFromProjectOutput = useCompiledClassesFromProjectOutput,
          pathToCompiledClassesArchivesMetadata = buildOptionsTemplate?.pathToCompiledClassesArchivesMetadata?.takeIf { !useCompiledClassesFromProjectOutput },
          pathToCompiledClassesArchive = buildOptionsTemplate?.pathToCompiledClassesArchive?.takeIf { !useCompiledClassesFromProjectOutput },
          classOutDir = classOutDir,

          validateModuleStructure = false,
          cleanOutDir = false,
          outRootDir = runDir,
          compilationLogEnabled = false,
          logDir = buildDir.resolve("log"),
        )
        options.setTargetOsAndArchToCurrent()
        options.buildStepsToSkip += listOf(
          BuildOptions.PREBUILD_SHARED_INDEXES,
          BuildOptions.GENERATE_JAR_ORDER_STEP,
          BuildOptions.FUS_METADATA_BUNDLE_STEP,
        )

        if (request.isUnpackedDist && options.enableEmbeddedJetBrainsClient) {
          options.enableEmbeddedJetBrainsClient = false
        }

        options.generateRuntimeModuleRepository = options.generateRuntimeModuleRepository && request.generateRuntimeModuleRepository

        CompilationContextImpl.createCompilationContext(
          projectHome = request.projectDir,
          buildOutputRootEvaluator = { _ -> runDir },
          setupTracer = false,
          // will be enabled later in [com.intellij.platform.ide.bootstrap.enableJstack] instead
          enableCoroutinesDump = false,
          options = options,
        )
      }
    }

    BuildContextImpl(
      compilationContext = compilationContext.await(),
      productProperties = productProperties.await(),
      windowsDistributionCustomizer = object : WindowsDistributionCustomizer() {},
      linuxDistributionCustomizer = object : LinuxDistributionCustomizer() {},
      macDistributionCustomizer = object : MacDistributionCustomizer() {},
      proprietaryBuildTools = ProprietaryBuildTools.DUMMY,
    )
  }
}

private fun getBuildDateInSeconds(): Long {
  val now = OffsetDateTime.now()
  // licence expired - 30 days
  return now
    .with(if (now.dayOfMonth >= 30) TemporalAdjusters.previous(DayOfWeek.MONDAY) else TemporalAdjusters.firstDayOfMonth())
    .truncatedTo(ChronoUnit.DAYS)
    .toEpochSecond()
}

private fun isPluginApplicable(bundledMainModuleNames: Set<String>, plugin: PluginLayout, context: BuildContext): Boolean {
  if (!bundledMainModuleNames.contains(plugin.mainModule)) {
    return false
  }

  if (plugin.bundlingRestrictions == PluginBundlingRestrictions.NONE) {
    return true
  }

  return satisfiesBundlingRequirements(plugin, OsFamily.currentOs, JvmArchitecture.currentJvmArch, context) ||
         satisfiesBundlingRequirements(plugin, osFamily = null, JvmArchitecture.currentJvmArch, context)
}

internal suspend fun createProductProperties(productConfiguration: ProductConfiguration, request: BuildRequest): ProductProperties {
  val classPathFiles = getBuildModules(productConfiguration).map { request.productionClassOutput.resolve(it) }.toList()

  val classLoader = spanBuilder("create product properties classloader").useWithScope {
    PathClassLoader(UrlClassLoader.build().files(classPathFiles).parent(BuildRequest::class.java.classLoader))
  }

  return spanBuilder("create product properties").useWithScope {
    val productPropertiesClass = try {
      classLoader.loadClass(productConfiguration.className)
    }
    catch (_: ClassNotFoundException) {
      val classPathString = classPathFiles.joinToString(separator = "\n") { file ->
        "$file (" + (if (Files.isDirectory(file)) "dir" else if (Files.exists(file)) "exists" else "doesn't exist") + ")"
      }
      throw RuntimeException("cannot create product properties (classPath=$classPathString")
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

private fun getBuildModules(productConfiguration: ProductConfiguration): Sequence<String> =
  sequenceOf("intellij.idea.community.build") + productConfiguration.modules.asSequence()

private suspend fun layoutPlatform(
  runDir: Path,
  platformLayout: PlatformLayout,
  context: BuildContext,
): Pair<List<DistributionFileEntry>, Set<Path>> {
  val entries = layoutPlatformDistribution(
    moduleOutputPatcher = ModuleOutputPatcher(),
    targetDirectory = runDir,
    platform = platformLayout,
    context = context,
    copyFiles = true,
  )
  lateinit var sortedClassPath: Set<Path>
  coroutineScope {
    launch(Dispatchers.IO) {
      copyDistFiles(context = context, newDir = runDir, os = OsFamily.currentOs, arch = JvmArchitecture.currentJvmArch)
    }

    launch {
      val classPath = LinkedHashSet<Path>()
      val libDir = runDir.resolve("lib")
      for (entry in entries) {
        val file = entry.path
        // exclude files like ext/platform-main.jar - if file in lib, take only direct children in an account
        if ((entry.relativeOutputFile ?: "").contains('/')) {
          continue
        }
        if (entry is ModuleOutputEntry &&
            (entry.moduleName == "intellij.platform.testFramework" || entry.moduleName.startsWith("intellij.platform.unitTestMode"))) {
          continue
        }

        classPath.add(file)
      }
      sortedClassPath = computeAppClassPath(libDir = libDir, existing = classPath, homeDir = runDir)
    }

    launch(Dispatchers.IO) {
      Files.writeString(runDir.resolve("build.txt"), context.fullBuildNumber)
    }
  }
  return entries to sortedClassPath
}

private fun getBundledMainModuleNames(context: BuildContext, additionalModules: List<String>): Set<String> =
  LinkedHashSet(context.bundledPluginModules) + additionalModules

fun getAdditionalModules(): Sequence<String>? =
  System.getProperty("additional.modules")?.splitToSequence(',')?.map(String::trim)?.filter { it.isNotEmpty() }

private fun computeAdditionalModulesFingerprint(additionalModules: List<String>): String {
  if (additionalModules.isEmpty()) {
    return ""
  }
  else {
    val hash = Hashing.komihash5_0().hashStream()
    hash.putUnorderedIterable(additionalModules, HashFunnel.forString(), Hashing.komihash5_0())
    return "-" + additionalModules.joinToString(separator = "-") { it.removePrefix("intellij.").take(4) } + "-" +
           java.lang.Long.toUnsignedString(hash.asLong, Character.MAX_RADIX)
  }
}

private fun getCommunityHomePath(homePath: Path): Path =
  if (Files.isDirectory(homePath.resolve("community"))) homePath.resolve("community") else homePath
