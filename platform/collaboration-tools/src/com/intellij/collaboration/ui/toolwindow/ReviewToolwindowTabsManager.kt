// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.toolwindow

import com.intellij.collaboration.async.disposingMainScope
import com.intellij.collaboration.ui.codereview.list.ReviewListViewModel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.*
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

/**
 * Manages review toolwindow tabs and their content.
 * If [reviewToolwindowViewModel.projectContext] is [null] UI for acquiring this context is shown (by [ReviewTabsComponentFactory.createEmptyTabContent]).
 *
 * When [reviewToolwindowViewModel.projectContext] appears Review List will be shown (by [ReviewTabsComponentFactory.createReviewListComponentDescriptor]),
 * and requests in [reviewTabsController] will be handled, according to described in [ReviewTab] [ReviewTab.id] logic.
 * So, new tabs will be shown using [ReviewTabsComponentFactory.createTabComponent].
 *
 * @see ReviewToolwindowDataKeys
 */
@ApiStatus.Experimental
fun <T : ReviewTab, C : ReviewToolwindowProjectContext> manageReviewToolwindowTabs(
  cs: CoroutineScope,
  toolwindow: ToolWindow,
  reviewToolwindowViewModel: ReviewToolwindowViewModel<C>,
  reviewTabsController: ReviewTabsController<T>,
  tabComponentFactory: ReviewTabsComponentFactory<T, C>
) {
  ReviewToolwindowTabsManager(cs, toolwindow, reviewToolwindowViewModel, reviewTabsController, tabComponentFactory)
}

private class ReviewToolwindowTabsManager<T : ReviewTab, C : ReviewToolwindowProjectContext>(
  parentCs: CoroutineScope,
  private val toolwindow: ToolWindow,
  private val reviewToolwindowViewModel: ReviewToolwindowViewModel<C>,
  private val reviewTabsController: ReviewTabsController<T>,
  private val tabComponentFactory: ReviewTabsComponentFactory<T, C>
) {
  private val contentManager = toolwindow.contentManager
  private val projectContext = reviewToolwindowViewModel.projectContext
  private val cs = parentCs.childScope(Dispatchers.Main)

  private val tabsSelector = object : ReviewToolwindowTabsContentSelector<T> {
    override suspend fun selectTab(reviewTab: T): Content? {
      val currentContext = projectContext.value ?: return null
      return selectExistedTabOrCreate(currentContext, reviewTab)
    }
  }

  init {
    toolwindow.refreshReviewListOnSelection { content ->
      val reviewListVm = content.getUserData(REVIEW_LIST_VIEW_MODEL) ?: return@refreshReviewListOnSelection

      reviewListVm.refresh()
    }

    contentManager.addDataProvider {
      when {
        ReviewToolwindowDataKeys.REVIEW_TABS_CONTROLLER.`is`(it) -> reviewTabsController
        ReviewToolwindowDataKeys.REVIEW_TABS_CONTENT_SELECTOR.`is`(it) -> tabsSelector
        ReviewToolwindowDataKeys.REVIEW_PROJECT_CONTEXT.`is`(it) -> projectContext.value
        ReviewToolwindowDataKeys.REVIEW_TOOLWINDOW_VM.`is`(it) -> reviewToolwindowViewModel
        else -> null
      }
    }

    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      projectContext.collect { contextState ->
        if (contextState == null) {
          setEmptyContent()
          return@collect
        }

        contentManager.removeAllContents(true)
        val newContent = createReviewListContent(contextState)
        contentManager.addContent(newContent)
        contentManager.setSelectedContent(newContent)
      }
    }

    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      reviewTabsController.openReviewTabRequest.collect { reviewTab ->
        val currentContext = projectContext.value ?: return@collect

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

  private fun selectExistedTabOrCreate(context: C, reviewTab: T): Content {
    val existedTab = findTabContent(reviewTab)
    if (existedTab != null) {
      contentManager.setSelectedContent(existedTab)
      return existedTab
    }
    else {
      val tabContent = createTabContent(context, reviewTab)
      contentManager.addContent(tabContent)
      contentManager.setSelectedContent(tabContent)
      return tabContent
    }
  }

  private fun closeExistedTabAndCreateNew(context: C, reviewTab: T) {
    val existedTab = findTabContent(reviewTab)
    if (existedTab != null) {
      contentManager.removeContent(existedTab, true)
    }

    val reviewDetailsContent = createTabContent(context, reviewTab)
    contentManager.addContent(reviewDetailsContent)
    contentManager.setSelectedContent(reviewDetailsContent)
  }

  private fun findTabContent(reviewTab: T): Content? {
    return contentManager.contents.find { it.getUserData(REVIEW_TAB)?.id == reviewTab.id }
  }

  private fun createReviewListContent(context: C): Content = createDisposableContent { content, contentCs ->
    content.isCloseable = false
    content.displayName = context.projectName

    val reviewListDescriptor = tabComponentFactory.createReviewListComponentDescriptor(contentCs, context)
    content.component = reviewListDescriptor.component
    content.putUserData(REVIEW_LIST_VIEW_MODEL, reviewListDescriptor.viewModel)
  }

  private fun createTabContent(context: C, reviewTab: T): Content = createDisposableContent { content, contentCs ->
    content.isCloseable = true
    content.displayName = reviewTab.displayName
    content.description = "${context.projectName}: ${reviewTab.description}"

    content.component = tabComponentFactory.createTabComponent(contentCs, context, reviewTab)

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
    return factory.createContent(null, null, false).apply {
      val disposable = Disposer.newDisposable()
      setDisposer(disposable)
      modifier(this, disposable.disposingMainScope())
    }
  }

  companion object {
    @JvmStatic
    private val REVIEW_TAB: Key<ReviewTab> = Key.create("com.intellij.collaboration.toolwindow.review.tab")

    @JvmStatic
    private val REVIEW_LIST_VIEW_MODEL: Key<ReviewListViewModel> = Key.create("com.intellij.collaboration.toolwindow.review.tab.list.vm")
  }
}