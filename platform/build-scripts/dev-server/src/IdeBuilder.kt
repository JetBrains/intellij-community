// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package org.jetbrains.intellij.build.devServer

import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope2
import com.intellij.util.PathUtilRt
import com.intellij.util.io.sha3_256
import com.intellij.util.lang.PathClassLoader
import com.intellij.util.lang.UrlClassLoader
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.*
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.impl.*
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.jps.model.artifact.JpsArtifactService
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.OffsetDateTime
import java.time.temporal.TemporalAdjusters
import kotlin.time.Duration.Companion.seconds

private const val PLUGIN_CACHE_DIR_NAME = "plugin-cache"

data class BuildRequest(
  @JvmField val platformPrefix: String,
  @JvmField val additionalModules: List<String>,
  @JvmField val isIdeProfileAware: Boolean = false,
  @JvmField val homePath: Path,
  @JvmField val productionClassOutput: Path = Path.of(System.getenv("CLASSES_DIR")
                                                      ?: homePath.resolve("out/classes/production").toString()).toAbsolutePath(),
  @JvmField val keepHttpClient: Boolean = true,
  @JvmField val platformClassPathConsumer: ((classPath: Set<Path>, runDir: Path) -> Unit)? = null,
  /**
   * If `true`, the dev build will include a [runtime module repository](psi_element://com.intellij.platform.runtime.repository). 
   * It's currently used only to run an instance of JetBrains Client from IDE's installation, 
   * and its generation makes build a little longer, so it should be enabled only if needed.
   */
  @JvmField val generateRuntimeModuleRepository: Boolean = false,
) {
  override fun toString(): String {
    return "BuildRequest(platformPrefix='$platformPrefix', " +
           "additionalModules=$additionalModules, " +
           "isIdeProfileAware=$isIdeProfileAware, homePath=$homePath, " +
           "productionClassOutput=$productionClassOutput, " +
           "keepHttpClient=$keepHttpClient, " +
           "generateRuntimeModuleRepository=$generateRuntimeModuleRepository"
  }
}

internal suspend fun buildProduct(productConfiguration: ProductConfiguration, request: BuildRequest): Path {
  val rootDir = withContext(Dispatchers.IO) {
    val rootDir = request.homePath.normalize().toAbsolutePath().resolve("out/dev-run")
    // if symlinked to ram disk, use a real path for performance reasons and avoid any issues in ant/other code
    if (Files.exists(rootDir)) {
      // toRealPath must be called only on existing file
      rootDir.toRealPath()
    }
    else {
      rootDir
    }
  }

  val runDir = withContext(Dispatchers.IO) {
    val classifier = if (request.isIdeProfileAware) computeAdditionalModulesFingerprint(request.additionalModules) else ""
    val productDirName = (if (request.platformPrefix == "Idea") "idea-community" else request.platformPrefix) + classifier

    val runDir = rootDir.resolve("$productDirName/$productDirName")
    // on start, delete everything to avoid stale data
    if (Files.isDirectory(runDir)) {
      val usePluginCache = spanBuilder("check plugin cache applicability").useWithScope2 {
        checkBuildModulesModificationAndMark(productConfiguration = productConfiguration, outDir = request.productionClassOutput)
      }
      prepareExistingRunDirForProduct(runDir = runDir, usePluginCache = usePluginCache)
    }
    else {
      Files.createDirectories(runDir)
      Span.current().addEvent("plugin cache is not reused because run dir doesn't exist")
    }
    runDir
  }

  val context = createBuildContext(productConfiguration = productConfiguration,
                                   request = request,
                                   runDir = runDir,
                                   jarCacheDir = rootDir.resolve("jar-cache"))
  coroutineScope {
    val platformLayout = async {
      createPlatformLayout(pluginsToPublish = emptySet(), context = context)
    }

    val platformDistributionEntries = async {
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

      val (platformDistributionEntries, classPath) = spanBuilder("layout platform").useWithScope2 {
        layoutPlatform(runDir = runDir, platformLayout = platformLayout.await(), context = context)
      }
      
      launch(Dispatchers.IO) {
        Files.writeString(runDir.resolve("core-classpath.txt"), classPath.joinToString(separator = "\n"))
      }

      request.platformClassPathConsumer?.invoke(classPath, runDir)
      platformDistributionEntries
    }

    val pluginDistributionEntries = async {
      val artifactTask = launch {
        val artifactOutDir = request.homePath.resolve("out/classes/artifacts").toString()
        for (artifact in JpsArtifactService.getInstance().getArtifacts(context.project)) {
          artifact.outputPath = "$artifactOutDir/${PathUtilRt.getFileName(artifact.outputPath)}"
        }
      }

      val bundledMainModuleNames = getBundledMainModuleNames(context.productProperties, request.additionalModules)

      val pluginRootDir = runDir.resolve("plugins")
      val pluginCacheRootDir = runDir.resolve(PLUGIN_CACHE_DIR_NAME)

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
        val pluginBuildDescriptor = PluginBuildDescriptor(dir = pluginRootDir.resolve(plugin.directoryName),
                                                          layout = plugin,
                                                          moduleNames = modules)
        for (name in modules) {
          moduleNameToPluginBuildDescriptor.put(name, pluginBuildDescriptor)
        }
        pluginBuildDescriptors.add(pluginBuildDescriptor)
      }

      withContext(Dispatchers.IO) {
        Files.createDirectories(pluginRootDir)
      }

      artifactTask.join()
      val pluginEntries = buildPlugins(pluginBuildDescriptors = pluginBuildDescriptors,
                                       outDir = request.productionClassOutput,
                                       platformLayout = platformLayout.await(),
                                       pluginCacheRootDir = pluginCacheRootDir,
                                       context = context)

      val additionalPluginPaths = context.productProperties.getAdditionalPluginPaths(context)
      if (additionalPluginPaths.isNotEmpty()) {
        withContext(Dispatchers.IO) {
          for (sourceDir in additionalPluginPaths) {
            copyDir(sourceDir = sourceDir, targetDir = pluginRootDir.resolve(sourceDir.fileName))
          }
        }
      }
      pluginEntries
    }

    if (context.generateRuntimeModuleRepository) {
      launch {
        val allDistributionEntries = platformDistributionEntries.await() + pluginDistributionEntries.await().flatten()
        spanBuilder("generate runtime repository").useWithScope2 {
          withContext(Dispatchers.IO) {
            generateRuntimeModuleRepositoryForDevBuild(entries = allDistributionEntries, targetDirectory = runDir, context = context)
          }
        }
      }
    }
  }

  return runDir
}

