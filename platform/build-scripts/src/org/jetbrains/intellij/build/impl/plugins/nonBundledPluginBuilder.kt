// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestOnlyProblems")

package org.jetbrains.intellij.build.impl.plugins

import com.intellij.openapi.util.io.NioFiles
import com.jetbrains.plugin.blockmap.core.BlockMap
import com.jetbrains.plugin.blockmap.core.FileHash
import com.jetbrains.plugin.structure.base.plugin.PluginCreationFail
import com.jetbrains.plugin.structure.base.plugin.PluginCreationSuccess
import com.jetbrains.plugin.structure.intellij.plugin.IdePluginManager
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.Zip64Mode
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.PLUGIN_XML_RELATIVE_PATH
import org.jetbrains.intellij.build.PluginBundlingRestrictions
import org.jetbrains.intellij.build.SearchableOptionSetDescriptor
import org.jetbrains.intellij.build.classPath.PluginBuildDescriptor
import org.jetbrains.intellij.build.executeStep
import org.jetbrains.intellij.build.getUnprocessedPluginXmlContent
import org.jetbrains.intellij.build.impl.BUILT_IN_HELP_MODULE_NAME
import org.jetbrains.intellij.build.impl.DescriptorCacheContainer
import org.jetbrains.intellij.build.impl.DistributionBuilderState
import org.jetbrains.intellij.build.impl.NoDuplicateZipArchiveOutputStream
import org.jetbrains.intellij.build.impl.PLUGIN_LAYOUT_COMPARATOR_BY_MAIN_MODULE
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.PluginRepositorySpec
import org.jetbrains.intellij.build.impl.SUPPORTED_DISTRIBUTIONS
import org.jetbrains.intellij.build.impl.buildHelpPlugin
import org.jetbrains.intellij.build.impl.buildKeymapPlugin
import org.jetbrains.intellij.build.impl.dir
import org.jetbrains.intellij.build.impl.executableFileUnixMode
import org.jetbrains.intellij.build.impl.generatePluginRepositoryMetaFile
import org.jetbrains.intellij.build.impl.handleCustomPlatformSpecificAssets
import org.jetbrains.intellij.build.impl.nonBundledPluginsStageDir
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.io.W_CREATE_NEW
import org.jetbrains.intellij.build.io.ZipArchiver
import org.jetbrains.intellij.build.io.archiveDir
import org.jetbrains.intellij.build.io.writeNewFile
import org.jetbrains.intellij.build.io.writeNewZipWithoutIndex
import org.jetbrains.intellij.build.io.zipWithCompression
import org.jetbrains.intellij.build.productLayout.util.mapConcurrent
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import tools.jackson.jr.ob.JSON
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue

internal suspend fun buildNonBundledPlugins(
  pluginsToPublish: Set<PluginLayout>,
  compressPluginArchive: Boolean,
  buildPlatformLibJob: Deferred<List<DistributionFileEntry>>?,
  state: DistributionBuilderState,
  searchableOptionSet: SearchableOptionSetDescriptor?,
  isUpdateFromSources: Boolean,
  descriptorCacheContainer: DescriptorCacheContainer,
  context: BuildContext,
): List<PluginBuildDescriptor> {
  return context.executeStep(spanBuilder("build non-bundled plugins").setAttribute("count", state.pluginsToPublish.size.toLong()), BuildOptions.NON_BUNDLED_PLUGINS_STEP) {
    buildNonBundledPlugins(
      scope = this,
      pluginsToPublish = pluginsToPublish,
      compressPluginArchive = compressPluginArchive,
      buildPlatformLibJob = buildPlatformLibJob,
      state = state,
      searchableOptionSet = searchableOptionSet,
      isUpdateFromSources = isUpdateFromSources,
      descriptorCacheContainer = descriptorCacheContainer,
      context = context,
    )
  } ?: emptyList()
}

