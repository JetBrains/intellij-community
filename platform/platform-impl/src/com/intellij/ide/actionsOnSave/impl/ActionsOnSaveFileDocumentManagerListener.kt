// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actionsOnSave.impl

import com.intellij.ide.actions.SaveDocumentAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
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
import com.intellij.util.concurrency.annotations.RequiresWriteLock

private val EP_NAME = ExtensionPointName<ActionsOnSaveFileDocumentManagerListener.ActionOnSave>("com.intellij.actionOnSave")

class ActionsOnSaveFileDocumentManagerListener : FileDocumentManagerListener {
  abstract class ActionOnSave {
    /**
     * Invoked in EDT, maybe inside write action. Should be fast. It's ok to return `true` and then do nothing in [.processDocuments]
     *
     * @param project it's initialized, open, not disposed, and not the default one; no need to double-check in implementations
     */
    @RequiresWriteLock
    open fun isEnabledForProject(project: Project): Boolean = false

    /**
     * Invoked in EDT, not inside write action. Potentially long implementations should run with modal progress synchronously.
     * Implementations don't need to save modified documents. Note that the passed documents may be unsaved if already modified by some other save action.
     */
    @RequiresEdt
    open fun processDocuments(project: Project, documents: Array<Document?>) {}
  }

  /**
   * Not empty state of this set means that processing has been scheduled (invokeLater(...)) but bot yet performed.
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
      ApplicationManager.getApplication().invokeLater({ processSavedDocuments() }, ModalityState.NON_MODAL)
    }
  }

  private fun processSavedDocuments() {
    val documents = documentsToProcess.toArray(Document.EMPTY_ARRAY)
    documentsToProcess.clear()

    // Although invokeLater() is called with ModalityState.NON_MODAL argument, somehow this might be called in modal context (for example on Commit File action)
    // It's quite weird if save action progress appears or documents get changed in modal context, let's ignore the request.
    if (ModalityState.current() !== ModalityState.NON_MODAL) {
      return
    }

    val manager = FileDocumentManager.getInstance()
    val processedDocuments: MutableList<Document> = ArrayList()
    for (project in ProjectManager.getInstance().openProjects) {
      if (project.isDisposed) {
        continue
      }

      val index = ProjectFileIndex.getInstance(project)
      val projectDocuments = documents.filter { document ->
        val file = manager.getFile(document)
        file != null && index.isInContent(file)
      }
      if (projectDocuments.isEmpty()) {
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

  internal class CurrentActionListener : AnActionListener {
    override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
      if (action is SaveDocumentAction) {
        service<CurrentActionHolder>().runningSaveDocumentAction = true
      }
    }

    override fun afterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult) {
      service<CurrentActionHolder>().runningSaveDocumentAction = false
    }
  }
}

@Service(Service.Level.APP)
private class CurrentActionHolder {
  var runningSaveDocumentAction = false
}
