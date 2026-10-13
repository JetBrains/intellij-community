// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit

import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal

@ApiStatus.Experimental
interface LightEditServiceListener {
  @Internal
  companion object {
    @Topic.AppLevel
    val TOPIC: Topic<LightEditServiceListener> = Topic(LightEditServiceListener::class.java, Topic.BroadcastDirection.NONE)
  }

  @RequiresEdt
  fun lightEditWindowOpened(project: Project) {}

  @RequiresEdt
  fun lightEditWindowClosed(project: Project) {}
}
