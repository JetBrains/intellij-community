// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.listeners.InlineSessionWiseCaretListener
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionEventListener
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionEventType
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker
import com.intellij.codeInsight.inline.completion.render.InlineCompletionBlock
import com.intellij.codeInsight.inline.completion.render.InlineCompletionInsertPolicy
import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSessionManager
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import com.intellij.util.EventDispatcher
import com.intellij.util.application
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Experimental
class InlineCompletionHandler(scope: CoroutineScope, private val parentDisposable: Disposable) {
  private val executor = SafeInlineCompletionExecutor(scope)
  private val eventListeners = EventDispatcher.create(InlineCompletionEventListener::class.java)
  private val sessionManager = createSessionManager()
  private val requestManager = InlineCompletionRequestManager(sessionManager::invalidate)

  init {
    addEventListener(InlineCompletionUsageTracker.Listener())
  }

  private fun getProvider(event: InlineCompletionEvent): InlineCompletionProvider? {
    if (application.isUnitTestMode && testProvider != null) {
      return testProvider
    }

    return InlineCompletionProvider.extensions().firstOrNull { it.isEnabled(event) }?.also {
      LOG.trace("Selected inline provider: $it")
    }
  }

  fun addEventListener(listener: InlineCompletionEventListener) {
    eventListeners.addListener(listener)
  }

  fun addEventListener(listener: InlineCompletionEventListener, parentDisposable: Disposable) {
    addEventListener(listener)
    Disposer.register(parentDisposable) { removeEventListener(listener) }
  }

  fun removeEventListener(listener: InlineCompletionEventListener) {
    eventListeners.removeListener(listener)
  }

  fun invoke(event: InlineCompletionEvent.DocumentChange) = invokeEvent(event)
  fun invoke(event: InlineCompletionEvent.LookupChange) = invokeEvent(event)
  fun invoke(event: InlineCompletionEvent.LookupCancelled) = invokeEvent(event)
  fun invoke(event: InlineCompletionEvent.DirectCall) = invokeEvent(event)

  @RequiresEdt
  internal fun allowDocumentChange(event: SimpleTypingEvent) {
    requestManager.allowDocumentChange(event)
  }

  @RequiresEdt
  fun invokeEvent(event: InlineCompletionEvent) {
    ThreadingAssertions.assertEventDispatchThread()
    LOG.trace("Start processing inline event $event")

    val provider = getProvider(event)
    val request = requestManager.getRequest(event)
    if (request == null || sessionManager.updateSession(request, provider) || provider == null) {
      return
    }

    // At this point, the previous session must be removed, otherwise, `init` will throw.
    val newSession = InlineCompletionSession.init(request.editor, provider, parentDisposable).apply {
      sessionManager.sessionCreated(this)
      guardCaretModifications(request)
    }
    executor.switchJobSafely(newSession::assignJob) {
      invokeRequest(request, newSession)
    }
  }

  private suspend fun invokeRequest(request: InlineCompletionRequest, session: InlineCompletionSession) {
    currentCoroutineContext().ensureActive()

    val context = session.context
    val editor = request.editor
    val offset = request.endOffset

    val resultFlow = try {
      request(session.provider, request).flowOn(Dispatchers.Default)
    }
    catch (e: Throwable) {
      LOG.errorIfNotCancellation(e)
      emptyFlow()
    }

    // If you write a test and observe an infinite hang here, set [UsefulTestCase.runInDispatchThread] to false.
    withContext(Dispatchers.EDT) {
      resultFlow
        .onEmpty {
          coroutineToIndicator {
            trace(InlineCompletionEventType.Empty)
            hide(editor, false, context)
          }
        }
        .onCompletion {
          val isActive = currentCoroutineContext().isActive
          coroutineToIndicator { complete(isActive, editor, it, context) }
          LOG.errorIfNotCancellation(it)
        }
        .collectIndexed { index, it ->
          ensureActive()
          showInlineElement(it, index, offset, context)
        }
    }
  }

  suspend fun request(provider: InlineCompletionProvider, request: InlineCompletionRequest): Flow<InlineCompletionBlock> {
    withContext(Dispatchers.EDT) {
      coroutineToIndicator {
        trace(InlineCompletionEventType.Request(System.currentTimeMillis(), request, provider::class.java))
      }
    }
    return provider.getProposals(request)
  }

  @RequiresEdt
  private suspend fun showInlineElement(
    element: InlineCompletionBlock,
    index: Int,
    offset: Int,
    context: InlineCompletionContext
  ) {
    coroutineToIndicator { trace(InlineCompletionEventType.Show(element, index)) }
    context.renderElement(element, offset)
  }

  @RequiresEdt
  private fun InlineCompletionContext.renderElement(element: InlineCompletionBlock, startOffset: Int) {
    element.render(editor, endOffset ?: startOffset)
    state.addElement(element)
  }

