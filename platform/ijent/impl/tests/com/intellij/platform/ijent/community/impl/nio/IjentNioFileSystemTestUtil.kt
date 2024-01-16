// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio

import com.intellij.platform.ijent.IjentId
import kotlinx.coroutines.CoroutineScope

enum class FsKind(val isWindows: Boolean) {
  UNIX(false), WINDOWS(true),
}

fun CoroutineScope.mockIjentFileSystemApi(fsKind: FsKind): MockIjentFileSystemApi {
  return MockIjentFileSystemApi(
    coroutineScope = this,
    id = IjentId("test"),
    isWindows = fsKind.isWindows,
  )
}