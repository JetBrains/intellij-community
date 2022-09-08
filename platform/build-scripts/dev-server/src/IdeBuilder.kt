// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "BlockingMethodInNonBlockingContext", "ReplaceNegatedIsEmptyWithIsNotEmpty")
package org.jetbrains.intellij.build.devServer

import com.intellij.diagnostic.telemetry.useWithScope
import com.intellij.diagnostic.telemetry.useWithScope2
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.PathUtilRt
import com.intellij.util.lang.PathClassLoader
import com.intellij.util.lang.UrlClassLoader
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.*
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.impl.*
import org.jetbrains.intellij.build.impl.projectStructureMapping.LibraryFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleOutputEntry
import org.jetbrains.jps.model.artifact.JpsArtifactService
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.util.JpsPathUtil
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

internal const val UNMODIFIED_MARK_FILE_NAME = ".unmodified"
private const val PLUGIN_CACHE_DIR_NAME = "plugin-cache"

private suspend fun computeLibClassPath(targetFile: Path, homePath: Path, context: BuildContext) {
  spanBuilder("compute lib classpath").useWithScope2 {
    Files.writeString(targetFile, createLibClassPath(homePath, context))
  }
}

internal class IdeBuilder(internal val pluginBuilder: PluginBuilder,
                          outDir: Path,
                          moduleNameToPlugin: Map<String, PluginBuildDescriptor>) {
  private class ModuleChangeInfo(@JvmField val moduleName: String,
                                 @JvmField var checkFile: Path,
                                 @JvmField var plugin: PluginBuildDescriptor)

  private val moduleChanges = moduleNameToPlugin.entries.map {
    val checkFile = outDir.resolve(it.key).resolve(UNMODIFIED_MARK_FILE_NAME)
    ModuleChangeInfo(moduleName = it.key, checkFile = checkFile, plugin = it.value)
  }

  fun checkChanged() {
    spanBuilder("check changes").useWithScope { span ->
      var changedModules = 0
      for (item in moduleChanges) {
        if (Files.notExists(item.checkFile)) {
          pluginBuilder.addDirtyPluginDir(item.plugin, item.moduleName)
          changedModules++
        }
      }
      span.setAttribute(AttributeKey.longKey("changedModuleCount"), changedModules.toLong())
    }
  }
}

internal suspend fun buildProduct(productConfiguration: ProductConfiguration,
                                  homePath: Path,
                                  outDir: Path,
                                  additionalModules: List<String>,
                                  platformPrefix: String,
                                  isServerMode: Boolean): IdeBuilder {
  val runDir = withContext(Dispatchers.IO) {
    val usePluginCache = spanBuilder("check plugin cache applicability").useWithScope2 {
      checkBuildModulesModificationAndMark(productConfiguration, outDir)
    }
    createRunDirForProduct(homePath = homePath, platformPrefix = platformPrefix, usePluginCache = usePluginCache)
  }

  val context = createBuildContext(productConfiguration = productConfiguration,
                                   outDir = outDir,
                                   homePath = homePath,
                                   runDir = runDir,
                                   isServerMode = isServerMode)

  val bundledMainModuleNames = getBundledMainModuleNames(context.productProperties, additionalModules)

  val pluginRootDir = runDir.resolve("plugins")
  val pluginCacheRootDir = runDir.resolve(PLUGIN_CACHE_DIR_NAME)

  val moduleNameToPluginBuildDescriptor = HashMap<String, PluginBuildDescriptor>()
  val pluginBuildDescriptors = mutableListOf<PluginBuildDescriptor>()
  for (plugin in context.productProperties.productLayout.pluginLayouts) {
    if (!isPluginApplicable(bundledMainModuleNames = bundledMainModuleNames, plugin = plugin, context = context)) {
      continue
    }

    // remove all modules without content root
    val modules = plugin.includedModuleNames
      .filter { it != plugin.mainModule && context.findRequiredModule(it).contentRootsList.urls.isEmpty() }
      .toList()
    val pluginBuildDescriptor = PluginBuildDescriptor(dir = pluginRootDir.resolve(plugin.directoryName),
                                                      layout = plugin,
                                                      moduleNames = modules)
    for (name in pluginBuildDescriptor.moduleNames) {
      moduleNameToPluginBuildDescriptor.put(name, pluginBuildDescriptor)
    }
    pluginBuildDescriptors.add(pluginBuildDescriptor)
  }

  val artifactOutDir = homePath.resolve("out/classes/artifacts").toString()
  for (artifact in JpsArtifactService.getInstance().getArtifacts(context.project)) {
    artifact.outputPath = "$artifactOutDir/${PathUtilRt.getFileName(artifact.outputPath)}"
  }

  // initial building
  val pluginBuilder = PluginBuilder(outDir = outDir,
                                    pluginRootDir = pluginRootDir,
                                    pluginCacheRootDir = pluginCacheRootDir,
                                    context = context)
  coroutineScope {
    withContext(Dispatchers.IO) {
      Files.createDirectories(pluginRootDir)
    }
    spanBuilder("build plugins").setAttribute(AttributeKey.longKey("count"), pluginBuildDescriptors.size.toLong()).useWithScope2 {
      initialBuild(pluginBuildDescriptors = pluginBuildDescriptors, pluginBuilder = pluginBuilder)
    }
    launch {
      computeLibClassPath(targetFile = runDir.resolve(if (isServerMode) "libClassPath.txt" else "core-classpath.txt"),
                          homePath = homePath,
                          context = context)
    }
  }
  return IdeBuilder(pluginBuilder = pluginBuilder, outDir = outDir, moduleNameToPlugin = moduleNameToPluginBuildDescriptor)
}

