// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.Internal
interface SaveAndSyncHandlerListener {
  companion object {
    @Topic.AppLevel
    @JvmField
    val TOPIC: Topic<SaveAndSyncHandlerListener> = Topic("SaveAndSyncHandler events",
                                                         SaveAndSyncHandlerListener::class.java,
                                                         Topic.BroadcastDirection.NONE)
  }

  fun beforeRefresh() {}

  fun beforeSave(task: SaveAndSyncHandler.SaveTask?, forceExecuteImmediately: Boolean) {}
}