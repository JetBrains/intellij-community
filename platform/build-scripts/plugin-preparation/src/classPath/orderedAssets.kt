package org.jetbrains.intellij.build.classPath

import com.intellij.platform.util.putMoreLikelyPluginJarsFirst
import org.jetbrains.annotations.ApiStatus
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.relativeToOrSelf

/**
 * Writes the plugin classpath record of one plugin.
 *
 * [cachedDescriptorContent] is the descriptor in its final byte form. The record copies the bytes as they are.
 */
@ApiStatus.Internal
fun generatePluginClassPathFromOrderedAssets(
  pluginDir: Path,
  orderedFiles: List<Path>,
  cachedDescriptorContent: ByteArray,
): ByteArray {
  return ByteArrayOutputStream().use { output ->
    DataOutputStream(output).use { data ->
      writeOrderedPluginClassPathEntry(data, orderedFiles, pluginDir, cachedDescriptorContent)
    }
    output.toByteArray()
  }
}

@ApiStatus.Internal
fun writeOrderedPluginClassPathEntry(
  out: DataOutputStream,
  orderedFiles: List<Path>,
  pluginDir: Path,
  pluginDescriptorContent: ByteArray,
) {
  val files = orderedFiles.distinct().toMutableList()
  for (file in files) {
    check(!file.startsWith(pluginDir) || pluginDir.relativize(file).nameCount == 2) {
      "plugin entry is not specified correctly: $file"
    }
  }
  if (files.size > 1) {
    putMoreLikelyPluginJarsFirst(pluginDirName = pluginDir.fileName.toString(), filesInLibUnderPluginDir = files)
  }
  writePluginClassPathEntryData(out = out, files = files, pluginDir = pluginDir, pluginDescriptorContent = pluginDescriptorContent)
}

@ApiStatus.Internal
fun writePluginClassPathEntryData(out: DataOutputStream, files: Collection<Path>, pluginDir: Path, pluginDescriptorContent: ByteArray) {
  // the plugin dir as the last item in the list
  out.writeShort(files.size)
  out.writeUTF(pluginDir.fileName.invariantSeparatorsPathString)

  out.writeInt(pluginDescriptorContent.size)
  out.write(pluginDescriptorContent)

  for (file in files) {
    out.writeUTF(file.relativeToOrSelf(pluginDir).invariantSeparatorsPathString)
  }
}
