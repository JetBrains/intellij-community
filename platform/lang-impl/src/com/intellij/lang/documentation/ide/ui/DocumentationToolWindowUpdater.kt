// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestOnlyProblems") // KTIJ-19938

package com.intellij.lang.documentation.ide.ui

import com.intellij.ide.DataManager
import com.intellij.ide.IdeEventQueue
import com.intellij.lang.documentation.ide.actions.documentationTargets
import com.intellij.lang.documentation.ide.impl.DocumentationBrowser
import com.intellij.lang.documentation.impl.documentationRequest
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.util.ui.EDT
import com.intellij.util.ui.update.Activatable
import kotlinx.coroutines.*
import java.lang.Runnable
import kotlin.coroutines.resume

internal class DocumentationToolWindowUpdater(
  private val project: Project,
  private val browser: DocumentationBrowser,
) : Activatable {

  private val cs = CoroutineScope(CoroutineName("DocumentationPreviewToolWindowUI"))

  override fun showNotify() {
    toggleAutoUpdate(true)
  }

  override fun hideNotify() {
    toggleAutoUpdate(false)
  }

  private var paused: Boolean = false

  fun pause(): Disposable {
    EDT.assertIsEdt()
    paused = true
    cs.coroutineContext.cancelChildren()
    return Disposable {
      EDT.assertIsEdt()
      paused = false
    }
  }

  private val autoUpdateRequest = Runnable(::requestAutoUpdate)
  private var autoUpdateDisposable: Disposable? = null

  private fun toggleAutoUpdate(state: Boolean) {
    if (state) {
      if (autoUpdateDisposable != null) {
        return
      }
      val disposable = Disposer.newDisposable("documentation auto updater")
      IdeEventQueue.getInstance().addActivityListener(autoUpdateRequest, disposable)
      autoUpdateDisposable = disposable
    }
    else {
      autoUpdateDisposable?.let(Disposer::dispose)
      autoUpdateDisposable = null
    }
  }

  private fun requestAutoUpdate() {
    cs.coroutineContext.cancelChildren()
    if (paused) {
      return
    }
    cs.launch {
      delay(DEFAULT_UI_RESPONSE_TIMEOUT)
      autoUpdate()
    }
  }

  private suspend fun autoUpdate() {
    val dataContext = focusDataContext()
    if (dataContext.getData(CommonDataKeys.PROJECT) != project) {
      return
    }
    val request = readAction {
      documentationTargets(dataContext).singleOrNull()?.documentationRequest()
    }
    if (request != null) {
      browser.resetBrowser(request)
    }
  }

  private suspend fun focusDataContext(): DataContext = suspendCancellableCoroutine {
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown({
      @Suppress("DEPRECATION")
      val dataContextFromFocusedComponent = DataManager.getInstance().dataContext
      val uiSnapshot = Utils.wrapToAsyncDataContext(dataContextFromFocusedComponent)
      val asyncDataContext = AnActionEvent.getInjectedDataContext(uiSnapshot)
      it.resume(asyncDataContext)
    }, ModalityState.any())
  }
}
