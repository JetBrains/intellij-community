// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide.impl

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.impl.LookupManagerImpl
import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.lang.documentation.impl.DocumentationRequest
import com.intellij.lang.documentation.impl.documentationRequest
import com.intellij.openapi.application.readAction
import com.intellij.psi.util.PsiUtilBase
import com.intellij.util.ui.EDT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

internal fun autoShowRequestFlow(lookup: Lookup): Flow<DocumentationRequest>? {
  val settings = CodeInsightSettings.getInstance()
  if (!settings.AUTO_POPUP_JAVADOC_INFO) {
    return null
  }
  val timeout = settings.JAVADOC_INFO_DELAY.toLong()
  return lookup
    .elementFlow()
    .filter { lookupElement: LookupElement ->
      LookupManagerImpl.isAutoPopupJavadocSupportedBy(lookupElement)
    }
    .onEach {
      delay(timeout)
    }
    .asRequestFlow(lookup)
    .filterNotNull()
}

private fun Flow<LookupElement>.asRequestFlow(lookup: Lookup): Flow<DocumentationRequest?> {
  return map(lookupElementToRequestMapper(lookup)).flowOn(Dispatchers.Default)
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
