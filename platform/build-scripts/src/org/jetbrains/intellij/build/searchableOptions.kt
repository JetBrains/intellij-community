// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.jetbrains.intellij.build.impl.BundledMavenDownloader
import org.jetbrains.intellij.build.impl.getLocalizationDir
import org.jetbrains.intellij.build.impl.productRunner.IntellijProductRunner
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.listDirectoryEntries

internal data class FileSource(
  @JvmField val relativePath: String,
  override var size: Int,
  override var hash: Long,
  @JvmField val file: Path,
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

suspend fun buildSearchableOptions(context: BuildContext, systemProperties: Map<String, String> = emptyMap()): SearchableOptionSetDescriptor? {
  return buildSearchableOptions(productRunner = IntellijProductRunner.createRunner(context), context = context, systemProperties = systemProperties)
}

/**
 * Build index which is used to search options in the Settings dialog.
 */
internal suspend fun buildSearchableOptions(
  productRunner: IntellijProductRunner,
  context: BuildContext,
  systemProperties: Map<String, String> = emptyMap(),
): SearchableOptionSetDescriptor? {
  val span = Span.current()
  if (context.isStepSkipped(BuildOptions.SEARCHABLE_OPTIONS_INDEX_STEP)) {
    span.addEvent("skip building searchable options index")
    return null
  }

  val targetDirectory = context.paths.searchableOptionDir
  val locales = mutableListOf(Locale.ENGLISH.toLanguageTag())
  if (!context.isStepSkipped(BuildOptions.LOCALIZE_STEP)) {
    val localizationDir = getLocalizationDir(context)
    locales.addAll(
      localizationDir?.resolve("properties")?.listDirectoryEntries()?.map { it.fileName.toString() }
      ?: emptyList()
    )
  }

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

  for (langTag in locales) {
    // Start the product in headless mode using com.intellij.ide.ui.search.TraverseUIStarter.
    // It'll process all UI elements in the `Settings` dialog and build an index for them.
    productRunner.runProduct(
      arguments = listOf("traverseUI", targetDirectory.toString(), "true"),
      additionalSystemProperties = systemProperties + getSystemPropertiesForSearchableOptions(langTag),
      isLongRunning = true,
    )
  }

  val index = readSearchableOptionIndex(targetDirectory)
  span.setAttribute(AttributeKey.longKey("moduleCountWithSearchableOptions"), index.index.size)
  span.setAttribute(AttributeKey.stringArrayKey("modulesWithSearchableOptions"), index.index.keys.toList())

  return index
}

private fun getSystemPropertiesForSearchableOptions(langTag: String): Map<String, String> {
  if (Locale.ENGLISH.toLanguageTag().equals(langTag)) {
    return emptyMap()
  }
  else {
    return mapOf(
      "intellij.searchableOptions.i18n.enabled" to "true",
      "intellij.searchableOptions.i18n.locale" to langTag //TODO: use corresponding property after IJPL-148813
    )
  }
}