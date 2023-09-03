// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl

import com.intellij.ide.FileEditorProvider
import com.intellij.ide.FileSelectInContext
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
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.util.PsiUtilCore
import kotlinx.coroutines.*
import java.util.function.Supplier

@Service
internal class SelectInProjectViewImpl(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) {

  fun selectInCurrentTarget(fileEditor: FileEditor?, invokedManually: Boolean) {
    coroutineScope.launch(CoroutineName("ProjectView.selectInCurrentTarget(invokedManually=$invokedManually,fileEditor=$fileEditor")) {
      if (LOG.isDebugEnabled) {
        LOG.debug("selectInCurrentTarget: fileEditor=$fileEditor, invokedManually=$invokedManually")
      }
      val editorsToCheck = if (fileEditor == null) allEditors() else listOf(fileEditor)
      selectInCurrentTarget(invokedManually, editorsToCheck)
    }
  }

  private suspend fun selectInCurrentTarget(invokedManually: Boolean, editorsToCheck: List<FileEditor>) {
    for (fileEditor in editorsToCheck) {
      if (
        !invokedManually &&
        AdvancedSettings.getBoolean("project.view.do.not.autoscroll.to.libraries") &&
        readAction { fileEditor.file?.let { file ->ProjectFileIndex.getInstance(project).isInLibrary(file) } == true }
      ) {
        if (LOG.isDebugEnabled) {
          LOG.debug("Skipping $fileEditor because the file is in a library and autoscroll to libraries is off")
        }
        continue
      }
      val psiFilePointer = getPsiFilePointer(fileEditor)
      if (psiFilePointer != null) {
        if (LOG.isDebugEnabled) {
          LOG.debug("Trying to select using $fileEditor with psiFilePointer=$psiFilePointer")
        }
        withContext(Dispatchers.EDT) {
          val selectInContext = createSelectInContext(psiFilePointer, fileEditor)
          if (LOG.isDebugEnabled) {
            LOG.debug("Created select-in context and delegating to it: $selectInContext")
          }
          selectInContext.selectInCurrentTarget(requestFocus = invokedManually)
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
    elementSupplier: Supplier<Any?>,
    requestFocus: Boolean,
    allowSubIdChange: Boolean,
    result: ActionCallback?,
  ) {
    coroutineScope.launch(CoroutineName("ProjectView.ensureSelected(pane=$paneId,virtualFile=$virtualFile,focus=$requestFocus))")) {
      if (LOG.isDebugEnabled) {
        LOG.debug("ensureSelected: " +
                  "paneId=$paneId, " +
                  "virtualFile=$virtualFile, " +
                  "elementSupplier=$elementSupplier, " +
                  "requestFocus=$requestFocus, " +
                  "allowSubIdChange=$allowSubIdChange, " +
                  "result=$result"
        )
      }
      val projectView = project.serviceOrNull<ProjectView>() as ProjectViewImpl?
      if (projectView == null) {
        LOG.debug("Not selecting anything because there is no project view")
        result?.setRejected()
        return@launch
      }
      val pane = if (requestFocus) null else projectView.getProjectViewPaneById(paneId)
      val target = if (pane == null) null else projectView.getProjectViewSelectInTarget(pane)
      if (!allowSubIdChange) {
        if (LOG.isDebugEnabled) {
          LOG.debug("SubId change not allowed, checking: " +
                    "pane=$pane, " +
                    "target=$target, " +
                    "virtualFile=$virtualFile, " +
                    "and isSubIdSelectable"
          )
        }
        val isSelectableInCurrentSubId =
          if (pane == null || target == null || virtualFile == null) {
            if (LOG.isDebugEnabled) {
              LOG.debug("File $virtualFile is NOT selectable because there's not enough non-null parameters to go on, not selecting anything")
            }
            false
          }
          else {
            val isSubIdSelectable = readAction {
              target.isSubIdSelectable(pane.subId, FileSelectInContext(project, virtualFile, null))
            }
            if (LOG.isDebugEnabled && !isSubIdSelectable) {
              LOG.debug("File $virtualFile is NOT selectable in $pane with target $target and changing subId is not allowed, not selecting anything")
            }
            isSubIdSelectable
          }
        if (!isSelectableInCurrentSubId) {
          return@launch
        }
      }
      val visibleAndSelectedUserObject = withContext(Dispatchers.EDT) {
        pane?.visibleAndSelectedUserObject
      }
      if (LOG.isDebugEnabled && !requestFocus) { // if requestFocus, pane is always null
        LOG.debug("Currently visible and selected node is $visibleAndSelectedUserObject")
      }

      data class SelectionContext(
        val isAlreadyVisibleAndSelected: Boolean,
        val virtualFile: VirtualFile?,
      )
      val context = readAction {
        val suppliedElement = elementSupplier.get()
        val elementToSelect = suppliedElement ?: virtualFile ?: return@readAction null
        if (LOG.isDebugEnabled) {
          LOG.debug("Element to select is $elementToSelect (from ${if (suppliedElement == null) "virtual file" else "supplier"})")
        }
        val isAlreadyVisibleAndSelected = visibleAndSelectedUserObject != null && visibleAndSelectedUserObject.canRepresent(elementToSelect)
        if (LOG.isDebugEnabled && isAlreadyVisibleAndSelected) {
          LOG.debug("This element is already visible and selected")
        }
        SelectionContext(
          isAlreadyVisibleAndSelected,
          virtualFile ?: (elementToSelect as? PsiElement)?.virtualFile
        )
      }
      if (LOG.isDebugEnabled) {
        LOG.debug("The selection context is $context")
      }
      if (context == null || context.isAlreadyVisibleAndSelected || context.virtualFile == null) {
        LOG.debug("Nothing to do")
        result?.setDone()
        return@launch
      }
      withContext(Dispatchers.EDT) {
        LOG.debug("Delegating back to the project view")
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
    val currentTarget = (project.serviceOrNull<ProjectView>() as ProjectViewImpl?)?.currentSelectInTarget
    if (LOG.isDebugEnabled) {
      LOG.debug("The current target is $currentTarget")
    }
    if (currentTarget == null) return
    currentTarget.selectIn(this, requestFocus)
  }

  override fun toString(): String {
    return "SimpleSelectInContext(project=$project) ${super.toString()}"
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
          LOG.debug("Not selecting anything because the editor is disposed")
          break
        }
        val offset = editor.caretModel.offset
        if (LOG.isDebugEnabled) {
          LOG.debug("Looking for the element at offset $offset")
        }
        val element = constrainedReadAction(ReadConstraint.withDocumentsCommitted(project)) {
          psiFile?.findElementAt(offset)
        }
        // No, the fact that the element is only used for logging isn't a bug:
        // the whole point of the read action above is to ensure the document is committed
        // and parsed before selecting the current element, otherwise it may be outdated.
        if (LOG.isDebugEnabled) {
          LOG.debug("The element is $element")
        }
        if (editor.caretModel.offset == offset && PsiDocumentManager.getInstance(project).isCommitted(editor.document)) {
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

  override fun toString(): String {
    return "EditorSelectInContext(editor=$editor) ${super.toString()}"
  }
}

private fun <T : FileEditor> T.nullIfInvalid(): T? = if (isValid) this else null
private fun <T : Editor> T.nullIfDisposed(): T? = if (isDisposed) null else this
private fun <T : VirtualFile> T.nullIfInvalid(): T? = if (isValid) this else null
private val PsiElement.virtualFile: VirtualFile? get() = PsiUtilCore.getVirtualFile(this)

internal val LOG = logger<SelectInProjectViewImpl>()
