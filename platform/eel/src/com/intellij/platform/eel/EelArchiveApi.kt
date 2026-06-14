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
  /** Extracts the archive at [archive] into the [target] directory; both paths are within the environment. */
  @Throws(IOException::class)
  suspend fun extract(archive: EelPath, target: EelPath)
}
