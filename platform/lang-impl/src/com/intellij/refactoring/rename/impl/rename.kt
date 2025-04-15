// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.impl

import com.intellij.codeInsight.actions.VcsFacade
import com.intellij.model.ModelPatch
import com.intellij.model.Pointer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.search.runSearch
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.rename.api.*
import com.intellij.refactoring.rename.api.ModifiableRenameUsage.*
import com.intellij.refactoring.rename.impl.FileUpdates.Companion.createFileUpdates
import com.intellij.refactoring.rename.ui.RenameDialog
import com.intellij.refactoring.rename.ui.commandName
import com.intellij.refactoring.rename.ui.progressTitle
import com.intellij.refactoring.rename.ui.withBackgroundIndicator
import com.intellij.util.Query
import com.intellij.util.asSafely
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.map
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly


internal typealias UsagePointer = Pointer<out RenameUsage>

/**
 * Entry point to perform rename with a dialog initialized with [targetName].
 */
internal fun showDialogAndRename(project: Project, target: RenameTarget, targetName: String = target.targetName) {
  ThreadingAssertions.assertEventDispatchThread()
  val initOptions = RenameDialog.Options(
    targetName = targetName,
    renameOptions = renameOptions(project, target)
  )
  val dialog = RenameDialog(project, target.presentation().presentableText, target.validator(), initOptions)
  if (!dialog.showAndGet()) {
    // cancelled
    return
  }
  val (newName: String, options: RenameOptions) = dialog.result()
  setTextOptions(target, options.textOptions)
  rename(project, target.createPointer(), newName, options, dialog.preview)
}

/**
 * Entry point to perform rename without a dialog.
 * @param preview whether the user explicitly requested the Preview
 */
internal fun rename(
  project: Project,
  targetPointer: Pointer<out RenameTarget>,
  newName: String,
  options: RenameOptions,
  preview: Boolean = false
) {
  val cs = CoroutineScope(CoroutineName("root rename coroutine"))
  rename(cs, project, targetPointer, newName, options, preview)
}

private fun rename(
  cs: CoroutineScope,
  project: Project,
  targetPointer: Pointer<out RenameTarget>,
  newName: String,
  options: RenameOptions,
  preview: Boolean
): Job {
  return cs.launch(Dispatchers.Default) {
    doRename(this, project, targetPointer, newName, options, preview)
  }
}

private suspend fun doRename(
  cs: CoroutineScope,
  project: Project,
  targetPointer: Pointer<out RenameTarget>,
  newName: String,
  options: RenameOptions,
  preview: Boolean
) {
  val query: Query<UsagePointer> = readAction {
    targetPointer.dereference()?.let { target ->
      buildQuery(project, target, options)
    }
  } ?: return
  val firstProgressTitle: String = targetPointer.progressTitle() ?: return // invalidated
  val channel: ReceiveChannel<UsagePointer> = runSearch(cs, project, query)
  val deferredUsages: Deferred<Collection<UsagePointer>> = withBackgroundIndicator(project, firstProgressTitle) {
    val (processedUsagePointers: Collection<UsagePointer>, forcePreview: Boolean) = if (preview) {
      ProcessUsagesResult(emptyList(), true)
    }
    else {
      processUsages(channel, newName)
    }
    if (forcePreview) {
      val usageView = showUsageView(project, targetPointer, newName, processedUsagePointers)
      appendUsages(usageView, channel, newName)
      cs.previewRenameAsync(project, targetPointer, newName, usageView) {
        runSearch(cs = this, project = project, query = query)
      }
    }
    else {
      cs.async {
        processedUsagePointers
      }
    }
  }
  val usagePointers: Collection<UsagePointer> = deferredUsages.await()
  val secondProgressTitle: String = targetPointer.progressTitle() ?: return
  val (fileUpdates: FileUpdates?, modelUpdate: ModelUpdate?) = withBackgroundIndicator(project, secondProgressTitle) {
    prepareRename(usagePointers, newName)
  }
  if (preview && fileUpdates != null && !previewInDialog(project, fileUpdates)) {
    return
  }
  val commandName: String = targetPointer.commandName(newName) ?: return
  WriteCommandAction
    .writeCommandAction(project)
    .withName(commandName)
    .run<Throwable> {
      if (modelUpdate != null) {
        targetPointer.dereference()?.targetName?.let { oldName ->
          val undoableAction = RenameUndoableAction(modelUpdate, oldName, newName)
          UndoManager.getInstance(project).undoableActionPerformed(undoableAction)
        }
        modelUpdate.updateModel(newName)
      }
      fileUpdates?.doUpdate()
    }
}

