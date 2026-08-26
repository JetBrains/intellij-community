// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.searchableOptionsInjector

import org.jetbrains.intellij.build.io.AddDirEntriesMode
import org.jetbrains.intellij.build.io.PackageIndexBuilder
import org.jetbrains.intellij.build.io.ZipEntryProcessorResult
import org.jetbrains.intellij.build.io.readZipFile
import org.jetbrains.intellij.build.io.writeZipUsingTempFile
import java.nio.file.Path

/**
 * The searchable options files to add to one JAR.
 *
 * @param jar the JAR to rewrite.
 * @param entries the files to add to the root of the JAR.
 */
class SearchableOptionsInjection(
  @JvmField val jar: Path,
  @JvmField val entries: List<SearchableOptionsEntry>
)

/**
 * One searchable options file.
 *
 * @param entryName the name that the platform uses to look up the file at runtime.
 *   A plugin uses a name such as `p-org.jetbrains.kotlin-searchableOptions.json`.
 *   A content module uses a name such as `m-intellij.some.module-searchableOptions.json`.
 * @param file the source file to copy into the JAR.
 */
class SearchableOptionsEntry(
  @JvmField val entryName: String,
  @JvmField val file: Path
)

/**
 * Adds the searchable options files to the JARs of a plugin distribution that the build made before.
 *
 * Each file must go into the JAR of its own module. The platform reads the file with
 * `classLoader.getResourceAsBytes(name, checkParents = false)`. The classloader of a content module that is not
 * embedded has only one root, which is the JAR of the module. Thus this function rewrites each JAR in place.
 * It does not add a new JAR to the distribution.
 *
 * This function rewrites each JAR one time only. It does not touch a JAR that gets no file.
 */
fun injectSearchableOptions(injections: List<SearchableOptionsInjection>) {
  for (injection in injections) {
    if (injection.entries.isEmpty()) {
      continue
    }
    injectIntoJar(injection.jar, injection.entries)
  }
}

private fun injectIntoJar(jar: Path, entries: List<SearchableOptionsEntry>) {
  val entryNames = entries.mapTo(HashSet()) { it.entryName }
  val packageIndexBuilder = PackageIndexBuilder(AddDirEntriesMode.NONE)
  writeZipUsingTempFile(jar, packageIndexBuilder) { zipWriter ->
    // `readZipFile` skips the stale `__index__` entry, thus this code makes the package index again from the start
    readZipFile(jar) { name, dataSupplier ->
      require(!entryNames.contains(name)) { "'$name' is already present in '$jar'" }
      packageIndexBuilder.addFile(name)
      zipWriter.uncompressedData(name, dataSupplier())
      ZipEntryProcessorResult.CONTINUE
    }
    for (entry in entries) {
      packageIndexBuilder.addFile(entry.entryName)
      zipWriter.file(entry.entryName, entry.file)
    }
  }
}
