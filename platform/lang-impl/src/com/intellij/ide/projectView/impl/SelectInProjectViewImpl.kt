// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl

import com.intellij.ide.FileEditorProvider
import com.intellij.ide.SmartSelectInContext
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ReadConstraint
import com.intellij.openapi.application.constrainedReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.util.PsiUtilCore
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.util.function.Supplier

@Service
@ApiStatus.Internal
class SelectInProjectViewImpl(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) {

  fun selectInCurrentTarget(fileEditor: FileEditor?, requestFocus: Boolean) {
    coroutineScope.launch(CoroutineName("ProjectView.selectInCurrentTarget(requestFocus=$requestFocus,fileEditor=$fileEditor")) {
      val editorsToCheck = if (fileEditor == null) allEditors() else listOf(fileEditor)
      selectInCurrentTarget(requestFocus, editorsToCheck)
    }
  }

  private suspend fun selectInCurrentTarget(requestFocus: Boolean, editorsToCheck: List<FileEditor>) {
    for (fileEditor in editorsToCheck) {
      val psiFilePointer = getPsiFilePointer(fileEditor)
      if (psiFilePointer != null) {
        withContext(Dispatchers.EDT) {
          createSelectInContext(psiFilePointer, fileEditor).selectInCurrentTarget(requestFocus)
        }
        break
      }
    }
  }

  private suspend fun allEditors(): List<FileEditor> = withContext(Dispatchers.EDT) {
    val fileEditorManager = FileEditorManager.getInstance(project)
    val result = mutableListOf<FileEditor?>()
    result.add(fileEditorManager.selectedEditor)
    result.addAll(fileEditorManager.selectedEditors)
    result.filterNotNull()
  }

  private suspend fun getPsiFilePointer(fileEditor: FileEditor): SmartPsiElementPointer<PsiFile>? =
    withContext(Dispatchers.EDT) {
      if (fileEditor is TextEditor) {
        fileEditor.nullIfInvalid()?.editor?.nullIfDisposed()?.document?.let { document ->
          readAction {
            project.serviceOrNull<PsiDocumentManager>()?.getPsiFile(document)?.let { psiFile ->
              project.serviceOrNull<SmartPointerManager>()?.createSmartPsiElementPointer(psiFile)
            }
          }
        }
      }
      else {
        fileEditor.nullIfInvalid()?.file?.let { virtualFile ->
          readAction {
            virtualFile.nullIfInvalid()?.let { validVirtualFile ->
              project.serviceOrNull<PsiManager>()?.findFile(validVirtualFile)?.let { psiFile ->
                project.serviceOrNull<SmartPointerManager>()?.createSmartPsiElementPointer(psiFile)
              }
            }
          }
        }
      }
    }

  private fun createSelectInContext(psiFilePointer: SmartPsiElementPointer<PsiFile>, fileEditor: FileEditor): SimpleSelectInContext =
    if (fileEditor is TextEditor) {
      EditorSelectInContext(project, psiFilePointer, fileEditor)
    }
    else {
      SimpleSelectInContext(project, psiFilePointer, fileEditor)
    }

  fun ensureSelected(
    paneId: String,
    virtualFile: VirtualFile?,
    elementSupplier: Supplier<Any>,
    requestFocus: Boolean,
    result: ActionCallback?
  ) {
    coroutineScope.launch(CoroutineName("ProjectView.ensureSelected(pane=$paneId,virtualFile=$virtualFile,focus=$requestFocus))")) {
      val projectView = project.serviceOrNull<ProjectView>() as ProjectViewImpl?
      if (projectView == null) {
        result?.setRejected()
        return@launch
      }
      val visibleAndSelectedUserObject = withContext(Dispatchers.EDT) {
        val pane = if (requestFocus) null else projectView.getProjectViewPaneById(paneId)
        pane?.visibleAndSelectedUserObject
      }
      data class SelectionContext(
        val isAlreadyVisibleAndSelected: Boolean,
        val virtualFile: VirtualFile?,
      )
      val context = readAction {
        val elementToSelect = elementSupplier.get()
        SelectionContext(
visibleAndSelectedUserObject != null && visibleAndSelectedUserObject.canRepresent(elementToSelect),
virtualFile ?: (elementToSelect as? PsiElement)?.virtualFile
        )
      }
      if (context.isAlreadyVisibleAndSelected || context.virtualFile == null) {
        result?.setDone()
        return@launch
      }
      withContext(Dispatchers.EDT) {
        projectView.select(elementSupplier, context.virtualFile, requestFocus, result)
      }
    }
  }

}

private open class SimpleSelectInContext(
  private val project: Project,
  psiFilePointer: SmartPsiElementPointer<PsiFile>,
  fileEditor: FileEditor,
) : SmartSelectInContext(project, fileEditor.file, psiFilePointer) {

  override fun getFileEditorProvider() = FileEditorProvider {
    project.serviceOrNull<FileEditorManager>()?.openFile (virtualFile, false)?.firstOrNull()
  }

  open suspend fun selectInCurrentTarget(requestFocus: Boolean) {
    val currentTarget = (project.serviceOrNull<ProjectView>() as ProjectViewImpl?)?.currentSelectInTarget ?: return
    currentTarget.selectIn(this, requestFocus)
  }

}

private class EditorSelectInContext(
  project: Project,
  psiFilePointer: SmartPsiElementPointer<PsiFile>,
  fileEditor: TextEditor,
) : SimpleSelectInContext(project, psiFilePointer, fileEditor) {

  private val editor: Editor = fileEditor.editor

  override suspend fun selectInCurrentTarget(requestFocus: Boolean) {
    withContext(Dispatchers.EDT) {
      while (true) {
        if (editor.isDisposed) {
          break
        }
        val offset = editor.caretModel.offset
        constrainedReadAction(ReadConstraint.withDocumentsCommitted(project)) {
          psiFile?.findElementAt(offset)
        }
        if (editor.caretModel.offset == offset) {
          super.selectInCurrentTarget(requestFocus)
          break
        }
      }
    }
  }

  override fun getSelectorInFile(): Any? {
    val file = psiFile
    if (file != null) {
      val offset: Int = editor.caretModel.offset
      val manager = PsiDocumentManager.getInstance(project)
      LOG.assertTrue(manager.isCommitted(editor.document))
      val element = file.findElementAt(offset)
      if (element != null) return element
    }
    return file
  }
}

private fun <T : FileEditor> T.nullIfInvalid(): T? = if (isValid) this else null
private fun <T : Editor> T.nullIfDisposed(): T? = if (isDisposed) null else this
private fun <T : VirtualFile> T.nullIfInvalid(): T? = if (isValid) this else null
private val PsiElement.virtualFile: VirtualFile? get() = PsiUtilCore.getVirtualFile(this)

private val LOG = logger<SelectInProjectViewImpl>()
