// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.ui

import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.MessageDiffRequest
import com.intellij.history.integration.LocalHistoryBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor.Wrapper
import com.intellij.openapi.vcs.changes.actions.diff.CombinedDiffPreview
import com.intellij.openapi.vcs.changes.actions.diff.CombinedDiffPreviewModel
import com.intellij.platform.lvcs.impl.ActivityDiffData
import com.intellij.platform.lvcs.impl.ActivityScope
import com.intellij.platform.lvcs.impl.filePath
import javax.swing.JComponent

internal open class CombinedActivityDiffPreview(project: Project,
                                                targetComponent: JComponent,
                                                val scope: ActivityScope,
                                                parentDisposable: Disposable) :
  CombinedDiffPreview(project, targetComponent, true, parentDisposable) {

  override fun createPreviewModel(): CombinedDiffPreviewModel {
    return CombinedActivityDiffPreviewModel(project, scope, parentDisposable)
  }

  override fun getCombinedDiffTabTitle(): String {
    val filePath = previewModel?.selected?.filePath
    if (filePath != null) return LocalHistoryBundle.message("activity.diff.tab.title.file", filePath.name)
    if (scope == ActivityScope.Recent) return LocalHistoryBundle.message("activity.diff.tab.title.recent")
    return LocalHistoryBundle.message("activity.diff.tab.title")
  }

  fun setDiffData(diffData: ActivityDiffData?) {
    (previewModel as? CombinedActivityDiffPreviewModel)?.diffData = diffData
    updatePreview()
  }
}

private class CombinedActivityDiffPreviewModel(project: Project, private val scope: ActivityScope, parentDisposable: Disposable) :
  CombinedDiffPreviewModel(project, parentDisposable) {

  var diffData: ActivityDiffData? = null

  override fun iterateAllChanges(): Iterable<Wrapper> {
    return diffData?.getPresentableChanges(project)?.takeIf { it.any() } ?: listOf(EmptyChangeWrapper(project, scope))
  }

  override fun iterateSelectedChanges(): Iterable<Wrapper> = iterateAllChanges()
  override fun selectChangeInSourceComponent(change: Wrapper) = Unit
}

private class EmptyChangeWrapper(private val project: Project, private val scope: ActivityScope) : Wrapper() {
  private val message get() = LocalHistoryBundle.message("activity.diff.empty.text")

  override fun getFilePath(): FilePath = (scope as? ActivityScope.File)?.filePath ?: LocalFilePath(project.guessProjectDir()!!.path,
                                                                                                   project.projectFile?.isDirectory == true)

  override fun getFileStatus(): FileStatus = FileStatus.NOT_CHANGED
  override fun getUserObject(): Any = message
  override fun getPresentableName(): String? = message

  override fun createProducer(project: Project?): DiffRequestProducer {
    return object : DiffRequestProducer {
      override fun getName(): String = message
      override fun process(context: UserDataHolder, indicator: ProgressIndicator): DiffRequest = MessageDiffRequest(message)
    }
  }
}
