// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actionsOnSave.impl

import com.intellij.ide.actions.SaveDocumentAction
import com.intellij.ide.impl.runUnderModalProgressIfIsEdt
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.*
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting

private val EP_NAME = ExtensionPointName<ActionsOnSaveFileDocumentManagerListener.ActionOnSave>("com.intellij.actionOnSave")

class ActionsOnSaveFileDocumentManagerListener : FileDocumentManagerListener {
  abstract class ActionOnSave {
    /**
     * Invoked in EDT, maybe inside write action. Should be fast. It's ok to return `true` and then do nothing in [.processDocuments]
     *
     * @param project it's initialized, open, not disposed, and not the default one; no need to double-check in implementations
     */
    @RequiresReadLock
    open fun isEnabledForProject(project: Project): Boolean = false

    /**
     * Invoked in EDT, not inside write action. Potentially long implementations should run with modal progress synchronously.
     * Implementations don't need to save modified documents. Note that the passed documents may be unsaved if already modified by some other save action.
     */
    @RequiresEdt
    open fun processDocuments(project: Project, documents: Array<Document?>) {
    }
  }

  /**
   * Not empty state of this set means that processing has been scheduled (invokeLater(...)) but not yet performed.
   */
  private val documentsToProcess = HashSet<Document>()

  override fun beforeDocumentSaving(document: Document) {
    if (!service<CurrentActionHolder>().runningSaveDocumentAction) {
      // There are hundreds of places in IntelliJ codebase where saveDocument() is called. IDE and plugins may decide to save some specific
      // document at any time. Sometimes a document is saved on typing (com.intellij.openapi.vcs.ex.LineStatusTrackerKt.saveDocumentWhenUnchanged).
      // Running Actions on Save on each document save might be unexpected and frustrating (Actions on Save might take noticeable time to run, they may
      // update the document in the editor). So the Platform won't run Actions on Save when an individual file is being saved, unless this
      // is caused by an explicit 'Save document' action.
      return
    }

    for (project in ProjectManager.getInstance().openProjects) {
      for (saveAction in EP_NAME.extensionList) {
        if (saveAction.isEnabledForProject(project)) {
          scheduleDocumentsProcessing(arrayOf(document))
          return
        }
      }
    }
  }

  override fun beforeAllDocumentsSaving() {
    val documents = FileDocumentManager.getInstance().unsavedDocuments
    if (documents.isEmpty()) {
      return
    }

    for (project in ProjectManager.getInstance().openProjects) {
      for (saveAction in EP_NAME.extensionList) {
        if (saveAction.isEnabledForProject(project)) {
          scheduleDocumentsProcessing(documents)
          return
        }
      }
    }
  }

  private fun scheduleDocumentsProcessing(documents: Array<Document>) {
    val processingAlreadyScheduled = !documentsToProcess.isEmpty()
    documentsToProcess.addAll(documents)
    if (!processingAlreadyScheduled) {
      service<CurrentActionHolder>().coroutineScope.launch(Dispatchers.EDT + ModalityState.nonModal().asContextElement()) {
        processSavedDocuments()
      }
    }
  }

  private suspend fun processSavedDocuments() {
    val documentsAndModStamps = documentsToProcess.map { it to it.modificationStamp }
    documentsToProcess.clear()

    val manager = FileDocumentManager.getInstance()
    val processedDocuments = ArrayList<Document>()
    for (project in ProjectManager.getInstance().openProjects) {
      if (project.isDisposed) {
        continue
      }

      val index = ProjectFileIndex.getInstance(project)
      val projectDocuments = withContext(Dispatchers.Default) {
        readAction {
          documentsAndModStamps.mapNotNull {
            val document = it.first
            val modStamp = it.second
            if (document.modificationStamp != modStamp) return@mapNotNull null // already edited after save
            val file = manager.getFile(document) ?: return@mapNotNull null
            if (index.isInContent(file)) document else null
          }
        }
      }
      if (project.isDisposed || projectDocuments.isEmpty()) {
        continue
      }
      for (saveAction in EP_NAME.extensionList) {
        if (saveAction.isEnabledForProject(project)) {
          processedDocuments.addAll(projectDocuments)
          saveAction.processDocuments(project, projectDocuments.toTypedArray())
        }
      }
    }
    for (document in processedDocuments) {
      manager.saveDocument(document)
    }
  }
}

private class CurrentActionListener : AnActionListener {
  override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
    if (action is SaveDocumentAction) {
      service<CurrentActionHolder>().runningSaveDocumentAction = true
    }
  }

  override fun afterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult) {
    service<CurrentActionHolder>().runningSaveDocumentAction = false
  }
}

@VisibleForTesting
@Service(Service.Level.APP)
class CurrentActionHolder(@JvmField val coroutineScope: CoroutineScope) {
  var runningSaveDocumentAction = false

  @TestOnly
  fun waitForTasks() {
    @Suppress("DEPRECATION")
    runUnderModalProgressIfIsEdt {
      coroutineScope.coroutineContext.job.children.toList().joinAll()
    }
  }
}
