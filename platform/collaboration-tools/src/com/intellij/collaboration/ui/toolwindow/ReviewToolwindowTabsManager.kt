// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.toolwindow

import com.intellij.collaboration.async.disposingMainScope
import com.intellij.collaboration.ui.codereview.list.ReviewListViewModel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.*
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * Manages review toolwindow tabs and their content.
 * If [reviewToolwindowViewModel.projectVm] is [null] UI for acquiring this context is shown (by [ReviewTabsComponentFactory.createEmptyTabContent]).
 *
 * When [reviewToolwindowViewModel.projectVm] appears Review List will be shown (by [ReviewTabsComponentFactory.createReviewListComponent]),
 * and requests in [reviewTabsController] will be handled, according to described in [ReviewTab] [ReviewTab.id] logic.
 * So, new tabs will be shown using [ReviewTabsComponentFactory.createTabComponent].
 *
 * @see ReviewToolwindowDataKeys
 */
@ApiStatus.Experimental
fun <T : ReviewTab, PVM : ReviewToolwindowProjectViewModel> manageReviewToolwindowTabs(
  cs: CoroutineScope,
  toolwindow: ToolWindow,
  reviewToolwindowViewModel: ReviewToolwindowViewModel<PVM>,
  reviewTabsController: ReviewTabsController<T>,
  tabComponentFactory: ReviewTabsComponentFactory<T, PVM>,
  tabTitle: @Nls String
) {
  ReviewToolwindowTabsManager(cs, toolwindow, reviewToolwindowViewModel, reviewTabsController, tabComponentFactory, tabTitle)
}

