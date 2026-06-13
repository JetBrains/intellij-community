// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.execution.impl.EditorHyperlinkUsageCollector.HyperlinkFollowedPlace
import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.MouseShortcut
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.platform.eel.isMac
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.HintHint
import com.intellij.ui.HyperlinkAdapter
import com.intellij.ui.LightweightHint
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.cancelOnDispose
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.util.concurrent.CancellationException
import javax.swing.JComponent
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal class InvisibleHyperlinkHintManager(private val editor: Editor, parentDisposable: Disposable) {

  private val coroutineScope: CoroutineScope = createCoroutineScope(editor, parentDisposable)

  private var hintInfoDeferred: Deferred<HintInfo>? = null

  private var wasEditorFocusedBeforePopupShown: Boolean = false
  private var hadTextSelection: Boolean = false

  @Volatile
  private var lastMousePressTime: Long = 0

  init {
    editor.addEditorMouseListener(object : EditorMouseListener {
      override fun mousePressed(event: EditorMouseEvent) {
        cancelPopup()
        if (event.mouseEvent.clickCount == 1) {
          // Capture editor focus state before editor's mousePressed grabs focus.
          // The popup is shown later on mouseReleased.
          wasEditorFocusedBeforePopupShown = editor.contentComponent.isFocusOwner
          // Capture editor selection state before editor's mousePressed removes it.
          hadTextSelection = editor.selectionModel.hasSelection()
          lastMousePressTime = event.mouseEvent.`when`
        }
      }
    }, parentDisposable)
    coroutineScope.launch(Dispatchers.UI + ModalityState.any().asContextElement()) {
      editor.contentComponent.launchOnShow(InvisibleHyperlinkHintManager::class.simpleName + ": cancel popup on hiding") {
        try {
          awaitCancellation()
        }
        finally {
          cancelPopup()
        }
      }
    }
  }

  fun isInsideHint(e: EditorMouseEvent): Boolean {
    val hint = getHintInfoIfVisible()?.hint
    return hint != null && hint.isInsideHint(RelativePoint(e.mouseEvent))
  }

  fun onHoveredLinkChange(hoveredLink: RangeHighlighter?, e: EditorMouseEvent) {
    val hintInfo = getHintInfoIfVisible()
    if (hintInfo != null && hintInfo.link !== hoveredLink && !isInsideHintOrBetweenHintAndLink(e)) {
      // hide the popup if the mouse is outside the link, the popup, and the area between them
      cancelPopup()
    }
  }

  private fun isInsideHintOrBetweenHintAndLink(e: EditorMouseEvent): Boolean {
    val hintInfo = getHintInfoIfVisible() ?: return false
    val hint = hintInfo.hint
    val mousePoint = RelativePoint(e.mouseEvent)
    if (hint.isInsideHint(mousePoint)) {
      return true
    }
    val hintBounds = Rectangle(hint.component.locationOnScreen, hint.component.size)
    val initialMouseY = RelativePoint(hintInfo.initialEvent.mouseEvent).screenPoint.y
    val neighbourhood = hintBounds.union(Rectangle(Point(hintBounds.location.x, initialMouseY)))
    return neighbourhood.height < hintBounds.height * 4 /* sanity check */ &&
           neighbourhood.contains(mousePoint.screenPoint)
  }

  fun showHint(link: RangeHighlighterEx, e: EditorMouseEvent, action: () -> Unit) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return
    }
    cancelPopup()
    if (e.mouseEvent.clickCount == 1 && !hadTextSelection) {
      hintInfoDeferred = coroutineScope.async {
        val delay = getMultiClickDetectionDelay()
        LOG.debug { "Awaiting $delay before showing the popup" }
        // A mousePressed during this delay cancels this coroutine.
        delay(delay)
        // Single click confirmed (not part of a multi-click) => show the popup.
        withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
          if (!link.isValid) {
            throw CancellationException("Invalid link")
          }
          LOG.debug { "Showing the popup" }
          showHintImmediately(link, e, action)
        }
      }
      e.consume()
    }
  }

  /**
   * Returns how long to wait before confirming a single click.
   */
  private fun getMultiClickDetectionDelay(): Duration {
    val delayIntervalMs = Registry.intValue("editor.invisible.hyperlink.popup.delay.ms", 180)
      .coerceIn(0, UIUtil.getMultiClickInterval())
    val delayMs = lastMousePressTime + delayIntervalMs - System.currentTimeMillis()
    return delayMs.coerceAtLeast(0).milliseconds
  }

  private fun showHintImmediately(link: RangeHighlighterEx, e: EditorMouseEvent, action: () -> Unit): HintInfo {
    var linkFollowed = false
    val component = createHintLabel(object : HyperlinkAdapter() {
      override fun hyperlinkActivated(e: HyperlinkEvent) {
        linkFollowed = true
        // Use Write Intent Lock like EditorImpl.MyMouseAdapter.mouseReleased does.
        // This ensures consistent threading regardless of how the action is triggered.
        WriteIntentReadAction.run(action)
        cancelPopup()
        EditorHyperlinkUsageCollector.logInvisibleHyperlinkFollowed(HyperlinkFollowedPlace.POPUP_LINK_CLICKED)
      }
    })
    val hint = showHintComponent(editor, e.offset, component)
    val hintInfo = HintInfo(hint, link, e)
    val copyWasEditorFocusedBeforePopupShown = wasEditorFocusedBeforePopupShown
    hint.addHintListener {
      if (hintInfoDeferred?.getNow() == hintInfo) {
        hintInfoDeferred = null
        EditorHyperlinkUsageCollector.logInvisibleHyperlinkPopupHidden(copyWasEditorFocusedBeforePopupShown, linkFollowed)
      }
    }
    EditorHyperlinkUsageCollector.logInvisibleHyperlinkPopupShown(wasEditorFocusedBeforePopupShown)
    return hintInfo
  }

  private fun createHintLabel(listener: HyperlinkListener): JComponent {
    val hintHint = HintUtil.getInformationHint()
    hintHint.setTextFg(JBUI.CurrentTheme.Tooltip.shortcutForeground())
    val hintLabel = HintUtil.createLabel(getHtmlText(hintHint), null, hintHint.textBackground, hintHint)
    hintLabel.pane!!.addHyperlinkListener(listener)
    return hintLabel
  }

  private fun getHtmlText(hintHint: HintHint): @Nls String {
    val htmlBodyText = HtmlBuilder()
      .append(HtmlChunk.link("", IdeBundle.message("editor.invisible.link.popup.open")))
      .append(HtmlChunk.nbsp())
      .append(HtmlChunk.text(getMouseShortcutText())).toString()
    return HintUtil.prepareHintText(htmlBodyText, hintHint)
  }

  private fun getMouseShortcutText(): @Nls String {
    val modifiersEx = if (localEel.platform.isMac) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK
    val shortcut = MouseShortcut(MouseEvent.BUTTON1, modifiersEx, 1)
    return KeymapUtil.getShortcutText(shortcut)
  }

  private fun cancelPopup() {
    getHintInfoIfVisible()?.hint?.hide()
    hintInfoDeferred?.cancel()
    hintInfoDeferred = null
  }

  private fun showHintComponent(editor: Editor, offset: Int, component: JComponent): LightweightHint {
    val hint = LightweightHint(component)
    val position = editor.offsetToLogicalPosition(offset)
    val constraint = HintManager.ABOVE
    val p = HintManagerImpl.getHintPosition(hint, editor, position, constraint)
    val hintHint = HintManagerImpl.createHintHint(editor, p, hint, constraint)
      .setContentActive(false)
      .setShowImmediately(true)
    HintManagerImpl.getInstanceImpl().showEditorHint(
      hint, editor, p,
      HintManager.HIDE_BY_ANY_KEY or HintManager.HIDE_BY_SCROLLING,
      0, false, hintHint
    )
    return hint
  }

  private fun getHintInfoIfVisible(): HintInfo? {
    return hintInfoDeferred?.getNow()?.takeIf { it.hint.isVisible }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun <T : Any> Deferred<T>.getNow(): T? {
    return if (isCompleted && getCompletionExceptionOrNull() == null) getCompleted() else null
  }

  private data class HintInfo(
    val hint: LightweightHint,
    val link: RangeHighlighterEx,
    val initialEvent: EditorMouseEvent,
  )

  companion object {
    private val LOG: Logger = logger<InvisibleHyperlinkHintManager>()

    private fun createCoroutineScope(editor: Editor, parentDisposable: Disposable): CoroutineScope {
      val baseScope = editor.project?.service<CoreUiCoroutineScopeHolder>()?.coroutineScope
                      ?: service<CoreUiCoroutineScopeHolder>().coroutineScope
      return baseScope.childScope(InvisibleHyperlinkHintManager::class.java.name).also {
        it.coroutineContext.job.cancelOnDispose(parentDisposable)
      }
    }
  }
}
