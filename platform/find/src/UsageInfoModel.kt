// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.find


import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiFileRange
import com.intellij.usageView.UsageInfo
import com.intellij.usages.UsageInfoAdapter
import com.intellij.usages.UsagePresentation
import com.intellij.usages.rules.MergeableUsage
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.CompletableFuture

private val LOG = logger<UsageInfoModel>()
class UsageInfoModel(val project: Project, val model: FindInFilesResult, val coroutineScope: CoroutineScope) : UsageInfoAdapter {

  override fun isValid(): Boolean = true

  override fun getMergedInfos(): Array<UsageInfo> {
    return emptyArray()
  }

  override fun getMergedInfosAsync(): CompletableFuture<Array<UsageInfo>> {
    val virtualFile = model.fileId.virtualFile() ?: return CompletableFuture.completedFuture(emptyArray())
    val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return CompletableFuture.completedFuture(emptyArray())
    val usageInfos = model.mergedOffsets.map {
      UsageInfo(
        psiFile,
        it, it + model.length
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
    val virtualFile = model.fileId.virtualFile()
    if (virtualFile == null) {
      LOG.error("Cannot find virtual file for ${model.fileId}")
      return
    }
    val openFileDescriptor = if (navigationOffset != -1)
      OpenFileDescriptor(project, virtualFile, navigationOffset)
    else
      OpenFileDescriptor(project, virtualFile, line, 0)
    openFileDescriptor.navigate(requestFocus)
   }

  override fun getPath(): String = model.presentablePath

  override fun getLine(): Int = model.line

  override fun getNavigationOffset(): Int = model.navigationOffset

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
    return false
  }

  override fun reset() {
  }
}