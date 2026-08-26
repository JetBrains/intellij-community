// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.distributionContent

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class FileEntry(
  /**
   * The file name in distribution.
   */
  @JvmField val name: String,

  @JvmField val os: String? = null,
  @JvmField val arch: String? = null,
  @JvmField val libc: String? = null,

  /**
   * The list of included in the file project libraries.
   */
  @JvmField val projectLibraries: List<ProjectLibraryEntry> = emptyList(),

  /**
   * The list of included in the file module outputs.
   */
  @JvmField val modules: List<ModuleEntry> = emptyList(),

  @JvmField val productModules: List<String> = emptyList(),
  @JvmField val productEmbeddedModules: List<String> = emptyList(),

  @JvmField val contentModules: List<ModuleEntry> = emptyList(),

  @JvmField val library: String? = null,
  @JvmField val module: String? = null,
  @JvmField val files: List<ModuleLibraryFile> = emptyList(),
  @JvmField val reason: String? = null,

  @JvmField val bundled: List<PluginContentReport> = emptyList(),
  @JvmField val nonBundled: List<PluginContentReport> = emptyList(),

  /**
   * How the build produced [name].
   *
   * `jar` for a jar the packer wrote, `dir` for a directory it put on the classpath instead of packing, `reused` for a
   * file it took as it already was - those three carry [sources]. `placed` for a file that reached the distribution by
   * some other route than the packer, and `link` for a symlink; both carry no [sources], because there is no source
   * list to give, and saying so is the point - a recipe that listed only jars would report the rest as absent rather
   * than as unexplained.
   *
   * Set only by the executed-recipe report of a dev-distribution fragment, so it is absent from every checked-in
   * content report. A packaging report says only what landed where; a recipe has to say how.
   */
  @JvmField val kind: String? = null,

  /**
   * Every source of [name], in the one order the build consumed them.
   *
   * [modules], [contentModules] and [projectLibraries] are three parallel lists, so between them they cannot state a
   * cross-kind order - and first-source-wins duplicate resolution depends on exactly that order, which is why the jar
   * cache keys on the merged list rather than on the three. This is the field that can state it, and it states
   * something the other three do not: which *input* each source is, rather than which member it belongs to.
   *
   * Set only by the executed-recipe report of a dev-distribution fragment; empty in every checked-in report.
   */
  @JvmField val sources: List<RecipeSource> = emptyList(),
) {
  fun compareImportantFields(o: FileEntry): Boolean {
    if (name == o.name && projectLibraries.size == o.projectLibraries.size && modules == o.modules) {
      return projectLibraries.asSequence().zip(o.projectLibraries.asSequence()).all {
        it.first.compareImportantFields(it.second)
      }
    }
    return false
  }
}

/**
 * One source of one output file, as the build actually consumed it.
 *
 * Identity is [label], an input-manifest key, and never an execution path: a recipe that named execution paths would
 * only be replayable on the machine that wrote it. [path] is the fallback for a source no manifest declares - a run
 * with no manifest at all, or content the build generated under its own scratch - and is macro-rooted for the same
 * reason.
 *
 * [needsCode] is the field this report exists to count. A source whose bytes no file holds cannot be replayed from a
 * recipe by any executor; only the build's own code produces it. Reporting those rather than dropping them is the
 * whole point of the artifact.
 */
