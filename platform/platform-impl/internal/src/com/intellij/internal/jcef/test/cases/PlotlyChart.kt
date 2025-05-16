// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.jcef.test.cases

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowserBuilder
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import org.intellij.lang.annotations.Language
import javax.swing.JComponent
import javax.swing.SwingUtilities

internal class PlotlyChart : Disposable {
  private var ready: Boolean = false
  private val CHART_DIV = "chart"
  private val requests = mutableListOf<String>()

  private val browser = JBCefBrowserBuilder().build().apply {
    this.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
      override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
          synchronized(this@PlotlyChart) {
            ready = true
            for (js in requests) {
              runJS(js)
            }
            requests.clear()
          }
      }
    }, this.cefBrowser)
  }

  init {
    val resourceAsStream =
      javaClass.getResourceAsStream("resources/performance_test/diagram_viewer.html")
      ?: throw RuntimeException("Cannot find diagram_viewer.html")
    val html = resourceAsStream.bufferedReader().readText()
    SwingUtilities.invokeLater { browser.loadHTML(html) }
  }

  fun getComponent(): JComponent = browser.component

  fun newPlot(
    @Language("JavaScript") data: String = "[]",
    @Language("JavaScript") layout: String = "{}",
    @Language("JavaScript") config: String = "{}",
  ) {
    runJS(
      "Plotly.newPlot('$CHART_DIV', ${data}, ${layout}, ${config})"
    )
  }

  fun react(
    @Language("JavaScript") data: String = "[]",
    @Language("JavaScript") layout: String = "{}",
    @Language("JavaScript") config: String = "{}",
  ) = runJS(
    "Plotly.react('$CHART_DIV', $data, $layout, $config)"
  )

  fun restyle(
    @Language("JavaScript") update: String = "{}",
    @Language("JavaScript") traceIndices: String = "null",
  ) = runJS(
    "Plotly.restyle('$CHART_DIV', $update, $traceIndices)"
  )

  fun update(
    @Language("JavaScript") dataUpdate: String = "{}",
    @Language("JavaScript") layoutUpdate: String = "{}",
    @Language("JCEF") traceIndices: String = "null",
  ) = runJS(
    "Plotly.update('$CHART_DIV', $dataUpdate, $layoutUpdate, $traceIndices)"
  )

  fun validate(
    @Language("JavaScript") data: String = "{}",
    @Language("JavaScript") layout: String = "{}",
  ) = runJS(
    "Plotly.validate($data, $layout)"
  )

  fun extendTraces(
    @Language("JavaScript") update: String = "{}",
    @Language("JavaScript") traceIndices: String = "[]",
  ) = runJS(
    "Plotly.extendTraces('$CHART_DIV', $update, $traceIndices)"
  )

  fun prependTraces(
    @Language("JavaScript") update: String = "{}",
    @Language("JavaScript") traceIndices: String = "[]",
  ) = runJS(
    "Plotly.prependTraces('$CHART_DIV', $update, $traceIndices)"
  )

  fun deleteTraces(
    @Language("JavaScript") traceIndices: String = "[]",
  ) = runJS(
    "Plotly.deleteTraces('$CHART_DIV', $traceIndices)"
  )

  fun purge() = runJS(
    "Plotly.purge('$CHART_DIV')"
  )

  fun addTraces(
    @Language("JavaScript") trace: String,
  ) = runJS(
    "Plotly.addTraces('$CHART_DIV', $trace)"
  )

  private fun runJS(@Language("JavaScript") js: String) {
    if (ready) {
      browser.cefBrowser.executeJavaScript(js, null, 0)
    }
    else {
      synchronized(this) {
        if (!ready) {
          requests.add(js)
        }
      }
    }
  }

  override fun dispose() {
    Disposer.dispose(browser)
  }
}