private data class ProcessUsagesResult(
  val processedUsagePointers: Collection<UsagePointer>,
  val forcePreview: Boolean
)

private suspend fun processUsages(usageChannel: ReceiveChannel<UsagePointer>, newName: String): ProcessUsagesResult {
  if (ApplicationManager.getApplication().isUnitTestMode) {
    return ProcessUsagesResult(usageChannel.toList(), false)
  }
  val usagePointers = ArrayList<UsagePointer>()
  for (pointer: UsagePointer in usageChannel) {
    usagePointers += pointer
    val forcePreview: Boolean? = readAction {
      pointer.dereference()?.let { renameUsage ->
        renameUsage !is ModifiableRenameUsage || renameUsage is TextRenameUsage || renameUsage.conflicts(newName).isNotEmpty()
      }
    }
    if (forcePreview == true) {
      return ProcessUsagesResult(usagePointers, true)
    }
  }
  return ProcessUsagesResult(usagePointers, false)
}

@ApiStatus.Internal
suspend fun prepareRename(allUsages: Collection<UsagePointer>, newName: String): Pair<FileUpdates?, ModelUpdate?> {
  return coroutineScope {
    require(!ApplicationManager.getApplication().isReadAccessAllowed)
    val (
      byFileUpdater: Map<FileUpdater, List<Pointer<out ModifiableRenameUsage>>>,
      byModelUpdater: Map<ModelUpdater, List<Pointer<out ModifiableRenameUsage>>>
    ) = readAction {
      classifyUsages(allUsages)
    }
    val fileUpdates: Deferred<FileUpdates?> = async {
      prepareFileUpdates(byFileUpdater, newName)
    }
    val modelUpdates: Deferred<ModelUpdate?> = async {
      prepareModelUpdate(byModelUpdater)
    }
    Pair(
      fileUpdates.await(),
      modelUpdates.await()
    )
  }
}

private fun classifyUsages(
  allUsages: Collection<UsagePointer>
): Pair<
  Map<FileUpdater, List<Pointer<out ModifiableRenameUsage>>>,
  Map<ModelUpdater, List<Pointer<out ModifiableRenameUsage>>>
  > {
  ApplicationManager.getApplication().assertReadAccessAllowed()

  val byFileUpdater = HashMap<FileUpdater, MutableList<Pointer<out ModifiableRenameUsage>>>()
  val byModelUpdater = HashMap<ModelUpdater, MutableList<Pointer<out ModifiableRenameUsage>>>()
  for (pointer: UsagePointer in allUsages) {
    ProgressManager.checkCanceled()
    val renameUsage: ModifiableRenameUsage = pointer.dereference() as? ModifiableRenameUsage ?: continue
    @Suppress("UNCHECKED_CAST") val modifiablePointer = pointer as Pointer<out ModifiableRenameUsage>
    renameUsage.fileUpdater?.let { fileUpdater: FileUpdater ->
      byFileUpdater.getOrPut(fileUpdater) { ArrayList() }.add(modifiablePointer)
    }
    renameUsage.modelUpdater?.let { modelUpdater: ModelUpdater ->
      byModelUpdater.getOrPut(modelUpdater) { ArrayList() }.add(modifiablePointer)
    }
  }
  return Pair(byFileUpdater, byModelUpdater)
}

private suspend fun prepareFileUpdates(
  byFileUpdater: Map<FileUpdater, List<Pointer<out ModifiableRenameUsage>>>,
  newName: String
): FileUpdates? {
  return byFileUpdater.entries
    .asFlow()
    .map { (fileUpdater: FileUpdater, usagePointers: List<Pointer<out ModifiableRenameUsage>>) ->
      prepareFileUpdates(fileUpdater, usagePointers, newName)
    }
    .fold(null) { accumulator: FileUpdates?, value: FileUpdates? ->
      FileUpdates.merge(accumulator, value)
    }
}