private suspend fun buildNonBundledPlugins(
  scope: CoroutineScope,
  pluginsToPublish: Set<PluginLayout>,
  compressPluginArchive: Boolean,
  buildPlatformLibJob: Deferred<List<DistributionFileEntry>>?,
  state: DistributionBuilderState,
  searchableOptionSet: SearchableOptionSetDescriptor?,
  isUpdateFromSources: Boolean,
  descriptorCacheContainer: DescriptorCacheContainer,
  context: BuildContext,
): List<PluginBuildDescriptor> {
  if (pluginsToPublish.isEmpty()) {
    return emptyList()
  }

  val buildKeymapPluginsTask = if (context.options.buildStepsToSkip.contains(BuildOptions.KEYMAP_PLUGINS_STEP)) {
    null
  }
  else {
    scope.async(CoroutineName("build keymap plugins")) {
      buildKeymapPlugins(targetDir = context.nonBundledPluginsToBePublished, context = context)
    }
  }

  val stageDir = nonBundledPluginsStageDir(context)
  NioFiles.deleteRecursively(stageDir)

  // buildPlugins pluginBuilt listener is called concurrently
  val pluginSpecs = ConcurrentLinkedQueue<PluginRepositorySpec>()
  val isPluginArchiveEnabled = !context.isStepSkipped(BuildOptions.ARCHIVE_PLUGINS)
  val prepareCustomPluginRepository = context.productProperties.productLayout.prepareCustomPluginRepositoryForPublishedPlugins && isPluginArchiveEnabled
  val isPluginValidationEnabled = !isUpdateFromSources && !context.isStepSkipped(BuildOptions.VALIDATE_PLUGINS_TO_BE_PUBLISHED)
  val json: Lazy<JSON> = lazy { JSON.std.without(JSON.Feature.USE_FIELDS) }
  val pluginDirs = getOsSpecificNonBundledPluginsDirs(context)
  val mappings = pluginDirs.mapNotNull { (os, arch, targetDir) ->
    val filteredPlugins = pluginsToPublish.filter {
      satisfiesOsArchRestrictions(plugin = it, osFamily = os, arch = arch)
    }.sortedWith(PLUGIN_LAYOUT_COMPARATOR_BY_MAIN_MODULE)

    Span.current().addEvent("build non-bundled plugins")
      .setAttribute("os", os?.osId ?: "all")
      .setAttribute("arch", arch?.name ?: "all")
      .setAttribute("count", filteredPlugins.size.toLong())
      .setAttribute("outDir", targetDir.toString())

    if (filteredPlugins.isEmpty()) {
      return@mapNotNull null
    }

    buildPlugins(
      plugins = filteredPlugins,
      os = os,
      arch = arch,
      targetDir = targetDir,
      state = state,
      platformEntriesProvider = buildPlatformLibJob?.let { it::await },
      searchableOptionSet = searchableOptionSet,
      descriptorCacheContainer = descriptorCacheContainer,
      context = context,
    ) { plugin, pluginDirOrFile ->
      val pluginVersion = if (plugin.mainModule == BUILT_IN_HELP_MODULE_NAME) {
        context.buildNumber
      }
      else {
        val outputProvider = context.outputProvider
        val pluginModule = outputProvider.findRequiredModule(plugin.mainModule)
        var cachedPluginXml: String? = null
        val pluginXmlSupplier: suspend () -> String = {
          cachedPluginXml ?: getUnprocessedPluginXmlContent(pluginModule, outputProvider)
            .decodeToString()
            .also { cachedPluginXml = it }
        }
        plugin.versionEvaluator.evaluate(
          pluginXmlSupplier = pluginXmlSupplier,
          ideBuildVersion = context.pluginBuildNumber,
          context = context,
        ).pluginVersion
      }

      val targetDirectory = if (context.pluginAutoPublishList.test(plugin)) {
        context.nonBundledPluginsToBePublished
      }
      else {
        context.nonBundledPlugins
      }
      val destFile = targetDirectory.resolve("${plugin.directoryName}-$pluginVersion.zip")
      val pluginXml = checkNotNull(descriptorCacheContainer.forPlugin(pluginDirOrFile).getCachedFileData(PLUGIN_XML_RELATIVE_PATH)) {
        "Patched plugin descriptor is not found for module ${plugin.mainModule} in '$pluginDirOrFile'"
      }
      pluginSpecs.add(PluginRepositorySpec(destFile, pluginXml))

      val entries = handleCustomPlatformSpecificAssets(layout = plugin, targetPlatform = null, context = context, pluginDir = pluginDirOrFile, isDevMode = true)

      if (isPluginArchiveEnabled) {
        archivePlugin(
          optimizedZip = !plugin.enableSymlinksAndExecutableResources,
          source = pluginDirOrFile,
          target = destFile,
          compress = compressPluginArchive,
          withBlockMap = compressPluginArchive,
          context = context,
          json = json,
        )

        if (isPluginValidationEnabled) {
          spanBuilder("plugin validation").use { span ->
            if (Files.notExists(destFile)) {
              span.addEvent("doesn't exist, skipped", Attributes.of(AttributeKey.stringKey("path"), "$destFile"))
            }
            else {
              validatePlugin(file = destFile, span = span, context = context)
            }
          }
        }
      }

      entries
    }
  }.flatten()


  val helpPlugin = buildHelpPlugin(context.pluginBuildNumber, context)
  if (helpPlugin != null) {
    val helpPluginLayout = helpPlugin.first
    val targetDir = context.nonBundledPluginsToBePublished
    descriptorCacheContainer.forPlugin(targetDir.resolve(helpPluginLayout.directoryName)).put(PLUGIN_XML_RELATIVE_PATH, helpPlugin.second.encodeToByteArray())
    val spec = buildHelpPlugin(
      helpPluginLayout = helpPluginLayout,
      pluginXml = helpPlugin.second,
      pluginsToPublishDir = stageDir,
      targetDir = targetDir,
      state = state,
      searchableOptionSetDescriptor = searchableOptionSet,
      descriptorCacheContainer = descriptorCacheContainer,
      context = context,
    )
    pluginSpecs.add(spec)
  }

  buildKeymapPluginsTask?.let {
    for (item in it.await()) {
      pluginSpecs.add(PluginRepositorySpec(pluginZip = item.first, pluginXml = item.second))
    }
  }

  if (prepareCustomPluginRepository) {
    val list = pluginSpecs.sortedBy { it.pluginZip }
    if (list.isNotEmpty()) {
      scope.launch {
        generatePluginRepositoryMetaFile(pluginSpecs = list, targetDir = context.nonBundledPlugins, buildNumber = context.buildNumber)
      }
    }

    val pluginsToBePublished = list.filter { it.pluginZip.startsWith(context.nonBundledPluginsToBePublished) }
    if (pluginsToBePublished.isNotEmpty()) {
      scope.launch {
        generatePluginRepositoryMetaFile(pluginSpecs = pluginsToBePublished, targetDir = context.nonBundledPluginsToBePublished, buildNumber = context.buildNumber)
      }
    }
  }

  return mappings
}

