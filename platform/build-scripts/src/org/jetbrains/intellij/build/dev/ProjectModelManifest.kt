// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("RAW_RUN_BLOCKING")

package org.jetbrains.intellij.build.dev

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively

/**
 * Builds the checkout-shaped tree a build needs to load the JPS project model, out of files the caller declares.
 *
 * A caller that is not a checkout - a Bazel action, whose working directory is an execroot full of symlinked inputs - names
 * every model file it depends on in [manifest] and gets them laid out at the paths the loader expects, under [target].
 * Rows are tab-separated:
 *
 * ```
 * copy<TAB>path/to/the/input<TAB>relative/destination/in/the/tree
 * create<TAB><TAB>relative/destination/in/the/tree
 * ```
 *
 * `create` rows carry the repository marker files, and they are not optional: `IdeaProjectLoaderUtil` treats
 * `intellij.build.ultimate.home.path` as a place to start searching upwards from rather than as an answer, so a tree
 * without `.ultimate.root.marker` is not recognized as a repository however that property is set.
 *
 * Twin: `JpsModuleToBazelTargetsOnly` reads the same manifest format for the targets-json tool. The two cannot share code -
 * that tool lives in the standalone `jps_to_bazel` Bazel project - so the format is duplicated deliberately, and a change to
 * one of them has to be made in the other.
 *
 * @return [target], for use as a project directory.
 */
@Internal
@OptIn(ExperimentalPathApi::class)
fun materializeProjectModelTree(manifest: Path, target: Path): Path {
  // rebuilt from scratch every time: a file left over from a previous run is a model entry that nothing declares
  target.deleteRecursively()

  val rows = Files.readAllLines(manifest).mapNotNull { line ->
    if (line.isBlank()) {
      return@mapNotNull null
    }

    val fields = line.split('\t', limit = 3)
    require(fields.size == 3) { "Invalid project model manifest line (expected 'action<TAB>source<TAB>destination'): $line" }
    val destination = target.resolve(fields[2]).normalize()
    require(destination.startsWith(target) && destination != target) {
      "Project model manifest destination '${fields[2]}' escapes $target"
    }
    ProjectModelRow(action = fields[0], source = fields[1], destination = destination)
  }

  // up front and single-threaded: parallel `createDirectories` calls race on the ancestors they share
  for (row in rows) {
    row.destination.parent?.createDirectories()
  }

  // a project model is tens of thousands of small files, and copying them one after another costs as much as the assembly
  // that follows
  runBlocking(Dispatchers.IO) {
    for (chunk in rows.chunked(512)) {
      launch {
        for (row in chunk) {
          when (row.action) {
            // a missing source fails here rather than quietly producing a thinner project model
            "copy" -> Path.of(row.source).copyTo(row.destination)
            "create" -> Files.createFile(row.destination)
            else -> error("Unknown project model manifest action '${row.action}' for '${row.destination}'")
          }
        }
      }
    }
  }
  return target
}

private class ProjectModelRow(@JvmField val action: String, @JvmField val source: String, @JvmField val destination: Path)
