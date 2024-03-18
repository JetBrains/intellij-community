// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.logger

import com.intellij.internal.statistic.eventLog.events.BooleanEventField
import com.intellij.internal.statistic.eventLog.events.ClassEventField
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.platform.ml.impl.apiPlatform.MLApiPlatform
import com.intellij.platform.ml.impl.monitoring.MLApiStartupListener
import com.intellij.platform.ml.impl.monitoring.MLApiStartupProcessListener
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MLApiPlatformStartupLogger : EventIdRecordingMLEvent(), MLApiStartupListener {
  companion object {
    val SUCCESS = BooleanEventField("success")
    val EXCEPTION = ClassEventField("exception")
  }

  override val eventName: String = "startup"

  override val declaration: Array<EventField<*>> = arrayOf(SUCCESS)

  override fun onBeforeStarted(apiPlatform: MLApiPlatform): MLApiStartupProcessListener {
    val eventId = getEventId(apiPlatform)
    return object : MLApiStartupProcessListener {
      override fun onFinished() = eventId.log(SUCCESS with true)

      override fun onFailed(exception: Throwable?) {
        val fields = mutableListOf<EventPair<*>>(SUCCESS with false)
        exception?.let { fields += EXCEPTION with it.javaClass }
        eventId.log(*fields.toTypedArray())
      }
    }
  }
}
