// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.ide.actions.searcheverywhere.SearchRestartReason
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal class SearchEverywhereSessionService {
  companion object {
    @JvmStatic
    fun getInstance(): SearchEverywhereSessionService =
      ApplicationManager.getApplication().getService(SearchEverywhereSessionService::class.java)
  }

  private val sessionIdCounter = AtomicInteger()
  private var activeSession: AtomicReference<SearchEverywhereMLSearchSession?> = AtomicReference()

  @Synchronized
  fun getCurrentSession(): SearchEverywhereMLSearchSession? {
    return activeSession.get()
  }

  @Synchronized
  fun onSessionStarted(project: Project?) {
    activeSession.updateAndGet { SearchEverywhereMLSearchSession(project, sessionIdCounter.incrementAndGet()) }
  }

  fun onSearchRestart(tabId: String,
                      reason: SearchRestartReason,
                      keysTyped: Int,
                      backspacesTyped: Int,
                      textLength: Int,
                      previousElementsProvider: () -> List<SearchEverywhereFoundElementInfo>) {
    getCurrentSession()?.onSearchRestart(previousElementsProvider, reason, tabId, keysTyped, backspacesTyped, textLength)
  }

  fun onItemSelected(indexes: IntArray, closePopup: Boolean, elementsProvider: () -> List<SearchEverywhereFoundElementInfo>) {
    getCurrentSession()?.onItemSelected(indexes, closePopup, elementsProvider)
  }

  fun onSearchFinished(elementsProvider: () -> List<SearchEverywhereFoundElementInfo>) {
    getCurrentSession()?.onSearchFinished(elementsProvider)
  }

  @Synchronized
  fun onDialogClose() {
    activeSession.updateAndGet { null }
  }
}