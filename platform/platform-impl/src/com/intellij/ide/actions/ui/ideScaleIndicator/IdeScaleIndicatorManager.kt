// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.ui.ideScaleIndicator

import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.ui.UISettingsUtils
import com.intellij.ide.ui.percentValue
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.UpdateScaleHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import java.awt.Point
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Service(Service.Level.PROJECT)
internal class IdeScaleIndicatorManager(private val project: Project, coroutineScope: CoroutineScope) {
  private var balloon: Balloon? = null
  private var indicator: IdeScaleIndicator? = null
  private val updateRequests = MutableSharedFlow<Duration>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val updateScaleHelper = UpdateScaleHelper { UISettingsUtils.getInstance().currentIdeScale }

  init {
    setupLafListener(coroutineScope)

    coroutineScope.launch {
      updateRequests.collectLatest {
        if (it == Duration.ZERO) {
          // cancel request
          return@collectLatest
        }

        delay(it)
        withContext(Dispatchers.EDT) {
          cancelPopupIfNotHovered()
        }
      }
    }
  }

  private fun showIndicator() {
    check(updateRequests.tryEmit(Duration.ZERO))
    cancelCurrentPopup()
    val ideFrame = WindowManager.getInstance().getIdeFrame(project)?.component ?: return
    val indicator = IdeScaleIndicator(UISettingsUtils.getInstance().currentIdeScale.percentValue)
    this.indicator = indicator

    val newUI = ExperimentalUI.isNewUI()
    balloon = JBPopupFactory.getInstance().createBalloonBuilder(indicator)
      .setRequestFocus(false)
      .setShadow(true)
      .setHideOnAction(false)
      .setFillColor(indicator.background)
      .setBorderColor(if (newUI) JBColor.namedColor("Editor.Toolbar.borderColor", JBColor(0xEBECF0, 0x43454A))
                      else JBColor.border())
      .setShowCallout(false)
      .setFadeoutTime(0)
      .createBalloon().apply { setAnimationEnabled(false) }

    val point = RelativePoint(ideFrame, Point(ideFrame.width / 2, ideFrame.height - JBUIScale.scale(70)))
    balloon?.show(point, Balloon.Position.above)
    scheduleCancellation(POPUP_TIMEOUT)
  }

  private fun scheduleCancellation(delay: Duration) {
    check(updateRequests.tryEmit(delay))
  }

  private fun cancelPopupIfNotHovered() {
    if (indicator?.isHovered == true) {
      scheduleCancellation(POPUP_SHORT_TIMEOUT)
    }
    else {
      cancelCurrentPopup()
    }
  }

  private fun cancelCurrentPopup() {
    balloon?.hide()
    balloon = null
    indicator = null
  }

  private fun setupLafListener(coroutineScope: CoroutineScope) {
    ApplicationManager.getApplication().messageBus.connect(coroutineScope).subscribe(LafManagerListener.TOPIC, LafManagerListener {
      updateScaleHelper.saveScaleAndRunIfChanged {
        if (shouldIndicate) {
          showIndicator()
        }
      }
    })
  }

  companion object {
    private val POPUP_TIMEOUT = 4.seconds
    private val POPUP_SHORT_TIMEOUT = 1.seconds
    private var shouldIndicate: Boolean = false

    fun getInstance(project: Project): IdeScaleIndicatorManager = project.service<IdeScaleIndicatorManager>()

    fun indicateIfChanged(update: () -> Unit) {
      shouldIndicate = true
      try {
        update()
      }
      finally {
        shouldIndicate = false
      }
    }
  }
}