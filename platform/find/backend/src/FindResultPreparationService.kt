// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.find.backend

import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class FindResultPreparationService {
  open fun shouldWaitForUpdate(virtualFile: VirtualFile): Boolean = false

  companion object {
    @JvmStatic
    fun getInstance(): FindResultPreparationService {
      return service<FindResultPreparationService>()
    }
  }
}
