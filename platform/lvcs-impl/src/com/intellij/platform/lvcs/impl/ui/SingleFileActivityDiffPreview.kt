// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.ui

import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.impl.DiffEditorViewer
import com.intellij.history.integration.LocalHistoryBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.ListSelection
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.DiffPreviewUpdateProcessor
import com.intellij.openapi.vcs.changes.EditorTabDiffPreview
import com.intellij.openapi.vcs.changes.SingleFileDiffPreviewProcessor
import com.intellij.platform.lvcs.impl.ActivityScope
import com.intellij.platform.lvcs.impl.ActivitySelection
import com.intellij.platform.lvcs.impl.filePath
import com.intellij.platform.lvcs.impl.statistics.LocalHistoryCounter
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import org.jetbrains.annotations.Nls

internal abstract class SingleFileActivityDiffPreview(project: Project, private val scope: ActivityScope,
                                                      disposable: Disposable) : EditorTabDiffPreview(project) {
  init {
    Disposer.register(disposable, this)
  }

  abstract val selection: ActivitySelection?
  abstract fun onSelectionChange(disposable: Disposable, runnable: () -> Unit)
  abstract fun getDiffRequestProducer(scope: ActivityScope, selection: ActivitySelection): DiffRequestProducer?

  override fun hasContent() = selection != null

  override fun createViewer(): DiffEditorViewer {
    val processor = MyDiffRequestProcessor(project)
    onSelectionChange(processor) { processor.updatePreview() }
    UiNotifyConnector.installOn(processor.component, object : Activatable {
      override fun showNotify() = processor.updatePreview()
    })
    return processor
  }

  override fun collectDiffProducers(selectedOnly: Boolean): ListSelection<DiffRequestProducer>? {
    val diffRequestProducer = getCurrentDiffRequestProducer() ?: return null
    return ListSelection.createSingleton(diffRequestProducer)
  }

  override fun performDiffAction(): Boolean {
    LocalHistoryCounter.logActionInvoked(LocalHistoryCounter.ActionKind.Diff, scope)
    return super.performDiffAction()
  }

  override fun getEditorTabName(processor: DiffEditorViewer?) = getDiffTitleFor((scope as? ActivityScope.File)?.filePath, scope)

  private fun getCurrentDiffRequestProducer(): DiffRequestProducer? {
    val currentSelection = selection ?: return null
    return getDiffRequestProducer(scope, currentSelection)
  }

  private inner class MyDiffRequestProcessor(project: Project) : SingleFileDiffPreviewProcessor(project, DIFF_PLACE), DiffPreviewUpdateProcessor {
    override fun getCurrentRequestProvider() = getCurrentDiffRequestProducer()
  }

  companion object {
    const val DIFF_PLACE = "ActivityView"

    fun getDiffTitleFor(filePath: FilePath?, activityScope: ActivityScope): @Nls String {
      if (filePath != null) return LocalHistoryBundle.message("activity.diff.tab.title.file", filePath.name)
      if (activityScope == ActivityScope.Recent) return LocalHistoryBundle.message("activity.diff.tab.title.recent")
      return LocalHistoryBundle.message("activity.diff.tab.title")
    }
  }
}