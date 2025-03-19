// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsUtils
import com.intellij.ide.ui.percentStringValue
import com.intellij.ide.ui.percentValue
import com.intellij.internal.statistic.service.fus.collectors.IdeZoomEventFields
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger.IdeZoomChanged
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger.IdeZoomSwitcherClosed
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.Condition
import com.intellij.ui.hover.HoverListener
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.concurrency.EdtScheduler
import kotlinx.coroutines.Job
import java.awt.Component
import javax.swing.JList
import javax.swing.event.ListSelectionEvent
import kotlin.math.roundToInt

private class QuickChangeIdeScaleAction : QuickSwitchSchemeAction(), ActionRemoteBehaviorSpecification.Frontend {
  private var job: Job? = null
  private var popupSession: PopupSession? = null

  override fun fillActions(project: Project?, group: DefaultActionGroup, dataContext: DataContext) {
    val initialScale = UISettingsUtils.getInstance().currentIdeScale

    val options = IdeScaleTransformer.Settings.currentScaleOptions.toMutableList()
    if (options.firstOrNull { it.percentValue == initialScale.percentValue } == null) {
      options.add(initialScale)
      options.sort()
    }

    options.forEach { scale ->
      group.add(ChangeScaleAction(scale))
    }
  }

  override fun isEnabled(): Boolean = IdeScaleTransformer.Settings.currentScaleOptions.isNotEmpty()

  override fun getAidMethod(): JBPopupFactory.ActionSelectionAid = JBPopupFactory.ActionSelectionAid.SPEEDSEARCH

  override fun showPopup(e: AnActionEvent?, popup: ListPopup) {
    val initialScale = UISettingsUtils.getInstance().currentIdeScale
    cancelJob()

    popup.addListSelectionListener { event: ListSelectionEvent ->
      val item = (event.source as JList<*>).selectedValue
      if (item is AnActionHolder) {
        val anAction = item.action
        if (anAction is ChangeScaleAction) {
          job?.cancel()
          job = EdtScheduler.getInstance().schedule(SELECTION_THROTTLING_MS, Runnable {
            applyUserScale(scale = anAction.scale, shouldLog = true)
            if (!popup.isDisposed) {
              popup.pack(true, true)
              popupSession?.updateLocation()
            }
          })
        }
      }
    }

    popup.addListener(object : JBPopupListener {
      override fun onClosed(event: LightweightWindowEvent) {
        cancelJob()
        popupSession = null
        if (!event.isOk) {
          applyUserScale(initialScale, false)
          logSwitcherClosed(false)
        }
      }
    })

    val hoverListener = object: HoverListener() {
      override fun mouseEntered(component: Component, x: Int, y: Int) {
        popupSession?.updateMouseCoordinates(x, y)
      }

      override fun mouseMoved(component: Component, x: Int, y: Int) {
        popupSession?.updateMouseCoordinates(x, y)
      }

      override fun mouseExited(component: Component) {
        popupSession?.mouseIsInside = false
      }
    }

    hoverListener.addTo(popup.content)
    popupSession = PopupSession(popup)

    super.showPopup(e, popup)
  }

  private fun cancelJob() {
    job?.let {
      it.cancel()
      job = null
    }
  }

  override fun preselectAction(): Condition<in AnAction?> {
    return Condition { a: AnAction? -> a is ChangeScaleAction && a.scale.percentValue == UISettingsUtils.getInstance().currentIdeScale.percentValue }
  }

  private class ChangeScaleAction(val scale: Float) : DumbAwareAction(scale.percentStringValue) {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT
    override fun actionPerformed(e: AnActionEvent) {
      applyUserScale(scale, true)
      logSwitcherClosed(true)
    }
  }

  companion object {
    private fun applyUserScale(scale: Float, shouldLog: Boolean) {
      if (UISettingsUtils.getInstance().currentIdeScale.percentValue == scale.percentValue) return
      if (shouldLog) logIdeZoomChanged(scale)

      UISettingsUtils.getInstance().setCurrentIdeScale(scale)
      UISettings.getInstance().fireUISettingsChanged()
    }

    private const val SELECTION_THROTTLING_MS = 500
  }

  /**
   * Helper for recalculating popup location to avoid popup jumping after selecting zoom level by a mouse cursor
   *
   * @param popup The list popup associated with this session.
   */
  private class PopupSession(val popup: ListPopup) {
    var lastMouseX = -1
    var lastMouseY = -1
    var lastScale = 1f
    var mouseIsInside = false

    fun updateMouseCoordinates(x: Int, y: Int) {
      mouseIsInside = true
      lastMouseX = x
      lastMouseY = y
      lastScale = JBUIScale.scale(1f)
    }

    fun updateLocation() {
      val oldX = lastMouseX
      val oldY = lastMouseY
      val oldScale = lastScale

      if (popup.isDisposed || !mouseIsInside || lastMouseX < 0 || lastMouseY < 0) return

      val newScale = JBUIScale.scale(1f)

      val newX = (oldX * newScale / oldScale).roundToInt()
      val newY = (oldY * newScale / oldScale).roundToInt()

      val dX = newX - oldX
      val dY = newY - oldY

      val location = popup.locationOnScreen
      location.x -= dX
      location.y -= dY
      popup.setLocation(location)
    }
  }
}

private fun logIdeZoomChanged(value: Float) {
  IdeZoomChanged.log(
    IdeZoomEventFields.zoomMode.with(if (value.percentValue > UISettingsUtils.getInstance().currentIdeScale.percentValue) IdeZoomEventFields.ZoomMode.ZOOM_IN
                                     else IdeZoomEventFields.ZoomMode.ZOOM_OUT),
    IdeZoomEventFields.place.with(IdeZoomEventFields.Place.SWITCHER),
    IdeZoomEventFields.zoomScalePercent.with(value.percentValue),
    IdeZoomEventFields.presentationMode.with(UISettings.getInstance().presentationMode)
  )
}

private fun logSwitcherClosed(applied: Boolean) {
  IdeZoomSwitcherClosed.log(
    IdeZoomEventFields.applied.with(applied),
    IdeZoomEventFields.finalZoomScalePercent.with(UISettingsUtils.getInstance().currentIdeScale.percentValue),
    IdeZoomEventFields.presentationMode.with(UISettings.getInstance().presentationMode)
  )
}
