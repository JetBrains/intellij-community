// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.html

import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.openapi.application.UI
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Dimension
import java.awt.Image
import java.awt.Rectangle
import java.awt.Shape
import java.awt.Toolkit
import java.awt.image.BufferedImage
import java.awt.image.MemoryImageSource
import java.util.Hashtable
import javax.swing.JEditorPane
import javax.swing.text.GlyphView
import javax.swing.text.View
import kotlin.math.roundToInt

@TestApplication
internal class ResizingHtmlImageViewTest {
  @Test
  fun `small image stays inline on the text baseline`(): Unit = timeoutRunBlocking {
    val layout = renderImage(Dimension(16, 16))

    assertThat(layout.textBaselines).hasSize(2).containsOnly(layout.image.y + layout.image.height)
    assertThat(layout.image.size).isEqualTo(Dimension(16, 16))
  }

  @Test
  fun `image wraps without shrinking when it fits the next line`(): Unit = timeoutRunBlocking {
    val layout = renderImage(Dimension(90, 30), paneWidth = 120)

    assertThat(layout.image.size).isEqualTo(Dimension(90, 30))
    assertThat(layout.textBaselines.first()).isLessThan(layout.image.y + layout.image.height)
  }

  @Test
  fun `wide image fits the paragraph`(): Unit = timeoutRunBlocking {
    val layout = renderImage(Dimension(1000, 500), paneWidth = 120)

    assertThat(layout.image.width).isBetween(1, 120)
    assertThat(layout.image.height).isBetween(1, 500)
    assertThat(layout.image.width).isBetween(layout.image.height * 2 - 1, layout.image.height * 2 + 1)
  }

  @Test
  fun `tall image keeps the height limit`(): Unit = timeoutRunBlocking {
    val layout = renderImage(Dimension(200, 2000), paneWidth = 400)

    assertThat(layout.image.height).isEqualTo(500)
    assertThat(layout.image.width).isEqualTo(50)
  }

  @Test
  fun `middle image sits above the text baseline`(): Unit = timeoutRunBlocking {
    for (attributes in listOf("align='absmiddle'", "style='vertical-align: middle'")) {
      val layout = renderImage(Dimension(16, 16), attributes = attributes)

      assertThat(layout.textBaselines).hasSize(2).containsOnly(layout.textBaselines.first())
      assertThat(layout.image.centerY).isLessThan(layout.textBaselines.first().toDouble())
      assertThat(layout.image.maxY).isGreaterThan(layout.textBaselines.first().toDouble())
    }
  }

  @Test
  fun `legacy middle aligns the image centre with the text baseline`(): Unit = timeoutRunBlocking {
    val layout = renderImage(Dimension(16, 16), attributes = "align='middle'")

    assertThat(layout.textBaselines).containsOnly(layout.image.centerY.toInt())
  }

  @Test
  fun `buffered raster uses image coordinates for layout`(): Unit = timeoutRunBlocking {
    for (scale in listOf(1.0, 1.5, 2.0)) {
      val raster = BufferedImage((17 * scale).roundToInt(), (13 * scale).roundToInt(), BufferedImage.TYPE_INT_ARGB)
      val image = BufferedImage(raster.colorModel, raster.raster, false, Hashtable<String, Any>().apply {
        put("JBImageToolkit.scale", scale)
      })
      val layout = renderImage(Dimension(17, 13), image = image)

      assertThat(layout.image.size).isEqualTo(Dimension(17, 13))
    }
  }

  @Test
  fun `toolkit raster uses image coordinates for layout`(): Unit = timeoutRunBlocking {
    for (scale in listOf(1.0, 1.5, 2.0)) {
      val width = (17 * scale).roundToInt()
      val height = (13 * scale).roundToInt()
      val properties = Hashtable<String, Any>().apply { put("JBImageToolkit.scale", scale) }
      val source = MemoryImageSource(width, height, IntArray(width * height), 0, width, properties)
      val layout = renderImage(Dimension(17, 13), image = Toolkit.getDefaultToolkit().createImage(source))

      assertThat(layout.image.size).isEqualTo(Dimension(17, 13))
    }
  }

  private suspend fun renderImage(
    imageSize: Dimension, paneWidth: Int = 400, attributes: String = "", image: Image? = null,
  ): Layout {
    val requested = CompletableDeferred<Unit>()
    val pane = withContext(Dispatchers.UI) {
      SimpleHtmlPane(
        addBrowserListener = false,
        customImageLoader = AsyncHtmlImageLoader { _, _ ->
          requested.complete(Unit)
          image ?: BufferedImage(imageSize.width, imageSize.height, BufferedImage.TYPE_INT_ARGB)
        },
      ).apply {
        text = "<html><body><p>before <img src='https://example.invalid/image.png' $attributes> after</p></body></html>"
        size = Dimension(paneWidth, 1000)
        render()
      }
    }
    requested.await()

    return withContext(Dispatchers.UI) {
      pane.render()
      var imageBounds: Rectangle? = null
      val baselines = ArrayList<Int>()
      fun visit(view: View, allocation: Shape) {
        if (view.element.name == "img") imageBounds = allocation.bounds
        if (view is GlyphView) {
          val text = view.document.getText(view.startOffset, minOf(view.endOffset, view.document.length) - view.startOffset)
          if (text.contains("before") || text.contains("after")) {
            baselines.add(allocation.bounds.y + pane.getFontMetrics(view.font).ascent)
          }
        }
        for (index in 0 until view.viewCount) {
          view.getChildAllocation(index, allocation)?.let { visit(view.getView(index), it) }
        }
      }
      visit(pane.ui.getRootView(pane), Rectangle(pane.width, pane.height))
      Layout(checkNotNull(imageBounds), baselines)
    }
  }

  private fun JEditorPane.render() {
    val graphics = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).createGraphics()
    try {
      ui.getRootView(this).setSize(width.toFloat(), height.toFloat())
      paint(graphics)
    }
    finally {
      graphics.dispose()
    }
  }

  private data class Layout(val image: Rectangle, val textBaselines: List<Int>)
}
