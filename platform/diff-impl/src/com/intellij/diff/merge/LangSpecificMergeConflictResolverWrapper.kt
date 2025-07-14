// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge

import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.fragments.MergeLineFragment
import com.intellij.diff.merge.LangSpecificMergeConflictResolverWrapper.CoroutineScopeService.Companion.scope
import com.intellij.diff.tools.util.text.LineOffsets
import com.intellij.diff.tools.util.text.LineOffsetsUtil
import com.intellij.diff.util.MergeConflictResolutionStrategy
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry.Companion.`is`
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class LangSpecificMergeConflictResolverWrapper(private val project: Project?, contentList: List<DocumentContent>) {
  private val resolver: LangSpecificMergeConflictResolver? = if (`is`("semantic.merge.conflict.resolution", false)) {
    LangSpecificMergeConflictResolver.findApplicable(contentList)
  }
  else {
    null
  }
  private val mutex = Mutex()
  private val resolvedChanges: MutableList<CharSequence?> = mutableListOf()
  private var hasChunksInitiallyResolved: Boolean = false

  @RequiresBlockingContext
  fun init(lineOffsetsList: List<LineOffsets>, fragmentList: List<MergeLineFragment>, fileList: List<PsiFile>) {
    if (!isAvailable()) return
    val action: suspend CoroutineScope.() -> Unit = {
      calculateConflicts(lineOffsetsList, fragmentList, fileList)
      if (resolvedChanges.any { it != null }) hasChunksInitiallyResolved = true
    }
    val application = ApplicationManager.getApplication()
    if (application.isDispatchThread) {
      check(application.isUnitTestMode && project != null) { "From EDT this method can only be called in unit tests."}
      runWithModalProgressBlocking(project, "", action)
    } else {
      runBlockingCancellable(action)
    }
  }

  private suspend fun calculateConflicts(
    lineOffsetsList: List<LineOffsets>,
    fragmentList: List<MergeLineFragment>,
    fileList: List<PsiFile>,
  ) {
    if (!isAvailable()) return
    val context = LangSpecificMergeContext(project, fragmentList, fileList, lineOffsetsList)
    val newContentList = withContext(Dispatchers.Default) {
      resolver?.tryResolveMergeConflicts(context)
    } ?: List(fragmentList.size) { null }
    mutex.withLock {
      resolvedChanges.clear()
      resolvedChanges.addAll(newContentList)
    }
  }

  @RequiresEdt
  fun updateHighlighting(fileList: List<PsiFile>, mergeChangeList: List<TextMergeChange>, scheduleRediff: Runnable) {
    val localMergeChangeList = mergeChangeList.toList()
    if (!isAvailable() || project == null || !hasChunksInitiallyResolved || localMergeChangeList.size != resolvedChanges.size) return
    project.scope.coroutineContext.cancelChildren()
    project.scope.launch(ModalityState.defaultModalityState().asContextElement()) {
      val lineOffsetsList = fileList.map { LineOffsetsUtil.create(it.fileDocument) }
      val lineFragmentList = localMergeChangeList.map { it.fragment }

      calculateConflicts(lineOffsetsList, lineFragmentList, fileList)

      withContext(Dispatchers.EDT) {
        for (i in localMergeChangeList.indices) {
          val textMergeChange: TextMergeChange = localMergeChangeList[i]
          if (!textMergeChange.isConflict || textMergeChange.isResolved) continue
          val type = textMergeChange.conflictType
          if (type.resolutionStrategy == MergeConflictResolutionStrategy.TEXT) continue

          val resolveResult: CharSequence? = getResolvedConflictContent(i)

          type.resolutionStrategy = if (resolveResult != null) MergeConflictResolutionStrategy.SEMANTIC else null

          textMergeChange.reinstallHighlighters()
        }
        scheduleRediff.run()
      }
    }
  }

  fun canResolveConflictSemantically(index: Int): Boolean {
    checkIndexInRange(index)
    return resolvedChanges.getOrNull(index) != null
  }

  fun getResolvedConflictContent(index: Int): CharSequence? {
    checkIndexInRange(index)
    return resolvedChanges.getOrNull(index)
  }

  fun isAvailable(): Boolean = resolver != null && project != null

  private fun checkIndexInRange(index: Int) {
    if (resolver == null) return
    check(index in resolvedChanges.indices) { "Index out of bounds: $index, size: ${resolvedChanges.size}. Possibly conflicting chunks wasn't resolve correctly."}
  }

  @Service(Service.Level.PROJECT)
  private class CoroutineScopeService(private val coroutineScope: CoroutineScope) {
    companion object {
      val Project.scope: CoroutineScope
        get() = service<CoroutineScopeService>().coroutineScope
    }
  }
}