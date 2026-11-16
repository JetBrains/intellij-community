// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.render

import com.intellij.lang.documentation.ide.impl.DocumentationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.util.Disposer
import com.intellij.platform.backend.documentation.DocumentationLinkHandler
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.InlineDocumentation
import com.intellij.platform.backend.documentation.LinkResolveResult
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.editorFixture
import com.intellij.testFramework.junit5.fixture.extensionPointFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.mock
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent
import javax.swing.text.html.HTMLDocument
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestApplication
class DocRenderLinkHoverTest {
  private val project = projectFixture()
  private val sourceRoot = project.moduleFixture().sourceRootFixture()
  private val file = sourceRoot.psiFileFixture("comment.txt", "Documentation\nDeclaration")
  private val editor by file.editorFixture()
  private val linkHandler by extensionPointFixture(DocumentationLinkHandler.EP_NAME, LoadingOrder.FIRST) {
    TestLinkHandler()
  }

  @ParameterizedTest
  @ValueSource(strings = ["psi_element://cref://0", "psi_element://example.Symbol"])
  fun `hover resolves the rendered link in its documentation context`(url: String): Unit =
    timeoutRunBlocking {
      val target = mock<DocumentationTarget>()
      val resolved = CompletableDeferred<Pair<DocumentationTarget, String>>()
      linkHandler.resolve = { target, url ->
        resolved.complete(target to url)
        null
      }
      withRenderer(url, target) { _, pane ->
        fireLinkEvent(pane, url, HyperlinkEvent.EventType.ENTERED)
        assertEquals(target to url, withTimeoutOrNull(5.seconds) { resolved.await() })
      }
    }

