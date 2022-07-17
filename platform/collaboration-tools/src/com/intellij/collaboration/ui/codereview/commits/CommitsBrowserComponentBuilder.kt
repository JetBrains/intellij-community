// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview.commits

import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.*
import com.intellij.ui.components.JBList
import com.intellij.util.containers.MultiMap
import com.intellij.util.ui.ListUiUtil
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.ui.details.commit.CommitDetailsPanel
import com.intellij.vcs.log.ui.frame.CommitPresentationUtil
import javax.swing.*

class CommitsBrowserComponentBuilder(private val project: Project,
                                     private val commitsModel: SingleValueModel<List<VcsCommitMetadata>>) {
  private var commitRenderer: ListCellRenderer<VcsCommitMetadata> = CommitsListCellRenderer()
  private var showCommitDetailsPanel = true
  private var onCommitSelected: (VcsCommitMetadata?) -> Unit = { /* do nothing */ }
  private var emptyListText: String? = null
  private var popupActions: Pair<ActionGroup, String/* place */>? = null

  fun setCustomCommitRenderer(customRenderer: ListCellRenderer<VcsCommitMetadata>): CommitsBrowserComponentBuilder {
    commitRenderer = customRenderer
    return this
  }

  fun setEmptyCommitListText(@NlsContexts.StatusText emptyText: String): CommitsBrowserComponentBuilder {
    this.emptyListText = emptyText
    return this
  }

  fun installPopupActions(actionGroup: ActionGroup, place: String): CommitsBrowserComponentBuilder {
    popupActions = actionGroup to place
    return this
  }

  fun showCommitDetails(show: Boolean): CommitsBrowserComponentBuilder {
    showCommitDetailsPanel = show
    return this
  }

  fun onCommitSelected(onCommitSelected: (VcsCommitMetadata?) -> Unit): CommitsBrowserComponentBuilder {
    this.onCommitSelected = onCommitSelected
    return this
  }

  fun create(): JComponent {
    val commitsListModel = CollectionListModel(commitsModel.value)

    val commitsList = JBList(commitsListModel).apply {
      emptyListText?.let { emptyText.text = it }
      selectionMode = ListSelectionModel.SINGLE_SELECTION
      val renderer = commitRenderer
      cellRenderer = renderer
      if (renderer is JComponent)
        UIUtil.putClientProperty(this, UIUtil.NOT_IN_HIERARCHY_COMPONENTS, listOf(renderer))
    }.also { list ->
      ScrollingUtil.installActions(list)
      ListUiUtil.Selection.installSelectionOnFocus(list)
      ListUiUtil.Selection.installSelectionOnRightClick(list)

      ListSpeedSearch(list) { commit -> commit.subject }

      popupActions?.let { pair ->
        PopupHandler.installSelectionListPopup(list, pair.first, pair.second)
      }
    }

    commitsModel.addAndInvokeListener {
      val currentList = commitsListModel.toList()
      val newList = commitsModel.value
      if (currentList != newList) {
        val selectedCommit = commitsList.selectedValue
        commitsListModel.replaceAll(newList)
        commitsList.setSelectedValue(selectedCommit, true)
      }
    }

    val commitDetailsModel = SingleValueModel<VcsCommitMetadata?>(null)
    val commitDetailsComponent = if (showCommitDetailsPanel) createCommitDetailsComponent(commitDetailsModel) else null

    commitsList.addListSelectionListener { e ->
      if (e.valueIsAdjusting) return@addListSelectionListener
      onCommitSelected(commitsList.selectedValue)
    }

    val commitsScrollPane = ScrollPaneFactory.createScrollPane(commitsList, true).apply {
      isOpaque = false
      viewport.isOpaque = false
      horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    }

    val commitsBrowserComponent = OnePixelSplitter(true, "Github.PullRequest.Commits.Browser", 0.7f).apply {
      firstComponent = commitsScrollPane
      secondComponent = commitDetailsComponent

      UIUtil.putClientProperty(this, COMMITS_LIST_KEY, commitsList)
    }

    commitsList.addListSelectionListener { e ->
      if (e.valueIsAdjusting) return@addListSelectionListener

      val index = commitsList.selectedIndex
      commitDetailsModel.value = if (index != -1) commitsListModel.getElementAt(index) else null
      commitsBrowserComponent.validate()
      commitsBrowserComponent.repaint()
      if (index != -1) ScrollingUtil.ensureRangeIsVisible(commitsList, index, index)
    }

    return commitsBrowserComponent
  }

  private fun createCommitDetailsComponent(model: SingleValueModel<VcsCommitMetadata?>): JComponent {

    val commitDetailsPanel = CommitDetailsPanel()
    val scrollpane = ScrollPaneFactory.createScrollPane(commitDetailsPanel, true).apply {
      isVisible = false
      isOpaque = false
      viewport.isOpaque = false
      horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    }

    model.addAndInvokeListener {
      val commit = model.value
      if (commit != null) {
        val hashAndAuthor = CommitPresentationUtil.formatCommitHashAndAuthor(commit.id, commit.author, commit.authorTime, commit.committer,
                                                                             commit.commitTime)

        val presentation = object : CommitPresentationUtil.CommitPresentation(project, commit.root, commit.fullMessage, hashAndAuthor,
                                                                              MultiMap.empty()) {
          override fun getText(): String {
            val separator = myRawMessage.indexOf("\n\n")
            val subject = if (separator > 0) myRawMessage.substring(0, separator) else myRawMessage
            val description = myRawMessage.substring(subject.length)
            if (subject.contains("\n")) {
              // subject has new lines => that is not a subject
              return myRawMessage
            }

            return """<b>$subject</b><br/><br/>$description"""
          }
        }
        commitDetailsPanel.setCommit(presentation)
      }
      scrollpane.isVisible = commit != null
    }
    return scrollpane
  }

  companion object {
    val COMMITS_LIST_KEY = Key.create<JList<VcsCommitMetadata>>("COMMITS_LIST")
  }
}