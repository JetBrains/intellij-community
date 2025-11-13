// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.intellij.build.classPath

import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.platform.util.putMoreLikelyPluginJarsFirst
import org.jdom.Element
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.PLATFORM_LOADER_JAR
import org.jetbrains.intellij.build.PLUGIN_XML_RELATIVE_PATH
import org.jetbrains.intellij.build.UTIL_8_JAR
import org.jetbrains.intellij.build.UTIL_JAR
import org.jetbrains.intellij.build.getUnprocessedPluginXmlContent
import org.jetbrains.intellij.build.impl.DescriptorCacheContainer
import org.jetbrains.intellij.build.impl.ModuleIncludeReasons
import org.jetbrains.intellij.build.impl.PRODUCT_DESCRIPTOR_META_PATH
import org.jetbrains.intellij.build.impl.PlatformJarNames
import org.jetbrains.intellij.build.impl.PlatformJarNames.APP_BACKEND_JAR
import org.jetbrains.intellij.build.impl.PlatformJarNames.APP_JAR
import org.jetbrains.intellij.build.impl.PlatformJarNames.PLATFORM_CORE_NIO_FS
import org.jetbrains.intellij.build.impl.PlatformJarNames.PRODUCT_BACKEND_JAR
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.ScopedCachedDescriptorContainer
import org.jetbrains.intellij.build.impl.filterAndProcessContentModules
import org.jetbrains.intellij.build.impl.projectStructureMapping.CustomAssetEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleOutputEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleOwnedFileEntry
import org.jetbrains.intellij.build.io.ZipEntryProcessorResult
import org.jetbrains.intellij.build.io.readZipFile
import org.jetbrains.intellij.build.isWindows
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.relativeToOrSelf

internal fun generateClassPathByLayoutReport(libDir: Path, entries: List<DistributionFileEntry>, skipNioFs: Boolean): Set<Path> {
  val classPath = LinkedHashSet<Path>()
  for (entry in entries) {
    if (entry is ModuleOwnedFileEntry && entry.owner?.reason == ModuleIncludeReasons.PRODUCT_MODULES) {
      continue
    }

    // exclude files like ext/platform-main.jar - if a file in lib, take only direct children in an account
    if ((entry.relativeOutputFile ?: "").contains('/') && !(entry is ModuleOutputEntry && entry.reason == ModuleIncludeReasons.PRODUCT_EMBEDDED_MODULES)) {
      continue
    }

    if (entry is ModuleOutputEntry) {
      val moduleName = entry.owner.moduleName
      if (moduleName.startsWith("intellij.platform.unitTestMode")) {
        continue
      }
      if (skipNioFs && moduleName == "intellij.platform.core.nio.fs") {
        continue
      }
      val fileName = entry.relativeOutputFile
      if (fileName == PlatformJarNames.TEST_FRAMEWORK_JAR) {
        continue
      }
    }

    val file = entry.path
    val parent = file.parent
    if (parent == libDir) {
      val fileName = file.fileName.toString()
      if (fileName == PlatformJarNames.TEST_FRAMEWORK_JAR) {
        continue
      }

      // This code excludes `PLATFORM_CORE_NIO_FS` because this JAR is supposed to be loaded with the boot classloader.
      // Without this code, it's possible that classes from nio-fs.jar are loaded twice, leading to sporadic `ClassCastException`.
      // nio-fs.jar added via -Xbootclasspath/a
      if (skipNioFs && fileName == PLATFORM_CORE_NIO_FS) {
        continue
      }
    }

    classPath.add(file)
  }

  val result = LinkedHashSet<Path>(classPath.size + 4)
  // add first - should be listed first
  sequenceOf(PLATFORM_LOADER_JAR, UTIL_8_JAR, APP_JAR, UTIL_JAR, PRODUCT_BACKEND_JAR, APP_BACKEND_JAR).map(libDir::resolve).filterTo(result, classPath::contains)
  // sorted to ensure stable performance results
  result.addAll(if (isWindows) classPath.sortedBy(Path::toString) else classPath.sorted())
  return result
}

/**
 * Provides a set of paths that should be included in the core classpath.
 * This generation is based on the plugins distribution,
 * so we would like to include all distribution entities of **embedded** modules (and their libraries) from plugins marked with `use-idea-classloader`.
 */
