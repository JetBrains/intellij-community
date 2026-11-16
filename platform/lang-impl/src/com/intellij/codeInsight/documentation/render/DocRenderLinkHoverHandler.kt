// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.render

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.asContextElement
import com.intellij.lang.documentation.ide.impl.DocumentationManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.editor.impl.EditorMouseHoverPopupControl
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.platform.backend.documentation.impl.InternalResolveLinkResult
import com.intellij.platform.backend.documentation.impl.resolveLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Rectangle
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener
import kotlin.math.ceil
import kotlin.time.Duration.Companion.milliseconds

internal class DocRenderLinkHoverHandler(
  private val renderer: DocRenderer,
  private val activationHandler: DocRenderLinkActivationHandler,
) : MouseMotionAdapter(), HyperlinkListener, Disposable {
  private var job: Job? = null
  internal var session: DocumentationManager.DocumentationOnHoverSession? = null
  private var currentLink: HyperlinkEvent? = null
  private var hoveredLink: HyperlinkEvent? = null
  private val keyListener = object : KeyAdapter() {
    override fun keyPressed(event: KeyEvent) {
      cancelHover()
    }
  }

  override fun hyperlinkUpdate(event: HyperlinkEvent) {
    when (event.eventType) {
      HyperlinkEvent.EventType.ENTERED -> {
        if (hoveredLink == null) renderer.item.editor.contentComponent.addKeyListener(keyListener)
        hoveredLink = event
        enterLink(event, event.inputEvent?.modifiersEx ?: 0)
      }
      HyperlinkEvent.EventType.EXITED -> {
        stopTrackingLink()
        job?.cancel()
        session?.mouseOutsideOfSourceArea()
      }
      HyperlinkEvent.EventType.ACTIVATED -> cancelHover()
    }
  }

  override fun mouseMoved(event: MouseEvent) {
    val link = hoveredLink ?: return
    if (event.modifiersEx != 0) {
      cancelHover()
    }
    else if (job == null) {
      enterLink(link, event.modifiersEx)
    }
  }

  override fun mouseDragged(event: MouseEvent) {
    cancelHover()
  }

  private fun enterLink(event: HyperlinkEvent, modifiers: Int) {
    if (modifiers != 0) {
      cancelHover()
      return
    }
    val item = renderer.item
    val editor = item.editor
    val project = editor.project ?: return
    val settings = EditorSettingsExternalizable.getInstance()
    if (!settings.isShowQuickDocOnMouseOverElement
        || EditorMouseHoverPopupControl.arePopupsDisabled(editor)
        || LookupManager.getActiveLookup(editor) != null) {
      return
    }
    val location = activationHandler.getLocation(event) ?: return
    if (currentLink?.description == event.description && currentLink?.sourceElement === event.sourceElement && session != null) {
      session?.mouseWithinSourceArea()
      return
    }
    if (session?.tryFinishImmediately() == false) {
      return
    }
    job?.cancel()
    currentLink = event
    job = editor.coroutineScope.launch(Dispatchers.EDT
                                       + ModalityState.current().asContextElement()
                                       + ClientId.current.asContextElement()) {
      delay(settings.tooltipsDelay.milliseconds)
      val result = try {
        withContext(Dispatchers.Default) {
          resolveLink(item::getInlineDocumentationTarget, event.description)
        }
      }
      catch (_: IndexNotReadyException) {
        return@launch
      }
      if (result !is InternalResolveLinkResult.Value
          || editor.isDisposed
          || !editor.contentComponent.isShowing
          || item.foldRegion?.isValid != true
          || !settings.isShowQuickDocOnMouseOverElement
          || EditorMouseHoverPopupControl.arePopupsDisabled(editor)) {
        return@launch
      }
      val position = activationHandler.popupPosition(location, renderer)
      val height = ceil(location.height).toInt()
      val area = Rectangle(position.x, position.y - height, 1, height)
      session = DocumentationManager
        .getInstance(project)
        .showDocumentationOnHoverAroundByRequests(
          listOf(result.value), project, editor.contentComponent, area, 0, 0,
        ) {
          if (currentLink === event) session = null
        }
    }
  }

  override fun dispose() {
    stopTrackingLink()
    cancelHover()
  }

  private fun stopTrackingLink() {
    hoveredLink = null
    renderer.item.editor.contentComponent.removeKeyListener(keyListener)
  }

  private fun cancelHover() {
    job?.cancel()
    job = null
    // A popup that the user clicked or moves towards stays open. The session then ends through its own callback.
    if (session?.tryFinishImmediately() == false) return
    session = null
    currentLink = null
  }
}
