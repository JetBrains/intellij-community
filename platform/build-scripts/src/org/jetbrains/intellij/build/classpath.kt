// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.intellij.build

import com.intellij.platform.ijent.community.buildConstants.isMultiRoutingFileSystemEnabledForProduct
import com.intellij.platform.util.putMoreLikelyPluginJarsFirst
import io.opentelemetry.api.common.AttributeKey
import org.jetbrains.intellij.build.impl.ModuleOutputPatcher
import org.jetbrains.intellij.build.impl.PlatformJarNames
import org.jetbrains.intellij.build.impl.PlatformJarNames.PLATFORM_CORE_NIO_FS
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.io.ZipEntryProcessorResult
import org.jetbrains.intellij.build.io.readZipFile
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

internal fun excludedLibJars(context: BuildContext): Set<String> {
  return java.util.Set.of(PlatformJarNames.TEST_FRAMEWORK_JAR) +
         if (isMultiRoutingFileSystemEnabledForProduct(context.productProperties.platformPrefix)) java.util.Set.of(PLATFORM_CORE_NIO_FS) else java.util.Set.of()
}

internal suspend fun generateClasspath(context: BuildContext): List<String> {
  val homeDir = context.paths.distAllDir
  val libDir = homeDir.resolve("lib")
  return spanBuilder("generate classpath")
    .setAttribute("dir", homeDir.toString())
    .use { span ->
      val excluded = excludedLibJars(context)
      val existing = HashSet<Path>()
      Files.newDirectoryStream(libDir).use { stream ->
        stream.filterTo(existing) { it.toString().endsWith(".jar") && !excluded.contains(it.fileName.toString()) }
      }
      val result = computeAppClassPath(libDir, existing).map { libDir.relativize(it).toString() }
      span.setAttribute(AttributeKey.stringArrayKey("result"), result)
      result
    }
}

internal fun computeAppClassPath(libDir: Path, existing: Set<Path>): LinkedHashSet<Path> {
  val result = LinkedHashSet<Path>(existing.size + 4)
  // add first - should be listed first
  sequenceOf(PLATFORM_LOADER_JAR, UTIL_8_JAR, "app-client.jar", UTIL_JAR, "product.jar", "app.jar", "app.jar").map(libDir::resolve).filterTo(result, existing::contains)
  // sorted to ensure stable performance results
  result.addAll(if (isWindows) existing.sortedBy(Path::toString) else existing.sorted())
  return result
}

internal data class PluginBuildDescriptor(
  @JvmField val dir: Path,
  @JvmField val os: OsFamily?,
  @JvmField val layout: PluginLayout,
  @JvmField val moduleNames: List<String>,
)

internal fun writePluginClassPathHeader(out: DataOutputStream, isJarOnly: Boolean, pluginCount: Int, moduleOutputPatcher: ModuleOutputPatcher, context: BuildContext) {
  // format version
  out.write(2)
  // jarOnly
  out.write(if (isJarOnly) 1 else 0)

  // main plugin
  val mainDescriptor = moduleOutputPatcher.getPatchedContent(context.productProperties.applicationInfoModule).let {
    it.get("META-INF/plugin.xml") ?: it.get("META-INF/${context.productProperties.platformPrefix}Plugin.xml")
  }

  val mainPluginDescriptorContent = requireNotNull(mainDescriptor) {
    "Cannot find core plugin descriptor (module=${context.productProperties.applicationInfoModule})"
  }
  out.writeInt(mainPluginDescriptorContent.size)
  out.write(mainPluginDescriptorContent)

  // bundled plugin metadata
  out.writeShort(pluginCount)
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
      if (!uniqueGuard.add(file)) {
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
    out.writeUTF(pluginDir.relativize(file).invariantSeparatorsPathString)
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
