// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.toolwindow

import com.intellij.collaboration.async.cancelledWith
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.codereview.list.ReviewListViewModel
import com.intellij.openapi.actionSystem.EdtNoGetDataProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.content.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * Manages review toolwindow tabs and their content.
 * If [ReviewToolwindowViewModel.projectVm] in [reviewToolwindowViewModel] is null UI for acquiring this context is shown (by [ReviewTabsComponentFactory.createEmptyTabContent]).
 *
 * When [ReviewToolwindowViewModel.projectVm] appears Review List will be shown (by [ReviewTabsComponentFactory.createReviewListComponent]),
 * and [ReviewToolwindowProjectViewModel.tabs] will be managed, according to described in [ReviewTab] [ReviewTab.id] logic.
 * So, new tabs will be shown using [ReviewTabsComponentFactory.createTabComponent].
 *
 * @see ReviewToolwindowDataKeys
 */
@ApiStatus.Experimental
fun <T : ReviewTab, TVM : ReviewTabViewModel, PVM : ReviewToolwindowProjectViewModel<T, TVM>> manageReviewToolwindowTabs(
  cs: CoroutineScope,
  toolwindow: ToolWindow,
  reviewToolwindowViewModel: ReviewToolwindowViewModel<PVM>,
  tabComponentFactory: ReviewTabsComponentFactory<TVM, PVM>,
  tabTitle: @Nls String
) {
  ReviewToolwindowTabsManager(cs, toolwindow, reviewToolwindowViewModel, tabComponentFactory, tabTitle)
}

private class ReviewToolwindowTabsManager<
  T : ReviewTab,
  TVM : ReviewTabViewModel,
  PVM : ReviewToolwindowProjectViewModel<T, TVM>
  >(
  parentCs: CoroutineScope,
  private val toolwindow: ToolWindow,
  private val reviewToolwindowViewModel: ReviewToolwindowViewModel<PVM>,
  private val tabComponentFactory: ReviewTabsComponentFactory<TVM, PVM>,
  private val tabTitle: @Nls String
) {
  private val contentManager = toolwindow.contentManager
  private val projectVm = reviewToolwindowViewModel.projectVm
  private val cs = parentCs.childScope(Dispatchers.Main)

  init {
    contentManager.addDataProvider(EdtNoGetDataProvider { sink ->
      sink[ReviewToolwindowDataKeys.REVIEW_TOOLWINDOW_PROJECT_VM] = projectVm.value
      sink[ReviewToolwindowDataKeys.REVIEW_TOOLWINDOW_VM] = reviewToolwindowViewModel
    })

    cs.launchNow {
      projectVm.collectLatest { vm ->
        try {
          if (vm == null) {
            val loginContent = createDisposableContent(createTabDebugName("Login")) { content, contentCs ->
              content.component = tabComponentFactory.createEmptyTabContent(contentCs)
              content.isCloseable = false
            }
            withContext(Dispatchers.EDT + NonCancellable) {
              contentManager.addContent(loginContent)
              contentManager.setSelectedContent(loginContent)
            }
          }
          else {
            manageProjectTabs(vm)
          }
        }
        catch (e: Exception) {
          withContext(NonCancellable) {
            contentManager.removeAllContents(true)
          }
        }
      }
    }
  }

  private suspend fun manageProjectTabs(projectVm: PVM) {
    val listContent = createReviewListContent(projectVm)
    withContext(NonCancellable) {
      contentManager.addContent(listContent)
      contentManager.setSelectedContent(listContent)
    }
    refreshReviewListOnTabSelection(projectVm.listVm, contentManager, listContent)
    refreshListOnToolwindowShow(projectVm.listVm, toolwindow, listContent)

    currentCoroutineContext().ensureActive()

    // required for backwards sync contentManager -> VM
    val syncListener = object : ContentManagerListener {
      override fun contentRemoved(event: ContentManagerEvent) {
        event.content.getUserData(REVIEW_TAB_KEY)?.let {
          projectVm.closeTab(it)
        }
      }

      override fun selectionChanged(event: ContentManagerEvent) {
        if(event.operation == ContentManagerEvent.ContentOperation.add) {
          event.content.getUserData(REVIEW_TAB_KEY).let {
            projectVm.selectTab(it)
          }
        }
      }
    }

    projectVm.tabs.collect { tabsState ->
      contentManager.removeContentManagerListener(syncListener)
      contentManager.contents.forEach { content ->
        if (content !== listContent) {
          val tab = content.getUserData(REVIEW_TAB_KEY)
          if (tab == null || !tabsState.tabs.containsKey(tab)) {
            contentManager.removeContent(content, true)
          }
        }
      }

      for ((tabType, tabVm) in tabsState.tabs) {
        val existing = findTabContent(tabType)
        if (existing == null || existing.getUserData(REVIEW_TAB_VM_KEY) !== tabVm) {
          closeExistingTabAndCreateNew(tabType, projectVm, tabVm)
        }
      }

      val contentToSelect = tabsState.selectedTab?.let(::findTabContent) ?: listContent
      contentManager.setSelectedContent(contentToSelect, true)
      contentManager.addContentManagerListener(syncListener)
    }
  }

  private fun findTabContent(reviewTab: T): Content? = contentManager.contents.find { it.getUserData(REVIEW_TAB_KEY) == reviewTab }

  private fun closeExistingTabAndCreateNew(tab: T, projectVm: PVM, tabVm: TVM) {
    val existingContent = findTabContent(tab)
    if (existingContent != null) {
      contentManager.removeContent(existingContent, true)
    }

    val content = createTabContent(tab, projectVm, tabVm)
    contentManager.addContent(content)
  }

  private fun createReviewListContent(projectVm: PVM): Content =
    createDisposableContent(createTabDebugName(projectVm.projectName)) { content, contentCs ->
      content.isCloseable = false
      content.displayName = projectVm.projectName

      content.component = tabComponentFactory.createReviewListComponent(contentCs, projectVm)
    }

  private fun createTabContent(tab: T, projectVm: PVM, tabVm: TVM): Content =
    createDisposableContent(createTabDebugName(tabVm.displayName)) { content, contentCs ->
      content.isCloseable = true
      content.displayName = tabVm.displayName
      content.description = "${projectVm.projectName}: ${tabVm.description}"

      content.component = tabComponentFactory.createTabComponent(contentCs, projectVm, tabVm)

      content.putUserData(REVIEW_TAB_KEY, tab)
      content.putUserData(REVIEW_TAB_VM_KEY, tabVm)
    }

  private fun createDisposableContent(debugName: String, modifier: (Content, CoroutineScope) -> Unit): Content {
    val factory = ContentFactory.getInstance()
    return factory.createContent(null, tabTitle, false).apply {
      val disposable = Disposer.newDisposable()
      setDisposer(disposable)
      modifier(this, cs.childScope(debugName).cancelledWith(disposable))
    }
  }

  private val REVIEW_TAB_KEY: Key<T> = Key.create("com.intellij.collaboration.toolwindow.review.tab")
  private val REVIEW_TAB_VM_KEY: Key<TVM> = Key.create("com.intellij.collaboration.toolwindow.review.tab.vm")
}

private fun createTabDebugName(name: String) = "Review Toolwindow Tab [$name]"

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