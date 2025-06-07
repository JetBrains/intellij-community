// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.jcef.test.cases

import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowser
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.LinkedList
import javax.swing.JCheckBox
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.AbstractDocument
import javax.swing.text.AttributeSet
import javax.swing.text.DocumentFilter

private class FpsTestPanel : JPanel(BorderLayout()) {
  val browser: JBCefBrowser = JBCefBrowser()
  val repaintListener = PerformanceTest.RepaintListener(browser.component)

  init {
    browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
      override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
        browser.devToolsClient.executeDevToolsMethod("Overlay.setShowFPSCounter", "{ \"show\": true }")
      }

    }, browser.cefBrowser)

    add(repaintListener, BorderLayout.CENTER)
    val controlPanel = JPanel()
    val fpsLimitLabel = JLabel("FPS limit:")
    val fpsLimitInput = JTextField(5).apply {
      applyNumericOnlyFilter(this)
      document.addDocumentListener(object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent?) = updateFpsLimit()
        override fun removeUpdate(e: DocumentEvent?) = updateFpsLimit()
        override fun changedUpdate(e: DocumentEvent?) = updateFpsLimit()
        private fun updateFpsLimit() = browser.cefBrowser.setWindowlessFrameRate(text.toIntOrNull() ?: 0)
      })
    }

    controlPanel.add(fpsLimitLabel)
    controlPanel.add(fpsLimitInput)

    val primitivesNumberLabel = JLabel("Number of primitives:")
    val primitivesNumberInput = JTextField(5).apply {
      applyNumericOnlyFilter(this)
      document.addDocumentListener(object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent?) = update()
        override fun removeUpdate(e: DocumentEvent?) = update()
        override fun changedUpdate(e: DocumentEvent?) = update()
        private fun update() = browser.cefBrowser.executeJavaScript("Particles.setParticlesCount(${text.toIntOrNull() ?: 0})", null, 0)
      })
    }

    controlPanel.add(primitivesNumberLabel)
    controlPanel.add(primitivesNumberInput)
    val animateBackgroundCheckbox = JCheckBox("Pause", false).apply {
      addActionListener {
        if (this.isSelected) {
          browser.cefBrowser.executeJavaScript("Particles.pause()", null, 0)
        }
        else {
          browser.cefBrowser.executeJavaScript("Particles.continue()", null, 0)
        }
      }
    }

    controlPanel.add(animateBackgroundCheckbox)
    add(controlPanel, BorderLayout.SOUTH)
    isVisible = true
    revalidate()
    repaint()
  }

  fun load() {
    val resourceAsStream =
      javaClass.getResourceAsStream("resources/performance_test/fps_test.html")
      ?: throw RuntimeException("Cannot find fps_test.html")
    val html = resourceAsStream.bufferedReader().readText()
    browser.loadHTML(html)
  }

  private fun applyNumericOnlyFilter(textField: JTextField) {
    (textField.document as? AbstractDocument)?.documentFilter = object : DocumentFilter() {
      override fun insertString(fb: FilterBypass, offset: Int, string: String?, attr: AttributeSet?) {
        if (string != null && string.all { it.isDigit() }) {
          super.insertString(fb, offset, string, attr)
        }
      }

      override fun replace(fb: FilterBypass, offset: Int, length: Int, text: String?, attrs: AttributeSet?) {
        if (text != null && text.all { it.isDigit() }) {
          super.replace(fb, offset, length, text, attrs)
        }
      }
    }
  }

}

internal fun runFpsTest() {
  SwingUtilities.invokeLater {
    val testPanel = FpsTestPanel().apply { this.load() }
    val chart = PlotlyChart()

    val repaintTimestamps = LinkedList<Long>()
    val timeToFpsInstant = mutableListOf<Pair<Double, Int>>()
    val timeToFpsAverage = mutableListOf<Pair<Double, Int>>()
    var firstEvent: Long? = null
    var lastPaintTime: Long = 0

    testPanel.repaintListener.onRepaint = {
      var timeNow = System.nanoTime()
      if (firstEvent == null) {
        firstEvent = timeNow
      }
      else {
        timeNow = timeNow - firstEvent
      }

      if (lastPaintTime != 0L) {
        val fps = 1_000_000_000 / (timeNow - lastPaintTime)
        timeToFpsInstant.add(Pair(timeNow / 1_000_000_000.0, fps.toInt()))

        repaintTimestamps.add(timeNow)
        while(!repaintTimestamps.isEmpty() && timeNow - repaintTimestamps.first() > 1_000_000_000) repaintTimestamps.removeAt(0)
        timeToFpsAverage.add(Pair(timeNow / 1_000_000_000.0, (repaintTimestamps.size)))
      }
      lastPaintTime = timeNow
    }

    val fpsTimer = Timer(1000) {
      if (repaintTimestamps.isEmpty()) return@Timer
      chart.extendTraces(
        """
          {
            x: [[${timeToFpsInstant.map { it.first }.joinToString(", ")}]],
            y: [[${timeToFpsInstant.map { it.second }.joinToString(", ")}]]
          }
        """.trimIndent(),
        "[0]"
      )
      timeToFpsInstant.clear()

      chart.extendTraces(
        """
          {
            x: [[${timeToFpsAverage.map { it.first }.joinToString(", ")}]],
            y: [[${timeToFpsAverage.map { it.second }.joinToString(", ")}]]
          }
        """.trimIndent(),
        "[1]"
      )
      timeToFpsAverage.clear()
    }

    fpsTimer.isRepeats = true
    fpsTimer.start()

    val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT).apply {
      leftComponent = testPanel
      rightComponent = chart.getComponent()
      resizeWeight = 0.5
    }

    val frame = JFrame("JCEF FPS test")
    frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
    frame.setSize(1200, 800)
    frame.contentPane.add(splitPane)
    frame.isVisible = true
    frame.addWindowListener(object : WindowAdapter() {
      override fun windowClosed(e: WindowEvent?) {
        Disposer.dispose(testPanel.browser)
        Disposer.dispose(chart)
      }
    })

    chart.newPlot(
      data =
        """
          [{
            x: [],
            y: [],
            mode: 'lines',
            name: 'FPS Instant',
            line: { color: 'rgba(255, 99, 132, 0.5)' },
          },
          {
            x: [],
            y: [],
            mode: 'lines',
            name: 'FPS Average',
            line: { color: 'rgba(54, 162, 235, 1)' },
          }]
        """.trimIndent(),
      layout =
        """
        {
          title: 'FPS',
          xaxis: { title: 'Time, ms' },
          yaxis: { title: 'Framerate, fps' },
          showlegend: true,
          legend: {
            orientation: 'h',
            y: -0.2,
            x: 0.5,
            xanchor: 'center',
          },
          dragmode: 'pan',
        }
        """.trimIndent()
    )
  }
}