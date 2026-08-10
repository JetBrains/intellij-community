// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.io.copyFile
import org.jetbrains.intellij.build.io.linkOrCopyDir
import org.jetbrains.intellij.build.io.linkOrCopyFile
import java.nio.file.Path

/**
 * Puts an entry of an immutable cache - a download-cache file, an extracted archive, a Bazel runfile -
 * into the layout, hardlinking it where the layout is a disposable dev run directory and copying it
 * everywhere else.
 *
 * Use this only for a [source] nothing will rewrite and a [target] nothing will patch in place: a
 * hardlink makes the two one file on disk, so a later `chmod`, re-sign, or patch of the target would
 * reach back into the cache every other build reads. See [BuildOptions.linkImmutableCacheEntries] for
 * why only an in-process dev-mode assembly turns the linking on.
 */
fun materializeCacheFile(source: Path, target: Path, context: BuildContext) {
  if (context.options.linkImmutableCacheEntries) {
    linkOrCopyFile(source, target)
  }
  else {
    copyFile(source, target)
  }
}

/**
 * [materializeCacheFile] for a whole extracted tree. Symbolic links inside it stay symbolic links.
 */
fun materializeCacheDir(sourceDir: Path, targetDir: Path, context: BuildContext) {
  if (context.options.linkImmutableCacheEntries) {
    linkOrCopyDir(sourceDir, targetDir)
  }
  else {
    copyDir(sourceDir, targetDir)
  }
}