  @RequiresEdt
  @RequiresBlockingContext
  fun insert(editor: Editor) {
    val context = InlineCompletionContext.getOrNull(editor) ?: return
    val offset = context.startOffset ?: return
    trace(InlineCompletionEventType.Insert)

    val insertions = context.state.elements.map { it.insertPolicy }
    hide(editor, false, context)

    var offsetDelta = 0
    for (insertPolicy in insertions) {
      when (insertPolicy) {
        is InlineCompletionInsertPolicy.Append -> {
          editor.document.insertString(offset + offsetDelta, insertPolicy.text)
        }
        is InlineCompletionInsertPolicy.Skip -> Unit
      }
      offsetDelta += insertPolicy.caretShift
    }
    editor.caretModel.moveToOffset(offset + offsetDelta)

    LookupManager.getActiveLookup(editor)?.hideLookup(false) //TODO: remove this
  }

  @RequiresEdt
  @RequiresBlockingContext
  fun hide(editor: Editor, explicit: Boolean, context: InlineCompletionContext) {
    LOG.assertTrue(!context.isDisposed)
    if (context.isCurrentlyDisplayingInlays) {
      trace(InlineCompletionEventType.Hide(explicit))
    }

    InlineCompletionSession.remove(editor)
    sessionManager.sessionRemoved()
  }

  fun cancel(editor: Editor) {
    executor.cancel()
    application.invokeAndWait {
      InlineCompletionContext.getOrNull(editor)?.let {
        hide(editor, false, it)
      }
    }
  }

  @RequiresEdt
  @RequiresBlockingContext
  fun complete(isActive: Boolean, editor: Editor, cause: Throwable?, context: InlineCompletionContext) {
    trace(InlineCompletionEventType.Completion(cause, isActive))
    if (cause != null && !context.isDisposed) {
      hide(editor, false, context)
    }
  }

  private fun createSessionManager(): InlineCompletionSessionManager {
    return object : InlineCompletionSessionManager() {
      override fun onUpdate(session: InlineCompletionSession, result: UpdateSessionResult) {
        val context = session.context
        when (result) {
          is UpdateSessionResult.Changed -> {
            context.editor.inlayModel.execute(true) {
              context.clear()
              trace(InlineCompletionEventType.Change(result.truncateTyping))
              result.newElements.forEach { context.renderElement(it, context.endOffset ?: result.reason.endOffset) }
            }
          }
          UpdateSessionResult.Same -> Unit
          UpdateSessionResult.Invalidated -> {
            hide(session.context.editor, false, session.context)
          }
        }
      }
    }
  }

  @RequiresEdt
  private fun InlineCompletionSession.guardCaretModifications(request: InlineCompletionRequest) {
    val editor = request.editor
    val expectedOffset = { context.startOffset ?: request.endOffset }
    val cancel = { hide(editor, false, context) }
    val listener = InlineSessionWiseCaretListener(expectedOffset, cancel)
    editor.caretModel.addCaretListener(listener)
    whenDisposed { editor.caretModel.removeCaretListener(listener) }
  }

  @RequiresBlockingContext
  @RequiresEdt
  private fun trace(event: InlineCompletionEventType) {
    eventListeners.getMulticaster().on(event)
  }

  @Deprecated(
    "replaced with direct event call type",
    ReplaceWith("invoke(InlineCompletionEvent.DocumentChange(event, editor))"),
    DeprecationLevel.ERROR
  )
  fun invoke(event: DocumentEvent, editor: Editor) {
    return invoke(InlineCompletionEvent.DocumentChange(event, editor))
  }

  @Deprecated(
    "replaced with direct event call type",
    ReplaceWith("invoke(InlineCompletionEvent.LookupChange(event))"),
    DeprecationLevel.ERROR
  )
  fun invoke(event: LookupEvent) {
    return invoke(InlineCompletionEvent.LookupChange(event))
  }

  @Deprecated(
    "replaced with direct event call type",
    ReplaceWith("invoke(InlineCompletionEvent.DirectCall(editor, file, caret, context))"),
    DeprecationLevel.ERROR
  )
  fun invoke(editor: Editor, file: PsiFile, caret: Caret, context: DataContext?) {
    return invoke(InlineCompletionEvent.DirectCall(editor, file, caret, context))
  }

  @TestOnly
  suspend fun awaitExecution() {
    executor.awaitAll()
  }

  companion object {
    private val LOG = thisLogger()
    val KEY = Key.create<InlineCompletionHandler>("inline.completion.handler")

    fun getOrNull(editor: Editor) = editor.getUserData(KEY)

    private fun Logger.errorIfNotCancellation(e: Throwable?) {
      if (e != null && e !is CancellationException) {
        error(e)
      }
    }

    private var testProvider: InlineCompletionProvider? = null

    @TestOnly
    fun registerTestHandler(provider: InlineCompletionProvider) {
      testProvider = provider
    }

    @TestOnly
    fun unRegisterTestHandler() {
      testProvider = null
    }
  }
}
