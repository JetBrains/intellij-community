// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup.impl

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.injected.editor.DocumentWindow
import com.intellij.injected.editor.EditorWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import org.jetbrains.annotations.ApiStatus
import kotlin.math.max

@ApiStatus.Internal
object LookupImplUtil {
  @JvmStatic
  fun getPsiElement(lookup: Lookup): PsiElement? {
    val file = lookup.psiFile ?: return null

    val lookupStart = lookup.lookupStart
    val editor = lookup.editor
    val offset = if (editor is EditorWindow) {
      val hPos = lookup.topLevelEditor.offsetToLogicalPosition(lookupStart)
      val pos = editor.hostToInjected(hPos)
      editor.logicalPositionToOffset(pos)
    }
    else lookupStart
    val offset1 = max(offset - 1, 0)
    return file.findElementAt(offset1)
  }

  @JvmStatic
  fun Editor.getPivotOffset(): Int {
    return if (selectionModel.hasSelection())
      selectionModel.selectionStart
    else
      caretModel.offset
  }

  @JvmStatic
  fun getEditor(project: Project, toplevelEditor: Editor): Editor {
    val documentWindow = getInjectedDocument(project, toplevelEditor, toplevelEditor.caretModel.offset) ?: return toplevelEditor
    val injectedFile = PsiDocumentManager.getInstance(project).getPsiFile(documentWindow)
    return InjectedLanguageUtil.getInjectedEditorForInjectedFile(toplevelEditor, injectedFile)
  }

  private fun getInjectedDocument(project: Project, editor: Editor, offset: Int): DocumentWindow? {
    val hostFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument()) ?: return null

    // inspired by com.intellij.codeInsight.editorActions.TypedHandler.injectedEditorIfCharTypedIsSignificant()
    val injected = InjectedLanguageManager.getInstance(project).getCachedInjectedDocumentsInRange(
      /* hostPsiFile = */ hostFile,
      /* range = */ TextRange.create(offset, offset)
    )
    return injected.firstOrNull { documentWindow ->
      documentWindow.isValid() && documentWindow.containsRange(offset, offset)
    }
  }

  @JvmStatic
  fun getPsiFile(lookup: Lookup): PsiFile? {
    val editor = lookup.getEditor()
    val project: Project = lookup.getProject()
    val document = editor.getDocument()
    return PsiDocumentManager.getInstance(project).getPsiFile(document)
  }

}