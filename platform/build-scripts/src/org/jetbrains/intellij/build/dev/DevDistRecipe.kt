// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import com.intellij.platform.distributionContent.FileEntry
import com.intellij.platform.distributionContent.ModuleEntry
import com.intellij.platform.distributionContent.RecipeSource
import com.intellij.platform.distributionContent.serializeContentEntries
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.CustomAssetShimSource
import org.jetbrains.intellij.build.DirSource
import org.jetbrains.intellij.build.FileSource
import org.jetbrains.intellij.build.InMemoryContentSource
import org.jetbrains.intellij.build.LazySource
import org.jetbrains.intellij.build.MAVEN_REPO
import org.jetbrains.intellij.build.Source
import org.jetbrains.intellij.build.UnpackedZipSource
import org.jetbrains.intellij.build.ZipSource
import org.jetbrains.intellij.build.generateInclusionReasonForContentModule
import org.jetbrains.intellij.build.impl.BaseLayout
import org.jetbrains.intellij.build.impl.BazelBuildInputs
import org.jetbrains.intellij.build.impl.ModuleItem
import org.jetbrains.intellij.build.impl.PluginLayout
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.io.path.createDirectories
import kotlin.io.path.invariantSeparatorsPathString

/**
 * Writes out the packaging recipe a dev-distribution assembly just executed: for every output the assembly produced,
 * how it produced it and which sources, in which order, went into it.
 *
 * ### Why this is a report of an executed run and not of a layout
 *
 * The recipe is not recomputed. `JarPackager` already merges an output's per-module source lists and its own source
 * list into the *one* ordered list it hands to the jar writer and to the jar cache, and that merged list is what is
 * recorded here - the same object, at the moment the packer used it. A second computation over the layout would be a
 * claim about the run; this is the run.
 *
 * ### Off unless asked for
 *
 * [start] arms the recording and nothing else does. With it unarmed, [record] is one volatile read per output, and no
 * file is written - which is what lets `JarPackager` call it unconditionally.
 *
 * It is a process-wide object rather than state on `BuildContext`, for the same reason `BazelBuildInputs` is: an
 * assembly forks a context per parallel task, so context-held state would be recorded into whichever fork packed the
 * output, and one assembly per process is exactly what `DevDistMain` is.
 *
 * ### Identity is a label
 *
 * Every source names the input-manifest key it was declared under ([BazelBuildInputs.labelOf]), never an execution
 * path, so the recipe means the same thing on another machine. A source no manifest declares falls back to a
 * macro-rooted path, which is visible in the report as `path` where a `label` would be.
 */
@ApiStatus.Internal
object DevDistRecipe {
  private class Recording(
    @JvmField val distRoot: Path,
    @JvmField val projectHome: Path,
    @JvmField val scratchDir: Path?,
  ) {
    /** Outputs are packed concurrently, so recording order is not reproducible; [write] sorts. */
    @JvmField val entries: ConcurrentLinkedQueue<FileEntry> = ConcurrentLinkedQueue()
  }

  @Volatile
  private var recording: Recording? = null

  /**
   * Starts recording the assembly about to run.
   *
   * @param distRoot the directory the assembly writes its distribution into; an output under it is named relative to it.
   * @param projectHome the checkout, or the materialized project-model tree standing in for one.
   * @param scratchDir where the assembly generates content that is not in the distribution and not in the checkout.
   */
  fun start(distRoot: Path, projectHome: Path, scratchDir: Path?) {
    recording = Recording(distRoot = distRoot, projectHome = projectHome, scratchDir = scratchDir)
  }

