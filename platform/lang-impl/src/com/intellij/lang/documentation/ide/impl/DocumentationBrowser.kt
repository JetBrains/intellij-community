// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.impl

import com.intellij.codeInsight.documentation.actions.DocumentationDownloader
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.lang.documentation.ide.impl.DocumentationUsageCollector.logDownloadFinished
import com.intellij.lang.documentation.ide.ui.DocumentationUI
import com.intellij.lang.documentation.ide.ui.UISnapshot
import com.intellij.model.Pointer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEntry
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.impl.DocumentationRequest
import com.intellij.platform.backend.documentation.impl.InternalLinkResult
import com.intellij.platform.ide.documentation.DocumentationBrowserFacade
import com.intellij.util.lateinitVal
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlin.coroutines.EmptyCoroutineContext

internal class DocumentationBrowser private constructor(
  private val project: Project,
  initialPage: DocumentationPage,
) : DocumentationBrowserFacade, Disposable {
  var ui: DocumentationUI by lateinitVal()
  var closeTrigger: (() -> Unit)? = null

  private sealed class BrowserRequest {
    class Load(val request: DocumentationRequest, val reset: Boolean) : BrowserRequest()

    object Reload : BrowserRequest()

    class Link(val url: String) : BrowserRequest()

    class Restore(val snapshot: HistorySnapshot) : BrowserRequest()
  }

  private val myRequestFlow: MutableSharedFlow<BrowserRequest> = MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  @Suppress("RAW_SCOPE_CREATION")
  private val cs = CoroutineScope(EmptyCoroutineContext)

  init {
    cs.launch(CoroutineName("DocumentationBrowser requests")) {
      myRequestFlow.collectLatest {
        handleBrowserRequest(it)
      }
    }
  }

  override fun dispose() {
    cs.cancel("DocumentationBrowser disposal")
    myHistory.clear()
  }

  internal fun closeTrigger(close: () -> Unit) {
    closeTrigger = close
  }

  internal fun clearCloseTrigger() {
    closeTrigger = null
  }

  fun resetBrowser(request: DocumentationRequest) {
    load(request, reset = true)
  }

  private fun load(request: DocumentationRequest, reset: Boolean) {
    check(myRequestFlow.tryEmit(BrowserRequest.Load(request, reset)))
  }

  override fun reload() {
    check(myRequestFlow.tryEmit(BrowserRequest.Reload))
  }

  fun handleLink(url: String) {
    check(myRequestFlow.tryEmit(BrowserRequest.Link(url)))
  }

  private val myPageFlow = MutableStateFlow(initialPage)

  val pageFlow: SharedFlow<DocumentationPage> = myPageFlow.asSharedFlow()

  internal var page: DocumentationPage
    get() = myPageFlow.value
    set(value) {
      myPageFlow.value = value
    }

  override val targetPointer: Pointer<out DocumentationTarget> get() = page.request.targetPointer

  private suspend fun handleBrowserRequest(request: BrowserRequest): Unit = when (request) {
    is BrowserRequest.Load -> handleLoadRequest(request.request, request.reset)
    is BrowserRequest.Reload -> page.loadPage()
    is BrowserRequest.Link -> handleLinkRequest(request.url)
    is BrowserRequest.Restore -> handleRestoreRequest(request.snapshot)
  }

  private suspend fun handleLoadRequest(request: DocumentationRequest, reset: Boolean) {
    val page = withContext(Dispatchers.EDT) {
      if (reset) {
        myHistory.clear()
      }
      else {
        myHistory.nextPage()
      }
      DocumentationPage(listOf(request), project).also {
        this@DocumentationBrowser.page = it
      }
    }
    page.loadPage()
  }

  private suspend fun handleLinkRequest(url: String) {
    if (url.startsWith(DocumentationDownloader.HREF_PREFIX)) {
      handleDownloadSourcesRequest(url)
      return
    }

    val targetPointer = this.targetPointer
    val internalResult = try {
      handleLink(project, targetPointer, url, page)
    }
    catch (_: IndexNotReadyException) {
      return // normal situation, nothing to do
    }

    when (internalResult) {
      is OrderEntry -> withContext(Dispatchers.EDT) {
        if (internalResult.isValid) {
          ProjectSettingsService.getInstance(project).openLibraryOrSdkSettings(internalResult)
        }
      }
      InternalLinkResult.InvalidTarget -> {
        // TODO ? target was invalidated
      }
      InternalLinkResult.CannotResolve -> withContext(Dispatchers.EDT) {
        logLinkClicked(DocumentationLinkProtocol.of(url))
        @Suppress("ControlFlowWithEmptyBody")
        if (!openUrl(project, targetPointer, url)) {
          // TODO ? can't resolve link to target & nobody can open the link
        }
      }
      is InternalLinkResult.Request -> {
        logLinkClicked(DocumentationLinkProtocol.PSI_ELEMENT)
        load(internalResult.request, reset = false)
      }
      is InternalLinkResult.Updater -> {
        page.updatePage(internalResult.updater)
      }
    }
  }

  private suspend fun handleDownloadSourcesRequest(href: String) {
    val filePath = href.replaceFirst(DocumentationDownloader.HREF_PREFIX, "")
    val file = VirtualFileManager.getInstance().findFileByUrl(filePath)
    if (file != null) {
      val handler = DocumentationDownloader.EP.extensionList.find { it.canHandle(project, file) }
      if (handler != null) {
        blockingContext {
          val callback = handler.download(project, file)
          callback.doWhenProcessed {
            logDownloadFinished(project, handler::class.java, callback.isDone)
          }
        }
      }
      closeTrigger?.invoke()
    }
  }

  private suspend fun handleRestoreRequest(snapshot: HistorySnapshot) {
    val page = snapshot.page
    val restored = page.restorePage(snapshot.ui)
    this.page = page
    if (!restored) {
      page.loadPage()
    }
  }

  fun currentExternalUrl(): String? = page.currentContent?.links?.externalUrl

  val history: DocumentationHistory get() = myHistory

  private val myHistory = DocumentationBrowserHistory(::historySnapshot, ::restore)

  private class HistorySnapshot(
    val page: DocumentationPage,
    val ui: UISnapshot,
  )

  private fun historySnapshot(): HistorySnapshot = HistorySnapshot(page, ui.uiSnapshot())

  private fun restore(snapshot: HistorySnapshot) {
    check(myRequestFlow.tryEmit(BrowserRequest.Restore(snapshot)))
  }

  private fun logLinkClicked(protocol: DocumentationLinkProtocol) {
    DocumentationUsageCollector.DOCUMENTATION_LINK_CLICKED.log(
      protocol,
      LookupManager.getInstance(project).activeLookup != null
    )
  }

  companion object {
    fun createBrowser(project: Project, requests: List<DocumentationRequest>): DocumentationBrowser {
      val browser = DocumentationBrowser(project, DocumentationPage(requests, project))
      browser.reload() // init loading
      return browser
    }

    /**
     * @return `true` if a loaded page has some content, `false` if a loaded page is empty
     */
    suspend fun DocumentationBrowser.waitForContent(): Boolean = pageFlow.first().waitForContent()
  }
}
