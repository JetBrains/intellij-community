// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

interface VirtualFileExtraDataReceiver {
  companion object {
    val EP_NAME: ExtensionPointName<VirtualFileExtraDataReceiver> = ExtensionPointName.create("com.intellij.virtualFileExtraDataReceiver")
  }

  fun tryConsume(project: Project, virtualFile: VirtualFile, key: String, value: String?)
}