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
class UsageInfoModel(val project: Project, val model: FindInProjectResult, val coroutineScope: CoroutineScope) : UsageInfoAdapter {

  override fun isValid(): Boolean = true

  //don't use it, use getMergedUsageInfos() instead
  override fun getMergedInfos(): Array<UsageInfo> {
    return emptyArray()
  }

  override fun getMergedInfosAsync(): CompletableFuture<Array<UsageInfo>> {
    val virtualFile = model.fileId.virtualFile() ?: return CompletableFuture.completedFuture(emptyArray())
      val usageInfo = UsageInfo(
        PsiManager.getInstance(project).findFile(virtualFile) ?: return CompletableFuture.completedFuture(emptyArray()),
        model.offset, model.offset + model.length
      )

    val future = CompletableFuture<Array<UsageInfo>>()
    future.complete(arrayOf(usageInfo))
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
    val openFileDescriptor = if (model.offset != -1)
      OpenFileDescriptor(project, virtualFile, model.offset)
    else
      OpenFileDescriptor(project, virtualFile, model.line, 0)
    openFileDescriptor.navigate(requestFocus)
   }

  override fun getPath(): String = model.path

  override fun getLine(): Int = model.line

  override fun getNavigationOffset(): Int = model.offset

  override fun getPresentation(): UsagePresentation {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getLocation(): FileEditorLocation? {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun highlightInEditor() {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun selectInEditor() {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun merge(mergeableUsage: MergeableUsage): Boolean {
    return false
  }

  override fun reset() {
    TODO("Not yet implemented")
  }
}