// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")
package org.jetbrains.intellij.build.devServer

import com.intellij.util.PathUtilRt
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.ProprietaryBuildTools
import org.jetbrains.intellij.build.impl.DistributionJARsBuilder
import org.jetbrains.intellij.build.impl.ModuleOutputPatcher
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry.LibraryFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleOutputEntry
import org.jetbrains.jps.model.artifact.JpsArtifactService
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.util.JpsPathUtil
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

internal const val UNMODIFIED_MARK_FILE_NAME = ".unmodified"

class IdeBuilder(val pluginBuilder: PluginBuilder,
                 homePath: Path,
                 runDir: Path,
                 outDir: Path,
                 moduleNameToPlugin: Map<String, BuildItem>) {
  private class ModuleChangeInfo(@JvmField val moduleName: String,
                                 @JvmField var checkFile: Path,
                                 @JvmField var plugin: BuildItem)

  private val moduleChanges = moduleNameToPlugin.entries.map {
    val checkFile = outDir.resolve(it.key).resolve(UNMODIFIED_MARK_FILE_NAME)
    ModuleChangeInfo(moduleName = it.key, checkFile = checkFile, plugin = it.value)
  }

  init {
    Files.writeString(runDir.resolve("libClassPath.txt"), createLibClassPath(pluginBuilder.buildContext, homePath))
  }

  fun checkChanged() {
    LOG.info("Checking changes...")
    var changedModules = 0
    for (item in moduleChanges) {
      if (!Files.exists(item.checkFile)) {
        pluginBuilder.addDirtyPluginDir(item.plugin, item.moduleName)
        changedModules++
      }
    }
    LOG.info("$changedModules changed modules")
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
                                                ProprietaryBuildTools.DUMMY, createBuildOptions(runDir))
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
    // by intention, we don't use buildContext.findModule as getPluginsByModules does - module name must match
    // (old module names are not supported)
    var item = mainModuleToNonTrivialPlugin.get(mainModuleName)
    if (item == null) {
      val pluginLayout = PluginLayout.plugin(mainModuleName)
      val pluginDir = pluginsDir.resolve(DistributionJARsBuilder.getActualPluginDirectoryName(pluginLayout, buildContext))
      item = BuildItem(pluginDir, PluginLayout.plugin(mainModuleName))
    }
    else {
      for (entry in item.layout.jarToIncludedModuleNames) {
        if (!entry.key.contains('/')) {
          for (name in entry.value) {
            moduleNameToPlugin.put(name, item)
          }
          item.moduleNames.addAll(entry.value)
        }
      }
    }

    item.moduleNames.add(mainModuleName)
    moduleNameToPlugin.put(mainModuleName, item)
    item
  }

  val artifactOutDir = homePath.resolve("out/classes/artifacts").toString()
  for (artifact in JpsArtifactService.getInstance().getArtifacts(buildContext.project)) {
    artifact.outputPath = "$artifactOutDir/${PathUtilRt.getFileName(artifact.outputPath)}"
  }

  // initial building
  val start = System.currentTimeMillis()
  val pluginBuilder = PluginBuilder(buildContext, outDir)
  pluginBuilder.initialBuild(plugins = pluginLayouts)
  LOG.info("Initial full build of ${pluginLayouts.size} plugins in ${TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - start)}s")
  return IdeBuilder(pluginBuilder = pluginBuilder,
                    homePath = homePath,
                    runDir = runDir,
                    outDir = outDir,
                    moduleNameToPlugin = moduleNameToPlugin)
}

private fun createLibClassPath(context: BuildContext, homePath: Path): String {
  val platformLayout = DistributionJARsBuilder.createPlatformLayout(emptySet(), context)
  val isPackagedLib = System.getProperty("dev.server.pack.lib") == "true"
  val projectStructureMapping = DistributionJARsBuilder.processLibDirectoryLayout(ModuleOutputPatcher(),
                                                                                  platformLayout,
                                                                                  context,
                                                                                  isPackagedLib).fork().join()
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
      library.getRootUrls(JpsOrderRootType.COMPILED).mapTo(classPath) {
        JpsPathUtil.urlToPath(it)
      }
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
    println("Additional modules: ${it.joinToString()}")
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

private fun createBuildOptions(runDir: Path): BuildOptions {
  val buildOptions = BuildOptions()
  buildOptions.useCompiledClassesFromProjectOutput = true
  buildOptions.targetOS = BuildOptions.OS_NONE
  buildOptions.cleanOutputFolder = false
  buildOptions.skipDependencySetup = true
  buildOptions.outputRootPath = runDir.toString()
  buildOptions.buildStepsToSkip.add(BuildOptions.PREBUILD_SHARED_INDEXES)
  return buildOptions
}