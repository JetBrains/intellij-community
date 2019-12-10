// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.cds

import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.concurrency.*

@Suppress("unused")
object CDSForTests {
  /**
   * See com.intellij.openapi.ui.playback.commands.CallCommand
   */
  @JvmStatic
  @Suppress("unused")
  fun toggleCDSForPerfTests(@Suppress("UNUSED_PARAMETER") context: PlaybackContext, enableCDS: String): Promise<String> {
    val promise = AsyncPromise<String>()

    AppExecutorUtil.getAppExecutorService().execute {
      ProgressManager.getInstance().run(object : Task.Backgroundable(
        null,
        "Class Data Sharing for tests",
        false,
        PerformInBackgroundOption.ALWAYS_BACKGROUND
      ) {
        override fun run(indicator: ProgressIndicator) {
          indicator.isIndeterminate = true

          try {
            if (enableCDS.toBoolean()) {
              val paths = CDSManager.installCDS(indicator)
              if (paths == null) promise.setError("Failed to install AppCDS, see log for errors")
              else promise.setResult("ok")
            }
            else {
              CDSManager.removeCDS()
              promise.setResult("ok")
            }
          }
          catch (t: Throwable) {
            promise.setError(t)
          }
        }
      })
    }

    return promise
  }
}
