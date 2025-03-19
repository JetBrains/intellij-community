// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.service

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PostQuickFixTaskServiceImpl : PostQuickFixTaskService {

  override fun runOrRegisterPostQuickFixTask(filesToSave: List<VirtualFile>, block: () -> Unit) {
    block()
  }
}
