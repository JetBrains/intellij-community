// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vcs.changes.ui.SimpleAsyncChangesTreeModel.Companion.create
import com.intellij.platform.lvcs.impl.ActivityDiffData
import com.intellij.platform.lvcs.impl.ActivityDiffObject
import com.intellij.ui.ScrollableContentBorder
import com.intellij.ui.Side

class ActivityChangesBrowser(project: Project) : AsyncChangesBrowserBase(project, false, false), Disposable {
  var diffData: ActivityDiffData? = null
    set(value) {
      field = value
      myViewer.rebuildTree()
    }

  init {
    init()

    hideViewerBorder()
    ScrollableContentBorder.setup(viewerScrollPane, Side.TOP)
  }

  override val changesTreeModel: AsyncChangesTreeModel
    get() {
      return create { grouping ->
        val builder = TreeModelBuilder(myProject, grouping)
        val differenceObjectList = diffData?.presentableChanges ?: emptyList()
        for (diffObject in differenceObjectList) {
          val filePathNode = ActivityDiffObjectNode(diffObject)
          builder.insertChangeNode(diffObject.filePath, builder.myRoot, filePathNode)
        }
        return@create builder.build()
      }
    }

  override fun getDiffRequestProducer(userObject: Any): ChangeDiffRequestChain.Producer? {
    val diffObject = userObject as? ActivityDiffObject ?: return null
    return diffObject.createProducer(myProject)
  }

  override fun getData(dataId: String): Any? {
    if (ActivityViewDataKeys.SELECTED_DIFFERENCES.`is`(dataId)) {
      return VcsTreeModelData.selected(myViewer).iterateUserObjects(ActivityDiffObject::class.java)
    }
    return super.getData(dataId)
  }

  override fun createToolbarActions(): List<AnAction> {
    return super.createToolbarActions() + ActionManager.getInstance().getAction("ActivityView.ChangesBrowser.Toolbar")
  }

  override fun createPopupMenuActions(): List<AnAction> {
    return super.createPopupMenuActions() + ActionManager.getInstance().getAction("ActivityView.ChangesBrowser.Popup")
  }

  override fun dispose() = shutdown()
}

private class ActivityDiffObjectNode(diffObject: ActivityDiffObject)
  : AbstractChangesBrowserFilePathNode<ActivityDiffObject>(diffObject, diffObject.fileStatus) {
  override fun filePath(userObject: ActivityDiffObject): FilePath = userObject.filePath
}