  /**
   * Records one output of the assembly.
   *
   * @param outputFile where the output is - inside [Recording.distRoot] for a jar this assembly wrote, outside it for a
   *   file or directory the assembly put on the classpath as it already was.
   * @param isDir whether the output is a directory rather than a file.
   * @param sources the merged, ordered source list the packer consumed, exactly as it consumed it.
   * @param includedModules the modules this output holds the output of.
   * @param layout the layout being packed, which is what tells a plugin's content modules from its other members.
   */
  internal fun record(
    outputFile: Path,
    isDir: Boolean,
    sources: Collection<Source>,
    includedModules: Collection<ModuleItem>,
    layout: BaseLayout?,
  ) {
    val recording = recording ?: return

    val contentModuleReason = (layout as? PluginLayout)?.let { generateInclusionReasonForContentModule(it.mainModule) }
    val members = includedModules.groupBy { contentModuleReason != null && it.reason == contentModuleReason }

    recording.entries.add(
      FileEntry(
        name = recording.nameOf(outputFile),
        kind = when {
          isDir -> "dir"
          outputFile.startsWith(recording.distRoot) -> "jar"
          else -> "reused"
        },
        // Names only. Sizes are a measurement of the run rather than part of a recipe, and a module's merged libraries
        // are already in `sources` under their own labels - which is where an executor has to read them anyway.
        modules = members[false].orEmpty().map { ModuleEntry(name = it.moduleName) },
        contentModules = members[true].orEmpty().map { ModuleEntry(name = it.moduleName) },
        sources = sources.map { recording.describe(it) },
      )
    )
  }

  /**
   * Writes what was recorded, or nothing at all when [start] was never called.
   *
   * The header names the fragment, because the entries cannot: the schema is keyed by output file, and one assembly's
   * recipe is one fragment's.
   */
  fun write(file: Path, fragment: String) {
    val recording = recording ?: return
    val recorded = recording.entries.toList()
    // Two outputs can only collide on a name outside the distribution, where a name is a label rather than a path, so
    // the tiebreaker is the first source - enough to keep a diff of two runs from reordering on its own.
    val entries = (recorded + recording.placedOutputs(recorded))
      .sortedWith(compareBy({ it.name }, { it.sources.firstOrNull()?.label ?: it.sources.firstOrNull()?.path ?: "" }))
    file.parent?.createDirectories()
    Files.writeString(
      file,
      "# The packaging recipe the '$fragment' dev-distribution fragment executed, in the checked-in content-report" +
      " schema (com.intellij.platform.distributionContent.FileEntry).\n" +
      "# Written by DevDistRecipe; ${entries.size} outputs.\n" +
      serializeContentEntries(entries),
    )
  }

  /**
   * A row for every file in the fragment's output that the packer did not produce.
   *
   * The packer is not the fragment's only writer, and a recipe that listed only jars would report the rest as absent
   * rather than as unexplained. `bin/` and its natives, `build.txt`, `product-info.json`, `idea.properties`, the
   * `DistFile`s (IJent, and the natives lifted out of a jar to be signed), the OS-specific copied files, and the jars a
   * `resourceGenerator` builds with its own `buildJar` call are all placed outside [record]'s reach - and the last of
   * those appears in no content report at all today, so nothing else in the repository names it either.
   *
   * They get `kind` and nothing else: there is no source list to give, and that *is* the finding. `placed` means the
   * recipe cannot say how the file was produced, and `link` means the output is a symlink, which is a different
   * production rule again. Both are counted by whatever reads this, instead of being invisible.
   *
   * Derived from the finished tree rather than from more call sites, so a writer nobody hooked still shows up.
   */
  private fun Recording.placedOutputs(recorded: List<FileEntry>): List<FileEntry> {
    if (!Files.isDirectory(distRoot)) {
      return emptyList()
    }
    val packed = recorded.mapTo(HashSet(recorded.size), FileEntry::name)
    Files.walk(distRoot).use { paths ->
      return paths
        .filter { !Files.isDirectory(it, LinkOption.NOFOLLOW_LINKS) }
        .map { path ->
          FileEntry(
            name = distRoot.relativize(path).invariantSeparatorsPathString,
            kind = if (Files.isSymbolicLink(path)) "link" else "placed",
          )
        }
        .filter { !packed.contains(it.name) }
        .toList()
    }
  }

