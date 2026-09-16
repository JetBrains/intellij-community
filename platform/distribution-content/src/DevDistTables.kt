// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.distributionContent

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus.Internal

/** One jar under `lib/` of a product, with its members in merge order. */
@Internal
data class PlatformJarRow(
  @JvmField val product: String,
  @JvmField val relativeOutputFile: String,
  @JvmField val members: List<String>,
)

/**
 * A library the platform layout of a product declares. [relativeOutputFile] is `null` when the layout names no jar.
 * [moduleName] identifies the owner of a module library. It is `null` for a project library.
 */
@Internal
data class PlatformLibraryRow(
  @JvmField val product: String,
  @JvmField val library: String,
  @JvmField val relativeOutputFile: String?,
  @JvmField val moduleName: String? = null,
)

/**
 * A library the packer merges into a member's jar of a product.
 *
 * The layout does not declare the library. The packer's rule selects it, and [relativeOutputFile] is the jar of the
 * member that depends on it.
 */
@Internal
data class PlatformMergedLibraryRow(
  @JvmField val product: String,
  @JvmField val library: String,
  @JvmField val relativeOutputFile: String,
)

/**
 * A module the platform layout of a product includes as a product content module.
 *
 * The inclusion reason of the module is `productModule` or `productEmbeddedModule`, the reasons a `product-modules.yaml`
 * entry gives. A plain platform module has another reason and gets no row.
 */
@Internal
data class PlatformContentModuleRow(
  @JvmField val product: String,
  @JvmField val module: String,
)

/**
 * A plugin the build of a product publishes as a non-bundled plugin.
 *
 * The set is the plugins-to-publish set of the distribution state at the `LAYOUT` stage. For a product that builds
 * every compatible plugin, the set is the compatible plugin universe.
 */
@Internal
data class NonBundledPluginRow(
  @JvmField val product: String,
  @JvmField val mainModule: String,
)

@Internal
class DevDistPlatformJars(
  @JvmField val platformJars: List<PlatformJarRow>,
  @JvmField val platformLibraries: List<PlatformLibraryRow>,
  @JvmField val platformMergedLibraries: List<PlatformMergedLibraryRow>,
  @JvmField val platformContentModules: List<PlatformContentModuleRow>,
  @JvmField val nonBundledPlugins: List<NonBundledPluginRow>,
)

/**
 * A file the build adds outside jar packing.
 *
 * A null [os], [arch], or [libc] includes the file in every distribution along that axis.
 * [path] is relative to the distribution root, with library versions removed.
 */
@Internal
data class DistFileRow(
  @JvmField val os: String?,
  @JvmField val arch: String?,
  @JvmField val libc: String?,
  @JvmField val path: String,
)

/** An expanded product observation for test artifacts. The report is not a build input. */
@Internal
data class DevDistProductReport(
  @JvmField val bundledPlugins: List<PluginContentReport>,
  @JvmField val nonBundledPlugins: List<PluginContentReport>,
  @JvmField val platformContentModules: List<String>,
  @JvmField val distFiles: List<DistFileRow>,
)

@Serializable
private class ProductReportYaml(
  val bundledPlugins: List<PluginContentReport>,
  val nonBundledPlugins: List<PluginContentReport>,
  val platformContentModules: List<String>,
  val distFiles: List<DistFileGroup>,
)

@Serializable
internal class DistFileGroup(
  val os: String? = null,
  val arch: String? = null,
  val libc: String? = null,
  val paths: List<String>,
)

/** Reads the expanded observation for one product. */
@Internal
fun readDevDistProductReport(text: String): DevDistProductReport {
  val report = yaml.decodeFromString(ProductReportYaml.serializer(), text)
  return DevDistProductReport(
    bundledPlugins = report.bundledPlugins,
    nonBundledPlugins = report.nonBundledPlugins,
    platformContentModules = report.platformContentModules,
    distFiles = readDistFileGroups(report.distFiles),
  )
}

/** Writes the expanded observation in canonical order. Dist files are grouped by OS, architecture, and libc. */
@Internal
fun writeDevDistProductReport(report: DevDistProductReport): String {
  return yaml.encodeToString(ProductReportYaml.serializer(), ProductReportYaml(
    bundledPlugins = summarizePlugins(report.bundledPlugins),
    nonBundledPlugins = summarizePlugins(report.nonBundledPlugins),
    platformContentModules = report.platformContentModules.distinct().sorted(),
    distFiles = groupDistFiles(report.distFiles),
  )).trimEnd() + "\n"
}

internal fun summarizePlugins(plugins: List<PluginContentReport>): List<PluginContentReport> {
  return plugins.map { it.copy(content = emptyList()) }.distinct().sortedWith(compareBy({ it.mainModule }, { it.os }, { it.arch }))
}

internal fun groupDistFiles(files: List<DistFileRow>): List<DistFileGroup> {
  files.forEach(::validateDistFileRow)
  return files.distinct().sortedWith(compareBy({ it.os }, { it.arch }, { it.libc }, { it.path }))
    .groupBy { Triple(it.os, it.arch, it.libc) }
    .map { (platform, entries) ->
      DistFileGroup(os = platform.first, arch = platform.second, libc = platform.third, paths = entries.map { it.path })
    }
}

internal fun readDistFileGroups(groups: List<DistFileGroup>): List<DistFileRow> {
  return groups.flatMap { group ->
    require(group.paths.isNotEmpty()) { "A dist file group must contain a path." }
    group.paths.map { path ->
      DistFileRow(os = group.os, arch = group.arch, libc = group.libc, path = path).also(::validateDistFileRow)
    }
  }
}

private fun validateDistFileRow(row: DistFileRow) {
  require(row.os == null || row.os in setOf("linux", "mac", "windows")) { "Unknown OS: ${row.os}." }
  require(row.arch == null || row.arch in setOf("aarch64", "amd64")) { "Unknown architecture: ${row.arch}." }
  require(row.libc == null || row.libc in setOf("DEFAULT", "GLIBC", "MUSL")) { "Unknown libc: ${row.libc}." }
  require(row.path.isNotBlank() && row.path.none { it == '\\' || it == ':' || it.isISOControl() } &&
          row.path.split('/').none { it.isEmpty() || it == "." || it == ".." }) {
    "Invalid distribution path: ${row.path}."
  }
}
