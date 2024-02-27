// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.ui

import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.MessageDiffRequest
import com.intellij.diff.tools.combined.COMBINED_DIFF_VIEWER_KEY
import com.intellij.diff.tools.combined.CombinedPathBlockId
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
import com.intellij.openapi.vcs.changes.ui.PresentableChange
import com.intellij.platform.lvcs.impl.ActivityDiffData
import com.intellij.platform.lvcs.impl.ActivityDiffObject
import com.intellij.platform.lvcs.impl.ActivityScope
import com.intellij.platform.lvcs.impl.filePath
import com.intellij.platform.lvcs.impl.statistics.LocalHistoryCounter
import javax.swing.JComponent

internal abstract class CombinedActivityDiffPreview(project: Project,
                                                    targetComponent: JComponent,
                                                    val scope: ActivityScope,
                                                    parentDisposable: Disposable) :
  CombinedDiffPreview(project, targetComponent, true, parentDisposable) {

  private var diffData: ActivityDiffData? = null

  abstract fun loadDiffDataSynchronously(): ActivityDiffData?

  override fun performDiffAction(): Boolean {
    LocalHistoryCounter.logActionInvoked(LocalHistoryCounter.ActionKind.Diff, scope)
    setDiffData(loadDiffDataSynchronously(), forceUpdate = true)
    return super.performDiffAction()
  }

  override fun createPreviewModel(): CombinedDiffPreviewModel {
    return CombinedActivityDiffPreviewModel(project, parentDisposable)
  }

  override fun getCombinedDiffTabTitle(): String {
    val combinedDiffViewer = previewModel?.processor?.context?.getUserData(COMBINED_DIFF_VIEWER_KEY)
    val filePath = (combinedDiffViewer?.getCurrentBlockId() as? CombinedPathBlockId)?.path
                   ?: previewModel?.selected?.filePath
    if (filePath != null) return LocalHistoryBundle.message("activity.diff.tab.title.file", filePath.name)
    if (scope == ActivityScope.Recent) return LocalHistoryBundle.message("activity.diff.tab.title.recent")
    return LocalHistoryBundle.message("activity.diff.tab.title")
  }

  fun setDiffData(diffData: ActivityDiffData?, forceUpdate: Boolean = false) {
    val dataChanged = this.diffData != diffData
    if (dataChanged) {
      this.diffData = diffData
    }
    if (forceUpdate) updatePreviewProcessor.updateBlocks()
    else if (dataChanged) updatePreview()
  }

  private inner class CombinedActivityDiffPreviewModel(project: Project, parentDisposable: Disposable) :
    CombinedDiffPreviewModel(project, null, parentDisposable) {

    override fun iterateAllChanges(): Iterable<Wrapper> {
      val wrappers = diffData?.presentableChanges?.map { DiffObjectWrapper(it) }
      if (wrappers.isNullOrEmpty()) return listOf(EmptyChangeWrapper(project, scope))
      return wrappers
    }

    override fun iterateSelectedChanges(): Iterable<Wrapper> = iterateAllChanges()
    override fun selectChangeInSourceComponent(change: Wrapper) = Unit
  }
}

private class DiffObjectWrapper(private val diffObject: ActivityDiffObject) : Wrapper(), PresentableChange by diffObject {
  override fun getUserObject(): Any = diffObject
  override fun getPresentableName(): String = diffObject.filePath.name
  override fun createProducer(project: Project?): DiffRequestProducer = diffObject.createProducer(project)
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
