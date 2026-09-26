// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.universal

import com.intellij.platform.eel.fs.EelFileSystemApi.FileChangeType
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
interface FileWatcherAdapter {
  suspend fun subscribe(path: Path): Flow<FileChangeType>?

  suspend fun unsubscribe(path: Path)

  suspend fun stop()
}