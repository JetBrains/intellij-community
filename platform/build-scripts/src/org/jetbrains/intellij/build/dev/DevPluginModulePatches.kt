package org.jetbrains.intellij.build.dev

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.FileSource
import org.jetbrains.intellij.build.InMemoryContentSource
import org.jetbrains.intellij.build.PLUGIN_XML_RELATIVE_PATH
import org.jetbrains.intellij.build.impl.ModuleOutputPatcher

@ApiStatus.Internal
fun DevPluginPreparationContext.prepareModulePatches(
  output: String,
  moduleName: String,
  patcher: ModuleOutputPatcher,
  includePluginDescriptor: Boolean,
): DevPluginPreparedSource {
  val entries = ArrayList<DevPluginPreparedEntry>()
  for ((name, source) in patcher.getPatchedSources(moduleName)) {
    if (!includePluginDescriptor && name == PLUGIN_XML_RELATIVE_PATH) {
      continue
    }
    val reference = when (source) {
      is InMemoryContentSource -> writeFile(output, "patches/${entries.size}", source.data)
      is FileSource -> referenceForPath(source.file)
      else -> error("Module '$moduleName' has an unsupported patch source")
    }
    entries.add(DevPluginPreparedEntry(kind = "patch", name = name, input = reference))
  }
  return DevPluginPreparedSource(output, listOf(DevPluginExecutionSource(kind = "entries", manifest = "keep", entries = entries)))
}
