// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestOnlyProblems") // KTIJ-19938

package com.intellij.lang.documentation.ide.impl

import com.intellij.lang.documentation.ContentUpdater
import com.intellij.lang.documentation.DocumentationData
import com.intellij.lang.documentation.DocumentationTarget
import com.intellij.lang.documentation.ide.DocumentationBrowserFacade
import com.intellij.lang.documentation.ide.ui.DocumentationUI
import com.intellij.lang.documentation.ide.ui.ScrollingPosition
import com.intellij.lang.documentation.ide.ui.UISnapshot
import com.intellij.lang.documentation.impl.DocumentationRequest
import com.intellij.lang.documentation.impl.InternalLinkResult
import com.intellij.lang.documentation.impl.computeDocumentationAsync
import com.intellij.model.Pointer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEntry
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.util.containers.Stack
import com.intellij.util.lateinitVal
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn

internal class DocumentationBrowser private constructor(
  private val project: Project
) : DocumentationBrowserFacade, Disposable {

  private val cs = CoroutineScope(SupervisorJob())

  @Volatile // written from EDT, read from any thread
  private lateinit var state: BrowserState

  private val stateListeners = ArrayList<BrowserStateListener>(2)
  private val backStack = Stack<HistorySnapshot>()
  private val forwardStack = Stack<HistorySnapshot>()

  override fun dispose() {
    cs.cancel()
    stateListeners.clear()
    backStack.clear()
    forwardStack.clear()
  }

  var ui: DocumentationUI by lateinitVal()

  override val targetPointer: Pointer<out DocumentationTarget> get() = state.request.targetPointer

  private fun setState(state: BrowserState, byLink: Boolean) {
    EDT.assertIsEdt()
    this.state = state
    fireStateUpdate(state, byLink)
  }

  private fun fireStateUpdate(state: BrowserState, byLink: Boolean) {
    stateListeners.map { listener ->
      listener.stateChanged(state.request, state.result, byLink)
    }
  }

  fun addStateListener(listener: BrowserStateListener): Disposable {
    EDT.assertIsEdt()
    stateListeners.add(listener)
    listener.stateChanged(state.request, state.result, byLink = false)
    return Disposable {
      EDT.assertIsEdt()
      stateListeners.remove(listener)
    }
  }

  fun resetBrowser(request: DocumentationRequest) {
    cs.coroutineContext.cancelChildren()
    cs.launch(Dispatchers.EDT) {
      backStack.clear()
      forwardStack.clear()
      browseDocumentation(request, byLink = false)
    }
  }

  override fun reload() {
    cs.coroutineContext.cancelChildren()
    cs.launch(Dispatchers.EDT) {
      browseDocumentation(state.request, false)
    }
  }

  private fun browseDocumentation(request: DocumentationRequest, byLink: Boolean) {
    setState(BrowserState(request, cs.computeDocumentationAsync(request.targetPointer)), byLink)
  }

  fun navigateByLink(url: String) {
    EDT.assertIsEdt()
    cs.coroutineContext.cancelChildren()
    cs.launch(Dispatchers.EDT + ModalityState.current().asContextElement(), start = CoroutineStart.UNDISPATCHED) {
      handleLink(url)
    }
  }

  private suspend fun handleLink(url: String) {
    EDT.assertIsEdt()
    val targetPointer = state.request.targetPointer
    val internalResult = try {
      handleLink(project, targetPointer, url)
    }
    catch (e: IndexNotReadyException) {
      return // normal situation, nothing to do
    }
    when (internalResult) {
      is OrderEntry -> if (internalResult.isValid) {
        ProjectSettingsService.getInstance(project).openLibraryOrSdkSettings(internalResult)
      }
      InternalLinkResult.InvalidTarget -> {
        // TODO ? target was invalidated
      }
      InternalLinkResult.CannotResolve -> {
        @Suppress("ControlFlowWithEmptyBody")
        if (!openUrl(project, targetPointer, url)) {
          // TODO ? can't resolve link to target & nobody can open the link
        }
      }
      is InternalLinkResult.Request -> {
        backStack.push(historySnapshot())
        forwardStack.clear()
        browseDocumentation(internalResult.request, byLink = true)
      }
      is InternalLinkResult.Updater -> {
        handleContentUpdates(internalResult.updater)
      }
    }
  }

  private suspend fun handleContentUpdates(updater: ContentUpdater) {
    val updates: Flow<String> = updater.contentUpdates(ui.editorPane.text)
    updates
      .flowOn(Dispatchers.IO) // run flow in IO
      .collectLatest { // handle results in EDT
        ui.update(it, ScrollingPosition.Keep)
      }
  }

  fun currentExternalUrl(): String? {
    EDT.assertIsEdt()
    val result = state.result
    if (!result.isCompleted || result.isCancelled) return null
    @Suppress("EXPERIMENTAL_API_USAGE")
    return result.getCompleted()?.externalUrl
  }

  private class HistorySnapshot(
    val state: BrowserState,
    val ui: UISnapshot,
  )

  private class BrowserState(
    val request: DocumentationRequest,
    val result: Deferred<DocumentationData?>,
  )

  private fun historySnapshot(): HistorySnapshot {
    EDT.assertIsEdt()
    return HistorySnapshot(
      state = state,
      ui = ui.uiSnapshot(),
    )
  }

  private fun restore(snapshot: HistorySnapshot) {
    EDT.assertIsEdt()
    restore(snapshot.state)
    snapshot.ui.invoke()
  }

  private fun restore(state: BrowserState) {
    cs.coroutineContext.cancelChildren()
    val result = state.result
    if (result.isCompleted && !result.isCancelled) {
      setState(state, false)
    }
    else {
      // This can happen in the following scenario:
      // 1. Show doc.
      // 2. Click a link.
      // 3. Invoke the Back action during "Fetching..." message.
      //    At this point the request from link is cancelled, but stored in history.
      // 4. Invoke the Forward action.
      //    Here we reload that cancelled request again
      browseDocumentation(state.request, byLink = false)
    }
  }

  val history: DocumentationHistory = object : DocumentationHistory {

    override fun canBackward(): Boolean {
      return !backStack.isEmpty()
    }

    override fun backward() {
      forwardStack.push(historySnapshot())
      restore(backStack.pop())
    }

    override fun canForward(): Boolean {
      return !forwardStack.isEmpty()
    }

    override fun forward() {
      backStack.push(historySnapshot())
      restore(forwardStack.pop())
    }
  }

  companion object {

    fun createBrowser(project: Project, initialRequest: DocumentationRequest): DocumentationBrowser {
      return createBrowserAndGetJob(project, initialRequest).first
    }

    fun createBrowserAndGetJob(project: Project, initialRequest: DocumentationRequest): Pair<DocumentationBrowser, Job> {
      val browser = DocumentationBrowser(project)
      val initialJob = browser.cs.computeDocumentationAsync(initialRequest.targetPointer)
      browser.state = BrowserState(initialRequest, initialJob)
      return Pair(browser, initialJob)
    }
  }
}
