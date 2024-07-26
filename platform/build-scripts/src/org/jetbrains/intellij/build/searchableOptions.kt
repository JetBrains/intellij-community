// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build

import io.opentelemetry.api.common.AttributeKey
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.impl.BundledMavenDownloader
import org.jetbrains.intellij.build.io.DEFAULT_TIMEOUT
import org.jetbrains.intellij.build.productRunner.IntellijProductRunner
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import java.nio.file.Files
import java.nio.file.Path

@Internal
@Serializable
data class FileSource(
  @JvmField val relativePath: String,
  override var size: Int,
  override var hash: Long,
  @JvmField  @Contextual val file: Path,
) : Source

@Serializable
data class SearchableOptionSetIndexItem(@JvmField val file: String, @JvmField val size: Int, @JvmField val hash: Long)

class SearchableOptionSetDescriptor(
  @JvmField internal val index: Map<String, List<SearchableOptionSetIndexItem>>,
  @JvmField val baseDir: Path,
) {
  fun createSourceByModule(moduleName: String): List<Source> {
    val list = index.get(moduleName) ?: return emptyList()
    return list.map {
      FileSource(relativePath = it.file, size = it.size, hash = it.hash, file = baseDir.resolve(it.file))
    }
  }

  fun createSourceByPlugin(pluginId: String): Collection<Source> {
    return createSourceByModule(pluginId)
  }
}

internal fun readSearchableOptionIndex(baseDir: Path): SearchableOptionSetDescriptor {
  return Files.newInputStream(baseDir.resolve("content.json")).use {
    SearchableOptionSetDescriptor(
      index = Json.decodeFromStream<Map<String, List<SearchableOptionSetIndexItem>>>(it),
      baseDir = baseDir,
    )
  }
}

suspend fun buildSearchableOptions(context: BuildContext, systemProperties: VmProperties = VmProperties(emptyMap())): SearchableOptionSetDescriptor? {
  return buildSearchableOptions(productRunner = context.createProductRunner(), context = context, systemProperties = systemProperties)
}

/**
 * Build index which is used to search options in the Settings dialog.
 */
internal suspend fun buildSearchableOptions(
  productRunner: IntellijProductRunner,
  context: BuildContext,
  systemProperties: VmProperties = VmProperties(emptyMap()),
): SearchableOptionSetDescriptor? {
  return context.executeStep(spanBuilder("building searchable options index"), BuildOptions.SEARCHABLE_OPTIONS_INDEX_STEP) { span ->
    val targetDirectory = context.paths.searchableOptionDir
    // bundled maven is also downloaded during traverseUI execution in an external process,
    // making it fragile to call more than one traverseUI at the same time (in the reproducibility test, for example),
    // so it's pre-downloaded with proper synchronization
    coroutineScope {
      launch {
        BundledMavenDownloader.downloadMaven4Libs(context.paths.communityHomeDirRoot)
      }
      launch {
        BundledMavenDownloader.downloadMaven3Libs(context.paths.communityHomeDirRoot)
      }
      launch {
        BundledMavenDownloader.downloadMavenDistribution(context.paths.communityHomeDirRoot)
      }
      launch {
        BundledMavenDownloader.downloadMavenTelemetryDependencies(context.paths.communityHomeDirRoot)
      }
    }

    // Start the product in headless mode using com.intellij.ide.ui.search.TraverseUIStarter.
    // It'll process all UI elements in the `Settings` dialog and build an index for them.
    productRunner.runProduct(
      args = listOf("traverseUI", targetDirectory.toString(), "true"),
      additionalVmProperties = systemProperties + VmProperties(mapOf("idea.l10n.keys" to "only")),
      timeout = DEFAULT_TIMEOUT,
    )

    val index = readSearchableOptionIndex(targetDirectory)
    span.setAttribute(AttributeKey.longKey("moduleCountWithSearchableOptions"), index.index.size)
    span.setAttribute(AttributeKey.stringArrayKey("modulesWithSearchableOptions"), index.index.keys.toList())

    index
  }
}