private class ReviewToolwindowTabsManager<T : ReviewTab, PVM : ReviewToolwindowProjectViewModel>(
  parentCs: CoroutineScope,
  private val toolwindow: ToolWindow,
  private val reviewToolwindowViewModel: ReviewToolwindowViewModel<PVM>,
  private val reviewTabsController: ReviewTabsController<T>,
  private val tabComponentFactory: ReviewTabsComponentFactory<T, PVM>,
  private val tabTitle: @Nls String
) {
  private val contentManager = toolwindow.contentManager
  private val projectVm = reviewToolwindowViewModel.projectVm
  private val cs = parentCs.childScope(Dispatchers.Main)

  private val tabsSelector = object : ReviewToolwindowTabsContentSelector<T> {
    override suspend fun selectTab(reviewTab: T): Content? {
      val currentContext = projectVm.value ?: return null
      return selectExistedTabOrCreate(currentContext, reviewTab)
    }
  }

  init {
    contentManager.addDataProvider {
      when {
        ReviewToolwindowDataKeys.REVIEW_TABS_CONTROLLER.`is`(it) -> reviewTabsController
        ReviewToolwindowDataKeys.REVIEW_TABS_CONTENT_SELECTOR.`is`(it) -> tabsSelector
        ReviewToolwindowDataKeys.REVIEW_TOOLWINDOW_PROJECT_VM.`is`(it) -> projectVm.value
        ReviewToolwindowDataKeys.REVIEW_TOOLWINDOW_VM.`is`(it) -> reviewToolwindowViewModel
        else -> null
      }
    }

    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      projectVm.collect { vm ->
        if (vm == null) {
          setEmptyContent()
          return@collect
        }

        contentManager.removeAllContents(true)
        val newContent = createReviewListContent(vm)
        contentManager.addContent(newContent)
        contentManager.setSelectedContent(newContent)
        refreshReviewListOnTabSelection(vm.listVm, contentManager, newContent)
        refreshListOnToolwindowShow(vm.listVm, toolwindow, newContent)
      }
    }

    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      reviewTabsController.openReviewTabRequest.collect { reviewTab ->
        val currentContext = projectVm.value ?: return@collect

        if (reviewTab.reuseTabOnRequest) {
          selectExistedTabOrCreate(currentContext, reviewTab)
        }
        else {
          closeExistedTabAndCreateNew(currentContext, reviewTab)
        }
      }
    }

    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      reviewTabsController.closeReviewTabRequest.collect { reviewTab ->
        val contentToClose = findTabContent(reviewTab)
        if (contentToClose != null) {
          contentManager.removeContent(contentToClose, true)

          // select review list on requested tab close
          contentManager.getContent(0)?.let { contentManager.setSelectedContent(it) }
        }
      }
    }
  }

  private fun selectExistedTabOrCreate(projectVm: PVM, reviewTab: T): Content {
    val existedTab = findTabContent(reviewTab)
    if (existedTab != null) {
      contentManager.setSelectedContent(existedTab)
      return existedTab
    }
    else {
      val tabContent = createTabContent(projectVm, reviewTab)
      contentManager.addContent(tabContent)
      contentManager.setSelectedContent(tabContent)
      return tabContent
    }
  }

  private fun closeExistedTabAndCreateNew(projectVm: PVM, reviewTab: T) {
    val existedTab = findTabContent(reviewTab)
    if (existedTab != null) {
      contentManager.removeContent(existedTab, true)
    }

    val reviewDetailsContent = createTabContent(projectVm, reviewTab)
    contentManager.addContent(reviewDetailsContent)
    contentManager.setSelectedContent(reviewDetailsContent)
  }

  private fun findTabContent(reviewTab: T): Content? {
    return contentManager.contents.find { it.getUserData(REVIEW_TAB)?.id == reviewTab.id }
  }

  private fun createReviewListContent(projectVm: PVM): Content = createDisposableContent { content, contentCs ->
    content.isCloseable = false
    content.displayName = projectVm.projectName

    content.component = tabComponentFactory.createReviewListComponent(contentCs, projectVm)
  }

  private fun createTabContent(projectVm: PVM, reviewTab: T): Content = createDisposableContent { content, contentCs ->
    content.isCloseable = true
    content.displayName = reviewTab.displayName
    content.description = "${projectVm.projectName}: ${reviewTab.description}"

    content.component = tabComponentFactory.createTabComponent(contentCs, projectVm, reviewTab)

    content.putUserData(REVIEW_TAB, reviewTab)
  }

  private fun setEmptyContent() {
    contentManager.removeAllContents(true)
    val loginContent = createDisposableContent { content, contentCs ->
      content.component = tabComponentFactory.createEmptyTabContent(contentCs)
      content.isCloseable = false
    }
    contentManager.addContent(loginContent)
    contentManager.setSelectedContent(loginContent)
  }


  private fun createDisposableContent(modifier: (Content, CoroutineScope) -> Unit): Content {
    val factory = ContentFactory.getInstance()
    return factory.createContent(null, tabTitle, false).apply {
      val disposable = Disposer.newDisposable()
      setDisposer(disposable)
      modifier(this, disposable.disposingMainScope())
    }
  }

  companion object {
    @JvmStatic
    private val REVIEW_TAB: Key<ReviewTab> = Key.create("com.intellij.collaboration.toolwindow.review.tab")
  }
}

private fun refreshReviewListOnTabSelection(listVm: ReviewListViewModel, contentManager: ContentManager, content: Content) {
  val listener = object : ContentManagerListener {
    override fun selectionChanged(event: ContentManagerEvent) {
      if (event.operation == ContentManagerEvent.ContentOperation.add && event.content === content) {
        // tab selected
        listVm.refresh()
      }
    }
  }
  contentManager.addContentManagerListener(listener)
  Disposer.register(content) {
    contentManager.removeContentManagerListener(listener)
  }
}

private fun refreshListOnToolwindowShow(listVm: ReviewListViewModel, toolwindow: ToolWindow, content: Content) {
  toolwindow.project.messageBus.connect(content)
    .subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      override fun toolWindowShown(shownToolwindow: ToolWindow) {
        if (shownToolwindow.id == toolwindow.id) {
          val selectedContent = shownToolwindow.contentManager.selectedContent
          if (selectedContent === content) {
            listVm.refresh()
          }
        }
      }
    })
}