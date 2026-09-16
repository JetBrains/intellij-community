package org.jetbrains.intellij.build.dev

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.devDist.DISTRIBUTION_ASSET_SCOPE
import org.jetbrains.intellij.build.devDist.PluginPackingPlan
import org.jetbrains.intellij.build.impl.NativeFilesMatcher
import org.jetbrains.intellij.build.impl.getLibNameBySourceFile
import org.jetbrains.intellij.build.impl.isNativeDistributionEntry
import org.jetbrains.intellij.build.impl.nativeLibraryRelativePath
import org.jetbrains.intellij.build.io.ZipEntryProcessorResult
import org.jetbrains.intellij.build.io.defaultLibrarySourcesNamesFilter
import org.jetbrains.intellij.build.io.readZipFile

/** Applies the original native archive selection to one declared source. */
@ApiStatus.Internal
@Suppress("unused")
class DevPluginPresignedNativeRecipeRuntime : DevPluginPresignedNativeRuntime {
  override fun compile(operation: DevPluginPreparationOperation, plan: PluginPackingPlan): DevPluginPreparationAction {
    validateConsumers(operation, plan)
    val (targetOs, targetArch) = parseTarget(plan.variant)
    return DevPluginPreparationAction { context ->
      val archive = context.inputPath(operation.input)
      val nativeEntries = LinkedHashSet<String>()
      readZipFile(archive) { name, _ ->
        if (defaultLibrarySourcesNamesFilter(name) && isNativeDistributionEntry(name)) {
          nativeEntries.add(name)
        }
        ZipEntryProcessorResult.CONTINUE
      }
      require(nativeEntries.isNotEmpty()) { "Presigned native archive '$archive' has no native entries" }

      val matcher = NativeFilesMatcher(nativeEntries.toList(), listOf(targetOs), targetArch)
      val matches = ArrayList<NativeFilesMatcher.Match>()
      while (true) {
        matches.add(matcher.findNext() ?: break)
      }
      val selectedNames = matches.mapTo(HashSet(), NativeFilesMatcher.Match::pathWithPrefix)
      val selectedContent = HashMap<String, ByteArray>(selectedNames.size)
      readZipFile(archive) { name, dataSupplier ->
        if (name in selectedNames && !selectedContent.containsKey(name)) {
          val buffer = dataSupplier()
          selectedContent.put(name, ByteArray(buffer.remaining()).also(buffer::get))
        }
        ZipEntryProcessorResult.CONTINUE
      }
      require(selectedContent.keys == selectedNames) { "Presigned native entries changed while reading '$archive'" }

      val libraryName = getLibNameBySourceFile(archive)
      for (match in matches) {
        val fileName = match.path.substringAfterLast('/')
        context.writeFile(
          output = operation.output,
          relativePath = nativeLibraryRelativePath(libraryName, match.arch, fileName, match.path),
          content = selectedContent.getValue(match.pathWithPrefix),
          executable = match.osFamily != OsFamily.WINDOWS && !fileName.contains('.'),
        )
      }
      if (matches.isEmpty()) context.omitTreeRoot(operation.output)
      listOf(
        context.prepareNativeArchive(
          output = operation.output,
          input = operation.input,
          manifest = operation.manifest,
          filter = operation.filter,
          overrides = nativeEntries.map { DevPluginNativeOverride(kind = "reserve", name = it) },
        )
      )
    }
  }
}

private fun validateConsumers(operation: DevPluginPreparationOperation, plan: PluginPackingPlan) {
  val consumers = plan.assets.filter { operation.output in it.asset.inputs }
  val treeConsumers = consumers.filter { planned ->
    planned.artifact == null && planned.asset.kind == "tree" && planned.asset.inputs == listOf(operation.output) && !planned.asset.classPath
    && planned.asset.scope == DISTRIBUTION_ASSET_SCOPE
  }
  val jarSources = consumers.flatMap { it.asset.recipe?.sources.orEmpty() }.filter { it.input == operation.output }
  require(
    consumers.size == 2 && treeConsumers.size == 1 && jarSources.size == 1 &&
    jarSources.single().kind == "prepared" && jarSources.single().filter == "prepared"
  ) {
    "Presigned native preparation '${operation.id}' requires one tree consumer and one prepared jar source"
  }
}

private fun parseTarget(variant: String): Pair<OsFamily, JvmArchitecture> {
  val separator = variant.lastIndexOf('_')
  require(separator > 0 && separator < variant.lastIndex) { "Unknown native target variant '$variant'" }
  val osId = variant.substring(0, separator)
  val archId = variant.substring(separator + 1)
  val os = OsFamily.entries.singleOrNull { (if (it == OsFamily.MACOS) "darwin" else it.osId) == osId }
  val arch = JvmArchitecture.entries.singleOrNull { it.name == archId }
  require(os != null && arch != null) { "Unknown native target variant '$variant'" }
  return os to arch
}
