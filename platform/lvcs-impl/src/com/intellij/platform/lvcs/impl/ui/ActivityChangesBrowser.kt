// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.ui

import com.intellij.history.core.HistoryPathFilter
import com.intellij.history.integration.LocalHistoryBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vcs.changes.ui.SimpleAsyncChangesTreeModel.Companion.create
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder.PATH_COMPARATOR
import com.intellij.platform.lvcs.impl.ActivityDiffData
import com.intellij.platform.lvcs.impl.ActivityFileChange
import com.intellij.platform.lvcs.impl.DirectoryDiffMode
import com.intellij.platform.lvcs.impl.settings.ActivityViewApplicationSettings
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.ui.ScrollableContentBorder
import com.intellij.ui.Side
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.util.ui.NamedColorUtil
import org.jetbrains.annotations.Nls
import java.awt.event.ActionListener
import java.util.Comparator.comparing
import javax.swing.JTree
import javax.swing.text.JTextComponent
import javax.swing.tree.DefaultTreeModel

internal class ActivityChangesBrowser(private val project: Project, private val isSwitchingDiffModeAllowed: Boolean, private val searchField: JTextComponent) : AsyncChangesBrowserBase(project, false, false), Disposable {
  private var diffData: ActivityDiffData? = null

  init {
    init()

    hideViewerBorder()
    ScrollableContentBorder.setup(viewerScrollPane, Side.TOP)
  }

  override val changesTreeModel: AsyncChangesTreeModel
    get() {
      return create { grouping -> TreeModelBuilder(myProject, grouping).buildTreeModel(diffData) }
    }

  private fun TreeModelBuilder.buildTreeModel(activityDiffData: ActivityDiffData?): DefaultTreeModel {
    if (activityDiffData == null) return build()

    val changesList = activityDiffData.presentableChanges
    val sortedChangesList = changesList.sortedWith(comparing(PresentableChange::getFilePath, PATH_COMPARATOR))
    val matcher = HistoryPathFilter.create(searchField.text, project)?.matcher
    for (presentableChange in sortedChangesList) {
      val filePath = presentableChange.filePath
      val filePathNode = if (filePath.isDirectory) PresentableDirectoryChangeNode(presentableChange, matcher) else PresentableChangeNode(presentableChange, matcher)
      insertChangeNode(filePath, myRoot, filePathNode)
    }
    if (isSwitchingDiffModeAllowed && activityDiffData.isSingleSelection && sortedChangesList.isNotEmpty()) {
      insertSubtreeRoot(LinkNode(getDiffModeText(activityDiffData), getSwitchDiffModeLink(activityDiffData)) {
        switchDiffMode(activityDiffData)
      })
    }
    return build()
  }

  internal fun loadingStarted() {
    viewer.setEmptyText(LocalHistoryBundle.message("activity.empty.text.loading"))
  }

  internal fun loadingFinished(data: ActivityDiffData?) {
    updateEmptyText(data)
    diffData = data
    viewer.rebuildTree()
  }

  private fun updateEmptyText(data: ActivityDiffData?) {
    val emptyText = if (data == null) LocalHistoryBundle.message("activity.browser.empty.text.no.selection") else LocalHistoryBundle.message("activity.browser.empty.text")
    viewer.setEmptyText(emptyText)
    if (isSwitchingDiffModeAllowed && data?.isSingleSelection == true) {
      viewer.emptyText.appendLine(getSwitchDiffModeLink(data), GRAY_LINK_ATTRIBUTES, ActionListener { switchDiffMode(data) })
    }
  }

  private fun switchDiffMode(activityDiffData: ActivityDiffData) {
    service<ActivityViewApplicationSettings>().diffMode = activityDiffData.diffMode.opposite()
  }

  override fun getDiffRequestProducer(userObject: Any): ChangeDiffRequestChain.Producer? {
    val activityFileChange = userObject as? ActivityFileChange ?: return null
    return activityFileChange.createProducer(myProject)
  }

  override fun uiDataSnapshot(sink: DataSink) {
    super.uiDataSnapshot(sink)
    sink[ActivityViewDataKeys.SELECTED_DIFFERENCES] =
      VcsTreeModelData.selected(myViewer).iterateUserObjects(PresentableChange::class.java)
  }

  override fun createToolbarActions(): List<AnAction> {
    return super.createToolbarActions() + ActionManager.getInstance().getAction("ActivityView.ChangesBrowser.Toolbar")
  }

  override fun createPopupMenuActions(): List<AnAction> {
    return super.createPopupMenuActions() + ActionManager.getInstance().getAction("ActivityView.ChangesBrowser.Popup")
  }

  override fun dispose() = shutdown()
}

private open class PresentableChangeNode(presentableChange: PresentableChange, private val activityMatcher: MinusculeMatcher?)
  : AbstractChangesBrowserFilePathNode<PresentableChange>(presentableChange, presentableChange.fileStatus) {
  override fun filePath(userObject: PresentableChange): FilePath = userObject.filePath

  override fun render(tree: JTree, renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    super.render(tree, renderer, selected, expanded, hasFocus)
    val speedSearch = TreeSpeedSearch.getSupply(tree)
    if (speedSearch?.isPopupActive != true) {
      val matchedFragments = activityMatcher?.matchingFragments(renderer.getCharSequence(true).toString())
      SpeedSearchUtil.applySpeedSearchHighlighting(renderer, matchedFragments, selected)
    }
  }
}

private class PresentableDirectoryChangeNode(presentableChange: PresentableChange, matcher: MinusculeMatcher?)
  : ChangesBrowserNode.NodeWithFilePath, PresentableChangeNode(presentableChange, matcher) {
  override fun getNodeFilePath(): FilePath = filePath(getUserObject())
}

private val GRAY_LINK_ATTRIBUTES = SimpleTextAttributes(SimpleTextAttributes.STYLE_UNDERLINE, NamedColorUtil.getInactiveTextColor())

private class LinkNode(private val text: @Nls String, private val linkText: @Nls String, private val onClick: () -> Unit) :
  ChangesBrowserNode<String>(text) {
  init {
    markAsHelperNode()
  }

  override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    renderer.append("$text  ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    renderer.append(linkText, GRAY_LINK_ATTRIBUTES, Runnable { onClick() })
  }
}

@Nls
private fun DirectoryDiffMode.presentation() = when (this) {
  DirectoryDiffMode.WithLocal -> LocalHistoryBundle.message("activity.browser.diff.mode.presentation.local")
  DirectoryDiffMode.WithNext -> LocalHistoryBundle.message("activity.browser.diff.mode.presentation.next")
}

private fun DirectoryDiffMode.opposite() = when (this) {
  DirectoryDiffMode.WithLocal -> DirectoryDiffMode.WithNext
  DirectoryDiffMode.WithNext -> DirectoryDiffMode.WithLocal
}

private fun getDiffModeText(diffData: ActivityDiffData): @Nls String {
  return LocalHistoryBundle.message("activity.browser.diff.mode.text", diffData.diffMode.presentation())
}

private fun getSwitchDiffModeLink(diffData: ActivityDiffData): @Nls String {
  return LocalHistoryBundle.message("activity.browser.diff.mode.link", diffData.diffMode.opposite().presentation())
}