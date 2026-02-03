// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.impl

import com.intellij.codeInsight.documentation.DocumentationHtmlUtil.lookupDocPopupMinHeight
import com.intellij.codeInsight.documentation.DocumentationHtmlUtil.lookupDocPopupWidth
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupEx
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.lang.documentation.ide.ui.DocumentationPopupUI
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.platform.backend.documentation.impl.DocumentationRequest
import com.intellij.platform.backend.documentation.impl.documentationRequest
import com.intellij.psi.util.PsiUtilBase
import com.intellij.ui.WidthBasedLayout
import com.intellij.ui.popup.AbstractPopup
import com.intellij.ui.popup.PopupPositionManager.Position
import com.intellij.ui.popup.PopupPositionManager.PositionAdjuster
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.applyIf
import com.intellij.util.ui.EDT
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.awt.Component
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.BoundedRangeModel
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.min

internal fun lookupPopupContext(editor: Editor?): PopupContext? {
  val lookup = LookupManager.getActiveLookup(editor)
               ?: return null
  return LookupPopupContext(lookup)
}

internal class LookupPopupContext(val lookup: LookupEx) : SecondaryPopupContext() {

  // otherwise, selecting lookup items by mouse would close the popup
  override val closeOnClickOutside: Boolean get() = false

  override fun setUpPopup(popup: AbstractPopup, popupUI: DocumentationPopupUI) {
    if ((lookup as LookupImpl).isLookupDisposed) {
      throw CancellationException()
    }
    super.setUpPopup(popup, popupUI)
    cancelPopupWhenLookupIsClosed(lookup, popup)
    emitDocContentsScrolledEvents(popup, popupUI)
  }

  override fun requestFlow(): Flow<DocumentationRequest?> = lookup.elementFlow().map(lookupElementToRequestMapper(lookup))

  override fun baseBoundsHandler(): PopupBoundsHandler {
    return LookupPopupBoundsHandler(lookup)
  }

  private fun emitDocContentsScrolledEvents(popup: AbstractPopup, popupUI: DocumentationPopupUI) {
    var scrollBarPos = 0
    val project = popupUI.ui.project
    val coroutineScope = popupUI.coroutineScope
    val model = popupUI.ui.scrollPane.verticalScrollBar.model
    val changeListener = object : ChangeListener {
      override fun stateChanged(e: ChangeEvent) {
        if ((e.source as BoundedRangeModel).value != scrollBarPos) {
          scrollBarPos = (e.source as BoundedRangeModel).value
          (e.source as BoundedRangeModel).removeChangeListener(this)
          coroutineScope.launch {
            project.messageBus.syncPublisher(DocumentationPopupListener.TOPIC).contentsScrolled()
          }
        }
      }
    }
    model.addChangeListener(changeListener)
    popup.addListener(object: JBPopupListener {
      override fun onClosed(event: LightweightWindowEvent) {
        model.removeChangeListener(changeListener)
      }
    })
  }
}

private fun cancelPopupWhenLookupIsClosed(lookup: Lookup, popup: AbstractPopup) {
  val listener = object : LookupListener {
    override fun itemSelected(event: LookupEvent): Unit = lookupClosed()
    override fun lookupCanceled(event: LookupEvent): Unit = lookupClosed()
    private fun lookupClosed(): Unit = popup.cancel()
  }
  lookup.addLookupListener(listener)
  Disposer.register(popup) {
    lookup.removeLookupListener(listener)
  }
}

fun Lookup.elementFlow(): Flow<LookupElement> {
  EDT.assertIsEdt()
  val items = MutableSharedFlow<LookupElement>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  addLookupListener(object : LookupListener {

    override fun currentItemChanged(event: LookupEvent) {
      event.item?.let { lookupElement ->
        items.tryEmit(lookupElement)
      }
    }

    override fun itemSelected(event: LookupEvent): Unit = lookupClosed()

    override fun lookupCanceled(event: LookupEvent): Unit = lookupClosed()

    private fun lookupClosed() {
      removeLookupListener(this)
    }
  })
  val currentItem = currentItem
  if (currentItem != null) {
    check(items.tryEmit(currentItem))
  }
  return items.asSharedFlow()
}

internal fun lookupElementToRequestMapper(lookup: Lookup): suspend (LookupElement) -> DocumentationRequest? {
  val project = lookup.project
  val editor = lookup.editor
  val ideTargetProvider = IdeDocumentationTargetProvider.getInstance(project)
  return { lookupElement: LookupElement ->
    readAction {
      if (!lookupElement.isValid) {
        return@readAction null
      }
      val file = PsiUtilBase.getPsiFileInEditor(editor, project)
                 ?: return@readAction null
      ideTargetProvider.documentationTargets(editor, file, lookupElement).firstOrNull()?.documentationRequest()
    }
  }
}

private class LookupPopupBoundsHandler(
  private val lookup: LookupEx,
) : BaseAdjustingPopupBoundsHandler(lookup.component) {
  override fun popupBounds(anchor: Component, size: Dimension): Rectangle {
    val preferredSize = Dimension(scale(lookupDocPopupWidth),
                                  min(size.height, max(scale(lookupDocPopupMinHeight), anchor.height)))
    val bounds = PositionAdjuster(anchor)
      .adjustBounds(preferredSize, arrayOf(Position.RIGHT, Position.LEFT))
      .applyIf(lookup.isPositionedAboveCaret) {
        val location = anchor.locationOnScreen
        Rectangle(x, location.y + anchor.height - height, width, height)
      }
    return Rectangle(bounds.x, bounds.y, bounds.width, bounds.height)
  }

  override fun componentResized(anchor: Component, popup: AbstractPopup) {
    repositionPopup(popup, anchor, popupSize(popup, false))
  }

  override fun popupSize(popup: AbstractPopup, resized: Boolean): Dimension {
    // For code completion popup always use preferred size
    val h = WidthBasedLayout.getPreferredHeight(popup.component, JBUI.scale(lookupDocPopupWidth))
    return Dimension(JBUI.scale(lookupDocPopupWidth), h)
  }

  override fun showPopup(popup: AbstractPopup) {
    super.showPopup(popup)
    lookup.project.messageBus.syncPublisher(DocumentationPopupListener.TOPIC).popupShown()
  }
}

