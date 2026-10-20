// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.navigation.impl

import com.intellij.codeInsight.multiverse.anyContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiFile

/**
 * Resolves a deferred target into markers after its document has loaded, if applicable
 */
internal suspend fun SourceNavigationRequest.asDecompilerRequestIfAny(): SourceNavigationRequest {
  val pointer = lazyDecompilerElement ?: return this
  return readAction {
    val document = FileDocumentManager.getInstance().getCachedDocument(file) ?: return@readAction this
    val element = pointer.element?.takeUnless { it is PsiFile } ?: return@readAction this
    val offset = element.textOffset.takeIf { it in 0..document.textLength } ?: return@readAction this
    val range = element.textRange?.takeIf { it.endOffset <= document.textLength }
    SharedSourceNavigationRequest(
      file = file,
      context = (this as? SharedSourceNavigationRequest)?.context ?: anyContext(),
      offsetMarker = document.createRangeMarker(offset, offset),
      elementRangeMarker = range?.let { document.createRangeMarker(it) },
      initialOffset = offset,
      initialFileStamp = file.modificationStamp,
      initialDocumentStamp = document.modificationStamp,
      useCurrentWindow = useCurrentWindow,
      usePreviewTab = usePreviewTab,
    )
  }
}