internal fun generateCoreClasspathFromPlugins(
  platformLayout: PlatformLayout,
  context: BuildContext,
  pluginEntities: List<PluginBuildDescriptor>,
): Set<Path> {
  val classPathResult = mutableSetOf<Path>()
  for (pluginEntity in pluginEntities) {
    val pluginLayout = pluginEntity.layout
    val cacheContainer = platformLayout.descriptorCacheContainer.forPlugin(pluginEntity.dir)
    val classPathModules = getEmbeddedContentModulesOfPluginsWithUseIdeaClassloader(context, pluginLayout.mainModule, cacheContainer)
    for (distributionEntry in pluginEntity.distribution) {
      if (distributionEntry is ModuleOwnedFileEntry && distributionEntry.owner?.moduleName in classPathModules) {
        classPathResult.add(distributionEntry.path)
      }
    }
  }
  return classPathResult
}

/**
 * Provides a set of content modules ("embedded" ones) and the module of the plugin itself, if it uses `use-idea-classloader`.
 * These modules should be included in the core classpath, also their libraries should be treated as platform libraries.
 */
internal fun getEmbeddedContentModulesOfPluginsWithUseIdeaClassloader(
  context: BuildContext,
  pluginMainModule: String,
  cacheContainer: ScopedCachedDescriptorContainer?,
): Set<String> {
  val pluginModule = context.findRequiredModule(pluginMainModule)
  val pluginXmlBytes = cacheContainer?.getCachedFileData(PLUGIN_XML_RELATIVE_PATH) ?: getUnprocessedPluginXmlContent(pluginModule, context)
  val pluginXmlContent = pluginXmlBytes.decodeToString()
  val rootElement = JDOMUtil.load(pluginXmlContent)
  if (rootElement.getAttribute("use-idea-classloader")?.value?.toBoolean() != true) {
    return emptySet()
  }
  val embeddedModules = mutableSetOf(pluginMainModule)
  filterAndProcessContentModules(rootElement, pluginMainModule, context) { _, moduleName, loadingRule ->
    if (loadingRule == "embedded") {
      embeddedModules.add(moduleName)
    }
  }
  return embeddedModules
}

internal data class PluginBuildDescriptor(
  @JvmField val dir: Path,
  @JvmField val os: OsFamily?,
  @JvmField val layout: PluginLayout,
  @JvmField val distribution: Collection<DistributionFileEntry>,
)

internal fun writePluginClassPathHeader(
  out: DataOutputStream,
  isJarOnly: Boolean,
  pluginCount: Int,
  platformLayout: PlatformLayout,
  descriptorCacheContainer: DescriptorCacheContainer,
  context: BuildContext,
) {
  // format version
  out.write(2)
  // jarOnly
  out.write(if (isJarOnly) 1 else 0)

  val mainPluginDescriptorContent = BufferExposingByteArrayOutputStream().use {
    JDOMUtil.write(createCachedProductDescriptor(platformLayout, descriptorCacheContainer.forPlatform(platformLayout), context), it)
    it
  }

  out.writeInt(mainPluginDescriptorContent.size())
  out.write(mainPluginDescriptorContent.internalBuffer, 0, mainPluginDescriptorContent.size())

  // bundled plugin metadata
  out.writeShort(pluginCount)
}

@VisibleForTesting
fun createCachedProductDescriptor(platformLayout: PlatformLayout, platformDescriptorCache: ScopedCachedDescriptorContainer, context: BuildContext): Element {
  val mainPluginDescriptor = requireNotNull(platformDescriptorCache.getCachedFileData(PRODUCT_DESCRIPTOR_META_PATH)) {
    "Cannot find core plugin descriptor (module=${context.productProperties.applicationInfoModule})"
  }.let { JDOMUtil.load(it) }

  val xIncludeResolver = XIncludeElementResolverImpl(
    searchPath = listOf(DescriptorSearchScope(
      modules = platformLayout.includedModules.mapTo(LinkedHashSet()) { it.moduleName },
      descriptorCache = platformDescriptorCache,
    )),
    context = context,
  )
  for (content in mainPluginDescriptor.getChildren("content")) {
    for (moduleElement in content.getChildren("module")) {
      resolveAndEmbedContentModuleDescriptor(moduleElement = moduleElement, descriptorCache = platformDescriptorCache, xIncludeResolver = xIncludeResolver, context = context)
    }
  }

  return mainPluginDescriptor
}

