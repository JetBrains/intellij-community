// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.DiskQueryRelay
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import java.io.IOException
import java.util.function.Function

@Service( Service.Level.APP)
internal class VfsUtilCoreApplicationService {

  val loadTextDQR: DiskQueryRelay<Pair<VirtualFile, Int>, String> = DiskQueryRelay<Pair<VirtualFile, Int>, String>(
    Function { pair: Pair<VirtualFile, Int> ->
      try {
        return@Function VfsUtilCore.loadText(pair.first, pair.second)
      }
      catch (e: IOException) {
        // Wrap IOException
        throw RuntimeException(e)
      }
    }
  )
}
