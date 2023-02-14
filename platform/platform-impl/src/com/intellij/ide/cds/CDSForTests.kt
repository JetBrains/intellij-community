// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.cds

import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import java.util.concurrent.TimeUnit

/**
 * this class is used from AppCDSIntegrationTest
 * See com.intellij.openapi.ui.playback.commands.CallCommand
 */
object CDSForTests {
  @JvmStatic
  fun waitForEnabledCDS(@Suppress("UNUSED_PARAMETER") context: PlaybackContext): String? {
    val promise = AsyncPromise<String>()

    object : Runnable {
      fun reschedule() {
        AppExecutorUtil.getAppScheduledExecutorService().schedule(this, 300, TimeUnit.MILLISECONDS)
      }

      override fun run() {
        val result = StartupActivity.POST_STARTUP_ACTIVITY.findExtension(CDSStartupActivity::class.java)?.setupResult?.get()
        if (result == null) return reschedule()

        if (result == "enabled:success") {
          promise.setResult(result)
        } else {
          promise.setError(result)
        }
      }
    }.run()

    return promise.get()
  }
}
