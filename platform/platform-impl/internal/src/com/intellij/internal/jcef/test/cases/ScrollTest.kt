// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.jcef.test.cases

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.TestUtils
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.SwingUtilities

internal class ScrollTestPanel : JPanel() {
  val browser: JBCefBrowser = JBCefBrowser()
  val repaintListener = PerformanceTest.RepaintListener(browser.component)

  init {
    add(repaintListener)
  }

  fun getComponent() = repaintListener

  fun load() {
    val resourceAsStream =
      javaClass.getResourceAsStream("resources/performance_test/scroll_test_page.html")
      ?: throw RuntimeException("Cannot find scroll_test_page.html")
    val html = resourceAsStream.bufferedReader().readText()
    browser.loadHTML(html)
  }
}

internal fun runScrollingTest() {
  SwingUtilities.invokeLater {
    val testPanel = ScrollTestPanel()
    val chart = PlotlyChart()

    chart.newPlot(
      data =
        """
        [
          {
            x: [],
            y: [],
            mode: 'lines',
            name: 'Scrolling Amount Requested',
            line: { color: 'rgba(255, 99, 132, 1)' },
          },
          {
            x: [],
            y: [],
            mode: 'lines',
            name: 'Scrolling Amount Performed',
            line: { color: 'rgba(54, 162, 235, 1)' },
          },
        ]
        """.trimIndent(),
      layout = """
        {
          title: 'Scroll Tracking Diagram',
          xaxis: { title: 'Time, ms' },
          yaxis: { title: 'Scrolling Amount, pixels' },
          showlegend: true,
          legend: {
            orientation: 'h',
            y: -0.2,
            x: 0.5,
            xanchor: 'center',
          },
          dragmode: 'pan',
        }
        """.trimMargin()
    )

    var scrollRequested = 0.0
    var firstEvent: Long? = null

    // index: 0 - requested, index: 1 - performed
    val appendToChart = { scrollAmount: Double, index: Int ->
      val timeNow = System.currentTimeMillis()
      if (firstEvent == null) {
        firstEvent = timeNow
      }
      chart.extendTraces(
        update = """
        {
          x: [[${timeNow - firstEvent}]],
          y: [[$scrollAmount]]
        }
      """.trimIndent(),
        traceIndices = "[$index]"
      )
    }

    val rotationFactor = RegistryManager.getInstance().intValue("ide.browser.jcef.osr.wheelRotation.factor")
    testPanel.browser.cefBrowser.uiComponent.addMouseWheelListener {
      scrollRequested += it.preciseWheelRotation * rotationFactor
      appendToChart(scrollRequested, 0)
    }

    testPanel.repaintListener.onRepaint = {
      val color = TestUtils.getColorAt(testPanel.browser.browserComponent, 0, 0)
      if (color != null) {
        val scrollPerformed: Double = (color.blue + (color.green shl 8) + (color.red shl 16)).toDouble()
        appendToChart(scrollPerformed, 1)
      }
    }
    testPanel.load()

    val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT).apply {
      leftComponent = testPanel.getComponent()
      rightComponent = chart.getComponent()
      resizeWeight = 0.5
    }

    val frame = JFrame("Scrolling Test")
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
  }
}