  private fun Recording.nameOf(file: Path): String {
    if (file.startsWith(distRoot)) {
      return distRoot.relativize(file).invariantSeparatorsPathString
    }
    // An output the assembly did not write has no place in the distribution to be named by, so it is named by what it
    // is: the declared input it reuses.
    return BazelBuildInputs.labelOf(file) ?: shorten(file)
  }

  private fun Recording.describe(source: Source): RecipeSource {
    val file = when (source) {
      is ZipSource -> source.file
      is UnpackedZipSource -> source.file
      is DirSource -> source.dir
      is FileSource -> source.file
      else -> null
    }
    val label = file?.let(BazelBuildInputs::labelOf)
    return RecipeSource(
      kind = when (source) {
        is ZipSource -> "zip"
        is UnpackedZipSource -> "unpackedZip"
        is DirSource -> "dir"
        is FileSource -> "file"
        is InMemoryContentSource -> "inMemory"
        is LazySource -> "lazy"
        is CustomAssetShimSource -> "customAsset"
      },
      label = label,
      path = if (label == null) file?.let { shorten(it) } else null,
      module = when (source) {
        is ZipSource -> source.moduleName
        is DirSource -> source.moduleName
        else -> null
      },
      prefix = (source as? DirSource)?.prefix?.takeIf { it.isNotEmpty() },
      filter = filterKindOf(source),
      filterCacheKey = source.filterCacheKey,
      presigned = source is ZipSource && source.isPreSignedAndExtractedCandidate,
      optimizeConfigId = (source as? ZipSource)?.optimizeConfigId,
      name = when (source) {
        is InMemoryContentSource -> source.relativePath
        is FileSource -> source.relativePath
        is LazySource -> source.name
        else -> null
      },
      size = when (source) {
        is InMemoryContentSource -> source.data.size
        is FileSource -> source.size
        else -> 0
      },
      hash = when (source) {
        is FileSource -> source.hash
        is LazySource -> source.precomputedHash
        else -> 0
      },
      // `UnpackedZipSource` is not in this set although it never reaches the packer directly: it is always wrapped in a
      // `LazySource`, which is, so the wrapper is what gets reported and the wrapped file keeps its label.
      needsCode = source is InMemoryContentSource || source is LazySource || source is CustomAssetShimSource,
    )
  }

  /**
   * What the build applies to a source's entries, as the build itself states it.
   *
   * `keyed` means the filter's inputs are in `filterCacheKey`. `unkeyed` means a filter runs whose inputs the build
   * states nowhere. The jar cache hashes only `filterCacheKey`, so an unkeyed filter is one the build treats as a
   * constant of the source's kind, which is code and not data.
   *
   * Both words name an obstacle to a pure-data executor, and `keyed` names the larger one. An `unkeyed` source costs
   * that one constant. A `keyed` source costs the constant plus the globs `filterCacheKey` names. Reporting the
   * distinction is the point, because different work removes each of the two.
   */
  private fun filterKindOf(source: Source): String? = when {
    source.filterCacheKey.isNotEmpty() -> "keyed"
    source.filter != null -> "unkeyed"
    // `DirSource` overrides no `filter`; its `excludes` are what a directory walk filters on.
    source is DirSource && source.excludes.isNotEmpty() -> "unkeyed"
    else -> null
  }

  private fun Recording.shorten(file: Path): String = when {
    file.startsWith(MAVEN_REPO) -> $$"$MAVEN_REPOSITORY$/" + MAVEN_REPO.relativize(file).invariantSeparatorsPathString
    file.startsWith(projectHome) -> $$"$PROJECT_DIR$/" + projectHome.relativize(file).invariantSeparatorsPathString
    scratchDir != null && file.startsWith(scratchDir) -> $$"$SCRATCH$/" + scratchDir.relativize(file).invariantSeparatorsPathString
    // Deliberately left absolute rather than trimmed to something that looks portable: a source under none of the roots
    // above is one this report cannot identify machine-independently, and that has to be visible in the artifact.
    else -> file.invariantSeparatorsPathString
  }
}
