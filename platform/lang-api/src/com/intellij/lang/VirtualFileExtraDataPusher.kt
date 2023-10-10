// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic

interface VirtualFileExtraDataPusher {
  companion object {
    @Topic.ProjectLevel
    val TOPIC: Topic<VirtualFileExtraDataPusher> = Topic.create("VirtualFileExtraDataPusher",
                                                                VirtualFileExtraDataPusher::class.java)
  }

  fun syncStarted(virtualFile: VirtualFile, accessor: VirtualFileExtraDataAccessor)

  fun syncFinished(virtualFile: VirtualFile)

  interface VirtualFileExtraDataAccessor {
    fun setExtraData(key: String, value: String?)

    fun isDisposed(): Boolean
  }

}