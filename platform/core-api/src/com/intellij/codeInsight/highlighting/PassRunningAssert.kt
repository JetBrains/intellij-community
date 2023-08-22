// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting

import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.atomic.AtomicLong

@Internal
class PassRunningAssert(private val assertMessage: String) {

  private val passIsRunning = ThreadLocal<Boolean>()

  fun runPass(): AccessToken = makeTokenForcingValue(true)

  fun suppressAssertInPass(): AccessToken = makeTokenForcingValue(false)

  private fun makeTokenForcingValue(forcedValue: Boolean): AccessToken {
    val prevValue = passIsRunning.get()
    passIsRunning.set(forcedValue)
    return object : AccessToken() {
      override fun finish() {
        if (prevValue != null)
          passIsRunning.set(prevValue)
        else
          passIsRunning.remove()
      }
    }
  }

  private val lastReport = AtomicLong(-1L)
  private val maxReportInterval = 10 * 1000

  fun assertPassNotRunning() {
    if (true != passIsRunning.get()) return
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      val curTime = System.currentTimeMillis()
      val lastReportVal = lastReport.get()
      if (curTime - lastReportVal < maxReportInterval) return
      if (!lastReport.compareAndSet(lastReportVal, curTime)) return
    }

    Logger.getInstance(PassRunningAssert::class.java).error(assertMessage)
  }

}