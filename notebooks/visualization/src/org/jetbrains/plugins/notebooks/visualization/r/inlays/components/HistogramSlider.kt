/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.components

import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.DocumentAdapter
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.notebooks.visualization.r.inlays.components.HistogramSlider.Companion.show
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Magic distribution histogram slider.
 * Provided [onUserInput] handler will be triggered each time
 * user specifies selection range with either text input fields
 * or mouse drag n dropping.
 * It's **not** guaranteed that two sequential calls of this handler
 * will pass different ranges.
 *
 * **Note:** it can be easily instantiated at desired position with utility method [show]
 */
class HistogramSlider(
  histogram: Histogram,
  drawingStyle: DrawingStyle? = null,
  private val onUserInput: ((ClosedRange<Double>?) -> Unit)? = null
) {

  private var xFirst = -1
  private var xSecond = -1
  private var backingSelectionRange: ClosedRange<Double>? = null
    set(range) {
      field = range
      if (range != null) {
        fromInputField.silentlyChangeValue(range.start)
        toInputField.silentlyChangeValue(range.endInclusive)
      }
    }

  private val frequencies = histogram.frequencies
  private val histogramStart = histogram.values.start
  private val histogramEnd = histogram.values.endInclusive
  private val histogramStep = (histogramEnd - histogramStart) / histogram.frequencies.size

  private val histogramPanel = HistogramPanel(frequencies, drawingStyle ?: DEFAULT_DRAWING_STYLE).apply {
    addMouseListener(object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent?) {
        if (e != null) {
          xFirst = e.x
        }
      }

      override fun mouseReleased(e: MouseEvent?) {
        if (e != null) {
          xSecond = e.x
          updateSelectionRange()
          notifyRangeChanged()
        }
      }
    })
    addMouseMotionListener(object : MouseAdapter() {
      override fun mouseDragged(e: MouseEvent?) {
        if (e != null) {
          xSecond = e.x
          updateSelectionRange()
        }
      }
    })
  }

  private val fromInputField: SwitchableNumberField = SwitchableNumberField { newFrom ->
    selectionRange = makeRange(newFrom, toInputField.value)
    notifyRangeChanged()
  }

  private val toInputField: SwitchableNumberField = SwitchableNumberField { newTo ->
    selectionRange = makeRange(fromInputField.value, newTo)
    notifyRangeChanged()
  }

  private val rootPanel = JPanel(GridBagLayout()).apply {
    addWithConstraints(histogramPanel, 0, 0, width = 3, yWeight = 1.0)
    addWithConstraints(fromInputField, 0, 1, xWeight = 0.5, yWeight = 0.0)
    addWithConstraints(JLabel("-", JLabel.CENTER), 1, 1, xWeight = 0.0, yWeight = 0.0)
    addWithConstraints(toInputField, 2, 1, xWeight = 0.5, yWeight = 0.0)
  }

  val component: JComponent
    get() = rootPanel

  var selectionRange: ClosedRange<Double>?
    get() = backingSelectionRange
    set(range) {
      if (range != backingSelectionRange) {
        backingSelectionRange = range
        val selectedIndices = convertValuesToIndices(range)
        updateHistogramPanel(selectedIndices)
      }
    }

  private fun notifyRangeChanged() {
    onUserInput?.invoke(backingSelectionRange)
  }

  private fun convertValuesToIndices(values: ClosedRange<Double>?): IntRange? {
    return values?.let { range ->
      val startIndex = convertValueToIndex(range.start) { floor(it) }
      val endIndex = convertValueToIndex(range.endInclusive) { ceil(it) }
      IntRange(startIndex, endIndex)
    }
  }

  private fun convertValueToIndex(value: Double, rounding: (Double) -> Double): Int {
    val fractional = (value - histogramStart) / histogramStep
    return rounding(fractional).toInt()
  }

  private fun convertIndicesToValues(indices: IntRange?): ClosedRange<Double>? {
    return indices?.let { range ->
      val from = convertIndexToValue(range.first)
      val to = convertIndexToValue(range.last)
      from.rangeTo(to)
    }
  }

  private fun convertIndexToValue(index: Int): Double {
    return index * histogramStep + histogramStart
  }

  private fun convertCoordinateToValue(coordinate: Int): Double {
    val width = histogramPanel.width
    val ratio = coordinate.toDouble() / width.toDouble()
    return floor((frequencies.size * histogramStep * ratio) + histogramStart)
  }

  private fun updateSelectionRange() {
    val width = histogramPanel.width
    val xFrom = min(xFirst, xSecond).coerceIn(1, width - 1)
    val xTo = max(xFirst, xSecond).coerceIn(1, width - 1)
    val valueFrom = convertCoordinateToValue(xFrom)
    val valueTo = convertCoordinateToValue(xTo)
    selectionRange = valueFrom.rangeTo(valueTo)
    backingSelectionRange = convertIndicesToValues(histogramPanel.selectedIndices)  // Note: auto round selection range to closest ticks
  }

  private fun updateHistogramPanel(newSelectedIndices: IntRange?) {
    if (histogramPanel.selectedIndices != newSelectedIndices) {
      histogramPanel.selectedIndices = newSelectedIndices
      component.repaint()
    }
  }

  data class Histogram(
    /** Observations for y-axis, must fall in range [0.0, 1.0] */
    val frequencies: List<Double>,

    /** Range of x-axis covered by histogram */
    val values: ClosedRange<Double>
  )

  data class DrawingStyle(
    val histogramColor: Color,
    val selectionColor: Color,
    val backgroundColor: Color?
  )

  private class HistogramPanel(
    private val frequencies: List<Double>,
    private val drawingStyle: DrawingStyle,
    var selectedIndices: IntRange? = null
  ) : JPanel() {

    override fun paintComponent(g: Graphics) {
      drawingStyle.backgroundColor?.let { color ->
        g.color = color
        g.fillRect(0, 0, width, height)
      }
      val columnWidth = width / frequencies.size
      g.color = drawingStyle.histogramColor
      for ((index, value) in frequencies.withIndex()) {
        val clamped = value.coerceIn(0.0, 1.0)
        val columnHeight = (height * clamped).toInt()
        g.fillRect(index * columnWidth, height - columnHeight, columnWidth, columnHeight)
      }
      selectedIndices?.let { indices ->
        val first = indices.first.coerceIn(0, frequencies.size)
        val last = indices.last.coerceIn(0, frequencies.size)
        val from = first * columnWidth
        val selectionWidth = (last - first) * columnWidth
        g.color = drawingStyle.selectionColor
        g.fillRect(from, 0, selectionWidth, height)
      }
    }
  }

  private class SwitchableNumberField(onValueChanged: (Double?) -> Unit) : JTextField() {
    private var isActive = true

    var value: Double?
      get() = text.toDoubleOrNull()
      set(newValue) {
        text = newValue?.toString()
      }

    init {
      document.addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          if (isActive) {
            val newValue = text.toDoubleOrNull()
            onValueChanged(newValue)
          }
        }
      })
    }

    fun silentlyChangeValue(newValue: Double?) {
      if (value != newValue) {
        isActive = false
        value = newValue
        isActive = true
      }
    }
  }

  companion object {
    private val DEFAULT_DRAWING_STYLE = DrawingStyle(
      Color(96, 160, 255),
      Color(255, 0, 0, 96),
      null
    )

    fun show(
      owner: JComponent,
      point: Point,
      preferredSize: Dimension,
      histogram: Histogram,
      selectedValues: ClosedRange<Double>,
      drawingStyle: DrawingStyle? = null,
      onUserInput: (ClosedRange<Double>?) -> Unit
    ) {
      val slider = HistogramSlider(histogram, drawingStyle, onUserInput).apply {
        component.preferredSize = preferredSize
        selectionRange = selectedValues
      }
      val popupBuilder = JBPopupFactory.getInstance().createComponentPopupBuilder(slider.component, null).apply {
        setFocusable(true)
        setRequestFocus(true)
      }
      val popup = popupBuilder.createPopup()
      popup.showInScreenCoordinates(owner, point)
    }

    private fun JPanel.addWithConstraints(component: JComponent, x: Int, y: Int, width: Int = 1, xWeight: Double? = null, yWeight: Double? = null) {
      val constraints = GridBagConstraints().apply {
        fill = GridBagConstraints.BOTH
        insets = JBUI.insets(2 * width)
        gridx = x
        gridy = y
        gridwidth = width
        if (xWeight != null) {
          weightx = xWeight
        }
        if (yWeight != null) {
          weighty = yWeight
        }
      }
      add(component, constraints)
    }

    private fun makeRange(from: Double?, to: Double?): ClosedRange<Double>? {
      return from?.let {
        to?.let {
          if (from <= to) from.rangeTo(to) else null
        }
      }
    }
  }
}