private suspend fun createBuildContext(productConfiguration: ProductConfiguration,
                                       outDir: Path,
                                       homePath: Path,
                                       runDir: Path,
                                       isServerMode: Boolean): BuildContext {
  return coroutineScope {
    // ~1 second
    val productProperties = async {
      createProductProperties(productConfiguration, outDir, homePath)
    }

    // load project is executed as part of compilation context creation - ~1 second
    val compilationContext = async {
      spanBuilder("create build context").useWithScope2 {
        CompilationContextImpl.createCompilationContext(
          communityHome = getCommunityHomePath(homePath),
          projectHome = homePath,
          buildOutputRootEvaluator = { _ -> runDir },
          options = createBuildOptions(runDir).also { it.setupTracer = isServerMode }
        )
      }
    }

    BuildContextImpl.createContext(compilationContext = compilationContext.await(),
                                   projectHome = homePath,
                                   productProperties = productProperties.await()
    )
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
                                       context = context) ||
         satisfiesBundlingRequirements(plugin = plugin,
                                       osFamily = null,
                                       arch = JvmArchitecture.currentJvmArch,
                                       context = context)
}

private suspend fun createProductProperties(productConfiguration: ProductConfiguration, outDir: Path, homePath: Path): ProductProperties {
  val classLoader = spanBuilder("create product properties classloader").useWithScope2 {
    PathClassLoader(
      UrlClassLoader.build()
        .useCache()
        .files(getBuildModules(productConfiguration).map { outDir.resolve(it) }.toList())
        .parent(IdeBuilder::class.java.classLoader)
    )
  }

  val productProperties = spanBuilder("create product properties").useWithScope2 {
    val productPropertiesClass = classLoader.loadClass(productConfiguration.className)
    MethodHandles.lookup()
      .findConstructor(productPropertiesClass, MethodType.methodType(Void.TYPE, Path::class.java))
      .invoke(homePath) as ProductProperties
  }
  return productProperties
}

