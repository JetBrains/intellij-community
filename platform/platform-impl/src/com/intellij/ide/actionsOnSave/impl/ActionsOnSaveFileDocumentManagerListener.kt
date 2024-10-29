// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actionsOnSave.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.concurrency.IntelliJContextElement
import com.intellij.concurrency.currentThreadContext
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.SaveDocumentAction
import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveFileDocumentManagerListener.DocumentUpdatingActionOnSave
import com.intellij.ide.impl.runUnderModalProgressIfIsEdt
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportProgress
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

private val EP_NAME = ExtensionPointName<ActionsOnSaveFileDocumentManagerListener.ActionOnSave>("com.intellij.actionOnSave")

class ActionsOnSaveFileDocumentManagerListener private constructor(private val project: Project) : FileDocumentManagerListener {
  /**
   * **Note:** If the Action on Save is going to update file contents, for example, to format a file on save,
   * then extend [DocumentUpdatingActionOnSave].
   * Unlike [ActionOnSave.processDocuments], which assumes modal progress,
   * [DocumentUpdatingActionOnSave.updateDocument] runs with cancelable background progress.
   */
  abstract class ActionOnSave {
    /**
     * Invoked in EDT, maybe inside write action. Should be fast. It's ok to return `true` and then do nothing in [processDocuments]
     *
     * @param project it's initialized, open, not disposed, and not the default one; no need to double-check in implementations
     */
    @RequiresReadLock
    open fun isEnabledForProject(project: Project): Boolean = false

    /**
     * **Note:** If the Action on Save is going to update file contents, for example, to format a file on save,
     * then implement [DocumentUpdatingActionOnSave.updateDocument] instead of this function.
     * Unlike this function, [DocumentUpdatingActionOnSave.updateDocument] runs with cancelable background progress.
     *
     * This function is invoked in EDT, not inside write action.
     * Potentially long implementations should run with modal progress synchronously.
     * Implementations don't need to save modified documents.
     * Note that the passed documents may be unsaved if already modified by some other save action.
     */
    @RequiresEdt
    open fun processDocuments(project: Project, documents: Array<Document>) {
    }
  }

  /**
   * Action on Save that updates file contents, for example, formats a file on save.
   * Unlike [ActionOnSave.processDocuments], which assumes modal progress,
   * [DocumentUpdatingActionOnSave.updateDocument] runs with cancelable background progress.
   */
  abstract class DocumentUpdatingActionOnSave : ActionOnSave() {
    final override fun processDocuments(project: Project, documents: Array<Document>) {}

    /**
     * For progress reporting: `"${actionOnSave.presentableName}: processing $fileName"`
     */
    abstract val presentableName: String

    /**
     * Checks applicability and runs the Action on Save for the [document] if needed.
     *
     * Implementations should be cancellation-friendly because the Platform cancels the job
     * if the processed [document] is updated before Actions on Save are completed.
     * For example, typing in the document cancels Actions on Save for this document.
     * Actions on Save for other documents keep running.
     *
     * Typical implementation:
     * ```
     * override suspend fun updateDocument(project: Project, document: Document) {
     *   if (!isApplicable(project, document)) return
     *   val changeInfo = prepareChange(project, document)
     *   writeCommandAction(project, "Format file") {
     *     applyChange(project, document, changeInfo)
     *   }
     * }
     * ```
     * Another example for the case when a `readAction` is needed to calculate the document change:
     * ```
     * override suspend fun updateDocument(project: Project, document: Document) {
     *   if (!isApplicable(project, document)) return
     *   readAndWriteAction {
     *     val changeInfo = prepareChange(project, document)
     *     writeAction {
     *       executeCommand(project, "Format file") {
     *         applyChange(project, document, changeInfo)
     *       }
     *     }
     *   }
     * }
     * ```
     */
    @RequiresBackgroundThread
    abstract suspend fun updateDocument(project: Project, document: Document)
  }

  override fun beforeDocumentSaving(document: Document) {
    if (!ActionsOnSaveManager.getInstance(project).runningSaveDocumentAction) {
      // There are hundreds of places in IntelliJ codebase where saveDocument() is called. IDE and plugins may decide to save some specific
      // document at any time. Sometimes a document is saved on typing (com.intellij.openapi.vcs.ex.LineStatusTrackerKt.saveDocumentWhenUnchanged).
      // Running Actions on Save on each document save might be unexpected and frustrating (Actions on Save might take noticeable time to run, they may
      // update the document in the editor). So the Platform won't run Actions on Save when an individual file is being saved, unless this
      // is caused by an explicit 'Save document' action.
      return
    }

    for (saveAction in EP_NAME.extensionList) {
      if (saveAction.isEnabledForProject(project)) {
        ActionsOnSaveManager.getInstance(project).scheduleDocumentsProcessing(arrayOf(document))
        return
      }
    }
  }

  override fun beforeAllDocumentsSaving() {
    val documents = FileDocumentManager.getInstance().unsavedDocuments
    if (documents.isEmpty()) {
      return
    }

    for (saveAction in EP_NAME.extensionList) {
      if (saveAction.isEnabledForProject(project)) {
        ActionsOnSaveManager.getInstance(project).scheduleDocumentsProcessing(documents)
        return
      }
    }
  }
}

