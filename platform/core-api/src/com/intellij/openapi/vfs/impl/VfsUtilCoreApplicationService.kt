// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.CheckedDiskQueryRelay
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ThrowableConvertor
import java.io.IOException

@Service(Service.Level.APP)
internal class VfsUtilCoreApplicationService {

  /** [CheckedDiskQueryRelay], so the Java caller sees the [IOException] of the function */
  val loadTextDQR: CheckedDiskQueryRelay<Pair<VirtualFile, Int>, String, IOException> = CheckedDiskQueryRelay.create(
    ThrowableConvertor { pair -> VfsUtilCore.loadText(pair.first, pair.second) }
  )
}
