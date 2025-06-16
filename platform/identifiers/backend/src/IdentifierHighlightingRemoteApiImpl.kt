// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.identifiers.highlighting.backend

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.IdentifierHighlightingComputer
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.impl.EditorId
import com.intellij.openapi.editor.impl.findEditorOrNull
import com.intellij.openapi.util.ProperTextRange
import com.intellij.platform.identifiers.highlighting.shared.IdentifierHighlightingRemoteApi
import com.intellij.platform.identifiers.highlighting.shared.IdentifierHighlightingRemoteApi.*
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.annotations.ApiStatus

/**
 * Identifier highlighting RPC implementation for the server: when all PSI/codeinsight services are available.
 * Unpacks remote arguments, calls [IdentifierHighlightingComputer], packs the results back
 */
@ApiStatus.Internal
internal class IdentifierHighlightingRemoteApiImpl : IdentifierHighlightingRemoteApi {
  override suspend fun getMarkupData(editorId: EditorId, visibleRange: SerializableRange, offset: Int): IdentifierHighlightingResultRemote {
    return readAction {
      val editor = editorId.findEditorOrNull() ?: return@readAction IdentifierHighlightingResultRemote.EMPTY
      val project = editor.project ?: return@readAction IdentifierHighlightingResultRemote.EMPTY
      val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return@readAction IdentifierHighlightingResultRemote.EMPTY

      val (occurrences, targets) = IdentifierHighlightingComputer(psiFile, editor, ProperTextRange(visibleRange.textRange), offset).computeRanges()

      val remoteOccurrences = occurrences.map {
        val serializableRange = SerializableRange(it.range.startOffset, it.range.endOffset)
        val needsUpdateOnTyping = ((it.highlightInfoType as? HighlightInfoType.UpdateOnTypingSuppressible)?.needsUpdateOnTyping() ?: false)
        val serializableInfoType = HighlightInfoTypeModel(it.highlightInfoType.getSeverity(null).name, it.highlightInfoType.attributesKey.externalName, needsUpdateOnTyping)
        IdentifierOccurrenceRemote(serializableRange, serializableInfoType)
      }
      val remoteTargets = targets.map { SerializableRange(it.startOffset, it.endOffset) }
      IdentifierHighlightingResultRemote(remoteOccurrences, remoteTargets)
    }
  }
}