// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("FontSizePopup")
@file:ApiStatus.Internal

package com.intellij.ui

import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBSlider
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.EDT
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.FlowLayout
import java.awt.MouseInfo
import java.awt.Point
import javax.swing.*

fun <T> showFontSizePopup(
  parentComponent: Component,
  initialFont: T,
  fontRange: List<T>,
  onPopupClose: () -> Unit,
  changeCallback: (T) -> Unit
): FontSizePopupData {
  val slider = JBSlider(0, fontRange.size - 1).apply {
    orientation = SwingConstants.HORIZONTAL
    isOpaque = true
    minorTickSpacing = 1
    paintTicks = true
    paintTrack = true
    snapToTicks = true
  }
  UIUtil.setSliderIsFilled(slider, true)
  val initialIndex = fontRange.indexOf(initialFont)
  slider.setValueWithoutEvents(initialIndex)
  slider.addChangeListener {
    changeCallback(fontRange[slider.value])
  }

  val panel = JPanel(FlowLayout(FlowLayout.RIGHT, 3, 0))
  panel.isOpaque = true
  panel.add(JLabel(ApplicationBundle.message("label.font.size")))
  panel.add(slider)
  panel.border = BorderFactory.createLineBorder(JBColor.border(), 1)

  val popup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, slider).createPopup()
  popup.addListener(object : JBPopupListener {
    override fun onClosed(event: LightweightWindowEvent) {
      onPopupClose()
    }
  })

  val location = MouseInfo.getPointerInfo().location
  popup.show(
    RelativePoint(Point(
      location.x - panel.preferredSize.width / 2,
      location.y - panel.preferredSize.height / 2
    )).getPointOn(parentComponent)
  )

  return FontSizePopupData(slider)
}

data class FontSizePopupData(val slider: JBSlider)

// Basically an observable value
interface FontSizeModel<T> {

  var value: T

  val values: List<T>

  /**
   * The returned flow immediately emits [value] when collected.
   * The returned flow never completes.
   */
  val updates: Flow<T>
}

/**
 * Shows a popup with a slider.
 *
 * [model] is bound to the slider:
 * - changing the slider value will emit a new value;
 * - changing the value will update the slider presentation.
 */
@RequiresEdt
fun <T> showFontSizePopup(model: FontSizeModel<T>, anchor: JComponent) {
  EDT.assertIsEdt()
  val popup = createFontSizePopup(model)
  val location = MouseInfo.getPointerInfo().location
  val content = popup.content
  val point = RelativePoint(Point(
    location.x - content.preferredSize.width / 2,
    location.y - content.preferredSize.height / 2,
  ))
  popup.show(point.getPointOn(anchor))
}

// This function can be generified to work with any enums.
private fun <T> createFontSizePopup(model: FontSizeModel<T>): JBPopup {
  val values = model.values

  val slider = JBSlider(0, values.size - 1).also {
    it.orientation = SwingConstants.HORIZONTAL
    it.isOpaque = true
    it.minorTickSpacing = 1
    it.paintTicks = true
    it.paintTrack = true
    it.snapToTicks = true
    UIUtil.setSliderIsFilled(it, true)
  }
  slider.addChangeListener {
    model.value = values[slider.value]
  }
  @OptIn(DelicateCoroutinesApi::class)
  val updateSliderJob = GlobalScope.launch(Dispatchers.EDT, start = CoroutineStart.UNDISPATCHED) {
    // The size can be changed externally, e.g. by scrolling mouse wheel.
    // This coroutine reflects the model changes in the UI.
    model.updates.collect {
      val index = values.indexOf(it)
      if (index >= 0) {
        slider.value = index
      }
    }
  }

  val panel = JPanel(FlowLayout(FlowLayout.RIGHT, 3, 0)).also {
    it.border = BorderFactory.createLineBorder(JBColor.border(), 1)
    it.isOpaque = true
  }
  panel.add(JLabel(ApplicationBundle.message("label.font.size")))
  panel.add(slider)

  return JBPopupFactory.getInstance().createComponentPopupBuilder(panel, slider).createPopup().also {
    it.size = panel.preferredSize
    Disposer.register(it) {
      updateSliderJob.cancel()
    }
  }
}
