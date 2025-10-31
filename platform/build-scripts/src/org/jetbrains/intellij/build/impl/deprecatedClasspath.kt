// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.JDOMUtil
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.MAVEN_REPO
import org.jetbrains.intellij.build.classPath.DescriptorSearchScope
import org.jetbrains.intellij.build.classPath.PLUGIN_XML_RELATIVE_PATH
import org.jetbrains.intellij.build.classPath.PluginBuildDescriptor
import org.jetbrains.intellij.build.classPath.XIncludeElementResolverImpl
import org.jetbrains.intellij.build.getUnprocessedPluginXmlContent
import org.jetbrains.intellij.build.impl.projectStructureMapping.CustomAssetEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.LibraryFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleLibraryFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleOutputEntry
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

@Deprecated("Do not use it")
suspend fun createIdeClassPath(platformLayout: PlatformLayout, context: BuildContext): Collection<String> {
  val contentReport = generateProjectStructureMapping(platformLayout = platformLayout, context = context)
  val pluginLayouts = context.productProperties.productLayout.pluginLayouts
  val classPath = LinkedHashSet<Path>()

  val libDir = context.paths.distAllDir.resolve("lib")
  for (entry in contentReport.first) {
    if (entry !is ModuleOutputEntry || !ModuleIncludeReasons.isProductModule(entry.reason)) {
      val relativePath = libDir.relativize(entry.path)
      if (relativePath.nameCount != 1) {
        continue
      }
    }

    if (entry is ModuleLibraryFileEntry && entry.libraryName == "jetbrains.lets.plot.shadowed") {
      continue
    }

    when (entry) {
      is ModuleOutputEntry -> {
        classPath.addAll(context.getModuleOutputRoots(context.findRequiredModule(entry.owner.moduleName)))
      }
      is LibraryFileEntry -> classPath.add(entry.libraryFile!!)
      else -> throw UnsupportedOperationException("Entry $entry is not supported")
    }
  }

  val pluginDir = context.paths.distAllDir.resolve(PLUGINS_DIRECTORY)
  for (entry in contentReport.second.flatMap { it.distribution }) {
    val relativePath = pluginDir.relativize(entry.path)
    // for plugins, our classloaders load JARs only from the "lib/" and "lib/modules/" directories
    if (!(relativePath.nameCount in 3..4 && relativePath.getName(1).toString() == LIB_DIRECTORY &&
          (relativePath.nameCount == 3 || relativePath.getName(2).toString() == "modules"))) {
      continue
    }

    when (entry) {
      is ModuleOutputEntry -> {
        classPath.addAll(context.getModuleOutputRoots(context.findRequiredModule(entry.owner.moduleName)))
        for (classpathPluginEntry in pluginLayouts.firstOrNull { it.mainModule == entry.owner.moduleName }?.scrambleClasspathPlugins ?: emptyList()) {
          classPath.addAll(context.getModuleOutputRoots(context.findRequiredModule(classpathPluginEntry.pluginMainModuleName)))
        }
      }
      is LibraryFileEntry -> classPath.add(entry.libraryFile!!)
      is CustomAssetEntry -> {}
      else -> throw UnsupportedOperationException("Entry $entry is not supported")
    }
  }
  return classPath.map { it.toString() }
}

private fun sortEntries(unsorted: Collection<DistributionFileEntry>): List<DistributionFileEntry> {
  // sort because projectStructureMapping is a concurrent collection
  // call invariantSeparatorsPathString because the result of Path ordering is platform-dependent
  return unsorted.sortedWith(
    compareBy(
      { it.path.invariantSeparatorsPathString },
      { it.type },
      { (it as? ModuleOutputEntry)?.owner?.moduleName },
      { (it as? LibraryFileEntry)?.libraryFile?.let(::isFromLocalMavenRepo) != true },
      { (it as? LibraryFileEntry)?.libraryFile?.invariantSeparatorsPathString },
    )
  )
}

// also, put libraries from Maven repo ahead of others, for them to not depend on the lexicographical order of Maven repo and source path
private fun isFromLocalMavenRepo(path: Path) = path.startsWith(MAVEN_REPO)

private suspend fun generateProjectStructureMapping(
  platformLayout: PlatformLayout,
  context: BuildContext,
): Pair<List<DistributionFileEntry>, List<PluginBuildDescriptor>> = coroutineScope {
  val moduleOutputPatcher = ModuleOutputPatcher()
  val libDirLayout = async(CoroutineName("layout platform distribution")) {
    sortEntries(JarPackager.pack(
      includedModules = platformLayout.includedModules,
      outputDir = context.paths.distAllDir.resolve(LIB_DIRECTORY),
      isRootDir = true,
      layout = platformLayout,
      platformLayout = platformLayout,
      moduleOutputPatcher = moduleOutputPatcher,
      searchableOptionSet = null,
      dryRun = true,
      descriptorCache = null,
      context = context,
    ))
  }

  val descriptorCacheContainer = DescriptorCacheContainer()
  val platformDescriptorCache = descriptorCacheContainer.forPlatform(platformLayout)

  val allPlugins = getPluginLayoutsByJpsModuleNames(modules = context.getBundledPluginModules(), productLayout = context.productProperties.productLayout)
  val entries = mutableListOf<PluginBuildDescriptor>()
  for (pluginLayout in allPlugins) {
    if (!satisfiesBundlingRequirements(plugin = pluginLayout, osFamily = null, arch = null, context = context)) {
      continue
    }

    val targetDir = context.paths.distAllDir.resolve(PLUGINS_DIRECTORY).resolve(pluginLayout.directoryName)
    val pluginModule = context.findRequiredModule(pluginLayout.mainModule)
    val descriptorContent = pluginLayout.rawPluginXmlPatcher(getUnprocessedPluginXmlContent(pluginModule, context).decodeToString(), context)
    val element = JDOMUtil.load(descriptorContent)

    // we need to put descriptorContent into cache as computeModuleSourcesByContent expects
    val pluginDescriptorCache = descriptorCacheContainer.forPlugin(targetDir)
    val xIncludeResolver = XIncludeElementResolverImpl(
      searchPath = listOf(
        DescriptorSearchScope(pluginLayout.includedModules.mapTo(LinkedHashSet()) { it.moduleName }, pluginDescriptorCache),
        DescriptorSearchScope(platformLayout.includedModules.mapTo(LinkedHashSet()) { it.moduleName }, platformDescriptorCache),
      ),
      context = context
    )
    resolveIncludes(element = element, elementResolver = xIncludeResolver)

    filterAndProcessContentModules(rootElement = element, pluginMainModuleName = pluginLayout.mainModule, context = context) { _, _, _ ->
      // no need to embed modules
    }

    pluginDescriptorCache.put(PLUGIN_XML_RELATIVE_PATH, JDOMUtil.write(element).encodeToByteArray())

    val outputDir = targetDir.resolve(LIB_DIRECTORY)
    val pluginEntries = JarPackager.pack(
      includedModules = pluginLayout.includedModules,
      outputDir = outputDir,
      isRootDir = false,
      layout = pluginLayout,
      platformLayout = platformLayout,
      moduleOutputPatcher = moduleOutputPatcher,
      searchableOptionSet = null,
      dryRun = true,
      descriptorCache = pluginDescriptorCache,
      context = context,
    )
    entries.add(PluginBuildDescriptor(dir = targetDir, os = null, layout = pluginLayout, distribution = pluginEntries))
  }
  libDirLayout.await() to entries
}