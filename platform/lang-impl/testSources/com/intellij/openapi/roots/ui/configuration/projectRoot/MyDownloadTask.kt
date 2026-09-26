// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.projectRoot

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import kotlin.io.path.pathString

@ApiStatus.Internal
@TestOnly
class MyDownloadTask(private val homeDir: Path, private val sdkName: String, private val onDownload:(ProgressIndicator)-> Unit) : SdkDownloadTask {
  override fun getSuggestedSdkName(): String = sdkName

  override fun getPlannedHomeDir(): String = homeDir.pathString

  override fun getPlannedVersion(): String = "1.2.3"

  override fun doDownload(indicator: ProgressIndicator) {
    onDownload(indicator)
  }

}