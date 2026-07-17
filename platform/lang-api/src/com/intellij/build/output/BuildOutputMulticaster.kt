// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.output

import com.intellij.build.BuildProgressListener
import com.intellij.build.events.BuildEvent
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.NonExtendable

@NonExtendable
interface BuildOutputMulticaster {

  fun notifyBuildEvent(event: BuildEvent)

  companion object {

    @Internal
    fun BuildProgressListener.asMulticaster(buildId: Any): BuildOutputMulticaster {
      return object : BuildOutputMulticaster {
        override fun notifyBuildEvent(event: BuildEvent) =
          this@asMulticaster.onEvent(buildId, event)
      }
    }
  }
}
