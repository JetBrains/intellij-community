// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.ide.actions.searcheverywhere.SearchRestartReason
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal class SearchEverywhereMlSessionService {
  companion object {
    internal const val RECORDER_CODE = "MLSE"

    @JvmStatic
    fun getInstance(): SearchEverywhereMlSessionService =
      ApplicationManager.getApplication().getService(SearchEverywhereMlSessionService::class.java)
  }

  private val sessionIdCounter = AtomicInteger()
  private var activeSession: AtomicReference<SearchEverywhereMLSearchSession?> = AtomicReference()

  private val experiment: SearchEverywhereMlExperiment = SearchEverywhereMlExperiment()

  fun shouldOrderByML(): Boolean = experiment.shouldOrderByMl()

  fun getCurrentSession(): SearchEverywhereMLSearchSession? {
    if (experiment.isAllowed) {
      return activeSession.get()
    }
    return null
  }

  fun onSessionStarted(project: Project?) {
    if (experiment.isAllowed) {
      activeSession.updateAndGet { SearchEverywhereMLSearchSession(project, sessionIdCounter.incrementAndGet()) }
    }
  }

  fun onSearchRestart(project: Project?,
                      tabId: String,
                      reason: SearchRestartReason,
                      keysTyped: Int,
                      backspacesTyped: Int,
                      textLength: Int,
                      previousElementsProvider: () -> List<SearchEverywhereFoundElementInfo>) {
    if (experiment.isAllowed) {
      getCurrentSession()?.onSearchRestart(project, previousElementsProvider, reason, tabId, keysTyped, backspacesTyped, textLength)
    }
  }

  fun onItemSelected(project: Project?, indexes: IntArray, closePopup: Boolean, elementsProvider: () -> List<SearchEverywhereFoundElementInfo>) {
    if (experiment.isAllowed) {
      getCurrentSession()?.onItemSelected(project, experiment, indexes, closePopup, elementsProvider)
    }
  }

  fun onSearchFinished(project: Project?, elementsProvider: () -> List<SearchEverywhereFoundElementInfo>) {
    if (experiment.isAllowed) {
      getCurrentSession()?.onSearchFinished(project, experiment, elementsProvider)
    }
  }

  fun onDialogClose() {
    activeSession.updateAndGet { null }
  }
}