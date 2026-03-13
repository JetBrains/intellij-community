// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.edit

import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.application
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@ApiStatus.Internal
object InlineEditDocumentUtils {

  /**
   * Ensures that the given [document] and its corresponding PSI file are synchronized.
   *
   * This function checks if the document is already committed (synced with PSI). If not,
   * it suspends until all pending commits are processed and the document is synchronized.
   *
   * @param project the project containing the document
   * @param document the document to synchronize with its PSI representation
   */
  suspend fun awaitDocumentIsCommitted(project: Project, document: Document) {
    val documentManager = PsiDocumentManager.getInstance(project)
    val isCommitted = readAction { documentManager.isCommitted(document) }
    if (isCommitted) {
      // We do not need one big readAction: it's enough to have them synced at this moment
      return
    }

    suspendCancellableCoroutine { continuation ->
      application.invokeLater {
        if (project.isDisposed) {
          continuation.resumeWithException(CancellationException())
        }
        else {
          documentManager.performWhenAllCommitted {
            continuation.resume(Unit)
          }
        }
      }
    }
  }
}
