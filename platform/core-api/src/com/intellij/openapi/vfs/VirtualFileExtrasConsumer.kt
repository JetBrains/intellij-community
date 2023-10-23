// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface VirtualFileExtrasConsumer<TData : Any> : VirtualFileExtras<TData> {
  companion object {
    val EP_NAME: ExtensionPointName<VirtualFileExtrasConsumer<*>> = ExtensionPointName.create("com.intellij.virtualFile.extras.consumer")
  }

  /**
   * Called from the frontend side
   */
  // TODO [A.Bukhonov] maybe make it suspend?
  fun consumeValue(project: Project, virtualFile: VirtualFile, value: TData)

  fun consumeValueAny(project: Project, virtualFile: VirtualFile, value: Any) {
    @Suppress("UNCHECKED_CAST")
    consumeValue(project, virtualFile, value as TData)
  }
}