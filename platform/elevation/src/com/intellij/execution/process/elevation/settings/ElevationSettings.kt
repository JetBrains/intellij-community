// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.elevation.settings

import com.intellij.execution.process.elevation.ElevationBundle
import com.intellij.execution.process.mediator.daemon.QuotaOptions
import com.intellij.ide.nls.NlsMessages
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.*
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages.CANCEL
import com.intellij.openapi.ui.Messages.YES
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.messages.Topic
import kotlin.time.ExperimentalTime
import kotlin.time.minutes

@Service
@State(name = "Elevation", storages = [Storage(value = "security.xml", roamingType = RoamingType.DISABLED)])
class ElevationSettings : PersistentStateComponentWithModificationTracker<ElevationSettings.ElevationOptions> {
  companion object {
    @JvmStatic
    fun getInstance() = service<ElevationSettings>()

    @Suppress("EXPERIMENTAL_IS_NOT_ENABLED")
    @OptIn(ExperimentalTime::class)
    private val DEFAULT_GRACE_PERIOD_MS = 15.minutes.toLongMilliseconds()
  }

  private val options = ElevationOptions()

  var quotaOptions: QuotaOptions
    get() = QuotaOptions(timeLimitMs = if (options.isKeepAuth) options.gracePeriodMs else 0L,
                         isRefreshable = options.isKeepAuth && options.isRefreshable)
    set(newValue) = updateQuotaOptions {
      val isKeepAuth = newValue.timeLimitMs != 0L
      options.isKeepAuth = isKeepAuth
      if (isKeepAuth) {
        options.gracePeriodMs = newValue.timeLimitMs
      }
    }

  var isKeepAuth: Boolean
    get() = options.isKeepAuth
    set(value) = updateQuotaOptions {
      options.isKeepAuth = value
    }.also {
      if (value) isSettingsUpdateDone = true
    }

  var isRefreshable: Boolean
    get() = options.isRefreshable
    set(value) = updateQuotaOptions {
      options.isRefreshable = value
    }

  var quotaTimeLimitMs: Long
    get() = options.gracePeriodMs
    set(value) = updateQuotaOptions {
      options.gracePeriodMs = value
    }

  private var isSettingsUpdateDone: Boolean
    get() = synchronized(this) { options.isSettingsUpdateDone }
    set(value) = synchronized(this) {
      options.isSettingsUpdateDone = value
    }

  private fun updateQuotaOptions(block: () -> Unit) {
    val oldValue = quotaOptions
    block()
    val newValue = quotaOptions
    if (oldValue != newValue) {
      ApplicationManager.getApplication().messageBus.syncPublisher(Listener.TOPIC).onDaemonQuotaOptionsChanged(oldValue, newValue)
    }
  }

  override fun getState() = options
  override fun loadState(state: ElevationOptions) = options.copyFrom(state)
  override fun getStateModificationCount() = options.modificationCount

  fun askEnableKeepAuthIfNeeded(): Boolean {
    return isSettingsUpdateDone || invokeAndWaitIfNeeded { doAskEnableKeepAuthIfNeeded() }
  }

  private fun doAskEnableKeepAuthIfNeeded(): Boolean {
    ApplicationManager.getApplication().assertIsDispatchThread()

    if (isSettingsUpdateDone) return true

    val yesText = ElevationBundle.message("keep.authorized.for.0", NlsMessages.formatDuration(DEFAULT_GRACE_PERIOD_MS))
    val noText = ElevationBundle.message("authorize.every.time")

    val message = ExplanatoryTextUiUtil.message(ElevationBundle.message("text.you.are.about.to.run.privileges.process"),
                                                maxLineLength = 70)
    val title =
      if (SystemInfo.isMac) ElevationBundle.message("update.elevation.preferences")
      else ElevationBundle.message("update.elevation.settings")

    val yesNoCancelResult = MessageDialogBuilder
      .yesNoCancel(title, message)
      .yesText(yesText)
      .noText(noText)
      .guessWindowAndAsk()

    if (yesNoCancelResult == CANCEL) return false

    quotaTimeLimitMs = DEFAULT_GRACE_PERIOD_MS
    isKeepAuth = (yesNoCancelResult == YES)
    isSettingsUpdateDone = true
    return true
  }

  interface Listener {
    companion object {
      @JvmField
      val TOPIC = Topic.create("ElevationSettings.Listener", Listener::class.java)
    }

    @JvmDefault
    fun onDaemonQuotaOptionsChanged(oldValue: QuotaOptions, newValue: QuotaOptions) = Unit
  }

  class ElevationOptions : BaseState() {
    var isSettingsUpdateDone by property(false)
    var isKeepAuth by property(false)
    var isRefreshable by property(true)
    var gracePeriodMs by property(DEFAULT_GRACE_PERIOD_MS)
  }
}
