// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.devServer

import com.intellij.util.PathUtilRt
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.ProprietaryBuildTools
import org.jetbrains.intellij.build.impl.DistributionJARsBuilder
import org.jetbrains.intellij.build.impl.LayoutBuilder
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleLibraryFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleOutputEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectLibraryEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectStructureMapping
import org.jetbrains.jps.model.artifact.JpsArtifactService
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class IdeBuilder(val pluginBuilder: PluginBuilder,
                 builder: DistributionJARsBuilder,
                 homePath: Path,
                 runDir: Path,
                 private val moduleNameToPlugin: Map<String, BuildItem>) {
  fun moduleChanged(moduleName: String, reason: Any) {
    val plugin = moduleNameToPlugin.get(moduleName) ?: return
    pluginBuilder.addDirtyPluginDir(plugin, reason)
  }

  init {
    Files.writeString(runDir.resolve("libClassPath.txt"), createLibClassPath(pluginBuilder.buildContext, builder, homePath))
  }
}

internal fun initialBuild(productConfiguration: ProductConfiguration, homePath: Path, outDir: Path): IdeBuilder {
  val productProperties = URLClassLoader.newInstance(productConfiguration.modules.map { outDir.resolve(it).toUri().toURL() }.toTypedArray())
    .loadClass(productConfiguration.className)
    .getConstructor(String::class.java).newInstance(homePath.toString()) as ProductProperties

  val allNonTrivialPlugins = productProperties.productLayout.allNonTrivialPlugins
  val bundledMainModuleNames = getBundledMainModuleNames(productProperties)

  val platformPrefix = productProperties.platformPrefix ?: "idea"
  val runDir = createRunDirForProduct(homePath, platformPrefix)

  val buildContext = BuildContext.createContext(getCommunityHomePath(homePath).toString(), homePath.toString(), productProperties,
                                                ProprietaryBuildTools.DUMMY, createBuildOptions(homePath))
  val pluginsDir = runDir.resolve("plugins")

  val mainModuleToNonTrivialPlugin = HashMap<String, BuildItem>(bundledMainModuleNames.size)
  for (plugin in allNonTrivialPlugins) {
    if (bundledMainModuleNames.contains(plugin.mainModule)) {
      val item = BuildItem(pluginsDir.resolve(DistributionJARsBuilder.getActualPluginDirectoryName(plugin, buildContext)), plugin)
      mainModuleToNonTrivialPlugin.put(plugin.mainModule, item)
    }
  }

  val moduleNameToPlugin = HashMap<String, BuildItem>()
  val pluginLayouts = bundledMainModuleNames.mapNotNull { mainModuleName ->
    // by intention we don't use buildContext.findModule as getPluginsByModules does - module name must match
    // (old module names are not supported)
    var item = mainModuleToNonTrivialPlugin.get(mainModuleName)
    if (item == null) {
      val pluginLayout = PluginLayout.plugin(mainModuleName)
      val pluginDir = pluginsDir.resolve(DistributionJARsBuilder.getActualPluginDirectoryName(pluginLayout, buildContext))
      item = BuildItem(pluginDir, PluginLayout.plugin(mainModuleName))
    }
    else {
      for (entry in item.layout.moduleJars.entrySet()) {
        if (!entry.key.contains('/')) {
          for (name in entry.value) {
            moduleNameToPlugin.put(name, item)
          }
        }
      }
    }
    moduleNameToPlugin.put(mainModuleName, item)
    item
  }

  val artifactOutDir = homePath.resolve("out/classes/artifacts").toString()
  JpsArtifactService.getInstance().getArtifacts(buildContext.project).forEach {
    it.outputPath = "$artifactOutDir/${PathUtilRt.getFileName(it.outputPath)}"
  }

  val builder = DistributionJARsBuilder(buildContext, null)

  // initial building
  val start = System.currentTimeMillis()
  // Ant is not able to build in parallel — not clear how correctly clone with all defined custom tasks
  buildPlugins(parallelCount = 1, buildContext, pluginLayouts, builder)
  LOG.info("Initial full build of ${pluginLayouts.size} plugins in ${TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - start)}s")

  val pluginBuilder = PluginBuilder(builder, buildContext, outDir)
  return IdeBuilder(pluginBuilder, builder, homePath, runDir, moduleNameToPlugin)
}

private fun createLibClassPath(buildContext: BuildContext,
                               builder: DistributionJARsBuilder,
                               homePath: Path): String {
  val layoutBuilder = LayoutBuilder(buildContext, false)
  val projectStructureMapping = ProjectStructureMapping()
  builder.processLibDirectoryLayout(layoutBuilder, projectStructureMapping, false)
  // for some reasons maybe duplicated paths - use set
  val classPath = LinkedHashSet<String>()
  for (entry in projectStructureMapping.entries) {
    when (entry) {
      is ModuleOutputEntry -> {
        classPath.add(buildContext.getModuleOutputPath(buildContext.findRequiredModule(entry.moduleName)))
      }
      is ProjectLibraryEntry -> {
        classPath.add(entry.libraryFilePath)
      }
      is ModuleLibraryFileEntry -> {
        classPath.add(entry.libraryFilePath)
      }
      else -> throw UnsupportedOperationException("Entry $entry is not supported")
    }
  }

  val projectLibDir = homePath.resolve("lib")
  @Suppress("SpellCheckingInspection")
  val extraJarNames = listOf("ideaLicenseDecoder.jar", "ls-client-api.jar", "y.jar", "ysvg.jar")
  for (extraJarName in extraJarNames) {
    val extraJar = projectLibDir.resolve(extraJarName)
    if (Files.exists(extraJar)) {
      classPath.add(extraJar.toAbsolutePath().toString())
    }
  }
  return classPath.joinToString(separator = "\n")
}

private fun getBundledMainModuleNames(productProperties: ProductProperties): Set<String> {
  val bundledPlugins = LinkedHashSet(productProperties.productLayout.bundledPluginModules)
  getAdditionalModules()?.let {
    bundledPlugins.addAll(it)
  }
  bundledPlugins.removeAll(skippedPluginModules)
  return bundledPlugins
}

fun getAdditionalModules(): Sequence<String>? {
  return (System.getProperty("additional.modules") ?: System.getProperty("additional.plugins") ?: return null)
    .splitToSequence(',')
    .map(String::trim)
    .filter { it.isNotEmpty() }
}

private fun createRunDirForProduct(homePath: Path, platformPrefix: String): Path {
  // if symlinked to ram disk, use real path for performance reasons and avoid any issues in ant/other code
  var rootDir = homePath.resolve("out/dev-run")
  if (Files.exists(rootDir)) {
    // toRealPath must be called only on existing file
    rootDir = rootDir.toRealPath()
  }

  val runDir = rootDir.resolve(platformPrefix)
  // on start delete everything to avoid stale data
  clearDirContent(runDir)
  Files.createDirectories(runDir)
  return runDir
}

private fun getCommunityHomePath(homePath: Path): Path {
  val communityDotIdea = homePath.resolve("community/.idea")
  return if (Files.isDirectory(communityDotIdea)) communityDotIdea.parent else homePath
}

private fun createBuildOptions(homePath: Path): BuildOptions {
  val buildOptions = BuildOptions()
  buildOptions.useCompiledClassesFromProjectOutput = true
  buildOptions.targetOS = BuildOptions.OS_NONE
  buildOptions.cleanOutputFolder = false
  buildOptions.skipDependencySetup = true
  buildOptions.outputRootPath = homePath.resolve("out/dev-server").toString()
  return buildOptions
}