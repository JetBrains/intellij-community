// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

/**
 * [FileDocumentManagerListener] that is permitted to run on a background thread.
 */
@ApiStatus.Experimental
interface FileDocumentManagerListenerBackgroundable : FileDocumentManagerListener {

  companion object {
    @Topic.AppLevel
    @JvmField
    val TOPIC: Topic<FileDocumentManagerListenerBackgroundable> = Topic(FileDocumentManagerListenerBackgroundable::class.java, Topic.BroadcastDirection.TO_DIRECT_CHILDREN, true)
  }
}