private suspend fun createBuildContext(productConfiguration: ProductConfiguration,
                                       request: BuildRequest,
                                       runDir: Path,
                                       jarCacheDir: Path): BuildContext {
  return coroutineScope {
    // ~1 second
    val productProperties = async {
      withTimeout(30.seconds) {
        createProductProperties(productConfiguration = productConfiguration, request = request)
      }
    }

    // load project is executed as part of compilation context creation - ~1 second
    val compilationContext = async {
      spanBuilder("create build context").useWithScope2 {
        // we cannot inject a proper build time as it is a part of resources, so, set to the first day of the current month
        val options = BuildOptions(
          jarCacheDir = jarCacheDir,
          buildDateInSeconds = OffsetDateTime.now().with(TemporalAdjusters.firstDayOfMonth()).toEpochSecond(),
          printFreeSpace = false,
          validateImplicitPlatformModule = false,
          skipDependencySetup = true,
        )
        options.useCompiledClassesFromProjectOutput = true
        options.setTargetOsAndArchToCurrent()
        options.cleanOutputFolder = false
        options.outputRootPath = runDir
        options.buildStepsToSkip.add(BuildOptions.PREBUILD_SHARED_INDEXES)
        options.buildStepsToSkip.add(BuildOptions.GENERATE_JAR_ORDER_STEP)
        options.buildStepsToSkip.add(BuildOptions.FUS_METADATA_BUNDLE_STEP)

        if (System.getProperty("idea.dev.build.unpacked").toBoolean()) {
          if (options.enableEmbeddedJetBrainsClient) {
            options.enableEmbeddedJetBrainsClient = false
          }

          // it downloads binaries from TC - it is bad
          options.buildStepsToSkip.add(BuildOptions.IJENT_EXECUTABLE_DOWNLOADING)
        }

        options.generateRuntimeModuleRepository = options.generateRuntimeModuleRepository && request.generateRuntimeModuleRepository

        CompilationContextImpl.createCompilationContext(
          communityHome = getCommunityHomePath(request.homePath),
          projectHome = request.homePath,
          buildOutputRootEvaluator = { _ -> runDir },
          setupTracer = false,
          options = options,
        )
      }
    }

    BuildContextImpl(compilationContext = compilationContext.await(),
                     productProperties = productProperties.await(),
                     windowsDistributionCustomizer = object : WindowsDistributionCustomizer() {},
                     linuxDistributionCustomizer = object : LinuxDistributionCustomizer() {},
                     macDistributionCustomizer = object : MacDistributionCustomizer() {},
                     proprietaryBuildTools = ProprietaryBuildTools.DUMMY)
  }
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

private suspend fun createProductProperties(productConfiguration: ProductConfiguration, request: BuildRequest): ProductProperties {
  val classPathFiles = getBuildModules(productConfiguration).map { request.productionClassOutput.resolve(it) }.toList()

  val classLoader = spanBuilder("create product properties classloader").useWithScope2 {
    PathClassLoader(UrlClassLoader.build().files(classPathFiles).parent(BuildRequest::class.java.classLoader))
  }

  return spanBuilder("create product properties").useWithScope2 {
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
        .invoke(if (request.platformPrefix == "Idea") getCommunityHomePath(request.homePath).communityRoot else request.homePath)
    } as ProductProperties
  }
}