@Service(Service.Level.PROJECT)
class ActionsOnSaveManager private constructor(private val project: Project, private val coroutineScope: CoroutineScope) {
  companion object {
    fun getInstance(project: Project) = project.service<ActionsOnSaveManager>()
  }

  /**
   * Not empty state of this set means that processing has been scheduled (invokeLater(...)) but not yet performed.
   */
  private val documentsToProcess = HashSet<Document>()

  internal var runningSaveDocumentAction = false

  internal fun scheduleDocumentsProcessing(documents: Array<Document>) {
    val processingAlreadyScheduled = !documentsToProcess.isEmpty()
    documentsToProcess.addAll(documents)
    if (!processingAlreadyScheduled) {
      coroutineScope.launch(Dispatchers.EDT + ModalityState.nonModal().asContextElement() + ClientId.coroutineContext()) {
        processSavedDocuments()
      }
    }
  }

  @RequiresEdt
  private suspend fun processSavedDocuments() {
    val documentsAndModStamps = documentsToProcess.associateWith { it.modificationStamp }
    documentsToProcess.clear()

    val index = ProjectFileIndex.getInstance(project)
    val projectDocuments = withContext(Dispatchers.Default) {
      readAction {
        documentsAndModStamps.mapNotNull {
          val document = it.key
          val modStamp = it.value
          if (document.modificationStamp != modStamp) return@mapNotNull null // already edited after save
          val file = FileDocumentManager.getInstance().getFile(document) ?: return@mapNotNull null
          if (index.isInContent(file)) document else null
        }.toMutableList()
      }
    }

    // filter out documents that have been already manually edited after save
    projectDocuments.removeAll { it.modificationStamp != documentsAndModStamps[it] }

    if (!project.isDisposed && projectDocuments.isNotEmpty()) {
      runActionsOnSave(projectDocuments)
    }
  }

  /**
   * Runs Actions on Save and saves documents, for which all applicable Actions on Save have finished successfully.
   * Manual editing of a document cancels all not-yet-started and not-yet-finished Actions on Save for this document,
   * but Actions on Save for other documents keep running.
   *
   * There are two APIs that the plugins may use to implement an Action on Save:
   * - [ActionsOnSaveFileDocumentManagerListener.ActionOnSave.processDocuments].
   * It is invoked synchronously in EDT.
   * This API is ok for actions like "schedule file upload" or "start file compilation".
   * But it's not recommended to use this API for actions that update the saved file contents.
   * Such actions are potentially long, so running them synchronously needs modal progress, and this is not what people like.
   * - [DocumentUpdatingActionOnSave.processDocuments].
   * It's invoked in a background thread with a cancelable background progress bar.
   * This API is preferred for actions that update the saved file contents, for example, format files on save.
   */
  @RequiresEdt
  private suspend fun runActionsOnSave(projectDocuments: List<Document>) {
    val projectActionsOnSave = EP_NAME.extensionList.filter { it.isEnabledForProject(project) }

    writeIntentReadAction {
      projectActionsOnSave.forEach { it.processDocuments(project, projectDocuments.toTypedArray()) }
    }

    val documentUpdatingActionsOnSave = projectActionsOnSave.filterIsInstance<DocumentUpdatingActionOnSave>()
    if (documentUpdatingActionsOnSave.isNotEmpty()) {
      // Document saving in this `if` branch is not needed because they will be saved in `runDocumentUpdatingActionsOnSaveAndSaveDocument`
      val documentsToModStamps = projectDocuments.associateWith { it.modificationStamp }
      withContext(Dispatchers.Default) {
        runDocumentUpdatingActionsOnSave(documentUpdatingActionsOnSave, documentsToModStamps)
      }
    }
    else {
      writeIntentReadAction { projectDocuments.forEach(FileDocumentManager.getInstance()::saveDocument) }
    }
  }

  /**
   * Runs [actionsOnSave] for [documents][documentsToModStamps],
   * which haven't been manually edited before the Actions on Save get a chance to start or to complete.
   *
   * Manual editing of a document cancels all not-yet-started and not-yet-finished Actions on Save for this document,
   * but Actions on Save for other documents keep running.
   *
   * When all Actions on Save finish successfully for the document, it is saved to the disk
   * (because users expect files to end up in saved state after pressing Ctrl+S/Cmd+S).
   * Manually edited documents (with canceled Actions on Save) will not be saved.
   *
   * @param documentsToModStamps Actions on Save must not be started for the `document`
   * if it has been already manually edited after save but before Actions on Save have been started.
   * This can be checked by comparing the current modification stamp with the one from this map.
   */
  @RequiresBackgroundThread
  private suspend fun runDocumentUpdatingActionsOnSave(
    actionsOnSave: List<DocumentUpdatingActionOnSave>,
    documentsToModStamps: Map<Document, Long>,
  ) {
    @Suppress("DialogTitleCapitalization") val progressTitle = IdeBundle.message("actions.on.save.background.progress")
    withBackgroundProgress(project, progressTitle) {
      reportProgress(size = documentsToModStamps.size) { progressReporter ->
        for ((document, modStamp) in documentsToModStamps) {
          // Not only does the `progressReporter.itemStep` call help with progress reporting,
          // it also makes sure that the documents are processed one at a time, not in parallel.
          // It's a safer path than parallel processing because there may be a lot of documents,
          // and Actions on Save may perform heavy operations or start external processes.
          progressReporter.itemStep {
            // On manual document editing, we should cancel only Actions on Save for the edited document
            // but keep running Actions on Save for other documents.
            // So, a separate child coroutine for each document is needed.
            launch(ActionOnSaveContextElement) {
              runDocumentUpdatingActionsOnSaveAndSaveDocument(actionsOnSave, document, modStamp, documentScope = this)
            }
          }
        }
      }
    }
  }

