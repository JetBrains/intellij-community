// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.intellij.build

import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.platform.util.putMoreLikelyPluginJarsFirst
import org.jdom.CDATA
import org.jdom.Element
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.intellij.build.impl.CachedDescriptorContainer
import org.jetbrains.intellij.build.impl.ModuleIncludeReasons
import org.jetbrains.intellij.build.impl.ModuleOutputPatcher
import org.jetbrains.intellij.build.impl.ModuleOutputProvider
import org.jetbrains.intellij.build.impl.PlatformJarNames
import org.jetbrains.intellij.build.impl.PlatformJarNames.APP_BACKEND_JAR
import org.jetbrains.intellij.build.impl.PlatformJarNames.APP_JAR
import org.jetbrains.intellij.build.impl.PlatformJarNames.PLATFORM_CORE_NIO_FS
import org.jetbrains.intellij.build.impl.PlatformJarNames.PRODUCT_BACKEND_JAR
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.XIncludeElementResolver
import org.jetbrains.intellij.build.impl.createXIncludePathResolver
import org.jetbrains.intellij.build.impl.findFileInModuleSources
import org.jetbrains.intellij.build.impl.projectStructureMapping.CustomAssetEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleOutputEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleOwnedFileEntry
import org.jetbrains.intellij.build.impl.resolveNonXIncludeElementFromCache
import org.jetbrains.intellij.build.impl.toLoadPath
import org.jetbrains.intellij.build.io.ZipEntryProcessorResult
import org.jetbrains.intellij.build.io.readZipFile
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
      if (TEST_FRAMEWORK_MODULE_NAMES.contains(moduleName) || moduleName.startsWith("intellij.platform.unitTestMode")) {
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

internal data class PluginBuildDescriptor(
  @JvmField val dir: Path,
  @JvmField val os: OsFamily?,
  @JvmField val layout: PluginLayout,
  @JvmField val moduleNames: List<String>,
)

internal fun writePluginClassPathHeader(
  out: DataOutputStream,
  isJarOnly: Boolean,
  pluginCount: Int,
  platformLayout: PlatformLayout,
  context: BuildContext,
) {
  // format version
  out.write(2)
  // jarOnly
  out.write(if (isJarOnly) 1 else 0)

  val mainPluginDescriptorContent = BufferExposingByteArrayOutputStream().use {
    JDOMUtil.write(createCachedProductDescriptor(platformLayout, context), it)
    it
  }

  out.writeInt(mainPluginDescriptorContent.size())
  out.write(mainPluginDescriptorContent.internalBuffer, 0, mainPluginDescriptorContent.size())

  // bundled plugin metadata
  out.writeShort(pluginCount)
}

@VisibleForTesting
fun createCachedProductDescriptor(platformLayout: PlatformLayout, context: BuildContext): Element {
  val cachedDescriptorContainer = platformLayout.cachedDescriptorContainer
  val mainPluginDescriptor = requireNotNull(cachedDescriptorContainer.productDescriptor) {
    "Cannot find core plugin descriptor (module=${context.productProperties.applicationInfoModule})"
  }

  val xIncludeResolver = object : XIncludeElementResolver {
    private val default by lazy {
      createXIncludePathResolver(
        includedPlatformModulesPartialList = platformLayout.includedModules.asSequence().map { it.moduleName }.distinct().toList(),
        context = context,
      )
    }

    override fun resolveElement(relativePath: String, isOptional: Boolean, isDynamic: Boolean): Element? {
      platformLayout.cachedDescriptorContainer.getCachedFileData(toLoadPath(relativePath))?.let {
        return JDOMUtil.load(it)
      }
      return default.resolvePath(relativePath = relativePath, base = null, isOptional = isOptional, isDynamic = isDynamic)?.let {
        JDOMUtil.load(it)
      }
    }
  }

  for (content in mainPluginDescriptor.getChildren("content")) {
    for (moduleElement in content.getChildren("module")) {
      processProductModule(
        moduleElement = moduleElement,
        cachedDescriptorContainer = cachedDescriptorContainer,
        xIncludeResolver = xIncludeResolver,
        moduleOutputProvider = context,
      )
    }
  }

  return mainPluginDescriptor
}

private fun processProductModule(
  moduleElement: Element,
  cachedDescriptorContainer: CachedDescriptorContainer,
  moduleOutputProvider: ModuleOutputProvider,
  xIncludeResolver: XIncludeElementResolver,
) {
  if (!moduleElement.content.isEmpty()) {
    return
  }

  val moduleName = moduleElement.getAttributeValue("name") ?: return
  val descriptorFile = "${moduleName.replace('/', '.')}.xml"

  val cachedFileData = cachedDescriptorContainer.getCachedFileData(descriptorFile)
  val xml = if (cachedFileData == null) {
    val file = requireNotNull(findFileInModuleSources(module = moduleOutputProvider.findRequiredModule(moduleName), relativePath = descriptorFile)) {
      "Cannot find file $descriptorFile in module $moduleName"
    }
    JDOMUtil.load(file)
  }
  else {
    JDOMUtil.load(cachedFileData)
  }

  resolveNonXIncludeElementFromCache(original = xml, elementResolver = xIncludeResolver)
  moduleElement.setContent(CDATA(JDOMUtil.write(xml)))
}

internal fun generatePluginClassPath(pluginEntries: List<Pair<PluginBuildDescriptor, List<DistributionFileEntry>>>, moduleOutputPatcher: ModuleOutputPatcher): ByteArray {
  val byteOut = ByteArrayOutputStream()
  val out = DataOutputStream(byteOut)

  val uniqueGuard = HashSet<Path>()
  for ((pluginAsset, entries) in pluginEntries) {
    val pluginDir = pluginAsset.dir

    val files = ArrayList<Path>(entries.size)
    uniqueGuard.clear()
    for (entry in entries) {
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

    var pluginDescriptorContent: ByteArray? = null
    for (file in files) {
      if (file.toString().endsWith(".jar")) {
        pluginDescriptorContent = readPluginXml(file)
        if (pluginDescriptorContent != null) {
          break
        }
      }
    }

    if (pluginDescriptorContent == null) {
      pluginDescriptorContent = moduleOutputPatcher.getPatchedPluginXml(pluginAsset.layout.mainModule)
    }

    writeEntry(out = out, files = files, pluginDir = pluginDir, pluginDescriptorContent = pluginDescriptorContent)
  }

  out.close()
  return byteOut.toByteArray()
}

private fun readPluginXml(file: Path): ByteArray? {
  var result: ByteArray? = null
  readZipFile(file) { name, dataProvider ->
    if (name == "META-INF/plugin.xml") {
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
