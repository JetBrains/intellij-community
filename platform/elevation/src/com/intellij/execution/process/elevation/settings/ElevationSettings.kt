// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.elevation.settings

import com.intellij.execution.process.mediator.daemon.QuotaOptions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.util.messages.Topic
import kotlin.time.ExperimentalTime
import kotlin.time.minutes

@Service
@State(name = "Elevation", storages = [Storage(value = "security.xml", roamingType = RoamingType.DISABLED)])
class ElevationSettings : PersistentStateComponentWithModificationTracker<ElevationSettings.ElevationOptions> {
  companion object {
    @JvmStatic
    fun getInstance() = service<ElevationSettings>()
  }

  private val options = ElevationOptions()

  var quotaOptions = QuotaOptions(options.quotaTimeLimitMs)
    set(newValue) {
      val oldValue = field
      field = newValue
      if (oldValue != newValue) {
        options.quotaTimeLimitMs = newValue.timeLimitMs
        ApplicationManager.getApplication().messageBus.syncPublisher(Listener.TOPIC).onDaemonQuotaOptionsChanged(oldValue, newValue)
      }
    }

  override fun getState() = options
  override fun loadState(state: ElevationOptions) = options.copyFrom(state)
  override fun getStateModificationCount() = options.modificationCount

  interface Listener {
    companion object {
      @JvmField
      val TOPIC = Topic.create("ElevationSettings.Listener", Listener::class.java)
    }

    @JvmDefault
    fun onDaemonQuotaOptionsChanged(oldValue: QuotaOptions, newValue: QuotaOptions) = Unit
  }

  @Suppress("EXPERIMENTAL_IS_NOT_ENABLED")
  @OptIn(ExperimentalTime::class)
  class ElevationOptions : BaseState() {
    var quotaTimeLimitMs by property(15.minutes.toLongMilliseconds())
  }
}
