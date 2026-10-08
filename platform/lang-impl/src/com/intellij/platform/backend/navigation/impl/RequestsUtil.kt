// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.navigation.impl

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.getOrLoadDocumentUnderProgress
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.PsiFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.ref.Reference

/**
 * Resolves a deferred decompiler target into markers on the loaded [document]
 */
internal suspend fun SourceNavigationRequest.asDecompilerRequestIfAny(document: Document): SourceNavigationRequest {
  val pointer = lazyDecompilerElement ?: return this
  return readAction {
    val element = pointer.element?.takeUnless { it is PsiFile } ?: return@readAction this
    val offset = element.textOffset.takeIf { it in 0..document.textLength } ?: return@readAction this
    val range = element.textRange?.takeIf { it.endOffset <= document.textLength }
    SharedSourceNavigationRequest(
      file = file,
      context = contextOrAny,
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

/**
 * Runs [action] with a request whose target offsets can be resolved.
 * A deferred decompiler target or markers could request a document loading.
 */
internal suspend inline fun <T> SourceNavigationRequest.withResolvedDecompilerTarget(
  project: Project,
  crossinline action: suspend (SourceNavigationRequest) -> T,
): T {
  if (lazyDecompilerElement == null && offsetMarker == null && elementRangeMarker == null) {
    return action(this)
  }
  val document = withContext(Dispatchers.Default) {
    val fileDocumentManager = FileDocumentManager.getInstance()
    fileDocumentManager.getCachedDocument(file) ?: withBackgroundProgress(
      project, IdeBundle.message("progress.title.preparing.file", file.name),
    ) {
      fileDocumentManager.getOrLoadDocumentUnderProgress(file)
    }
  }

  return try {
    if (document == null) {
      action(this)
    } else {
      action(asDecompilerRequestIfAny(document))
    }
  }
  finally {
    Reference.reachabilityFence(document)
  }
}