private fun getOsSpecificNonBundledPluginsDirs(context: BuildContext): List<Triple<OsFamily?, JvmArchitecture?, Path>> {
  val stageDir = nonBundledPluginsStageDir(context)
  val supportedWithNullArch = buildList {
    addAll(SUPPORTED_DISTRIBUTIONS.map { it.os to it.arch })

    // Add each unique OS with null architecture
    SUPPORTED_DISTRIBUTIONS.map { it.os }.toSet().forEach { os ->
      add(os to null)
    }

    // Add platform-independent (all OS and architectures)
    add(null to null)
  }
  return supportedWithNullArch.map {
    val os = it.first
    val arch = it.second
    if (os == null && arch == null) {
      Triple(null, null, stageDir)
    } else {
      val archName = arch?.name ?: "all"
      val path = stageDir.resolve("dist.${os!!.distSuffix}.$archName")
      Triple(os, arch, path)
    }
  }.sortedBy { it.first }
}

private fun satisfiesOsArchRestrictions(plugin: PluginLayout, osFamily: OsFamily?, arch: JvmArchitecture?): Boolean {
  val supportedOs = plugin.bundlingRestrictions.supportedOs
  val supportedArch = plugin.bundlingRestrictions.supportedArch
  return when {
    osFamily == null && arch == null && plugin.bundlingRestrictions == PluginBundlingRestrictions.MARKETPLACE -> true
    osFamily == null && supportedOs != OsFamily.ALL -> false
    osFamily != null && (supportedOs == OsFamily.ALL || !supportedOs.contains(osFamily)) -> false
    arch == null && supportedArch != JvmArchitecture.ALL -> false
    else -> arch == null || supportedArch.contains(arch) && supportedArch.size == 1
  }
}

private suspend fun archivePlugin(
  source: Path,
  target: Path,
  compress: Boolean,
  optimizedZip: Boolean,
  withBlockMap: Boolean,
  json: Lazy<JSON>,
  context: BuildContext,
) {
  spanBuilder("archive plugin")
    .setAttribute("input", source.toString())
    .setAttribute("outputFile", target.toString())
    .setAttribute("optimizedZip", optimizedZip)
    .use {
      archivePlugin(optimized = optimizedZip, target = target, compress = compress, source = source, context = context)
    }
  if (withBlockMap) {
    spanBuilder("build plugin blockmap").setAttribute("file", target.toString()).use {
      buildBlockMap(target, json.value)
    }
  }
}

private fun archivePlugin(optimized: Boolean, target: Path, compress: Boolean, source: Path, context: BuildContext) {
  if (optimized) {
    writeNewZipWithoutIndex(target, compress) { zipCreator ->
      val archiver = ZipArchiver()
      if (Files.isDirectory(source)) {
        archiver.setRootDir(source, source.fileName.toString())
        archiveDir(startDir = source, addFile = { archiver.addFile(it, zipCreator) })
      }
      else {
        archiver.setRootDir(source.parent)
        archiver.addFile(source, zipCreator)
      }
    }
  }
  else {
    writeNewFile(target) { outFileChannel ->
      NoDuplicateZipArchiveOutputStream(outFileChannel, context.options.compressZipFiles).use { out ->
        out.setUseZip64(Zip64Mode.Never)
        out.dir(startDir = source, prefix = "${source.fileName}/", entryCustomizer = { entry, file, _ ->
          if (Files.isExecutable(file)) {
            entry.unixMode = executableFileUnixMode
          }
        })
      }
    }
  }
}

