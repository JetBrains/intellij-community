// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.platform.buildScripts.concurrency.taskScope
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.impl.BundledMavenDownloader
import org.jetbrains.intellij.build.impl.additionalProperties
import org.jetbrains.intellij.build.io.DEFAULT_TIMEOUT
import org.jetbrains.intellij.build.productRunner.IntellijProductRunner
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import java.nio.file.Files
import java.nio.file.Path

@Internal
@Serializable
data class FileSource(
  @JvmField val relativePath: String,
  @JvmField val size: Int,
  @JvmField val hash: Long,
  @JvmField @Contextual val file: Path,
) : Source {
  init {
    assert(Files.isRegularFile(file)) { "'$file' is not a file" }
  }
}

@Serializable
data class SearchableOptionSetIndexItem(@JvmField val file: String, @JvmField val size: Int, @JvmField val hash: Long)

class SearchableOptionSetDescriptor(
  @JvmField internal val index: Map<String, List<SearchableOptionSetIndexItem>>,
  @JvmField val baseDir: Path,
) {
  fun createSourceByModule(moduleName: String): List<FileSource> {
    val list = index[moduleName] ?: return emptyList()
    return list.map {
      FileSource(relativePath = it.file, size = it.size, hash = it.hash, file = baseDir.resolve(it.file))
    }
  }

  fun createSourceByPlugin(pluginId: String): List<FileSource> = createSourceByModule(pluginId)
}

internal fun readSearchableOptionIndex(baseDir: Path): SearchableOptionSetDescriptor {
  return Files.newInputStream(baseDir.resolve("content.json")).use {
    SearchableOptionSetDescriptor(
      index = Json.decodeFromStream<Map<String, List<SearchableOptionSetIndexItem>>>(it),
      baseDir = baseDir,
    )
  }
}

fun buildSearchableOptions(context: BuildContext, systemProperties: VmProperties = VmProperties(emptyMap())): SearchableOptionSetDescriptor? =
  buildSearchableOptions(context.createProductRunner(), context, systemProperties)

/**
 * Build index which is used to search options in the Settings dialog.
 */
internal fun buildSearchableOptions(
  productRunner: IntellijProductRunner,
  context: BuildContext,
  systemProperties: VmProperties = VmProperties(emptyMap()),
): SearchableOptionSetDescriptor? {
  return context.executeStep(spanBuilder("building searchable options index"), BuildOptions.SEARCHABLE_OPTIONS_INDEX_STEP) { span ->
    prepareTraverseUiInput(context)
    val index = runTraverseUi(productRunner = productRunner, outDir = context.paths.searchableOptionDir, systemProperties = systemProperties)
    reportIndex(span, index)
    index
  }
}

private fun reportIndex(span: Span, index: SearchableOptionSetDescriptor) {
  span.setAttribute(AttributeKey.longKey("moduleCountWithSearchableOptions"), index.index.size.toLong())
  span.setAttribute(AttributeKey.stringArrayKey("modulesWithSearchableOptions"), index.index.keys.toList())
}

/**
 * Resolve the bundled Maven inputs before `traverseUI` starts an external process.
 *
 * Under Bazel these are read directly from declared runfiles.
 * Another build keeps its normal download-cache behavior.
 */
private fun prepareTraverseUiInput(context: BuildContext) {
  taskScope {
    fork("resolve maven4 libs") {
      BundledMavenDownloader.resolveMaven4Libs(context.paths.communityHomeDirRoot, context.httpSession)
    }
    fork("resolve maven3 libs") {
      BundledMavenDownloader.resolveMaven3Libs(context.paths.communityHomeDirRoot, context.httpSession)
    }
    fork("download maven distribution") {
      BundledMavenDownloader.downloadMavenDistribution(context.paths.communityHomeDirRoot, context.httpSession)
    }
    fork("resolve maven telemetry dependencies") {
      BundledMavenDownloader.resolveMavenTelemetryDependencies(context.paths.communityHomeDirRoot, context.httpSession)
    }
    join()
  }
}

/**
 * Start the product in headless mode with `com.intellij.ide.ui.search.TraverseUIStarter`.
 * It processes every UI element in the `Settings` dialog and writes an index for them.
 */
private fun runTraverseUi(
  productRunner: IntellijProductRunner,
  outDir: Path,
  systemProperties: VmProperties,
): SearchableOptionSetDescriptor {
  productRunner.runProduct(
    args = listOf("traverseUI", outDir.toString(), "true"),
    additionalVmProperties = systemProperties + VmProperties(mapOf("idea.l10n.keys" to "only")) + additionalProperties(),
    timeout = DEFAULT_TIMEOUT,
  )
  return readSearchableOptionIndex(outDir)
}
