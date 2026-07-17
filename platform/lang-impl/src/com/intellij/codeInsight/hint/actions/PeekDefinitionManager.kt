// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint.actions

import com.intellij.codeInsight.hint.ImplementationViewElement
import com.intellij.codeInsight.hint.ImplementationViewSession
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.inlay.EditorInlay
import com.intellij.openapi.editor.inlay.addEditorInlay
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiBinaryFile
import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import javax.swing.SwingUtilities

@ApiStatus.Internal
object PeekDefinitionManager {
  private val ACTIVE_PEEK = Key.create<EditorInlay>("peek.definition.active")

  @RequiresEdt
  fun show(session: ImplementationViewSession): Boolean {
    val hostEditor = session.editor as? EditorEx ?: return false
    val implementation = session.implementationElements.singleOrNull() ?: return false
    val peek = show(hostEditor, implementation) ?: return false
    Disposer.register(peek, session)
    return true
  }

  fun current(editor: Editor): EditorInlay? = editor.getUserData(ACTIVE_PEEK)

  private fun show(hostEditor: EditorEx, implementation: ImplementationViewElement): EditorInlay? {
    val target = ReadAction.computeBlocking<Target?, RuntimeException> {
      implementation.elementForShowUsages?.let(Target::from)
    } ?: return null
    return show(hostEditor, target)
  }

  private fun show(hostEditor: EditorEx, target: Target): EditorInlay? {
    if (hostEditor.isDisposed || hostEditor.editorKind == EditorKind.PREVIEW) return null

    val document = FileDocumentManager.getInstance().getDocument(target.file) ?: return null
    val targetOffset = target.offset.coerceIn(0, document.textLength)
    val hostDocument = hostEditor.document
    val hostOffset = hostEditor.caretModel.offset.coerceIn(0, hostDocument.textLength)
    val anchorOffset = hostDocument.getLineEndOffset(hostDocument.getLineNumber(hostOffset))

    val peek = hostEditor.addEditorInlay(anchorOffset) {
      val targetEditor = editor(document, target.file, viewer = !target.file.isWritable || !document.isWritable) {
        isLineNumbersShown = false
      }
      targetEditor.caretModel.moveToOffset(targetOffset)
      header()
      style {
        minimumSize = JBUI.size(200, 160)
        preferredSize = JBUI.size(600, 320)
      }
    } ?: return null

    current(hostEditor)?.let(Disposer::dispose)
    hostEditor.putUserData(ACTIVE_PEEK, peek)
    Disposer.register(peek) {
      if (current(hostEditor) === peek) {
        hostEditor.putUserData(ACTIVE_PEEK, null)
      }
    }

    val targetEditor = checkNotNull(peek.embeddedEditor)
    scrollToTarget(targetEditor)
    SwingUtilities.invokeLater {
      if (!targetEditor.isDisposed) {
        scrollToTarget(targetEditor)
      }
    }
    return peek
  }

  private fun scrollToTarget(editor: EditorEx) {
    editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
    editor.scrollingModel.scrollHorizontally(0)
  }

  private class Target(val file: VirtualFile, val offset: Int) {
    companion object {
      fun from(element: PsiElement): Target? {
        if (!element.isValid) return null
        val navigationElement = element.navigationElement
        val psiFile = navigationElement.containingFile?.originalFile ?: return null
        if (psiFile is PsiBinaryFile) return null
        val virtualFile = psiFile.virtualFile ?: return null
        if (!virtualFile.isValid || virtualFile.isDirectory) return null
        return Target(virtualFile, navigationElement.textOffset)
      }
    }
  }
}