private suspend fun buildKeymapPlugins(targetDir: Path, context: BuildContext): List<Pair<Path, ByteArray>> {
  val keymapDir = context.paths.communityHomeDir.resolve("platform/platform-resources/src/keymaps")
  withContext(Dispatchers.IO) {
    Files.createDirectories(targetDir)
  }
  return spanBuilder("build keymap plugins").use(Dispatchers.IO) {
    listOf(
      arrayOf("Mac OS X", "Mac OS X 10.5+"),
      arrayOf("Default for GNOME"),
      arrayOf("Default for KDE"),
      arrayOf("Default for XWin"),
      arrayOf("Emacs"),
      arrayOf("Sublime Text", "Sublime Text (Mac OS X)"),
    ).mapConcurrent { keymaps ->
      withContext(CoroutineName("build keymap plugin for ${keymaps[0]}")) {
        buildKeymapPlugin(keymaps = keymaps, buildNumber = context.buildNumber, targetDir = targetDir, keymapDir = keymapDir)
      }
    }
  }
}

/**
 * Builds a blockmap and hash files for a plugin.
 */
private fun buildBlockMap(file: Path, json: JSON) {
  val algorithm = "SHA-256"
  val bytes = Files.newInputStream(file).use { input ->
    json.asBytes(BlockMap(input, algorithm))
  }

  val fileParent = file.parent
  val fileName = file.fileName.toString()
  writeNewZipWithoutIndex(fileParent.resolve("$fileName.blockmap.zip"), compress = true) {
    it.compressedData("blockmap.json", ByteBuffer.wrap(bytes))
  }

  val hashFile = fileParent.resolve("$fileName.hash.json")
  Files.newInputStream(file).use { input ->
    Files.newOutputStream(hashFile, *W_CREATE_NEW.toTypedArray()).use { output ->
      json.write(FileHash(input, algorithm), output)
    }
  }
}

private fun validatePlugin(file: Path, span: Span, context: BuildContext) {
  val pluginManager = IdePluginManager.createManager()
  val result = pluginManager.createPlugin(pluginFile = file, validateDescriptor = true)
  // todo fix AddStatisticsEventLogListenerTemporary
  val id = when (result) {
    is PluginCreationSuccess -> result.plugin.pluginId
    is PluginCreationFail -> (pluginManager.createPlugin(pluginFile = file, validateDescriptor = false) as? PluginCreationSuccess)?.plugin?.pluginId
  }
  for (problem in context.productProperties.validatePlugin(id, result)) {
    val problemType = problem::class.java.simpleName
    span.addEvent(
      "plugin validation failed", Attributes.of(
      AttributeKey.stringKey("id"), "$id",
      AttributeKey.stringKey("path"), "$file",
      AttributeKey.stringKey("problemType"), problemType,
    )
    )
    context.messages.reportBuildProblem(
      description = "${id ?: file}, $problemType: $problem",
      identity = "${id ?: file}$problemType"
    )
  }
}

private suspend fun buildHelpPlugin(
  helpPluginLayout: PluginLayout,
  pluginXml: String,
  pluginsToPublishDir: Path,
  targetDir: Path,
  state: DistributionBuilderState,
  descriptorCacheContainer: DescriptorCacheContainer,
  searchableOptionSetDescriptor: SearchableOptionSetDescriptor?,
  context: BuildContext,
): PluginRepositorySpec {
  val directory = helpPluginLayout.directoryName
  val destFile = targetDir.resolve("$directory.zip")
  spanBuilder("build help plugin").setAttribute("dir", directory).use {
    val targetDir = pluginsToPublishDir.resolve(directory)
    buildPlugins(
      plugins = listOf(helpPluginLayout),
      os = null,
      arch = null,
      targetDir = targetDir,
      state = state,
      platformEntriesProvider = null,
      searchableOptionSet = searchableOptionSetDescriptor,
      descriptorCacheContainer = descriptorCacheContainer,
      context = context
    )
    zipWithCompression(targetFile = destFile, dirs = mapOf(targetDir to ""))
    null
  }
  return PluginRepositorySpec(pluginZip = destFile, pluginXml = pluginXml.encodeToByteArray())
}
