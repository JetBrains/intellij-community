// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestOnlyProblems")

package org.jetbrains.intellij.build.impl.plugins

import com.fasterxml.jackson.jr.ob.JSON
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.apache.commons.compress.archivers.zip.Zip64Mode
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.SearchableOptionSetDescriptor
import org.jetbrains.intellij.build.classPath.PluginBuildDescriptor
import org.jetbrains.intellij.build.executeStep
import org.jetbrains.intellij.build.getUnprocessedPluginXmlContent
import org.jetbrains.intellij.build.impl.BUILT_IN_HELP_MODULE_NAME
import org.jetbrains.intellij.build.impl.DescriptorCacheContainer
import org.jetbrains.intellij.build.impl.DistributionBuilderState
import org.jetbrains.intellij.build.impl.ModuleOutputPatcher
import org.jetbrains.intellij.build.impl.NoDuplicateZipArchiveOutputStream
import org.jetbrains.intellij.build.impl.PLUGIN_LAYOUT_COMPARATOR_BY_MAIN_MODULE
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.PluginRepositorySpec
import org.jetbrains.intellij.build.impl.buildHelpPlugin
import org.jetbrains.intellij.build.impl.buildKeymapPlugin
import org.jetbrains.intellij.build.impl.buildPlugins
import org.jetbrains.intellij.build.impl.dir
import org.jetbrains.intellij.build.impl.executableFileUnixMode
import org.jetbrains.intellij.build.impl.generatePluginRepositoryMetaFile
import org.jetbrains.intellij.build.impl.handleCustomPlatformSpecificAssets
import org.jetbrains.intellij.build.impl.nonBundledPluginsStageDir
import org.jetbrains.intellij.build.io.W_CREATE_NEW
import org.jetbrains.intellij.build.io.ZipArchiver
import org.jetbrains.intellij.build.io.archiveDir
import org.jetbrains.intellij.build.io.writeNewFile
import org.jetbrains.intellij.build.io.writeNewZipWithoutIndex
import org.jetbrains.intellij.build.io.zipWithCompression
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue

internal suspend fun buildNonBundledPlugins(
  pluginsToPublish: Set<PluginLayout>,
  compressPluginArchive: Boolean,
  buildPlatformLibJob: Job?,
  state: DistributionBuilderState,
  searchableOptionSet: SearchableOptionSetDescriptor?,
  descriptorCacheContainer: DescriptorCacheContainer,
  context: BuildContext,
): List<PluginBuildDescriptor> {
  return context.executeStep(spanBuilder("build non-bundled plugins").setAttribute("count", state.pluginsToPublish.size.toLong()), BuildOptions.NON_BUNDLED_PLUGINS_STEP) {
    doBuildNonBundledPlugins(
      pluginsToPublish = pluginsToPublish,
      compressPluginArchive = compressPluginArchive,
      buildPlatformLibJob = buildPlatformLibJob,
      state = state,
      searchableOptionSet = searchableOptionSet,
      isUpdateFromSources = false,
      descriptorCacheContainer = descriptorCacheContainer,
      context = context,
    )
  } ?: emptyList()
}