  @ParameterizedTest
  @ValueSource(ints = [InputEvent.CTRL_DOWN_MASK, InputEvent.META_DOWN_MASK, InputEvent.SHIFT_DOWN_MASK,
    InputEvent.ALT_DOWN_MASK, InputEvent.ALT_GRAPH_DOWN_MASK, InputEvent.BUTTON1_DOWN_MASK, InputEvent.BUTTON2_DOWN_MASK])
  fun `modifiers suppress hover until an unmodified move within the same link`(modifiers: Int): Unit = timeoutRunBlocking {
    val resolved = CompletableDeferred<Unit>()
    linkHandler.resolve = { _, _ ->
      resolved.complete(Unit)
      null
    }
    val url = "psi_element://cref://0"
    withRenderer(url, mock()) { _, pane ->
      moveOverLink(pane, modifiers)
      assertNull(withTimeoutOrNull(250.milliseconds) { resolved.await() }, "A modified hover must not resolve documentation")
      moveOverLink(pane, 0)
      resolved.await()
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `pressing Ctrl during resolution cancels hover`(move: Boolean): Unit = timeoutRunBlocking {
    val started = CompletableDeferred<Unit>()
    val cancelled = CompletableDeferred<Unit>()
    linkHandler.resolve = { _, _ ->
      LinkResolveResult.asyncResult {
        started.complete(Unit)
        try {
          awaitCancellation()
        }
        finally {
          cancelled.complete(Unit)
        }
      }
    }
    withRenderer("psi_element://cref://0", mock()) { _, pane ->
      moveOverLink(pane, 0)
      started.await()
      if (move) {
        moveOverLink(pane, InputEvent.CTRL_DOWN_MASK)
      }
      else {
        withContext(Dispatchers.EDT) {
          val component = editor.contentComponent
          val event = KeyEvent(component,
                               KeyEvent.KEY_PRESSED,
                               0,
                               InputEvent.CTRL_DOWN_MASK,
                               KeyEvent.VK_CONTROL,
                               KeyEvent.CHAR_UNDEFINED)
          component.keyListeners.forEach { it.keyPressed(event) }
        }
      }
      cancelled.await()
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `a cancelled hover keeps the session that refuses to finish`(refuses: Boolean): Unit = timeoutRunBlocking {
    withRenderer("psi_element://cref://0", mock()) { renderer, pane ->
      withContext(Dispatchers.EDT) {
        val handler = DocRenderLinkHoverHandler(renderer, DocRenderDefaultLinkActivationHandler)
        try {
          val session = object : DocumentationManager.DocumentationOnHoverSession {
            override fun mouseOutsideOfSourceArea() = Unit
            override fun mouseWithinSourceArea() = Unit
            override fun tryFinishImmediately(): Boolean = !refuses
          }
          handler.session = session
          handler.mouseDragged(MouseEvent(pane, MouseEvent.MOUSE_DRAGGED, 0, 0, 0, 0, 0, false))
          assertSame(if (refuses) session else null, handler.session)
        }
        finally {
          Disposer.dispose(handler)
        }
      }
    }
  }

  @Test
  fun `disposing the renderer removes its editor key listener`(): Unit = timeoutRunBlocking {
    val listeners = withContext(Dispatchers.EDT) { editor.contentComponent.keyListeners.toList() }
    withRenderer("psi_element://cref://0", mock()) { _, pane ->
      moveOverLink(pane, InputEvent.CTRL_DOWN_MASK)
    }
    assertEquals(listeners, withContext(Dispatchers.EDT) {
      editor.contentComponent.keyListeners.toList()
    })
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `leaving the link or disposing its renderer cancels resolution`(dispose: Boolean): Unit =
    timeoutRunBlocking {
      val started = CompletableDeferred<Unit>()
      val cancelled = CompletableDeferred<Unit>()
      val supplier: suspend () -> LinkResolveResult.Async? = {
        started.complete(Unit)
        try {
          awaitCancellation()
        }
        finally {
          cancelled.complete(Unit)
        }
      }
      linkHandler.resolve = { _, _ -> LinkResolveResult.asyncResult(supplier) }
      val url = "psi_element://cref://0"
      withRenderer(url, mock()) { renderer, pane ->
        fireLinkEvent(pane, url, HyperlinkEvent.EventType.ENTERED)
        started.await()
        if (dispose) {
          withContext(Dispatchers.EDT) { renderer.dispose() }
        }
        else {
          fireLinkEvent(pane, url, HyperlinkEvent.EventType.EXITED)
        }
        cancelled.await()
      }
    }

  private suspend fun withRenderer(
    url: String,
    target: DocumentationTarget,
    action: suspend (DocRenderer, JEditorPane) -> Unit,
  ) {
    val settings = EditorSettingsExternalizable.getInstance()
    val oldShowDocumentation = settings.isShowQuickDocOnMouseOverElement
    val oldDelay = settings.tooltipsDelay
    var renderer: DocRenderer? = null
    var rangeHighlighter: RangeHighlighter? = null
    try {
      val pane = withContext(Dispatchers.EDT) {
        settings.isShowQuickDocOnMouseOverElement = true
        settings.tooltipsDelay = 0
        val testEditor = editor
        val created = testEditor.markupModel.addRangeHighlighter(
          0, 0,
          0,
          null,
          HighlighterTargetArea.EXACT_RANGE)
        rangeHighlighter = created
        val item = object : DocRenderItem {
          override val textToRender: String = "<a href=\"$url\">target</a>"
          override val foldRegion: CustomFoldRegion? = null
          override val highlighter: RangeHighlighter = created
          override val editor: Editor = testEditor
          override fun calcFoldingGutterIconRenderer(): GutterIconRenderer? = null
          override fun setIconVisible(visible: Boolean) = Unit
          override fun toggle() = Unit
          override fun getInlineDocumentation(): InlineDocumentation? = null
          override fun getInlineDocumentationTarget(): DocumentationTarget = target
        }
        DocRenderer(item).also {
          renderer = it
        }.getRendererComponent(editor, 600, null)
      }
      action(renderer!!, pane)
    }
    finally {
      withContext(NonCancellable + Dispatchers.EDT) {
        renderer?.dispose()
        rangeHighlighter?.let {
          editor.markupModel.removeHighlighter(it)
        }
        settings.isShowQuickDocOnMouseOverElement = oldShowDocumentation
        settings.tooltipsDelay = oldDelay
      }
    }
  }

  private suspend fun fireLinkEvent(pane: JEditorPane, url: String, eventType: HyperlinkEvent.EventType) {
    withContext(Dispatchers.EDT) {
      val document = pane.document as HTMLDocument
      val offset = document.getText(0, document.length).indexOf("target")
      pane.fireHyperlinkUpdate(HyperlinkEvent(pane,
                                              eventType,
                                              null,
                                              url,
                                              document.getCharacterElement(offset)))
    }
  }

  private suspend fun moveOverLink(pane: JEditorPane, modifiers: Int) {
    withContext(Dispatchers.EDT) {
      val document = pane.document as HTMLDocument
      val offset = document.getText(0, document.length).indexOf("target")
      val start = pane.modelToView2D(offset)
      val end = pane.modelToView2D(offset + "target".length)
      val event = MouseEvent(pane, MouseEvent.MOUSE_MOVED, 0, modifiers,
                             ((start.x + end.x) / 2).toInt(), start.centerY.toInt(), 0, false)
      pane.dispatchEvent(event)
    }
  }
}

private class TestLinkHandler : DocumentationLinkHandler {
  @Volatile
  var resolve: (DocumentationTarget, String) -> LinkResolveResult? = { _, _ -> null }

  override fun resolveLink(target: DocumentationTarget, url: String): LinkResolveResult? = resolve(target, url)
}
