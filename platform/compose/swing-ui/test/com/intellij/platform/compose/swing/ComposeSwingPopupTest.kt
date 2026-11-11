// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing

import androidx.compose.runtime.DisposableEffect
import com.intellij.ide.DataManager
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.height
import org.jetbrains.compose.swing.foundation.layout.width
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.junit.jupiter.api.Test
import java.awt.Container
import java.awt.Dimension
import javax.swing.JLabel
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the popup entry points compose, what the content gives the popup before it shows, and when the composition
 * ends. A popup that shows needs a window, which the headless tests do not have. [PopupPositionProviderTest] covers
 * where a showing popup goes.
 */
@TestApplication
class ComposeSwingPopupTest {

  @Test
  fun thePopupReadsTheSizeOfTheContentBeforeItShows() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val popup = JBPopupFactory.getInstance().createComposeSwingPopup {
      Label("popup", modifier = SwingModifier.width(WIDTH))
    }
    try {
      assertEquals(listOf("popup"), popup.content.labels)
      assertEquals(WIDTH, UIUtil.findComponentOfType(popup.content, ComposeSwingPanel::class.java)!!.preferredSize.width)
    }
    finally {
      Disposer.dispose(popup)
    }
  }

  @Test
  fun theCompositionEndsWithThePopup() = timeoutRunBlocking(context = Dispatchers.EDT) {
    var disposed = false
    val popup = JBPopupFactory.getInstance().createComposeSwingPopup {
      DisposableEffect(Unit) { onDispose { disposed = true } }
    }
    assertFalse(disposed)

    Disposer.dispose(popup)

    assertTrue(disposed)
  }

  /** A popup host smaller than its content lays the content out at the size of the content, which the popup then takes. */
  @Test
  fun aHostSmallerThanItsContentLaysTheContentOutAtItsOwnSize() = timeoutRunBlocking(context = Dispatchers.EDT) {
    assertEquals(Dimension(WIDTH, HEIGHT), contentSizeInAHostOf(Dimension(WIDTH / 3, HEIGHT / 3)))
  }

  @Test
  fun aHostLargerThanItsContentLaysTheContentOutAtItsOwnSize() = timeoutRunBlocking(context = Dispatchers.EDT) {
    assertEquals(Dimension(WIDTH, HEIGHT), contentSizeInAHostOf(Dimension(WIDTH * 3, HEIGHT * 3)))
  }

  /** The popup reads the preferred size of the host when it packs, whatever width the host has. */
  @Test
  fun theHostPrefersTheSizeOfItsContentAtAnyWidth() = timeoutRunBlocking(context = Dispatchers.EDT) {
    withPopupHost { host ->
      host.size = Dimension(WIDTH * 3, HEIGHT * 3)
      assertEquals(Dimension(WIDTH, HEIGHT), host.preferredSize)
    }
  }

  /** Before its popup shows, the host passes a change of the content on to the window, which then reads the new size. */
  @Test
  fun theHostIsNoValidateRootBeforeItsPopupShows() = timeoutRunBlocking(context = Dispatchers.EDT) {
    withPopupHost { host ->
      assertFalse(host.isValidateRoot)
    }
    val popup = JBPopupFactory.getInstance().createComposeSwingPopup { Label("popup") }
    try {
      assertFalse(UIUtil.findComponentOfType(popup.content, ComposeSwingPopupHost::class.java)!!.isValidateRoot)
    }
    finally {
      Disposer.dispose(popup)
    }
  }

  /** The size of the content of [WIDTH] by [HEIGHT] after a popup host of [hostSize] lays it out. */
  private fun contentSizeInAHostOf(hostSize: Dimension): Dimension = withPopupHost { host ->
    host.size = hostSize
    host.doLayout()
    UIUtil.findComponentOfType(host, JLabel::class.java)!!.size
  }

  private fun <T> withPopupHost(block: (ComposeSwingPopupHost) -> T): T {
    val host = ComposeSwingPopupHost(positionProvider = null)
    val composition = host.setContentWithOwnRecomposer { Label("popup", modifier = SwingModifier.width(WIDTH).height(HEIGHT)) }
    try {
      return block(host)
    }
    finally {
      Disposer.dispose(composition)
    }
  }

  @Test
  fun theContentGivesTheDataOfItsProvider() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val popup = JBPopupFactory.getInstance().createComposeSwingPopup(dataProvider = UiDataProvider { it[KEY] = "value" }) {
      Label("popup")
    }
    // The headless data manager reads no Swing hierarchy otherwise.
    HeadlessDataManager.fallbackToProductionDataManager(popup)
    try {
      val label = UIUtil.findComponentOfType(popup.content, JLabel::class.java)!!
      assertEquals("value", DataManager.getInstance().getDataContext(label).getData(KEY))
    }
    finally {
      Disposer.dispose(popup)
    }
  }

  @Test
  fun aContentForAPlatformPopupComposesOnTheCallAndEndsWithItsDisposable(
    @TestDisposable testRootDisposable: Disposable,
  ) = timeoutRunBlocking(context = Dispatchers.EDT) {
    val parent = PopupParentDisposable()
    Disposer.register(testRootDisposable, parent)
    var disposed = false
    val content = composeSwingPopupContent(parent) {
      Label("content")
      DisposableEffect(Unit) { onDispose { disposed = true } }
    }
    assertEquals(listOf("content"), content.labels)

    Disposer.dispose(parent)

    assertTrue(disposed)
  }

  /** The text of every label in this container, which is what the popup reads before it shows. */
  private val Container.labels: List<String>
    get() = UIUtil.findComponentsOfType(this as javax.swing.JComponent, JLabel::class.java).map { it.text }

  private companion object {
    const val WIDTH: Int = 321
    const val HEIGHT: Int = 123
    val KEY: DataKey<String> = DataKey.create("compose.swing.popup.test")
  }

  private class PopupParentDisposable : Disposable {
    override fun dispose() {}
  }
}