internal suspend fun CoroutineScope.doBuildNonBundledPlugins(
  pluginsToPublish: Set<PluginLayout>,
  compressPluginArchive: Boolean,
  buildPlatformLibJob: Job?,
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
    async(CoroutineName("build keymap plugins")) {
      buildKeymapPlugins(targetDir = context.nonBundledPluginsToBePublished, context = context)
    }
  }

  val moduleOutputPatcher = ModuleOutputPatcher()
  val stageDir = nonBundledPluginsStageDir(context)
  NioFiles.deleteRecursively(stageDir)

  // buildPlugins pluginBuilt listener is called concurrently
  val pluginSpecs = ConcurrentLinkedQueue<PluginRepositorySpec>()
  val isPluginArchiveEnabled = !context.isStepSkipped(BuildOptions.ARCHIVE_PLUGINS)
  val prepareCustomPluginRepository = context.productProperties.productLayout.prepareCustomPluginRepositoryForPublishedPlugins && isPluginArchiveEnabled
  val plugins = pluginsToPublish.sortedWith(PLUGIN_LAYOUT_COMPARATOR_BY_MAIN_MODULE)
  val isPluginValidationEnabled = !isUpdateFromSources && !context.isStepSkipped(BuildOptions.VALIDATE_PLUGINS_TO_BE_PUBLISHED)
  val json: Lazy<JSON> = lazy { JSON.std.without(JSON.Feature.USE_FIELDS) }
  val mappings = buildPlugins(
    moduleOutputPatcher = moduleOutputPatcher,
    plugins = plugins,
    os = null,
    targetDir = stageDir,
    state = state,
    context = context,
    buildPlatformJob = buildPlatformLibJob,
    searchableOptionSet = searchableOptionSet,
    descriptorCacheContainer = descriptorCacheContainer,
    pluginBuilt = { plugin, pluginDirOrFile ->
      val pluginVersion = if (plugin.mainModule == BUILT_IN_HELP_MODULE_NAME) {
        context.buildNumber
      }
      else {
        plugin.versionEvaluator.evaluate(
          pluginXmlSupplier = { getUnprocessedPluginXmlContent(module = context.findRequiredModule(plugin.mainModule), context = context).decodeToString() },
          ideBuildVersion = context.pluginBuildNumber,
          context,
        ).pluginVersion
      }

      val targetDirectory = if (context.pluginAutoPublishList.test(plugin)) {
        context.nonBundledPluginsToBePublished
      }
      else {
        context.nonBundledPlugins
      }
      val destFile = targetDirectory.resolve("${plugin.directoryName}-$pluginVersion.zip")
      val pluginXml = moduleOutputPatcher.getPatchedPluginXml(plugin.mainModule)
      pluginSpecs.add(PluginRepositorySpec(destFile, pluginXml))

      val entries = handleCustomPlatformSpecificAssets(
        layout = plugin,
        targetPlatform = null,
        context = context,
        pluginDir = pluginDirOrFile,
        isDevMode = true,
      )

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
              validatePlugin(file = destFile, context = context, span = span)
            }
          }
        }
      }

      entries
    },
  )

  val helpPlugin = buildHelpPlugin(context.pluginBuildNumber, context)
  if (helpPlugin != null) {
    val spec = buildHelpPlugin(
      helpPlugin = helpPlugin,
      pluginsToPublishDir = stageDir,
      targetDir = context.nonBundledPluginsToBePublished,
      moduleOutputPatcher = moduleOutputPatcher,
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
      launch {
        generatePluginRepositoryMetaFile(pluginSpecs = list, targetDir = context.nonBundledPlugins, buildNumber = context.buildNumber)
      }
    }

    val pluginsToBePublished = list.filter { it.pluginZip.startsWith(context.nonBundledPluginsToBePublished) }
    if (pluginsToBePublished.isNotEmpty()) {
      launch {
        generatePluginRepositoryMetaFile(pluginSpecs = pluginsToBePublished, targetDir = context.nonBundledPluginsToBePublished, buildNumber = context.buildNumber)
      }
    }
  }

  return mappings
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
  Files.createDirectories(targetDir)
  return spanBuilder("build keymap plugins").use(Dispatchers.IO) {
    listOf(
      arrayOf("Mac OS X", "Mac OS X 10.5+"),
      arrayOf("Default for GNOME"),
      arrayOf("Default for KDE"),
      arrayOf("Default for XWin"),
      arrayOf("Emacs"),
      arrayOf("Sublime Text", "Sublime Text (Mac OS X)"),
    ).map {
      async(CoroutineName("build keymap plugin for ${it[0]}")) {
        buildKeymapPlugin(keymaps = it, buildNumber = context.buildNumber, targetDir = targetDir, keymapDir = keymapDir)
      }
    }
  }.map { it.getCompleted() }
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

private fun validatePlugin(file: Path, context: BuildContext, span: Span) {
  val pluginManager = IdePluginManager.createManager()
  val result = pluginManager.createPlugin(pluginFile = file, validateDescriptor = true)
  // todo fix AddStatisticsEventLogListenerTemporary
  val id = when (result) {
    is PluginCreationSuccess -> result.plugin.pluginId
    is PluginCreationFail -> (pluginManager.createPlugin(pluginFile = file, validateDescriptor = false) as? PluginCreationSuccess)?.plugin?.pluginId
  }
  for (problem in context.productProperties.validatePlugin(id, result, context)) {
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
  helpPlugin: PluginLayout,
  pluginsToPublishDir: Path,
  targetDir: Path,
  moduleOutputPatcher: ModuleOutputPatcher,
  state: DistributionBuilderState,
  descriptorCacheContainer: DescriptorCacheContainer,
  searchableOptionSetDescriptor: SearchableOptionSetDescriptor?,
  context: BuildContext,
): PluginRepositorySpec {
  val directory = helpPlugin.directoryName
  val destFile = targetDir.resolve("$directory.zip")
  spanBuilder("build help plugin").setAttribute("dir", directory).use {
    val targetDir = pluginsToPublishDir.resolve(directory)
    buildPlugins(
      moduleOutputPatcher = moduleOutputPatcher,
      plugins = listOf(helpPlugin),
      os = null,
      targetDir = targetDir,
      state = state,
      context = context,
      buildPlatformJob = null,
      descriptorCacheContainer = descriptorCacheContainer,
      searchableOptionSet = searchableOptionSetDescriptor
    )
    zipWithCompression(targetFile = destFile, dirs = mapOf(targetDir to ""))
    null
  }
  return PluginRepositorySpec(pluginZip = destFile, pluginXml = moduleOutputPatcher.getPatchedPluginXml(helpPlugin.mainModule))
}