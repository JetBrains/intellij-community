// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.ide.*
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.scopeView.ScopeViewPane
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ReadConstraint
import com.intellij.openapi.application.constrainedReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.debug
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
import com.intellij.platform.util.coroutines.sync.OverflowSemaphore
import com.intellij.psi.*
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.SlowOperations
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

@VisibleForTesting
fun isSelectInProjectViewServiceBusy(project: Project):Boolean = project.serviceOrNull<SelectInProjectViewImpl>()?.isBusy == true

@Service(Service.Level.PROJECT)
internal class SelectInProjectViewImpl(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) {

  private val semaphore = OverflowSemaphore(permits = 1, overflow = BufferOverflow.DROP_OLDEST)
  private var tasks = AtomicInteger()
  internal val isBusy: Boolean get() = tasks.get() > 0

  // Overload instead of a default value to allow for the last-lambda-outside syntax.
  private fun invokeWithSemaphore(taskName: String, task: suspend () -> Unit) {
    invokeWithSemaphore(taskName, task, null)
  }

  private fun invokeWithSemaphore(taskName: String, task: suspend () -> Unit, onDone: (() -> Unit)?) {
    if (LOG.isDebugEnabled) {
      LOG.debug("Attempting to start $taskName")
    }
    coroutineScope.launch(
      CoroutineName(taskName) + ClientId.coroutineContext(),
      start = CoroutineStart.UNDISPATCHED
    ) {
      try {
        tasks.incrementAndGet()
        semaphore.withPermit {
          yield() // Ensure the coroutine is redispatched, even if withPermit() didn't suspend, to free the EDT.
          if (LOG.isDebugEnabled) {
            LOG.debug("Started $taskName")
          }
          task()
        }
      }
      finally {
        try {
          onDone?.invoke()
        }
        finally {
          tasks.decrementAndGet()
          if (LOG.isDebugEnabled) {
            LOG.debug("Finished $taskName")
          }
        }
      }
    }
  }

  fun selectInCurrentTarget(fileEditor: FileEditor?, invokedManually: Boolean) {
    invokeWithSemaphore("SelectInProjectViewImpl.selectInCurrentTarget") {
      doSelectInCurrentTarget(fileEditor, invokedManually)
    }
  }

  private suspend fun doSelectInCurrentTarget(fileEditor: FileEditor?, invokedManually: Boolean) {
      if (LOG.isDebugEnabled) {
        LOG.debug("doSelectInCurrentTarget: fileEditor=$fileEditor, invokedManually=$invokedManually")
      }
      val editorsToCheck = if (fileEditor == null) allEditors() else listOf(fileEditor)
      selectInCurrentTarget(invokedManually, editorsToCheck)
  }

  private suspend fun selectInCurrentTarget(invokedManually: Boolean, editorsToCheck: List<FileEditor>) {
    for (fileEditor in editorsToCheck) {
      if (
        !invokedManually &&
        AdvancedSettings.getBoolean("project.view.do.not.autoscroll.to.libraries") &&
        readAction { fileEditor.file?.let { file -> ProjectFileIndex.getInstance(project).isInLibrary(file) } == true }
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
    val fileEditorManager = project.serviceAsync<FileEditorManager>()
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
    invokeWithSemaphore(
      "SelectInProjectViewImpl.ensureSelected",
      task = {
        doEnsureSelected(paneId, virtualFile, elementSupplier, requestFocus, allowSubIdChange, result)
      },
      onDone = {
        if (result?.isProcessed == false) { // exception, or coroutine cancelled
          result.setRejected()
        }
      },
    )
  }

  private suspend fun doEnsureSelected(
    paneId: String,
    virtualFile: VirtualFile?,
    elementSupplier: Supplier<Any?>,
    requestFocus: Boolean,
    allowSubIdChange: Boolean,
    result: ActionCallback?,
  ) {
      if (LOG.isDebugEnabled) {
        LOG.debug("doEnsureSelected: " +
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
        return
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
          return
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
        return
      }
      withContext(Dispatchers.EDT) {
        LOG.debug("Delegating back to the project view")
        projectView.select(elementSupplier, context.virtualFile, requestFocus, result)
      }
  }

  fun selectInAnyTarget(context: SelectInContext, targets: Collection<SelectInTarget>, requestFocus: Boolean) {
    invokeWithSemaphore("SelectInProjectViewImpl.selectInAnyTarget") {
      doSelectInAnyTarget(context, targets, requestFocus)
    }
  }

  private suspend fun doSelectInAnyTarget(context: SelectInContext, targets: Collection<SelectInTarget>, requestFocus: Boolean) {
    LOG.debug { "doSelectInAnyTarget: context=$context, targets=$targets, requestFocus=$requestFocus" }
    for (target in targets) {
      val canSelect = readAction { target.canSelect(context) }
      LOG.debug { "${if (canSelect) "Can" else "Can NOT"} select $context in $target" }
      if (canSelect) {
        withContext(Dispatchers.EDT) {
          LOG.debug { "Selecting $context in $target" }
          target.selectIn(context, requestFocus)
        }
        return
      }
    }
  }

  fun selectInScopeViewPane(pane: ScopeViewPane, pointer: SmartPsiElementPointer<PsiElement>?, file: VirtualFile, requestFocus: Boolean) {
    invokeWithSemaphore("SelectInProjectViewImpl.selectInScopeViewPane(element=$pointer, file=$file, requestFocus=$requestFocus)") {
      doSelectInScopeViewPane(pane, pointer, file, requestFocus)
    }
  }

  private suspend fun doSelectInScopeViewPane(
    pane: ScopeViewPane,
    pointer: SmartPsiElementPointer<PsiElement>?,
    file: VirtualFile,
    requestFocus: Boolean,
  ) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("doSelectInScopeViewPane: pane=$pane, pointer=$pointer, file=$file, requestFocus=$requestFocus")
    }
    val currentFilter = pane.getCurrentFilter()
    val allFilters = pane.filters.toMutableList()
    allFilters.remove(currentFilter)
    allFilters.add(0, currentFilter) // Start with the current filter and then fall back to others.
    for (filter in allFilters) {
      if (readAction { filter.accept(file) }) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("The file $file has been accepted by the filter $filter, delegating to the pane")
        }
        withContext(Dispatchers.EDT) {
          pane.select(pointer, file, requestFocus, filter)
        }
        break
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
        val offset = writeIntentReadAction { editor.caretModel.offset }
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
        val newOffset = writeIntentReadAction { editor.caretModel.offset }
        if (newOffset == offset && PsiDocumentManager.getInstance(project).isCommitted(editor.document)) {
          super.selectInCurrentTarget(requestFocus)
          break
        }
      }
    }
  }

  override fun getSelectorInFile(): Any? {
    val file = SlowOperations.knownIssue("IDEA-347342, EA-841926").use { psiFile ?: return null }
    val offset: Int = editor.caretModel.offset
    val manager = PsiDocumentManager.getInstance(project)
    LOG.assertTrue(manager.isCommitted(editor.document))
    val element = file.findElementAt(offset)
    return element ?: file
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
