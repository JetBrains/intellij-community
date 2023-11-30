// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.impl

import com.intellij.codeInsight.lookup.*
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.lang.documentation.ide.ui.DocumentationPopupUI
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Disposer
import com.intellij.platform.backend.documentation.impl.DocumentationRequest
import com.intellij.platform.backend.documentation.impl.documentationRequest
import com.intellij.psi.util.PsiUtilBase
import com.intellij.ui.popup.AbstractPopup
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map

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
  }

  override fun requestFlow(): Flow<DocumentationRequest?> = lookup.elementFlow().map(lookupElementToRequestMapper(lookup))

  override fun baseBoundsHandler(): PopupBoundsHandler {
    return AdjusterPopupBoundsHandler(lookup.component)
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
