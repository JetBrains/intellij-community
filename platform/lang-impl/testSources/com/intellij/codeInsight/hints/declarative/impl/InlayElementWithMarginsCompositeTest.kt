// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.impl.views.InlayElementWithMargins
import com.intellij.codeInsight.hints.declarative.impl.views.InlayElementWithMarginsCompositeBase
import com.intellij.codeInsight.hints.declarative.impl.views.Invalidable
import com.intellij.codeInsight.hints.declarative.impl.views.SubViewMetrics
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import java.awt.Point


private val mockElems = listOf(
  Triple(5, 10, 3),
  Triple(10,15, 8),
  Triple(6,20, 13)
).map { (leftMargin, boxWidth, rightMargin) ->
  object : MockInlayElementWithMargins {
    override fun computeLeftMargin(context: Unit): Int = leftMargin
    override fun computeRightMargin(context: Unit): Int = rightMargin
    override fun computeBoxWidth(context: Unit): Int = boxWidth
    override fun invalidate() {}
  }
}
private val compositeView = TestCompositeView(mockElems)


private fun TestCompositeView.capturePoint(x: Int, y: Int): MockInlayElementWithMargins? {
  return forSubViewAtPoint(Point(x, y)) { elem, _ -> elem }
}

class InlayElementWithMarginsCompositeTest {

  @Test
  fun `point is correctly captured`() {
    val elem = compositeView.capturePoint(25, 0)
    assertSame(mockElems[1], elem)
  }

  @Test
  fun `computed metrics are correct`() {
    val metrics = compositeView.getSubViewMetrics(Unit)
    val expected = SubViewMetrics(
      intArrayOf(5, 15, 25, 40, 48, 68),
      81
    )
    assertArrayEquals(expected.sortedBounds, metrics.sortedBounds)
  }

  @Test
  fun `points around right edge`() {
    assertNull(compositeView.capturePoint(69, 0))
    assertSame(mockElems[2], compositeView.capturePoint(67, 0))
  }

  @Test
  fun `point inside margin is ignored`() {
    assertNull(compositeView.capturePoint(20, 0))
  }
}

private class TestCompositeView(val subViews: List<MockInlayElementWithMargins>)
  : InlayElementWithMarginsCompositeBase<Unit, MockInlayElementWithMargins, Unit>() {
  override fun getSubView(index: Int): MockInlayElementWithMargins = subViews[index]

  override val subViewCount: Int = subViews.size
  override fun computeSubViewContext(context: Unit) = Unit

  // so that super.forSubViewAtPoint can remain inline
  fun <R> forSubViewAtPoint(
    pointInsideInlay: Point,
    action: (MockInlayElementWithMargins, Point) -> R
  ): R? = super.forSubViewAtPoint(pointInsideInlay, Unit, action)
}

interface MockInlayElementWithMargins : InlayElementWithMargins<Unit>, Invalidable