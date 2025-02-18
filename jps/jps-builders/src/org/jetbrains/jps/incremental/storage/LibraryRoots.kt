// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage.dataTypes

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
enum class LibRootUpdateResult {
  // if actually exists, is not a directory and has at lest namespace or timestamp changed
  EXISTS_AND_MODIFIED,
  DOES_NOT_EXIST,
  UNKNOWN
}

@ApiStatus.Internal
interface LibraryRoots {
  fun getNamespace(root: Path): String?

  fun getRoots(acc: MutableSet<Path>): Set<Path>

  fun removeRoots(toRemove: Iterable<Path>)

  fun updateIfExists(root: Path, namespace: String): LibRootUpdateResult
}