private fun checkBuildModulesModificationAndMark(productConfiguration: ProductConfiguration, outDir: Path): Boolean {
  // intellij.platform.devBuildServer
  var isApplicable = true
  for (module in getBuildModules(productConfiguration) + sequenceOf("intellij.platform.devBuildServer",
                                                                    "intellij.platform.buildScripts",
                                                                    "intellij.platform.buildScripts.downloader",
                                                                    "intellij.idea.community.build.tasks")) {
    val markFile = outDir.resolve(module).resolve(UNMODIFIED_MARK_FILE_NAME)
    if (Files.exists(markFile)) {
      continue
    }

    if (isApplicable) {
      Span.current().addEvent("plugin cache is not reused because at least $module is changed")
      isApplicable = false
    }

    createMarkFile(markFile)
  }

  if (isApplicable) {
    Span.current().addEvent("plugin cache will be reused (build modules were not changed)")
  }
  return isApplicable
}

private fun getBuildModules(productConfiguration: ProductConfiguration): Sequence<String> {
  return sequenceOf("intellij.idea.community.build") + productConfiguration.modules.asSequence()
}

private suspend fun layoutPlatform(runDir: Path,
                                   platformLayout: PlatformLayout,
                                   context: BuildContext): Pair<List<DistributionFileEntry>, Set<Path>> {
  val entries = layoutPlatformDistribution(moduleOutputPatcher = ModuleOutputPatcher(),
                                           targetDirectory = runDir,
                                           platform = platformLayout,
                                           context = context,
                                           copyFiles = true)
  lateinit var sortedClassPath: Set<Path>
  coroutineScope {
    launch(Dispatchers.IO) {
      copyDistFiles(context = context, newDir = runDir, os = OsFamily.currentOs, arch = JvmArchitecture.currentJvmArch)
    }

    launch {
      val classPath = LinkedHashSet<Path>()
      val libDir = runDir.resolve("lib")
      val hasher = Hashing.komihash5_0().hashStream()
      for (entry in entries) {
        val file = entry.path
        // exclude files like ext/platform-main.jar - if file in lib, take only direct children in an account
        if (file.startsWith(libDir) && libDir.relativize(file).nameCount != 1) {
          continue
        }

        classPath.add(file)

        hasher.putLong(entry.hash)
      }
      sortedClassPath = computeAppClassPath(libDir = libDir, existing = classPath, homeDir = runDir)

      withContext(Dispatchers.IO) {
        Files.writeString(runDir.resolve("fingerprint.txt"), java.lang.Long.toUnsignedString(hasher.asLong, Character.MAX_RADIX))
      }
    }

    launch(Dispatchers.IO) {
      Files.writeString(runDir.resolve("build.txt"), context.fullBuildNumber)
    }
  }
  return entries to sortedClassPath
}

private fun getBundledMainModuleNames(productProperties: ProductProperties, additionalModules: List<String>): Set<String> {
  return LinkedHashSet(productProperties.productLayout.bundledPluginModules) + additionalModules
}

fun getAdditionalModules(): Sequence<String>? {
  return System.getProperty("additional.modules")?.splitToSequence(',')?.map(String::trim)?.filter { it.isNotEmpty() }
}

fun computeAdditionalModulesFingerprint(additionalModules: List<String>): String {
  if (additionalModules.isEmpty()) {
    return ""
  }
  else {
    return BigInteger(1, sha3_256().digest(additionalModules.sorted()
                                             .joinToString(separator = ",")
                                             .toByteArray())).toString(Character.MAX_RADIX)
  }
}

private fun CoroutineScope.prepareExistingRunDirForProduct(runDir: Path, usePluginCache: Boolean) {
  launch {
    for (child in Files.newDirectoryStream(runDir).use { it.sorted() }) {
      if (child.endsWith("plugins") || child.endsWith(PLUGIN_CACHE_DIR_NAME)) {
        continue
      }

      if (Files.isDirectory(child)) {
        Files.newDirectoryStream(child).use { stream ->
          for (file in stream) {
            NioFiles.deleteRecursively(file)
          }
        }
      }
      else {
        Files.delete(child)
      }
    }
  }

  launch {
    val pluginCacheDir = runDir.resolve(PLUGIN_CACHE_DIR_NAME)
    val pluginDir = runDir.resolve("plugins")
    NioFiles.deleteRecursively(pluginCacheDir)
    if (usePluginCache) {
      // move to cache
      try {
        Files.move(pluginDir, pluginCacheDir)
      }
      catch (_: NoSuchFileException) {
      }
    }
    else {
      NioFiles.deleteRecursively(pluginDir)
    }
  }
}

private fun getCommunityHomePath(homePath: Path): BuildDependenciesCommunityRoot {
  var communityDotIdea = homePath.resolve("community/.idea")
  // Handle Rider repository layout
  if (Files.notExists(communityDotIdea)) {
    val riderSpecificCommunityDotIdea = homePath.parent.resolve("ultimate/community/.idea")
    if (Files.exists(riderSpecificCommunityDotIdea)) {
      communityDotIdea = riderSpecificCommunityDotIdea
    }
  }
  return BuildDependenciesCommunityRoot(if (Files.isDirectory(communityDotIdea)) communityDotIdea.parent else homePath)
}
