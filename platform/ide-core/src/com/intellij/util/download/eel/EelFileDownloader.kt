// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.download.eel

import com.intellij.util.download.DownloadableFileDescription
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Path

/**
 * Use [EelDownloadableFileService] to create instances of this interface
 */
@ApiStatus.Internal
interface EelFileDownloader {

  /**
   * Download files synchronously. Call this method under progress only (see [com.intellij.openapi.progress.Task]).
   *
   * @param targetDir target directory for downloaded files
   * @return list of downloaded files with their descriptions
   * @throws IOException On errors.
   */
  @Throws(IOException::class)
  fun download(targetDir: Path): MutableList<Pair<Path, DownloadableFileDescription>>
}