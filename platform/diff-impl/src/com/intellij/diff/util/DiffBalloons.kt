// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.util

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.dsl.builder.HyperlinkEventAction
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.UnscaledGapsY
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import java.awt.Color
import javax.swing.Icon

object DiffBalloons {
  @JvmStatic
  fun showSuccessPopup(title: @NlsContexts.PopupContent String,
                       message: @NlsContexts.PopupContent String,
                       point: RelativePoint,
                       disposable: Disposable,
                       hyperlinkHandler: Runnable) {
    createAndShowBalloon(
      title, message,
      if (ExperimentalUI.isNewUI()) AllIcons.Status.Success else null,
      JBUI.CurrentTheme.Editor.Tooltip.SUCCESS_BACKGROUND,
      JBUI.CurrentTheme.Editor.Tooltip.SUCCESS_BORDER,
      point,
      disposable,
      HyperlinkEventAction { hyperlinkHandler.run() }
    )
  }

  @JvmStatic
  fun showWarningPopup(title: @NlsContexts.PopupContent String,
                       message: @NlsContexts.PopupContent String,
                       point: RelativePoint,
                       disposable: Disposable
  ) {
    createAndShowBalloon(
      title, message,
      AllIcons.General.Warning,
      JBUI.CurrentTheme.Editor.Tooltip.WARNING_BACKGROUND,
      JBUI.CurrentTheme.Editor.Tooltip.WARNING_BORDER,
      point,
      disposable
    )
  }

  private fun createAndShowBalloon(
    title: @NlsContexts.PopupContent String,
    message: @NlsContexts.PopupContent String,
    icon: Icon?,
    fillColor: Color,
    borderColor: Color,
    point: RelativePoint,
    disposable: Disposable,
    listener: HyperlinkEventAction? = null
  ) {
    val balloonContent = panel {
      val gap = 6
      row {
        if (icon!= null) {
          icon(icon).customize(UnscaledGaps(right = gap, bottom = gap))
        }
        text(title).bold().customize(UnscaledGaps(bottom = gap))
      }.customize(UnscaledGapsY.EMPTY)
      row {
        if (icon!= null) {
          icon(EmptyIcon.create(icon)).customize(UnscaledGaps(right = gap))
        }
        text(message, action = listener ?: HyperlinkEventAction.HTML_HYPERLINK_INSTANCE).customize(UnscaledGaps.EMPTY)
      }
    }.andTransparent()

    val balloon = JBPopupFactory.getInstance()
      .createBalloonBuilder(balloonContent)
      .setBorderInsets(JBUI.insets(12, 13, 12, 21))
      .setFillColor(fillColor)
      .setBorderColor(borderColor)
      .setAnimationCycle(200)
      .createBalloon()
    balloon.show(point, Balloon.Position.below)

    Disposer.register(disposable, balloon)
  }
}