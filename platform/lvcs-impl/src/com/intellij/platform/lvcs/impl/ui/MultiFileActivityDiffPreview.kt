// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.ui

import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.impl.DiffEditorViewer
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor.Wrapper
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.platform.lvcs.impl.ActivityDiffObject
import com.intellij.platform.lvcs.impl.ActivityScope
import com.intellij.platform.lvcs.impl.statistics.LocalHistoryCounter
import com.intellij.platform.lvcs.impl.ui.SingleFileActivityDiffPreview.Companion.DIFF_PLACE
import com.intellij.platform.lvcs.impl.ui.SingleFileActivityDiffPreview.Companion.getDiffTitleFor
import com.intellij.util.containers.JBIterable

internal class MultiFileActivityDiffPreview(private val scope: ActivityScope, tree: ChangesTree, disposable: Disposable) :
  TreeHandlerEditorDiffPreview(tree, ActivityDiffPreviewHandler()) {

  init {
    Disposer.register(disposable, this)
  }

  override fun performDiffAction(): Boolean {
    LocalHistoryCounter.logActionInvoked(LocalHistoryCounter.ActionKind.Diff, scope)
    return super.performDiffAction()
  }

  override fun createViewer(): DiffEditorViewer {
    return createDefaultViewer(DIFF_PLACE)
  }

  override fun getEditorTabName(wrapper: Wrapper?): String {
    return getDiffTitleFor((wrapper?.userObject as? ActivityDiffObject)?.filePath, scope)
  }
}

internal class ActivityDiffPreviewHandler : ChangesTreeDiffPreviewHandlerBase() {
  override fun collectWrappers(treeModelData: VcsTreeModelData): JBIterable<Wrapper> {
    return treeModelData.iterateUserObjects(ActivityDiffObject::class.java).map { DiffObjectWrapper(it) }
  }
}

private class DiffObjectWrapper(private val diffObject: ActivityDiffObject) : Wrapper(), PresentableChange by diffObject {
  override fun getUserObject(): Any = diffObject
  override fun getPresentableName(): String = diffObject.filePath.name
  override fun createProducer(project: Project?): DiffRequestProducer = diffObject.createProducer(project)
}
