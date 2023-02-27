// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsUtils
import com.intellij.ide.ui.percentStringValue
import com.intellij.ide.ui.percentValue
import com.intellij.internal.statistic.service.fus.collectors.IdeZoomChanged
import com.intellij.internal.statistic.service.fus.collectors.IdeZoomEventFields
import com.intellij.internal.statistic.service.fus.collectors.IdeZoomSwitcherClosed
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.Condition
import com.intellij.util.Alarm
import javax.swing.JList
import javax.swing.event.ListSelectionEvent

class QuickChangeIdeScaleAction : QuickSwitchSchemeAction() {
  private val switchAlarm = Alarm()
  private var initialScale = UISettingsUtils.instance.currentIdeScale

  override fun fillActions(project: Project?, group: DefaultActionGroup, dataContext: DataContext) {
    initialScale = UISettingsUtils.instance.currentIdeScale

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

  override fun getAidMethod(): JBPopupFactory.ActionSelectionAid {
    return JBPopupFactory.ActionSelectionAid.SPEEDSEARCH
  }

  override fun showPopup(e: AnActionEvent?, popup: ListPopup) {
    switchAlarm.cancelAllRequests()

    popup.addListSelectionListener { event: ListSelectionEvent ->
      val item = (event.source as JList<*>).selectedValue
      if (item is AnActionHolder) {
        val anAction = item.action
        if (anAction is ChangeScaleAction) {
          switchAlarm.cancelAllRequests()
          switchAlarm.addRequest(Runnable {
            applyScale(anAction.scale, popup, true)
          }, SELECTION_THROTTLING_MS)
        }
      }
    }

    popup.addListener(object : JBPopupListener {
      override fun onClosed(event: LightweightWindowEvent) {
        switchAlarm.cancelAllRequests()
        if (!event.isOk) {
          applyScale(initialScale, popup, false)
          logSwitcherClosed(false)
        }
      }
    })

    super.showPopup(e, popup)
  }

  override fun preselectAction(): Condition<in AnAction?> {
    return Condition { a: AnAction? -> a is ChangeScaleAction && a.scale.percentValue == initialScale.percentValue }
  }

  private fun applyScale(scale: Float, popup: ListPopup, shouldLog: Boolean) {
    if (UISettingsUtils.instance.currentIdeScale.percentValue == scale.percentValue) return
    if (shouldLog) logIdeZoomChanged(scale)

    UISettingsUtils.instance.setCurrentIdeScale(scale)
    UISettings.getInstance().fireUISettingsChanged()

    if (!popup.isDisposed) {
      popup.pack(true, true)
    }
  }

  private class ChangeScaleAction(val scale: Float) : DumbAwareAction(scale.percentStringValue) {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT
    override fun actionPerformed(e: AnActionEvent) {
      val utils = UISettingsUtils.instance
      if (utils.currentIdeScale.percentValue != scale.percentValue) {
        logIdeZoomChanged(scale)
        utils.setCurrentIdeScale(scale)
        UISettings.getInstance().fireUISettingsChanged()
      }
      logSwitcherClosed(true)
    }
  }

  companion object {
    private const val SELECTION_THROTTLING_MS = 500
  }
}

private fun logIdeZoomChanged(value: Float) {
  IdeZoomChanged.log(
    IdeZoomEventFields.zoomMode.with(if (value.percentValue > UISettingsUtils.instance.currentIdeScale.percentValue) IdeZoomEventFields.ZoomMode.ZOOM_IN
                                     else IdeZoomEventFields.ZoomMode.ZOOM_OUT),
    IdeZoomEventFields.place.with(IdeZoomEventFields.Place.SWITCHER),
    IdeZoomEventFields.zoomScalePercent.with(value.percentValue),
    IdeZoomEventFields.presentationMode.with(UISettings.getInstance().presentationMode)
  )
}

private fun logSwitcherClosed(applied: Boolean) {
  IdeZoomSwitcherClosed.log(
    IdeZoomEventFields.applied.with(applied),
    IdeZoomEventFields.finalZoomScalePercent.with(UISettingsUtils.instance.currentIdeScale.percentValue),
    IdeZoomEventFields.presentationMode.with(UISettings.getInstance().presentationMode)
  )
}
