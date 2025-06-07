// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.ui

import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.impl.DiffEditorViewer
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.impl.DiffRequestProcessorListener
import com.intellij.diff.util.DiffUtil
import com.intellij.history.integration.LocalHistoryBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.ListSelection
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.EditorTabDiffPreview
import com.intellij.openapi.vcs.changes.SingleFileDiffPreviewProcessor
import com.intellij.platform.lvcs.impl.ActivityScope
import com.intellij.platform.lvcs.impl.ActivitySelection
import com.intellij.platform.lvcs.impl.filePath
import com.intellij.platform.lvcs.impl.statistics.LocalHistoryCounter
import com.intellij.util.EventDispatcher
import com.intellij.platform.vcs.impl.shared.changes.DiffPreviewUpdateProcessor
import org.jetbrains.annotations.Nls

internal class SingleFileActivityDiffPreview(project: Project, private val model: ActivityViewModel, disposable: Disposable) : EditorTabDiffPreview(project) {
  private val eventDispatcher = EventDispatcher.create(DiffRequestProcessorListener::class.java)

  init {
    Disposer.register(disposable, this)
  }

  override fun hasContent() = model.selection != null

  override fun createViewer(): DiffEditorViewer {
    return createViewer(project, model).also { viewer ->
      viewer.addListener(DiffRequestProcessorListener { eventDispatcher.multicaster.onViewerChanged() }, this)
    }
  }

  override fun collectDiffProducers(selectedOnly: Boolean): ListSelection<DiffRequestProducer>? {
    val diffRequestProducer = model.getSingleDiffRequestProducer() ?: return null
    return ListSelection.createSingleton(diffRequestProducer)
  }

  override fun performDiffAction(): Boolean {
    LocalHistoryCounter.logActionInvoked(LocalHistoryCounter.ActionKind.Diff, model.activityScope)
    return super.performDiffAction()
  }

  override fun getEditorTabName(processor: DiffEditorViewer?) = getDiffTitleFor((model.activityScope as? ActivityScope.File)?.filePath,
                                                                                model.activityScope)

  internal fun addListener(listener: DiffRequestProcessorListener, disposable: Disposable) {
    eventDispatcher.addListener(listener, disposable)
  }

  companion object {
    const val DIFF_PLACE = "ActivityView"

    fun getDiffTitleFor(filePath: FilePath?, activityScope: ActivityScope): @Nls String {
      if (filePath != null) return LocalHistoryBundle.message("activity.diff.tab.title.file", filePath.name)
      if (activityScope == ActivityScope.Recent) return LocalHistoryBundle.message("activity.diff.tab.title.recent")
      return LocalHistoryBundle.message("activity.diff.tab.title")
    }

    internal fun createViewer(project: Project, model: ActivityViewModel): DiffRequestProcessor {
      val processor = SingleFileActivityDiffRequestProcessor(project, model)
      model.addListener(object : ActivityModelListener {
        override fun onSelectionChanged(selection: ActivitySelection?) = processor.updatePreview()
      }, processor)
      DiffUtil.installShowNotifyListener(processor.component) { processor.updatePreview() }
      return processor
    }
  }
}

private class SingleFileActivityDiffRequestProcessor(project: Project, val model: ActivityViewModel) :
  SingleFileDiffPreviewProcessor(project, SingleFileActivityDiffPreview.DIFF_PLACE), DiffPreviewUpdateProcessor {

  override fun getCurrentRequestProvider() = model.getSingleDiffRequestProducer()
}

private fun ActivityViewModel.getSingleDiffRequestProducer(): DiffRequestProducer? {
  val currentSelection = selection ?: return null
  return activityProvider.loadSingleDiff(activityScope, currentSelection)
}