  /**
   * Runs [actionsOnSave] for the given [document]
   * unless it has been manually edited before the Actions on Save get a chance to start or to complete.
   *
   * Manual editing of a document cancels all not-yet-started and not-yet-finished Actions on Save for this document,
   * but Actions on Save for other documents keep running.
   *
   * When all Actions on Save finish successfully for the document, it is saved to the disk
   * (because users expect files to end up in saved state after pressing Ctrl+S/Cmd+S).
   * Manually edited documents (with canceled Actions on Save) will not be saved.
   *
   * @param modStamp Actions on Save must not be started on the [document] if it has been manually edited.
   * This is guaranteed by cancelling the [documentScope] in the `DocumentListener` (see the function implementation).
   * The passed [modStamp] helps to make sure that the [document] hasn't been edited before the `DocumentListener` has been added.
   *
   * @param documentScope the [CoroutineScope] in which the [actionsOnSave] run for the given [document].
   * Used to cancel Actions on Save for this document if it has been manually edited before the Actions on Save have finished.
   */
  @RequiresBackgroundThread
  private suspend fun runDocumentUpdatingActionsOnSaveAndSaveDocument(
    actionsOnSave: List<DocumentUpdatingActionOnSave>,
    document: Document,
    modStamp: Long,
    documentScope: CoroutineScope,
  ) {
    val documentListener = object : DocumentListener {
      override fun beforeDocumentChange(event: DocumentEvent) {
        // If the change in the document is not caused by the running Action on Save,
        // then cancel all not yet completed Actions on Save for the current document but keep running them for other documents.
        val docChangedByActionOnSave = currentThreadContext()[ActionOnSaveContextElement.Key] != null
        if (!docChangedByActionOnSave) {
          documentScope.cancel()
        }
      }
    }

    try {
      document.addDocumentListener(documentListener)

      // Check if the document has been already edited before the DocumentListener has been added and cancel Actions on Save if so.
      // Read action is needed to make sure we are not in the middle of a write action (when the doc has been already updated, bug modStamp - not yet).
      readAction {
        if (document.modificationStamp != modStamp) {
          documentScope.cancel()
        }
      }

      reportProgress(size = actionsOnSave.size) { progressReporter ->
        val fileName = FileDocumentManager.getInstance().getFile(document)?.name
        for (actionOnSave in actionsOnSave) {
          progressReporter.itemStep(IdeBundle.message("actions.on.save.processing.file", actionOnSave.presentableName, fileName)) {
            actionOnSave.updateDocument(project, document)
          }
        }
      }

      writeAction {
        // All Actions on Save have completed successfully for the document, so need to save it.
        // Otherwise, users would see unsaved documents after pressing Ctrl+S/Cmd+S, which would be unexpected.
        FileDocumentManager.getInstance().saveDocument(document)
      }
    }
    finally {
      document.removeDocumentListener(documentListener)
    }
  }

  private object ActionOnSaveContextElement : AbstractCoroutineContextElement(Key), IntelliJContextElement {

    override fun produceChildElement(parentContext: CoroutineContext, isStructured: Boolean): IntelliJContextElement = this

    object Key : CoroutineContext.Key<ActionOnSaveContextElement>
  }

  /**
   * Be careful when waiting in a modal context (ex: under a modal progress dialog).
   * The on-save actions need [ModalityState.nonModal] to be completed.
   */
  @ApiStatus.Experimental
  suspend fun awaitPendingActions() {
    coroutineScope.coroutineContext.job.children.toList().joinAll()
  }

  @ApiStatus.Experimental
  fun hasPendingActions(): Boolean {
    return coroutineScope.coroutineContext.job.children.iterator().hasNext()
  }

  @TestOnly
  fun waitForTasks() {
    @Suppress("DEPRECATION")
    runUnderModalProgressIfIsEdt {
      awaitPendingActions()
    }
  }
}

private class CurrentActionListener : AnActionListener {
  override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
    val project = event.project
    if (project != null && action is SaveDocumentAction) {
      ActionsOnSaveManager.getInstance(project).runningSaveDocumentAction = true
    }
  }

  override fun afterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult) {
    val project = event.project
    if (project != null) {
      ActionsOnSaveManager.getInstance(project).runningSaveDocumentAction = false
    }
  }
}
