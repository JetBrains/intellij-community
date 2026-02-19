// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.jcef.test.cases

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.TestUtils
import org.intellij.lang.annotations.Language
import java.awt.Color
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame

internal fun runSimpleResizeTest() {
  @Language("html")
  val htmlContent = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <style>
                body, html {
                    margin: 0;
                    padding: 0;
                    width: 100%;
                    height: 100%;
                    display: flex;
                    justify-content: center;
                    align-items: center;
                    background-color: transparent;
                }
                .red-rectangle {
                    background-color: red;
                    border: 1px solid blue;
                    width: 100%;
                    height: 100%;
                    box-sizing: border-box;
                }
            </style>
        </head>
        <body>
            <div class="red-rectangle"></div>
        </body>
        </html>
    """.trimIndent()

  val frame = JFrame("JCEF Browser Test")
  frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
  frame.setSize(600, 600)
  frame.setLocationRelativeTo(null)

  val toDispose:  MutableList<Disposable> = mutableListOf()

  val jcefBrowser = JBCefBrowser().apply { toDispose.add(this) }

  val BLUE_COLOR = Color(0, 0, 255, 255)

  val STEPS = 50
  val RESIZE_STEP = 100

  var totalTime = 0L
  var timeStart: Long? = null
  var count = 0
  var step = RESIZE_STEP
  val durations = mutableListOf<Long>()

  frame.contentPane.add(PerformanceTest.RepaintListener(jcefBrowser.component, {
    val cefComponent = jcefBrowser.browserComponent!!
    val scale = TestUtils.getPixelDensity(cefComponent)
    val colorTopLeft = TestUtils.getColorAt(cefComponent, 0, 0)
    val colorBottomRight = TestUtils.getColorAt(cefComponent, ((cefComponent.width - 1) * scale).toInt(),
                                                ((cefComponent.height - 1) * scale).toInt())

    if (colorTopLeft == BLUE_COLOR && colorBottomRight == BLUE_COLOR) {
      if (timeStart != null) {
        val duration = System.currentTimeMillis() - timeStart!!
        durations.add(duration)
        totalTime += duration
        count++
      }
      if (count == STEPS) {
        frame.contentPane.removeAll()

        val mean = durations.average()

        val chart = PlotlyChart().apply { toDispose.add(this) }
        chart.newPlot(
          """
            [
                {
                    x: ${durations.joinToString(separator = ", ", prefix = "[", postfix = "]")},
                    type: 'histogram',
                    marker: {color: 'rgba(100, 200, 255, 0.7)'}
                }
            ]
          """.trimIndent(),
          layout = """
            {
                title: 'Resize test',
                xaxis: {
                    title: 'Resize time, ms'
                },
                yaxis: {
                    title: 'Count'
                },
                shapes: [
                 {
                   type: 'line',
                   x0: $mean,
                   x1: $mean,
                   y0: 0,
                   y1: 1,
                   xref: 'x',
                   yref: 'paper',
                   line: {
                     color: 'red',
                     width: 2,
                     dash: 'dash'
                   }
                 }
                ],
                annotations: [
                  {
                    x: ${mean},
                    y: 1,
                    xref: 'x',
                    yref: 'paper',
                    text: `Mean: ${mean}`,
                    showarrow: true,
                    arrowhead: 2,
                    arrowcolor: "red",
                    ax: 0,
                    ay: -30
                  }
                ]
            }
          """.trimIndent()
        )

        frame.add(chart.getComponent())

        frame.revalidate()
        frame.repaint()
      }

      timeStart = System.currentTimeMillis()
      if (frame.height > 800 || frame.height < 600 || frame.width > 800 || frame.width < 600) {
        step = -step
      }

      frame.setSize(frame.width + step, frame.height + step)
    }
  }))

  jcefBrowser.loadHTML(htmlContent)

  frame.isVisible = true
  frame.addWindowListener(object : WindowAdapter() {
    override fun windowClosed(e: WindowEvent?) {
      for (disposable in toDispose) {
        Disposer.dispose(disposable)
      }
    }
  })
}
