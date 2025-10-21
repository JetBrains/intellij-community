// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.download.impl.eel

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.download.DownloadableFileDescription
import com.intellij.util.download.DownloadableFileSetDescription
import com.intellij.util.download.eel.EelDownloadableFileService
import com.intellij.util.download.eel.EelFileDownloader
import com.intellij.util.download.impl.DownloadableFileDescriptionImpl

internal class EelDownloadableFileServiceImpl : EelDownloadableFileService() {

  override fun createDownloader(description: DownloadableFileSetDescription): EelFileDownloader {
    return EelFileDownloaderImpl(description.getFiles())
  }

  override fun createFileDescription(downloadUrl: String, fileName: String): DownloadableFileDescription {
    return DownloadableFileDescriptionImpl(downloadUrl, FileUtilRt.getNameWithoutExtension(fileName), FileUtilRt.getExtension(fileName))
  }
}