@ApiStatus.Internal
@Serializable
data class RecipeSource(
  /** The `Source` subtype the build used: `zip`, `dir`, `unpackedZip`, `inMemory`, `lazy` or `customAsset`. */
  @JvmField val kind: String,

  /** The input-manifest key of the file this source reads, or `null` when no manifest declares that file. */
  @JvmField val label: String? = null,

  /** Macro-rooted path of the file this source reads. Written only when [label] is absent. */
  @JvmField val path: String? = null,

  /**
   * Which file of [label] this source reads, when [label] alone cannot say.
   *
   * The input manifest keys a library by the container target that groups its jars. It writes one line for each jar
   * and repeats the key. So every jar of a 26-jar library carries the same [label]. An output that takes fewer jars
   * than the container declares is unreplayable from [label] alone. This field is what names the one file.
   *
   * The value is the name of the file. If two files of one container share a name, the value widens. It becomes the
   * shortest trailing path of the file that names one file of the container. Every such path is a trailing path of an
   * execution path, so it means the same thing on another machine.
   *
   * The emitter leaves the field out in four cases:
   * 1. [label] is absent, so no key names a file: the run has no manifest, or the source reads no file at all;
   * 2. [label] declares one file, which is the file this source reads;
   * 3. [label] does not declare this source's file, which a consistent manifest never does;
   * 4. two files of one container shadow each other, as `a/b.jar` and `x/a/b.jar` do, and no width separates the
   *    shorter of the two.
   *
   * Only the second case means one file, and only the input manifest says which case an absent field is. So a replay
   * that needs one file consults the manifest. Where the manifest declares several files, the replay holds the output
   * out. It holds it out for the reason it already has, because the source still names no one file.
   */
  @JvmField val file: String? = null,

  /** The module whose output this source is, where the build recorded one. */
  @JvmField val module: String? = null,

  /** The path prefix this source's entries are placed under, when it is not the archive root. */
  @JvmField val prefix: String? = null,

  /**
   * What the build applies to this source's entries: `keyed` when [filterCacheKey] states the filter's inputs,
   * `unkeyed` when a filter runs whose inputs the build states nowhere. `null` means no filter at all.
   *
   * `unkeyed` is the cheaper of the two words to replay. An `unkeyed` filter is one constant name predicate and
   * nothing else. A `keyed` filter is that predicate plus the globs [filterCacheKey] names, so an executor of a
   * `keyed` source needs a per-source exclude vocabulary as well. Both words are reported rather than papered over,
   * and neither is narrowed further here.
   *
   * The predicate itself is not opaque. Every `ZipSource` a dev fragment packs gets one of two constant name
   * predicates: `createModuleSourcesNamesFilter` for a module output, `defaultLibrarySourcesNamesFilter` for a
   * library. Both are already reimplemented in the Go packer. But which of the two applies is not on the source: at
   * `JarPackager`'s three construction sites [module] happens to separate them exactly, and that is a property of
   * those three sites rather than of the type. A consumer may make that join; this report will not assert it.
   */
  @JvmField val filter: String? = null,

  /**
   * The filter's inputs, verbatim from `Source.filterCacheKey`. This is the whole of what the jar cache hashes for a
   * filter (`SourceAndCacheStrategy.putFilterCacheKey`), so it is the whole of what the build itself treats as the
   * filter's identity - the filter lambda's own identity is hashed nowhere.
   */
  @JvmField val filterCacheKey: List<String> = emptyList(),

  /** Whether this source's native files are candidates for being taken out, signed, and laid beside the jar. */
  @JvmField val presigned: Boolean = false,

  /**
   * `ZipSource.optimizeConfigId`, verbatim.
   *
   * Recorded because it is on the source and a recipe may not drop what the build set, not because it does anything:
   * the field is set at one site and read nowhere in the repository, and it is in neither `ZipSource.equals` nor the
   * jar-cache digest. A non-null value here therefore means "the build labelled this source and then ignored the
   * label"; it is not a transformation an executor has to reproduce.
   */
  @JvmField val optimizeConfigId: String? = null,

  /** The generated relative path of an `inMemory` source, or the name of a `lazy` one. */
  @JvmField val name: String? = null,

  /** The byte count of an `inMemory` source's content - the bytes in this output that come from no file. */
  @JvmField val size: Int = 0,

  /** The precomputed hash a `lazy` source stands for, which is its whole identity to the jar cache. */
  @JvmField val hash: Long = 0,

  /**
   * `true` when the bytes come from build code rather than from a file, so no recipe can state them: `inMemory`,
   * `lazy` and `customAsset`.
   */
  @JvmField val needsCode: Boolean = false,
)

@ApiStatus.Internal
@Serializable
data class ProjectLibraryEntry(
  /**
   * The library name.
   */
  @JvmField val name: String,

  /**
   * The list of library files.
   */
  @JvmField val files: List<ProjectLibraryFile> = emptyList(),

  /**
   * The modules that use the library.
   */
  @JvmField val dependentModules: Map<String, List<String>> = emptyMap(),

  /**
   * The reason why the project library was included in the distribution file.
   */
  @JvmField val reason: String? = null,
) {
  fun compareImportantFields(o: ProjectLibraryEntry): Boolean = name == o.name && files == o.files && reason == o.reason
}

@ApiStatus.Internal
@Serializable
data class ModuleEntry(
  /**
   * The library name.
   */
  @JvmField val name: String,

  /**
   * The module output size.
   */
  @JvmField val size: Int = 0,

  @JvmField val reason: String? = null,

  /**
   * The list of included module libraries.
   */
  @JvmField val libraries: Map<String, List<ModuleLibraryFile>> = emptyMap(),
)

@ApiStatus.Internal
@Serializable
data class ProjectLibraryFile(
  /**
   * The file name.
   */
  @JvmField val name: String,

  /**
   * The file size.
   */
  @JvmField val size: Int = 0,
)

@ApiStatus.Internal
@Serializable
data class ModuleLibraryFile(
  /**
   * The file name.
   */
  @JvmField val name: String,

  /**
   * The file size.
   */
  @JvmField val size: Int = 0,
)

@ApiStatus.Internal
@Serializable
data class PluginContentReport(
  @JvmField val mainModule: String,
  @JvmField val os: String? = null,
  @JvmField val arch: String? = null,
  @JvmField val content: List<FileEntry> = emptyList(),
)
