// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import java.nio.file.Path

/** The manifest entry, whose survival is decided per jar rather than per name - see [mergeIntoJar]. */
const val MANIFEST_ENTRY_NAME: String = "META-INF/MANIFEST.MF"

/**
 * One input of a merge: a jar, and which of its entries belong in the result.
 *
 * The filter is per source rather than per merge because the two kinds of input are filtered differently. A module
 * output contributes almost everything it holds; a third-party library jar has to lose its licences, signatures and
 * multi-release `module-info.class` entries, or several of them would collide on the same name and the survivors would
 * ship for nothing - see [defaultLibrarySourcesNamesFilter].
 */
class JarMergeSource(
  @JvmField val file: Path,
  @JvmField val nameFilter: (String) -> Boolean,
)

/**
 * Writes [target] from the entries of [sources], and returns the names that more than one source offered.
 *
 * Directory entries and the generated `__index__` are skipped on the way in ([readZipFile] does that), and a fresh
 * index is written for the result, so the output is a distribution-shaped jar regardless of how its inputs were
 * written. Entries are copied uncompressed: every input this is used on is already stored rather than deflated, so
 * there is nothing to gain by re-compressing and a copy is the cheapest correct thing.
 *
 * [keepManifest] carries the one policy the entry name cannot express. A jar that merges several sources must not keep
 * any `META-INF/MANIFEST.MF`, because the survivors would describe only one of them; a jar built from a single
 * meaningful source keeps its own. Which sources count as meaningful is the caller's decision, so it is passed in
 * rather than guessed from the source list.
 *
 * **First source wins.** Duplicates are expected - two libraries can legitimately carry the same `META-INF/services`
 * entry - so a collision is reported rather than fatal, and the caller decides whether a given name is worth failing
 * over. Sources are read in the order given, so that order is the precedence. To reproduce what the distribution
 * packer produces, the caller must emit **every library jar before every module output**: that is the order
 * `JarPackager` writes, and it means a library entry beats a module entry of the same name.
 */
fun mergeIntoJar(target: Path, sources: List<JarMergeSource>, keepManifest: Boolean = false): List<String> {
  val duplicates = ArrayList<String>()
  val seen = HashSet<String>()
  val packageIndexBuilder = PackageIndexBuilder(AddDirEntriesMode.NONE)
  ZipFileWriter(zipWriter(targetFile = target, packageIndexBuilder = packageIndexBuilder)).use { zipWriter ->
    for (source in sources) {
      readZipFile(source.file) { name, dataFetcher ->
        if ((keepManifest || name != MANIFEST_ENTRY_NAME) && source.nameFilter(name)) {
          if (seen.add(name)) {
            packageIndexBuilder.addFile(name)
            zipWriter.uncompressedData(name, dataFetcher())
          }
          else {
            duplicates.add(name)
          }
        }
        ZipEntryProcessorResult.CONTINUE
      }
    }
  }
  return duplicates
}

/**
 * Which entries of a *module output* jar are packed into a distribution jar.
 *
 * Far narrower than [defaultLibrarySourcesNamesFilter]: a module output is ours, so the only things to drop are the
 * icon-rule file that is a build-time input and the leftovers the compilation cache writes into an output directory.
 */
fun defaultModuleOutputNamesFilter(name: String): Boolean {
  return name != "icon-robots.txt" &&
         !name.endsWith("/icon-robots.txt") &&
         name != ".unmodified" &&
         name != ".hash" &&
         name != "classpath.index" &&
         name != "module-info.class"
}