internal fun generatePluginClassPath(
  pluginEntries: List<PluginBuildDescriptor>,
  descriptorFileProvider: DescriptorCacheContainer,
  platformLayout: PlatformLayout,
  context: BuildContext,
): ByteArray {
  val byteOut = ByteArrayOutputStream()
  val out = DataOutputStream(byteOut)

  val uniqueGuard = HashSet<Path>()
  for (pluginAsset in pluginEntries) {
    val pluginDir = pluginAsset.dir

    val files = ArrayList<Path>(pluginAsset.distribution.size)
    uniqueGuard.clear()
    for (entry in pluginAsset.distribution) {
      val relativeOutputFile = entry.relativeOutputFile
      if (relativeOutputFile != null && relativeOutputFile.contains('/')) {
        continue
      }

      val file = entry.path
      if (!uniqueGuard.add(file) || (entry is CustomAssetEntry && !file.toString().endsWith(".jar"))) {
        continue
      }

      files.add(file)

      check(!file.startsWith(pluginDir) || pluginDir.relativize(file).nameCount == 2) {
        "plugin entry is not specified correctly: $file"
      }
    }

    if (files.size > 1) {
      // always sort
      putMoreLikelyPluginJarsFirst(pluginDirName = pluginDir.fileName.toString(), filesInLibUnderPluginDir = files)
    }

    val pluginDescriptorContainer = descriptorFileProvider.forPlugin(pluginDir)
    var pluginDescriptorContent = requireNotNull(pluginDescriptorContainer.getCachedFileData(PLUGIN_XML_RELATIVE_PATH)) {
      "Cannot find plugin descriptor file $PLUGIN_XML_RELATIVE_PATH in $pluginDir (descriptorFileProvider=$descriptorFileProvider"
    }
    val pluginLayout = pluginAsset.layout
    val rootElement = JDOMUtil.load(pluginDescriptorContent)

    if (!pluginLayout.pathsToScramble.isEmpty()) {
      val platformDescriptorContainer = descriptorFileProvider.forPlatform(platformLayout)
      val xIncludeResolver = XIncludeElementResolverImpl(
        searchPath = listOf(
          DescriptorSearchScope(pluginLayout.includedModules.mapTo(LinkedHashSet()) { it.moduleName }, pluginDescriptorContainer),
          DescriptorSearchScope(platformLayout.includedModules.mapTo(LinkedHashSet()) { it.moduleName }, platformDescriptorContainer),
        ),
        context = context)

      embedContentModules(
        rootElement = rootElement,
        pluginLayout = pluginLayout,
        pluginDescriptorContainer = pluginDescriptorContainer,
        xIncludeResolver = xIncludeResolver,
        context = context,
      )
    }

    pluginDescriptorContent = ByteArrayOutputStream().use {
      JDOMUtil.write(rootElement, it)
      it
    }.toByteArray()

    writeEntry(out = out, files = files, pluginDir = pluginDir, pluginDescriptorContent = pluginDescriptorContent)
  }

  out.close()
  return byteOut.toByteArray()
}

private fun readPluginXml(file: Path): ByteArray? {
  var result: ByteArray? = null
  readZipFile(file) { name, dataProvider ->
    if (name == PLUGIN_XML_RELATIVE_PATH) {
      val byteBuffer = dataProvider()
      val bytes = ByteArray(byteBuffer.remaining())
      byteBuffer.get(bytes, 0, bytes.size)
      result = bytes
      ZipEntryProcessorResult.STOP
    }
    else {
      ZipEntryProcessorResult.CONTINUE
    }
  }
  return result
}

private fun writeEntry(out: DataOutputStream, files: Collection<Path>, pluginDir: Path, pluginDescriptorContent: ByteArray) {
  // the plugin dir as the last item in the list
  out.writeShort(files.size)
  out.writeUTF(pluginDir.fileName.invariantSeparatorsPathString)

  out.writeInt(pluginDescriptorContent.size)
  out.write(pluginDescriptorContent)

  for (file in files) {
    out.writeUTF(file.relativeToOrSelf(pluginDir).invariantSeparatorsPathString)
  }
}

internal fun generatePluginClassPathFromPrebuiltPluginFiles(pluginEntries: List<Pair<Path, List<Path>>>): ByteArray {
  val byteOut = ByteArrayOutputStream()
  val out = DataOutputStream(byteOut)

  for ((pluginDir, entries) in pluginEntries) {
    val files = entries.toMutableList()
    if (files.size > 1) {
      // always sort
      putMoreLikelyPluginJarsFirst(pluginDirName = pluginDir.fileName.toString(), filesInLibUnderPluginDir = files)
    }

    // move a dir with "plugin.xml" to the top (it may not exist if for some reason the main module dir still being packed into JAR)
    writeEntry(out = out, files = files, pluginDir = pluginDir, pluginDescriptorContent = reorderPluginClassPath(files))
  }

  out.close()
  return byteOut.toByteArray()
}

private fun reorderPluginClassPath(files: MutableList<Path>): ByteArray {
  for ((index, file) in files.withIndex()) {
    val pluginDescriptorContent = readPluginXml(file)
    if (pluginDescriptorContent != null) {
      files.add(0, files.removeAt(index))
      return pluginDescriptorContent
    }
  }

  throw IllegalStateException("plugin descriptor is not found among\n  ${files.joinToString(separator = "\n  ")}")
}