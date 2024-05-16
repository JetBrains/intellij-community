// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vcs.changes.ui.SimpleAsyncChangesTreeModel.Companion.create
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder.PATH_COMPARATOR
import com.intellij.platform.lvcs.impl.ActivityDiffData
import com.intellij.platform.lvcs.impl.ActivityFileChange
import com.intellij.ui.ScrollableContentBorder
import com.intellij.ui.Side
import java.util.Comparator.comparing

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
        val changesList = diffData?.presentableChanges ?: emptyList()
        for (presentableChange in changesList.sortedWith(comparing(PresentableChange::getFilePath, PATH_COMPARATOR))) {
          val filePath = presentableChange.filePath
          val filePathNode = if (filePath.isDirectory) PresentableDirectoryChangeNode(presentableChange) else PresentableChangeNode(presentableChange)
          builder.insertChangeNode(filePath, builder.myRoot, filePathNode)
        }
        return@create builder.build()
      }
    }

  override fun getDiffRequestProducer(userObject: Any): ChangeDiffRequestChain.Producer? {
    val activityFileChange = userObject as? ActivityFileChange ?: return null
    return activityFileChange.createProducer(myProject)
  }

  override fun getData(dataId: String): Any? {
    if (ActivityViewDataKeys.SELECTED_DIFFERENCES.`is`(dataId)) {
      return VcsTreeModelData.selected(myViewer).iterateUserObjects(PresentableChange::class.java)
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

private open class PresentableChangeNode(presentableChange: PresentableChange)
  : AbstractChangesBrowserFilePathNode<PresentableChange>(presentableChange, presentableChange.fileStatus) {
  override fun filePath(userObject: PresentableChange): FilePath = userObject.filePath
}

private class PresentableDirectoryChangeNode(presentableChange: PresentableChange) : ChangesBrowserNode.NodeWithFilePath,
                                                                                     PresentableChangeNode(presentableChange) {
  override fun getNodeFilePath(): FilePath = filePath(getUserObject())
}