private suspend fun prepareFileUpdates(
  fileUpdater: FileUpdater,
  usagePointers: List<Pointer<out ModifiableRenameUsage>>,
  newName: String
): FileUpdates? {
  return readAction {
    usagePointers.dereferenceOrNull()?.let { usages: List<ModifiableRenameUsage> ->
      createFileUpdates(fileUpdater.prepareFileUpdateBatch(usages, newName))
    }
  }
}


private suspend fun prepareModelUpdate(byModelUpdater: Map<ModelUpdater, List<Pointer<out ModifiableRenameUsage>>>): ModelUpdate? {
  val updates: List<ModelUpdate> = byModelUpdater
    .flatMap { (modelUpdater: ModelUpdater, usagePointers: List<Pointer<out ModifiableRenameUsage>>) ->
      readAction {
        usagePointers.dereferenceOrNull()
          ?.let { usages: List<ModifiableRenameUsage> ->
            modelUpdater.prepareModelUpdateBatch(usages)
          }
        ?: emptyList()
      }
    }
  return when (updates.size) {
    0 -> null
    1 -> updates[0]
    else -> object : ModelUpdate {
      override fun updateModel(newName: String) {
        for (update: ModelUpdate in updates) {
          update.updateModel(newName)
        }
      }
    }
  }
}

private fun <X : Any> Collection<Pointer<out X>>.dereferenceOrNull(): List<X>? {
  ApplicationManager.getApplication().assertReadAccessAllowed()
  return this.mapNotNull { pointer: Pointer<out X> ->
    pointer.dereference()
  }.takeUnless { list: List<X> ->
    list.isEmpty()
  }
}

private suspend fun previewInDialog(project: Project, fileUpdates: FileUpdates): Boolean {
  if (!Registry.`is`("ide.rename.preview.dialog")) {
    return true
  }
  val preview: Map<VirtualFile, CharSequence> = readAction {
    fileUpdates.preview()
  }
  // write action might happen here, but this code is internal, used to check preview dialog
  val patch = object : ModelPatch {
    override fun getBranchChanges(): Map<VirtualFile, CharSequence> = preview
    override fun applyBranchChanges() = error("not implemented")
  }
  return withContext(Dispatchers.EDT) {
    VcsFacade.getInstance().createPatchPreviewComponent(project, patch)?.let { previewComponent ->
      DialogBuilder(project)
        .title(RefactoringBundle.message("rename.preview.tab.title"))
        .centerPanel(previewComponent)
        .showAndGet()
    }
  } != false
}

@Internal
@TestOnly
fun renameAndWait(project: Project, target: RenameTarget, newName: String) {
  val application = ApplicationManager.getApplication()
  ThreadingAssertions.assertEventDispatchThread()
  require(application.isUnitTestMode)

  target.validator().validate(newName)
    .asSafely<RenameValidationResult.Companion.RenameValidationResultData>()
    ?.takeIf { it.level == RenameValidationResult.Companion.RenameValidationResultProblemLevel.ERROR }
    ?.let { throw IllegalArgumentException(it.message(newName)) }

  val targetPointer = target.createPointer()
  val options = RenameOptions(
    textOptions = TextOptions(
      commentStringOccurrences = true,
      textOccurrences = true,
    ),
    searchScope = target.maximalSearchScope ?: GlobalSearchScope.projectScope(project)
  )
  runBlocking {
    withTimeout(timeMillis = 1000 * 60 * 10) {
      val renameJob = rename(cs = this@withTimeout, project, targetPointer, newName, options, preview = false)
      while (renameJob.isActive) {
        UIUtil.dispatchAllInvocationEvents()
        delay(timeMillis = 10)
      }
    }
  }
  PsiDocumentManager.getInstance(project).commitAllDocuments()
}

internal object EmptyRenameValidator: RenameValidator {
  override fun validate(newName: String): RenameValidationResult {
    return RenameValidationResult.ok()
  }

}