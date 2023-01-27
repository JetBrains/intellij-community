// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.toolwindow

import com.intellij.collaboration.async.disposingMainScope
import com.intellij.openapi.util.Disposer
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
fun <T : ReviewTab, C : ReviewToolwindowProjectContext> manageReviewToolwindowTabs(
  contentManager: ContentManager,
  projectContext: StateFlow<C?>,
  reviewTabsController: ReviewTabsController<T>,
  tabComponentFactory: ReviewTabsComponentFactory<T, C>
) {
  ReviewToolwindowTabsManager(contentManager, projectContext, reviewTabsController, tabComponentFactory)
}

private class ReviewToolwindowTabsManager<T : ReviewTab, C : ReviewToolwindowProjectContext>(
  private val contentManager: ContentManager,
  private val projectContext: StateFlow<C?>,
  private val reviewTabsController: ReviewTabsController<T>,
  private val tabComponentFactory: ReviewTabsComponentFactory<T, C>
) {
  private val cs = contentManager.disposingMainScope()

  init {
    contentManager.addDataProvider {
      when {
        ReviewToolwindowDataKeys.REVIEW_TABS_CONTROLLER.`is`(it) -> reviewTabsController
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
  }

  private fun selectExistedTabOrCreate(context: C, reviewTab: T) {
    val existedTab = contentManager.contents.find { it.getUserData(ReviewToolwindowUserDataKeys.REVIEW_TAB)?.id == reviewTab.id }
    if (existedTab != null) {
      contentManager.setSelectedContent(existedTab)
    }
    else {
      val tabContent = createTabContent(context, reviewTab)
      contentManager.addContent(tabContent)
      contentManager.setSelectedContent(tabContent)
    }
  }

  private fun closeExistedTabAndCreateNew(context: C, reviewTab: T) {
    val existedTab = contentManager.contents.find { it.getUserData(ReviewToolwindowUserDataKeys.REVIEW_TAB)?.id == reviewTab.id }
    if (existedTab != null) {
      contentManager.removeContent(existedTab, true)
    }

    val reviewDetailsContent = createTabContent(context, reviewTab)
    contentManager.addContent(reviewDetailsContent)
    contentManager.setSelectedContent(reviewDetailsContent)
  }

  private fun createReviewListContent(context: C): Content = createDisposableContent { content, contentCs ->
    content.isCloseable = false
    content.displayName = context.projectName
    content.component = tabComponentFactory.createReviewListComponent(contentCs, context)
  }


  private fun createTabContent(context: C, reviewTab: T): Content = createDisposableContent { content, contentCs ->
    content.isCloseable = true
    content.displayName = reviewTab.displayName
    content.description = "${context.projectName}: ${reviewTab.displayName}"

    content.component = tabComponentFactory.createTabComponent(contentCs, context, reviewTab)

    content.putUserData(ReviewToolwindowUserDataKeys.REVIEW_TAB, reviewTab)
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
}