// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.impl

import com.intellij.codeInsight.actions.VcsFacade
import com.intellij.model.ModelPatch
import com.intellij.model.Pointer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.search.runSearch
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.rename.api.FileOperation
import com.intellij.refactoring.rename.api.ModifiableRenameUsage
import com.intellij.refactoring.rename.api.ModifiableRenameUsage.*
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.rename.api.RenameUsage
import com.intellij.refactoring.rename.api.ReplaceTextTargetContext.IN_COMMENTS_AND_STRINGS
import com.intellij.refactoring.rename.api.ReplaceTextTargetContext.IN_PLAIN_TEXT
import com.intellij.refactoring.rename.ui.*
import com.intellij.util.Query
import com.intellij.util.text.StringOperation
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.map
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext

internal val LOG: Logger = Logger.getInstance("#com.intellij.refactoring.rename.impl")

internal typealias UsagePointer = Pointer<out RenameUsage>

internal fun rename(project: Project, target: RenameTarget) {
  ApplicationManager.getApplication().assertIsDispatchThread()
  val canRenameTextOccurrences = !target.textTargets(IN_PLAIN_TEXT).isEmpty()
  val canRenameCommentAndStringOccurrences = !target.textTargets(IN_COMMENTS_AND_STRINGS).isEmpty()
  val initOptions = RenameDialog.Options(
    targetName = target.targetName,
    renameOptions = RenameOptions(
      renameTextOccurrences = if (canRenameTextOccurrences) true else null,
      renameCommentsStringsOccurrences = if (canRenameCommentAndStringOccurrences) true else null,
      searchScope = target.maximalSearchScope ?: GlobalSearchScope.allScope(project)
    )
  )
  val dialog = RenameDialog(project, target.presentation.presentableText, initOptions)
  if (!dialog.showAndGet()) {
    // cancelled
    return
  }
  val (newName: String, options: RenameOptions) = dialog.result()
  val preview: Boolean = dialog.preview
  val targetPointer: Pointer<out RenameTarget> = target.createPointer()
  CoroutineScope(CoroutineName("root rename coroutine")).launch(Dispatchers.Default) {
    rename(this, project, targetPointer, newName, options, preview)
  }
}

private suspend fun rename(
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
  val usagePointers = ArrayList<UsagePointer>()
  for (pointer: UsagePointer in usageChannel) {
    usagePointers += pointer
    val forcePreview: Boolean? = readAction {
      pointer.dereference()?.let { renameUsage ->
        renameUsage is TextUsage || renameUsage.conflicts(newName).isNotEmpty()
      }
    }
    if (forcePreview == true) {
      return ProcessUsagesResult(usagePointers, true)
    }
  }
  return ProcessUsagesResult(usagePointers, false)
}

private suspend fun prepareRename(allUsages: Collection<UsagePointer>, newName: String): Pair<FileUpdates?, ModelUpdate?> {
  return coroutineScope {
    require(!ApplicationManager.getApplication().isReadAccessAllowed)
    val (
      byFileUpdater: Map<FileUpdater, List<Pointer<out ModifiableRenameUsage>>>,
      byModelUpdater: Map<ModelUpdater, List<Pointer<out ModifiableRenameUsage>>>
    ) = readAction { ctx: CoroutineContext ->
      classifyUsages(ctx, allUsages)
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
  ctx: CoroutineContext,
  allUsages: Collection<UsagePointer>
): Pair<
  Map<FileUpdater, List<Pointer<out ModifiableRenameUsage>>>,
  Map<ModelUpdater, List<Pointer<out ModifiableRenameUsage>>>
  > {
  ApplicationManager.getApplication().assertReadAccessAllowed()

  val byFileUpdater = HashMap<FileUpdater, MutableList<Pointer<out ModifiableRenameUsage>>>()
  val byModelUpdater = HashMap<ModelUpdater, MutableList<Pointer<out ModifiableRenameUsage>>>()
  for (pointer: UsagePointer in allUsages) {
    ctx.ensureActive()
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
  return readAction { ctx: CoroutineContext ->
    usagePointers.dereferenceOrNull()?.let { usages: List<ModifiableRenameUsage> ->
      createFileUpdates(ctx, fileUpdater.prepareFileUpdateBatch(ctx, usages, newName))
    }
  }
}

private fun createFileUpdates(ctx: CoroutineContext, fileOperations: Collection<FileOperation>): FileUpdates {
  ApplicationManager.getApplication().assertReadAccessAllowed()

  val filesToAdd = ArrayList<Pair<Path, CharSequence>>()
  val filesToMove = ArrayList<Pair<VirtualFile, Path>>()
  val filesToRemove = ArrayList<VirtualFile>()
  val fileModifications = ArrayList<Pair<RangeMarker, CharSequence>>()

  loop@
  for (fileOperation: FileOperation in fileOperations) {
    ctx.ensureActive()
    when (fileOperation) {
      is FileOperation.Add -> filesToAdd += Pair(fileOperation.path, fileOperation.content)
      is FileOperation.Move -> filesToMove += Pair(fileOperation.file, fileOperation.path)
      is FileOperation.Remove -> filesToRemove += fileOperation.file
      is FileOperation.Modify -> {
        val document: Document = FileDocumentManager.getInstance().getDocument(fileOperation.file.virtualFile) ?: continue@loop
        for (stringOperation: StringOperation in fileOperation.modifications) {
          ctx.ensureActive()
          val rangeMarker: RangeMarker = document.createRangeMarker(stringOperation.range)
          fileModifications += Pair(rangeMarker, stringOperation.replacement)
        }
      }
    }
  }

  return FileUpdates(filesToAdd, filesToMove, filesToRemove, fileModifications)
}

private suspend fun prepareModelUpdate(byModelUpdater: Map<ModelUpdater, List<Pointer<out ModifiableRenameUsage>>>): ModelUpdate? {
  val updates: List<ModelUpdate> = byModelUpdater
    .flatMap { (modelUpdater: ModelUpdater, usagePointers: List<Pointer<out ModifiableRenameUsage>>) ->
      readAction { ctx: CoroutineContext ->
        usagePointers.dereferenceOrNull()
          ?.let { usages: List<ModifiableRenameUsage> ->
            modelUpdater.prepareModelUpdateBatch(ctx, usages)
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
  return withContext(uiDispatcher) {
    VcsFacade.getInstance().createPatchPreviewComponent(project, patch)?.let { previewComponent ->
      DialogBuilder(project)
        .title(RefactoringBundle.message("rename.preview.tab.title"))
        .centerPanel(previewComponent)
        .showAndGet()
    }
  } != false
}
