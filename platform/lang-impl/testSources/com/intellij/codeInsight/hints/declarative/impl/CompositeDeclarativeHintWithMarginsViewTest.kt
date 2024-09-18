// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.impl.views.CompositeDeclarativeHintWithMarginsView
import com.intellij.codeInsight.hints.presentation.InlayTextMetricsStorage
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import org.jmock.Mockery
import org.jmock.lib.legacy.ClassImposteriser
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.awt.Point


private val context = Mockery().apply {
  setImposteriser(ClassImposteriser.INSTANCE)
}
private val mockEditor = context.mock(Editor::class.java)
private val mockFontMetricsStorage = InlayTextMetricsStorage(mockEditor)
private val mockEditorMouseEvent = context.mock(EditorMouseEvent::class.java)
private val mockPresentationLists = listOf(
  5 to 10,
  10 to 15,
  15 to 20
).map { (margin, boxWidth) -> MockPresentationList(margin, boxWidth) }
private val compositeView = TestCompositeView(mockPresentationLists)


private fun TestCompositeView.simulateRightClick(x: Int, y: Int) {
  handleRightClick(mockEditorMouseEvent, Point(x, y), mockFontMetricsStorage)
}

class CompositeDeclarativeHintWithMarginsViewTest {

  @BeforeEach
  fun resetPresentationLists() {
    mockPresentationLists.forEach { it.clicked = false }
  }

  @Test
  fun `click is correctly propagated`() {
    compositeView.simulateRightClick(25, 0)
    assertEquals(listOf(false, true, false), mockPresentationLists.map { it.clicked })
  }

  @Test
  fun `clicks around right edge`() {
    compositeView.simulateRightClick(75, 0)
    assertEquals(listOf(false, false, false), mockPresentationLists.map { it.clicked })
    assertTrue(mockPresentationLists.none { it.clicked })

    compositeView.simulateRightClick(74, 0)
    assertEquals(listOf(false, false, true), mockPresentationLists.map { it.clicked })
  }

  @Test
  fun `click inside margin is ignored`() {
    compositeView.simulateRightClick(20, 0)
    assertTrue(mockPresentationLists.none { it.clicked })
  }
}

private class TestCompositeView(val subViews: List<MockPresentationList>)
  : CompositeDeclarativeHintWithMarginsView<InlayData, MockPresentationList>(false) {
  override fun getSubView(index: Int): MockPresentationList = subViews[index]

  override val subViewCount: Int = subViews.size
  override fun updateModel(newModel: InlayData) = fail("Should not be called")
}

private val mockDeclarativeHintViewWithMargins = context.mock(DeclarativeHintViewWithMargins::class.java)
private class MockPresentationList(override val margin: Int, val boxWidth: Int)
  : DeclarativeHintViewWithMargins by mockDeclarativeHintViewWithMargins {
  var clicked = false
  override fun getBoxWidth(storage: InlayTextMetricsStorage): Int = boxWidth
  override fun handleRightClick(e: EditorMouseEvent, pointInsideInlay: Point, fontMetricsStorage: InlayTextMetricsStorage) {
    clicked = true
  }
}