private fun checkBuildModulesModificationAndMark(productConfiguration: ProductConfiguration, outDir: Path): Boolean {
  // intellij.platform.devBuildServer
  var isApplicable = true
  for (module in getBuildModules(productConfiguration) + sequenceOf("intellij.platform.devBuildServer",
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

    try {
      Files.newByteChannel(markFile, TOUCH_OPTIONS)
    }
    catch (ignore: NoSuchFileException) {
    }
  }
  return isApplicable
}

private fun getBuildModules(productConfiguration: ProductConfiguration): Sequence<String> {
  return sequenceOf("intellij.idea.community.build") + productConfiguration.modules.asSequence()
}

@Suppress("SpellCheckingInspection")
private val extraJarNames = arrayOf("ideaLicenseDecoder.jar", "ls-client-api.jar", "y.jar", "ysvg.jar")

@Suppress("KotlinConstantConditions")
private suspend fun createLibClassPath(homePath: Path, context: BuildContext): String {
  val platformLayout = createPlatformLayout(pluginsToPublish = emptySet(), context = context)
  val isPackagedLib = System.getProperty("dev.server.pack.lib") == "true"
  val projectStructureMapping = processLibDirectoryLayout(moduleOutputPatcher = ModuleOutputPatcher(),
                                                          platform = platformLayout,
                                                          context = context,
                                                          copyFiles = isPackagedLib)
  // for some reasons maybe duplicated paths - use set
  val classPath = LinkedHashSet<String>()
  if (isPackagedLib) {
    projectStructureMapping.mapTo(classPath) { it.path.toString() }
  }
  else {
    for (entry in projectStructureMapping) {
      when (entry) {
        is ModuleOutputEntry -> {
          if (isPackagedLib) {
            classPath.add(entry.path.toString())
          }
          else {
            classPath.add(context.getModuleOutputDir(context.findRequiredModule(entry.moduleName)).toString())
          }
        }
        is LibraryFileEntry -> {
          if (isPackagedLib) {
            classPath.add(entry.path.toString())
          }
          else {
            classPath.add(entry.libraryFile.toString())
          }
        }
        else -> throw UnsupportedOperationException("Entry $entry is not supported")
      }
    }

    for (libName in platformLayout.projectLibrariesToUnpack.values()) {
      val library = context.project.libraryCollection.findLibrary(libName) ?: throw IllegalStateException("Cannot find library $libName")
      library.getRootUrls(JpsOrderRootType.COMPILED).mapTo(classPath, JpsPathUtil::urlToPath)
    }
  }

  val projectLibDir = homePath.resolve("lib")
  for (extraJarName in extraJarNames) {
    val extraJar = projectLibDir.resolve(extraJarName)
    if (Files.exists(extraJar)) {
      classPath.add(extraJar.toString())
    }
  }
  return classPath.joinToString(separator = "\n")
}

private fun getBundledMainModuleNames(productProperties: ProductProperties, additionalModules: List<String>): Set<String> {
  val bundledPlugins = LinkedHashSet(productProperties.productLayout.bundledPluginModules)
  if (!additionalModules.isEmpty()) {
    bundledPlugins.addAll(additionalModules)
  }
  bundledPlugins.removeAll(skippedPluginModules)
  return bundledPlugins
}

internal fun getAdditionalModules(): Sequence<String>? {
  return (System.getProperty("additional.modules") ?: System.getProperty("additional.plugins") ?: return null)
    .splitToSequence(',')
    .map(String::trim)
    .filter { it.isNotEmpty() }
}

private fun createRunDirForProduct(homePath: Path, platformPrefix: String, usePluginCache: Boolean): Path {
  // if symlinked to ram disk, use real path for performance reasons and avoid any issues in ant/other code
  var rootDir = homePath.resolve("out/dev-run")
  if (Files.exists(rootDir)) {
    // toRealPath must be called only on existing file
    rootDir = rootDir.toRealPath()
  }

  val runDir = rootDir.resolve(platformPrefix)
  // on start delete everything to avoid stale data
  if (!Files.isDirectory(runDir)) {
    Files.createDirectories(runDir)
    return runDir
  }

  Files.newDirectoryStream(runDir).use { stream ->
    for (child in stream) {
      if (usePluginCache && child.endsWith("plugins")) {
        // move to cache
        val pluginCache = runDir.resolve(PLUGIN_CACHE_DIR_NAME)
        NioFiles.deleteRecursively(pluginCache)
        Files.move(child, pluginCache)
      }
      if (child.endsWith("log")) {
        clearDirContent(child)
      }
      else {
        NioFiles.deleteRecursively(child)
      }
    }
  }
  return runDir
}

private fun getCommunityHomePath(homePath: Path): BuildDependenciesCommunityRoot {
  val communityDotIdea = homePath.resolve("community/.idea")
  return BuildDependenciesCommunityRoot(if (Files.isDirectory(communityDotIdea)) communityDotIdea.parent else homePath)
}

private fun createBuildOptions(runDir: Path): BuildOptions {
  val options = BuildOptions()
  options.printFreeSpace = false
  options.useCompiledClassesFromProjectOutput = true
  options.targetOs = BuildOptions.OS_NONE
  options.cleanOutputFolder = false
  options.skipDependencySetup = true
  options.outputRootPath = runDir
  options.buildStepsToSkip.add(BuildOptions.PREBUILD_SHARED_INDEXES)
  return options
}