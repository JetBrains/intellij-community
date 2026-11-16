// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.impl

import com.intellij.lang.documentation.ide.ui.PopupUpdateEvent
import com.intellij.openapi.application.UI
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.ui.popup.AbstractPopup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JPanel

@TestApplication
class ComponentAreaPopupContextTest {
  private val project = projectFixture()

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `a cancelled hover does not show a popup after documentation loads`(finish: Boolean): Unit = timeoutRunBlocking {
    withContext(Dispatchers.UI) {
      val context = ComponentAreaPopupContext(project.get(),
                                              JPanel(),
                                              Rectangle(),
                                              null,
                                              0,
                                              0)
      val session = context.session
      if (finish) {
        assertTrue(session.tryFinishImmediately())
      }
      else {
        session.mouseOutsideOfSourceArea()
      }
      val popup = mock<AbstractPopup>()
      context.boundsHandler().showPopup(popup)
      verify(popup).cancel()
    }
  }

  @Test
  fun `a popup above the area stays next to the area when it shrinks`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.UI) {
      val areaOwner = object : JPanel() {
        override fun isShowing() = true
        override fun getLocationOnScreen() = Point(0, 0)
      }
      val context = ComponentAreaPopupContext(project.get(), areaOwner, Rectangle(100, 704, 1, 20), null, 0, 0)
      val content = JPanel(BorderLayout()).apply {
        add(JPanel().apply { preferredSize = Dimension(300, 150) })
      }
      var popupLocation = Point(100, 100)
      var popupSize = Dimension(400, 600)
      val popup = mock<AbstractPopup> {
        on { isVisible } doReturn true
        on { component } doReturn content
        on { locationOnScreen } doAnswer { Point(popupLocation) }
        on { size } doAnswer { Dimension(popupSize) }
      }
      doAnswer { popupSize = it.getArgument(0) }.whenever(popup).size = any<Dimension>()
      doAnswer { popupLocation = it.getArgument(0) }.whenever(popup).setLocation(any<Point>())

      context.boundsHandler().updatePopup(popup, true, PopupUpdateEvent.ToolbarSizeChanged)

      assertTrue(popupSize.height < 600, "The popup must shrink to its content")
      val locations = argumentCaptor<Point>()
      verify(popup, atLeastOnce()).setLocation(locations.capture())
      // A headless screen has no size, so a later call moves the popup again. The first call is the one under test.
      assertEquals(Point(100, 700 - popupSize.height), locations.firstValue)
    }
  }
}
