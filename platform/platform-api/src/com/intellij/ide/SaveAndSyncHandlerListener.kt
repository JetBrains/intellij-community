// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface SaveAndSyncHandlerListener {
  companion object {
    @Topic.AppLevel
    val TOPIC = Topic.create("SaveAndSyncHandler events", SaveAndSyncHandlerListener::class.java)
  }

  fun beforeRefresh()

  fun beforeSave(task: SaveAndSyncHandler.SaveTask, forceExecuteImmediately: Boolean)
}