// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.path.EelPath
import org.jetbrains.annotations.ApiStatus
import java.io.IOException

/**
 * Extraction of archives within the environment, reached via [EelApi.archive].
 */
@ApiStatus.Internal
interface EelArchiveApi {
  /**
   * Extracts [archive] into [target]. Format is detected by the [archive] filename extension:
   * `.zip`, `.tar`, `.tar.{gz,bz2,xz}` and the aliases `.tgz` / `.tbz2` / `.txz`.
   * Anything else throws [IOException] (`Unsupported archive`).
   *
   * [target] is created if missing. Existing entries are overwritten.
   * Symbolic links are extracted as-is from archive metadata - not safe for untrusted archives.
   */
  @Throws(IOException::class)
  suspend fun extract(archive: EelPath, target: EelPath)
}
