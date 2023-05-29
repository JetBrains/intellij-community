// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceNegatedIsEmptyWithIsNotEmpty", "LiftReturnOrAssignment")

package org.jetbrains.intellij.build.devServer

import com.intellij.platform.diagnostic.telemetry.impl.useWithScope2
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.PathUtilRt
import com.intellij.util.lang.PathClassLoader
import com.intellij.util.lang.UrlClassLoader
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.*
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.impl.*
import org.jetbrains.intellij.build.impl.projectStructureMapping.LibraryFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleOutputEntry
import org.jetbrains.jps.model.artifact.JpsArtifactService
import org.jetbrains.xxh3.Xx3UnencodedString
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.seconds

internal const val UNMODIFIED_MARK_FILE_NAME = ".unmodified"
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
  @JvmField val isPackagedLib: Boolean = true,
)

internal suspend fun buildProduct(productConfiguration: ProductConfiguration, request: BuildRequest) {
  val rootDir = withContext(Dispatchers.IO) {
    val rootDir = request.homePath.resolve("out/dev-run")
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
    val runDir = rootDir.resolve((if (request.platformPrefix == "Idea") "idea-community" else request.platformPrefix) + classifier)
    // on start, delete everything to avoid stale data
    if (Files.isDirectory(runDir)) {
      val usePluginCache = spanBuilder("check plugin cache applicability").useWithScope2 {
        checkBuildModulesModificationAndMark(productConfiguration, request.productionClassOutput)
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

  val bundledMainModuleNames = getBundledMainModuleNames(context.productProperties, request.additionalModules)

  val pluginRootDir = runDir.resolve("plugins")
  val pluginCacheRootDir = runDir.resolve(PLUGIN_CACHE_DIR_NAME)

  val moduleNameToPluginBuildDescriptor = HashMap<String, PluginBuildDescriptor>()
  val pluginBuildDescriptors = mutableListOf<PluginBuildDescriptor>()
  for (plugin in getPluginLayoutsByJpsModuleNames(bundledMainModuleNames, context.productProperties.productLayout)) {
    if (!isPluginApplicable(bundledMainModuleNames = bundledMainModuleNames, plugin = plugin, context = context)) {
      continue
    }

    // remove all modules without content root
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

  val artifactOutDir = request.homePath.resolve("out/classes/artifacts").toString()
  for (artifact in JpsArtifactService.getInstance().getArtifacts(context.project)) {
    artifact.outputPath = "$artifactOutDir/${PathUtilRt.getFileName(artifact.outputPath)}"
  }

  coroutineScope {
    val buildPluginsJob = launch {
      withContext(Dispatchers.IO) {
        Files.createDirectories(pluginRootDir)
      }
      buildPlugins(pluginBuildDescriptors = pluginBuildDescriptors,
                   outDir = request.productionClassOutput,
                   pluginCacheRootDir = pluginCacheRootDir,
                   context = context)
    }
    val buildClassPathJob = launch {
      val isPackagedLib = request.isPackagedLib
      val classPath = spanBuilder("compute lib classpath").useWithScope2 {
        layoutPlatform(homePath = request.homePath, runDir = runDir, isPackagedLib = isPackagedLib, context = context)
      }

      val platformClassPathConsumer = request.platformClassPathConsumer

      withContext(Dispatchers.IO) {
        // PathManager.getBinPath() is used as a working dir for maven
        Files.createDirectories(runDir.resolve("bin"))

        Files.writeString(runDir.resolve("build.txt"), context.fullBuildNumber)
      }

      if (platformClassPathConsumer == null) {
        withContext(Dispatchers.IO) {
          val classPathFile = runDir.resolve("core-classpath.txt")
          Files.writeString(classPathFile, classPath.joinToString(separator = "\n"))
        }
      }
      else {
        platformClassPathConsumer(classPath, runDir)
      }
    }

    joinAll(buildPluginsJob, buildClassPathJob)
  }
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
        val options = BuildOptions(jarCacheDir = jarCacheDir)
        options.printFreeSpace = false
        options.validateImplicitPlatformModule = false
        options.useCompiledClassesFromProjectOutput = true
        options.setTargetOsAndArchToCurrent()
        options.cleanOutputFolder = false
        options.skipDependencySetup = true
        options.outputRootPath = runDir
        options.buildStepsToSkip.add(BuildOptions.PREBUILD_SHARED_INDEXES)
        options.buildStepsToSkip.add(BuildOptions.GENERATE_JAR_ORDER_STEP)

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
                     windowsDistributionCustomizer = null,
                     linuxDistributionCustomizer = null,
                     macDistributionCustomizer = null,
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

  return satisfiesBundlingRequirements(plugin = plugin,
                                       osFamily = OsFamily.currentOs,
                                       arch = JvmArchitecture.currentJvmArch,
                                       withEphemeral = false,
                                       context = context) ||
         satisfiesBundlingRequirements(plugin = plugin,
                                       osFamily = null,
                                       arch = JvmArchitecture.currentJvmArch,
                                       withEphemeral = false,
                                       context = context)
}

private suspend fun createProductProperties(productConfiguration: ProductConfiguration, request: BuildRequest): ProductProperties {
  val classPathFiles = getBuildModules(productConfiguration).map { request.productionClassOutput.resolve(it) }.toList()

  val classLoader = spanBuilder("create product properties classloader").useWithScope2 {
    PathClassLoader(UrlClassLoader.build().files(classPathFiles).parent(BuildRequest::class.java.classLoader))
  }

  val productProperties = spanBuilder("create product properties").useWithScope2 {
    val productPropertiesClass = try {
      classLoader.loadClass(productConfiguration.className)
    }
    catch (e: ClassNotFoundException) {
      val classPathString = classPathFiles.joinToString(separator = "\n") { file ->
        "$file (" + (if (Files.isDirectory(file)) "dir" else if (Files.exists(file)) "exists" else "doesn't exist") + ")"
      }
      throw RuntimeException("cannot create product properties (classPath=$classPathString")
    }

    MethodHandles.lookup()
      .findConstructor(productPropertiesClass, MethodType.methodType(Void.TYPE, Path::class.java))
      .invoke(request.homePath) as ProductProperties
  }
  return productProperties
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

@Suppress("SpellCheckingInspection")
private val extraJarNames = arrayOf("ideaLicenseDecoder.jar", "ls-client-api.jar", "y.jar", "ysvg.jar")

private suspend fun layoutPlatform(homePath: Path, runDir: Path, isPackagedLib: Boolean, context: BuildContext): Set<Path> {
  val platformLayout = createPlatformLayout(pluginsToPublish = emptySet(), context = context)
  val projectStructureMapping = layoutPlatformDistribution(moduleOutputPatcher = ModuleOutputPatcher(),
                                                           targetDirectory = runDir,
                                                           platform = platformLayout,
                                                           context = context,
                                                           copyFiles = isPackagedLib)
  // for some reasons, maybe duplicated paths - use set
  val classPath = LinkedHashSet<Path>()
  if (isPackagedLib) {
    projectStructureMapping.mapTo(classPath) { it.path }

    copyDistFiles(context = context, newDir = runDir, os = OsFamily.currentOs, arch = JvmArchitecture.currentJvmArch)
  }
  else {
    for (entry in projectStructureMapping) {
      when (entry) {
        is ModuleOutputEntry -> classPath.add(context.getModuleOutputDir(context.findRequiredModule(entry.moduleName)))
        is LibraryFileEntry -> classPath.add(entry.libraryFile!!)
        else -> throw UnsupportedOperationException("Entry $entry is not supported")
      }
    }

    val projectLibDir = homePath.resolve("lib")
    for (extraJarName in extraJarNames) {
      val extraJar = projectLibDir.resolve(extraJarName)
      if (Files.exists(extraJar)) {
        classPath.add(extraJar)
      }
    }
  }
  return classPath
}

private fun getBundledMainModuleNames(productProperties: ProductProperties, additionalModules: List<String>): Set<String> {
  val bundledPlugins = LinkedHashSet(productProperties.productLayout.bundledPluginModules)
  bundledPlugins.addAll(additionalModules)
  return bundledPlugins
}

fun getAdditionalModules(): Sequence<String>? {
  return (System.getProperty("additional.modules") ?: return null)
    .splitToSequence(',')
    .map(String::trim)
    .filter { it.isNotEmpty() }
}

fun computeAdditionalModulesFingerprint(additionalModules: List<String>): String {
  if (additionalModules.isEmpty()) {
    return ""
  }

  val string = additionalModules.sorted().joinToString(",")
  val result = Xx3UnencodedString.hashUnencodedString(string, 0).toString(26) +
               Xx3UnencodedString.hashUnencodedString(string, 301236010888646397L).toString(36)
  // - maybe here due to negative number
  return if (result.startsWith('-')) result else "-$result"
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
      catch (ignore: NoSuchFileException) {
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
  if (!communityDotIdea.exists()) {
    val riderSpecificCommunityDotIdea = homePath.parent.resolve("ultimate/community/.idea")
    if (riderSpecificCommunityDotIdea.exists()) {
      communityDotIdea = riderSpecificCommunityDotIdea
    }
  }
  return BuildDependenciesCommunityRoot(if (Files.isDirectory(communityDotIdea)) communityDotIdea.parent else homePath)
}