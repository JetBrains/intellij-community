// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.jcef.test.cases

import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.TestUtils
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.JFrame
import javax.swing.JLabel

internal fun runSimpleResizeTest() {
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

  val jcefBrowser = JBCefBrowser()
  val BLUE_COLOR = Color(0, 0, 255, 255)

  val STEPS = 50
  val RESIZE_STEP = 100

  var totalTime = 0L
  var timeStart: Long? = null
  var count = 0
  var step = RESIZE_STEP
  var durations = mutableListOf<Long>()

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

        val baskets = getBaskets(durations)
        val histogram = durationsToHistogram(durations, baskets)

        frame.contentPane.add(JLabel(
          "<html><pre>Average resize time: ${totalTime / count} ms" + "\n\nHistogram:\n" + histogramToString(histogram,
                                                                                                             baskets) + "</pre></html>"),
                              BorderLayout.CENTER)

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
}

internal fun getBaskets(durations: List<Long>): List<Pair<Long, Long>> {
  val baskets = mutableListOf<Pair<Long, Long>>()
  var min = durations.minOrNull() ?: 0
  val max = durations.maxOrNull() ?: 0
  val step = (max - min) / 10
  while (min < max) {
    baskets.add(Pair(min, min + step))
    min += step
  }
  return baskets
}

internal fun durationsToHistogram(durations: List<Long>, baskets: List<Pair<Long, Long>>): IntArray {
  val histogram = IntArray(baskets.size)
  for (duration in durations) {
    for ((basketIndex, basket) in baskets.withIndex()) {
      if (duration >= basket.first && duration < basket.second) {
        histogram[basketIndex]++
        break
      }
    }
  }

  return histogram
}

internal fun histogramToString(histogram: IntArray, baskets: List<Pair<Long, Long>>): String {
  val totalCount = histogram.sum()
  val sb = StringBuilder()

  for ((basketIndex, basket) in baskets.withIndex()) {
    val percent = (histogram[basketIndex] * 100.0) / totalCount
    sb.append("${basket.first} - ${basket.second} ms: $percent %\n")
    sb.appendLine()
  }

  return sb.toString()
}
