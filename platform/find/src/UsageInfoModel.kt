// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.find


import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.usageView.UsageInfo
import com.intellij.usages.UsageInfoAdapter
import com.intellij.usages.UsagePresentation
import com.intellij.usages.rules.MergeableUsage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.CompletableFuture

class UsageInfoModel(val project: Project, val model: FindInProjectResult, val coroutineScope: CoroutineScope) : UsageInfoAdapter {

  override fun isValid() = true

  override fun getMergedInfos(): Array<UsageInfo> {
    // this code has to be replaced with a panel anyway. currently it only affects some actions from the toolbar we don't use
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
    val request = RdFindInProjectNavigation(model.fileId, model.offset, requestFocus)
    coroutineScope.launch { FindRemoteApi.getInstance().navigate(request) }
  }

  override fun getPath(): String = model.path

  override fun getLine(): Int = model.line ?: 0

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