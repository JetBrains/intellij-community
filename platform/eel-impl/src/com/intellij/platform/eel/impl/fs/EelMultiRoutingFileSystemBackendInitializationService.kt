// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.fs

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.core.nio.fs.MultiRoutingFileSystem
import com.intellij.platform.eel.provider.MultiRoutingFileSystemBackend
import java.nio.file.FileSystems

internal class EelMultiRoutingFileSystemBackendInitializationService : MultiRoutingFileSystemBackend.InitializationService {
  init {
    init()
  }

  private fun init() {
    val log = logger<EelMultiRoutingFileSystemBackendInitializationService>()
    val fs = FileSystems.getDefault()
    if (fs !is MultiRoutingFileSystem) {
      log.info("The default file system is ${FileSystems.getDefault()}, Eel MultiRoutingFileSystem can't be initialized")
      return
    }

    GlobalEelMrfsBackendProvider.install(fs.provider())
    log.info("Eel MultiRoutingFileSystem backend is initialized")
  }
}