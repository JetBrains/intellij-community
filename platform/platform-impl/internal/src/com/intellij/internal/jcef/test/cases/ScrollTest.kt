// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.jcef.test.cases

import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.TestUtils
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JSplitPane
import javax.swing.SwingUtilities

internal class DiagramViewer {
  val browser: JBCefBrowser = JBCefBrowser()

  init {
    val resourceAsStream =
      javaClass.getResourceAsStream("resources/performance_test/diagram_viewer.html")
      ?: throw RuntimeException("Cannot find diagram_viewer.html")
    val html = resourceAsStream.bufferedReader().readText()

    browser.loadHTML(html)
  }

  fun getComponent(): JComponent = browser.component

  fun addPoint(time: Long, scrollRequested: Double, scrollPerformed: Int): Unit =
    browser.cefBrowser.executeJavaScript(
      "addPoint($time, $scrollRequested, $scrollPerformed)", null, 0
    )
}

internal class TestPanel : JFrame() {
  internal val browser: JBCefBrowser = JBCefBrowser()
  internal val repaintListener = PerformanceTest.RepaintListener(browser.component)

  init {
    contentPane.add(repaintListener)
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

internal data class ScrollRecord(val time: Long, val scrollRequested: Double, val scrollPerformed: Int)

internal fun runScrollingTest() {
  SwingUtilities.invokeLater {
    val testFrame = TestPanel()
    val diagramViewer = DiagramViewer()

    var scrollRequested = 0.0
    var scrollPerformed = 0
    var firstEvent: Long? = null
    val scrollRecord = mutableListOf<ScrollRecord>()
    val addRecordFunc = { scrollRequested: Double, scrollPerformed: Int ->
      val time = System.currentTimeMillis()
      if (firstEvent == null) {
        firstEvent = time
      }
      synchronized(scrollRecord) {
        scrollRecord.add(ScrollRecord(time - firstEvent!!, scrollRequested, scrollPerformed))
        if (scrollRecord.size > 10) {
          for (event in scrollRecord) {
            diagramViewer.addPoint(event.time, event.scrollRequested, event.scrollPerformed)
          }
          scrollRecord.clear()
        }
      }
    }

    val rotationFactor = RegistryManager.getInstance().intValue("ide.browser.jcef.osr.wheelRotation.factor")
    testFrame.browser.cefBrowser.uiComponent.addMouseWheelListener {
      scrollRequested += it.preciseWheelRotation * rotationFactor
      addRecordFunc(scrollRequested, scrollPerformed)
    }

    testFrame.repaintListener.onRepaint = {
      if (firstEvent == null) {
        firstEvent = System.currentTimeMillis()
      }
      val color = TestUtils.getColorAt(testFrame.browser.browserComponent, 0, 0)
      scrollPerformed = color.blue + (color.green shl 8) + (color.red shl 16)
      addRecordFunc(scrollRequested, scrollPerformed)
    }
    testFrame.load()

    val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT).apply {
      leftComponent = testFrame.getComponent()
      rightComponent = diagramViewer.getComponent()
      resizeWeight = 0.5
    }

    val frame = JFrame("Scrolling Test")
    frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
    frame.setSize(1200, 800)
    frame.contentPane.add(splitPane)
    frame.isVisible = true
  }
}