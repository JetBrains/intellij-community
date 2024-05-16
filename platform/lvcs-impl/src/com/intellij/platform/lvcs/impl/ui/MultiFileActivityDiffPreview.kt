// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.ui

import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.impl.DiffEditorViewer
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.impl.DiffRequestProcessorListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor.Wrapper
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.platform.lvcs.impl.ActivityFileChange
import com.intellij.platform.lvcs.impl.ActivityScope
import com.intellij.platform.lvcs.impl.statistics.LocalHistoryCounter
import com.intellij.platform.lvcs.impl.ui.SingleFileActivityDiffPreview.Companion.DIFF_PLACE
import com.intellij.platform.lvcs.impl.ui.SingleFileActivityDiffPreview.Companion.getDiffTitleFor
import com.intellij.util.EventDispatcher
import com.intellij.util.containers.JBIterable

internal class MultiFileActivityDiffPreview(private val scope: ActivityScope, tree: ChangesTree, disposable: Disposable) :
  TreeHandlerEditorDiffPreview(tree, ActivityDiffPreviewHandler()) {
  private val eventDispatcher = EventDispatcher.create(DiffRequestProcessorListener::class.java)

  init {
    Disposer.register(disposable, this)
  }

  override fun performDiffAction(): Boolean {
    LocalHistoryCounter.logActionInvoked(LocalHistoryCounter.ActionKind.Diff, scope)
    return super.performDiffAction()
  }

  override fun createViewer(): DiffEditorViewer {
    return createDefaultViewer(DIFF_PLACE).also { viewer ->
      if (viewer is DiffRequestProcessor) {
        viewer.addListener(DiffRequestProcessorListener { eventDispatcher.multicaster.onViewerChanged() }, this)
      }
    }
  }

  override fun getEditorTabName(wrapper: Wrapper?): String {
    return getDiffTitleFor((wrapper?.userObject as? ActivityFileChange)?.filePath, scope)
  }

  internal fun addListener(listener: DiffRequestProcessorListener, disposable: Disposable) {
    eventDispatcher.addListener(listener, disposable)
  }
}

internal class ActivityDiffPreviewHandler : ChangesTreeDiffPreviewHandlerBase() {
  override fun collectWrappers(treeModelData: VcsTreeModelData): JBIterable<Wrapper> {
    return treeModelData.iterateUserObjects(ActivityFileChange::class.java).map { PresentableFileChangeWrapper(it) }
  }
}

private class PresentableFileChangeWrapper(private val fileChange: ActivityFileChange) : Wrapper(), PresentableChange by fileChange {
  override fun getUserObject(): Any = fileChange
  override fun getPresentableName(): String = fileChange.filePath.name
  override fun createProducer(project: Project?): DiffRequestProducer = fileChange.createProducer(project)
}
