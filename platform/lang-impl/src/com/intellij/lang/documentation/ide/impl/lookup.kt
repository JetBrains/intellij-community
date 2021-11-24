// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide.impl

import com.intellij.codeInsight.lookup.*
import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.lang.documentation.ide.ui.DocumentationPopupUI
import com.intellij.lang.documentation.impl.DocumentationRequest
import com.intellij.lang.documentation.impl.documentationRequest
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.Disposer
import com.intellij.psi.util.PsiUtilBase
import com.intellij.ui.popup.AbstractPopup
import com.intellij.util.ui.EDT
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import java.awt.Component

internal class LookupPopupContext(val lookup: LookupEx) : SecondaryPopupContext() {

  override val referenceComponent: Component get() = lookup.component

  override fun setUpPopup(popup: AbstractPopup, popupUI: DocumentationPopupUI) {
    super.setUpPopup(popup, popupUI)
    cancelPopupWhenLookupIsClosed(lookup, popup)
  }

  override fun requestFlow(): Flow<DocumentationRequest?> = lookup.elementFlow().map(lookupElementToRequestMapper(lookup))
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

internal fun Lookup.elementFlow(): Flow<LookupElement> {
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
      ideTargetProvider.documentationTarget(editor, file, lookupElement)?.documentationRequest()
    }
  }
}
