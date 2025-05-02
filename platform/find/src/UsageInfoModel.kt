// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.find


import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.usageView.UsageInfo
import com.intellij.usages.UsageInfoAdapter
import com.intellij.usages.UsagePresentation
import com.intellij.usages.rules.MergeableUsage
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.CompletableFuture

private val LOG = logger<UsageInfoModel>()
class UsageInfoModel(val project: Project, private val model: FindInFilesResult, val coroutineScope: CoroutineScope) : UsageInfoAdapter {
  private val mergedUsages = mutableListOf(model)
  val mergedModel: FindInFilesResult
    get() = mergedUsages.last()
  override fun isValid(): Boolean = true

  override fun getMergedInfos(): Array<UsageInfo> {
    return emptyArray()
  }

  override fun getMergedInfosAsync(): CompletableFuture<Array<UsageInfo>> {
    val virtualFile = mergedModel.fileId.virtualFile() ?: return CompletableFuture.completedFuture(emptyArray())
    val usageInfos = mergedUsages.map {
      UsageInfo(
        PsiManager.getInstance(project).findFile(virtualFile) ?: return CompletableFuture.completedFuture(emptyArray()),
        it.originalOffset, it.originalOffset + it.originalLength
      )
    }.toTypedArray()

    val future = CompletableFuture<Array<UsageInfo>>()
    future.complete(usageInfos)
    return future
  }

  override fun isReadOnly(): Boolean = false

  override fun canNavigate(): Boolean = true
  override fun canNavigateToSource(): Boolean = true

  override fun navigate(requestFocus: Boolean) {
    val virtualFile = mergedModel.fileId.virtualFile()
    if (virtualFile == null) {
      LOG.error("Cannot find virtual file for ${mergedModel.fileId}")
      return
    }
    val openFileDescriptor = if (navigationOffset != -1)
      OpenFileDescriptor(project, virtualFile, navigationOffset)
    else
      OpenFileDescriptor(project, virtualFile, line, 0)
    openFileDescriptor.navigate(requestFocus)
   }

  override fun getPath(): String = mergedModel.path

  override fun getLine(): Int = mergedModel.line

  override fun getNavigationOffset(): Int = mergedModel.offset

  //used in tests see ExporterToTextFile; also in FindUsagesDumber
  override fun getPresentation(): UsagePresentation {
    TODO("not implemented")
  }

  //Show usages action
  override fun getLocation(): FileEditorLocation? {
    return null
  }

  //used for navigation by cmd-down from findPopup
  override fun highlightInEditor() {
    TODO("not implemented")
  }

  //used in findUsages not find in files
  override fun selectInEditor() {
  }

  override fun merge(mergeableUsage: MergeableUsage): Boolean {
    if (mergeableUsage !is UsageInfoModel) return false
    if (mergeableUsage.mergedModel.merged) {
      mergedUsages.add(mergeableUsage.mergedModel)
      return true
    }
    return false
  }

  override fun reset() {
    mergedUsages.clear()
    mergedUsages.add(model)
  }
}