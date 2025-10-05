// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.download.eel

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.download.DownloadableFileDescription
import com.intellij.util.download.DownloadableFileSetDescription
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class EelDownloadableFileService {

  abstract fun createDownloader(description: DownloadableFileSetDescription): EelFileDownloader

  abstract fun createFileDescription(downloadUrl: String, fileName: String): DownloadableFileDescription

  companion object {
    val instance: EelDownloadableFileService
      get() = ApplicationManager.getApplication().getService<EelDownloadableFileService>(EelDownloadableFileService::class.java)
  }
}