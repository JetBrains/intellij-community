// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowEx
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

fun ToolWindow.dontHideOnEmptyContent() {
  setToHideOnEmptyContent(false)
  (this as? ToolWindowEx)?.emptyText?.text = ""
}

/**
 * A helper class to reuse typesafe state mutation functionality
 */
@JvmInline
value class ReviewToolwindowTabsStateHolder<T : ReviewTab, VM : ReviewTabViewModel>(
  val tabs: MutableStateFlow<ReviewToolwindowTabs<T, VM>> = MutableStateFlow(ReviewToolwindowTabs(emptyMap(), null))
) {

  inline fun <reified _T, reified _VM> showTab(
    tab: _T,
    crossinline vmProducer: (_T) -> _VM,
    crossinline processVM: _VM.() -> Unit = {}
  ) where _T : T, _VM : VM {
    tabs.update { current ->
      val currentVm = current.tabs[tab]
      if (currentVm == null || currentVm !is _VM || !tab.reuseTabOnRequest) {
        if (currentVm is Disposable) {
          Disposer.dispose(currentVm)
        }
        val tabVm = vmProducer(tab).apply(processVM)
        current.copy(current.tabs + (tab to tabVm), tab)
      }
      else {
        currentVm.apply(processVM)
        current.copy(selectedTab = tab)
      }
    }
  }

  /**
   * Close [tabToClose] and show [tab]
   */
  inline fun <reified _T, reified _VM> showTabInstead(
    tabToClose: T,
    tab: _T,
    crossinline vmProducer: (_T) -> _VM,
    crossinline processVM: _VM.() -> Unit = {}
  ) where _T : T, _VM : VM {
    tabs.update { current ->
      val vmToClose = current.tabs[tabToClose]
      if (vmToClose != null) {
        if (vmToClose is Disposable) {
          Disposer.dispose(vmToClose)
        }
      }

      val currentVm = current.tabs[tab]
      if (currentVm == null || currentVm !is _VM || !tab.reuseTabOnRequest) {
        if (currentVm is Disposable) {
          Disposer.dispose(currentVm)
        }
        val tabVm = vmProducer(tab).apply(processVM)
        current.copy(current.tabs + (tab to tabVm) - tabToClose, tab)
      }
      else {
        currentVm.apply(processVM)
        current.copy(current.tabs - tabToClose, tab)
      }
    }
  }

  fun select(tab: T?) {
    tabs.update {
      it.copy(selectedTab = tab)
    }
  }

  fun close(tab: T) {
    tabs.update { current ->
      val currentVm = current.tabs[tab]
      if (currentVm != null) {
        if (currentVm is Disposable) {
          Disposer.dispose(currentVm)
        }
        current.copy(current.tabs - tab, null)
      }
      else {
        current
      }
    }
  }
}