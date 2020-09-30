// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.vfs.NonPhysicalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException

object PsiEditorUtilBase : PsiEditorUtil {
  private val LOG = logger<PsiEditorUtilBase>()

  /**
   * Tries to find editor for the given element.
   *
   *
   * There are at least two approaches to achieve the target. Current method is intended to encapsulate both of them:
   *
   *  * target editor works with a real file that remains at file system;
   *  * target editor works with a virtual file;
   *
   *
   *
   * Please don't use this method for finding an editor for quick fix.
   * @see com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
   *
   *
   * @param element   target element
   * @return          editor that works with a given element if the one is found; `null` otherwise
   */
  override fun findEditorByPsiElement(element: PsiElement): Editor? {
    val psiFile = element.containingFile
    val virtualFile = PsiUtilCore.getVirtualFile(element) ?: return null
    val project = psiFile.project
    if (virtualFile.isInLocalFileSystem || virtualFile.fileSystem is NonPhysicalFileSystem) { // Try to find editor for the real file.
      val fileEditorManager = FileEditorManager.getInstance(project)
      val editors = fileEditorManager?.getEditors(virtualFile) ?: emptyArray()
      for (editor in editors) {
        if (editor is TextEditor) {
          return editor.editor
        }
      }
    }
    // We assume that data context from focus-based retrieval should success if performed from EDT.
    val asyncResult = DataManager.getInstance().dataContextFromFocusAsync
    if (asyncResult.isSucceeded) {
      var editor: Editor? = null
      try {
        editor = CommonDataKeys.EDITOR.getData(requireNotNull(asyncResult.blockingGet(-1)))
      }
      catch (e: TimeoutException) {
        LOG.error(e)
      }
      catch (e: ExecutionException) {
        LOG.error(e)
      }
      if (editor != null) {
        val cachedDocument = PsiDocumentManager.getInstance(project).getCachedDocument(psiFile)
        // Ensure that target editor is found by checking its document against the one from given PSI element.
        if (cachedDocument === editor.document) {
          return editor
        }
      }
    }
    return null
  }
}