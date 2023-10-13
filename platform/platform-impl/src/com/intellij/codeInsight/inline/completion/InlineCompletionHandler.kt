// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.listeners.InlineSessionWiseCaretListener
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionEventListener
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionEventType
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker
import com.intellij.codeInsight.inline.completion.render.InlineCompletionInsertPolicy
import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSessionManager
import com.intellij.codeInsight.inline.completion.utils.SafeInlineCompletionExecutor
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.util.EventDispatcher
import com.intellij.util.application
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEmpty
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.errorIfNotMessage
import kotlin.reflect.KClass

/**
 * Use [InlineCompletion] for acquiring, installing and uninstalling [InlineCompletionHandler].
 */
class InlineCompletionHandler internal constructor(
  scope: CoroutineScope,
  val editor: Editor,
  private val parentDisposable: Disposable
) {
  private val executor = SafeInlineCompletionExecutor(scope)
  private val eventListeners = EventDispatcher.create(InlineCompletionEventListener::class.java)
  private val sessionManager = createSessionManager()
  private val requestManager = InlineCompletionRequestManager(sessionManager::invalidate)
  private val providerManager = InlineCompletionProviderManager()

  init {
    addEventListener(InlineCompletionUsageTracker.Listener())
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
  fun invoke(event: InlineCompletionEvent.Navigation) = invokeEvent(event)

  @RequiresEdt
  fun invokeEvent(event: InlineCompletionEvent) {
    ThreadingAssertions.assertEventDispatchThread()
    LOG.trace("Start processing inline event $event")

    val provider = providerManager.getProvider(event)
    val request = requestManager.getRequest(event)
    if (request == null || sessionManager.updateSession(request, provider) || provider == null) {
      return
    }

    // At this point, the previous session must be removed, otherwise, `init` will throw.
    val newSession = InlineCompletionSession.init(editor, provider, parentDisposable).apply {
      sessionManager.sessionCreated(this)
      guardCaretModifications(request)
    }

    //providerManager.getCache(provider::class)?.let { cached ->
    //  // TODO: add event logging)
    //  cached.forEach { newSession.context.renderElement(it, request.endOffset) }
    //  return
    //}

    executor.switchJobSafely(newSession::assignJob) {
      invokeRequest(provider::class, request, newSession)
    }
  }

  @RequiresEdt
  @RequiresBlockingContext
  fun insert() {
    val context = InlineCompletionContext.getOrNull(editor) ?: return
    val offset = context.startOffset() ?: return
    trace(InlineCompletionEventType.Insert)

    val insertions = context.state.elements.map { it.insertPolicy() }
    hide(false, context)

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
  fun hide(explicit: Boolean, context: InlineCompletionContext) {
    hide(editor, explicit, context, true)
  }

  @RequiresEdt
  @RequiresBlockingContext
  internal fun hide(editor: Editor, explicit: Boolean, context: InlineCompletionContext, clearCache: Boolean) {
    LOG.assertTrue(!context.isDisposed)
    if (clearCache) {
      providerManager.clear()
    }
    if (context.isCurrentlyDisplaying()) {
      trace(InlineCompletionEventType.Hide(explicit))
    }

    InlineCompletionSession.remove(editor)
    sessionManager.sessionRemoved()
  }

  fun cancel() {
    executor.cancel()
    application.invokeAndWait {
      InlineCompletionContext.getOrNull(editor)?.let {
        hide(false, it)
      }
    }
  }

  private suspend fun invokeRequest(
    provider: KClass<out InlineCompletionProvider>,
    request: InlineCompletionRequest,
    session: InlineCompletionSession,
  ) {
    currentCoroutineContext().ensureActive()

    val context = session.context
    val editor = request.editor
    val offset = request.endOffset

    val suggestion = try {
      request(session.provider, request)
    }
    catch (e: Throwable) {
      LOG.errorIfNotMessage(e)
      InlineCompletionSuggestion.EMPTY
    }

    // If you write a test and observe an infinite hang here, set [UsefulTestCase.runInDispatchThread] to false.
    withContext(Dispatchers.EDT) {
      suggestion.suggestionFlow.flowOn(Dispatchers.Default)
        .onEmpty {
          coroutineToIndicator {
            trace(InlineCompletionEventType.Empty)
            hide(editor, false, context)
          }
        }
        .onCompletion {
          val isActive = currentCoroutineContext().isActive
          coroutineToIndicator { complete(isActive, editor, it, context, provider, suggestion) }
          it?.let(LOG::errorIfNotMessage)
        }
        .collectIndexed { index, it ->
          ensureActive()
          showInlineElement(it, index, offset, context)
        }
    }
  }

  @RequiresEdt
  @RequiresBlockingContext
  private fun complete(
    isActive: Boolean,
    editor: Editor,
    cause: Throwable?,
    context: InlineCompletionContext,
    provider: KClass<out InlineCompletionProvider>,
    suggestion: InlineCompletionSuggestion,
  ) {
    trace(InlineCompletionEventType.Completion(cause, isActive))
    if (!suggestion.isUserDataEmpty) {
      suggestion.copyUserDataTo(context)
    }

    if (cause != null && !context.isDisposed) {
      hide(editor, false, context)
      return
    }
    if (suggestion.useCache) {
      providerManager.cacheSuggestion(provider, context.state.elements)
    }
  }

  @RequiresEdt
  internal fun allowDocumentChange(event: SimpleTypingEvent) {
    requestManager.allowDocumentChange(event)
  }

  internal fun eventSource(): InlineCompletionEvent? {
    return providerManager.source
  }

  private suspend fun request(provider: InlineCompletionProvider, request: InlineCompletionRequest): InlineCompletionSuggestion {
    withContext(Dispatchers.EDT) {
      coroutineToIndicator {
        trace(InlineCompletionEventType.Request(System.currentTimeMillis(), request, provider::class.java))
      }
    }
    return provider.getSuggestion(request)
  }

  @RequiresEdt
  private suspend fun showInlineElement(
    element: InlineCompletionElement,
    index: Int,
    offset: Int,
    context: InlineCompletionContext
  ) {
    coroutineToIndicator { trace(InlineCompletionEventType.Show(element, index)) }
    context.renderElement(element, offset)
  }

  @RequiresEdt
  private fun InlineCompletionContext.renderElement(element: InlineCompletionElement, startOffset: Int) {
    val presentable = element.toPresentable()
    presentable.render(editor, endOffset() ?: startOffset)
    state.addElement(presentable)
  }

  private fun createSessionManager(): InlineCompletionSessionManager {
    return object : InlineCompletionSessionManager() {
      override fun onUpdate(session: InlineCompletionSession, result: UpdateSessionResult) {
        val context = session.context
        when (result) {
          is UpdateSessionResult.Changed -> {
            editor.inlayModel.execute(true) {
              context.clear()
              trace(InlineCompletionEventType.Change(result.truncateTyping))
              result.newElements.forEach { context.renderElement(it, context.endOffset() ?: result.reason.endOffset) }
            }
          }
          UpdateSessionResult.Same -> Unit
          UpdateSessionResult.Invalidated -> {
            hide(false, session.context)
          }
          UpdateSessionResult.InvalidatedWithoutCache -> {
            hide(session.context.editor, false, session.context, false)
          }
        }
      }
    }
  }

  @RequiresEdt
  private fun InlineCompletionSession.guardCaretModifications(request: InlineCompletionRequest) {
    val expectedOffset = {
      // This caret listener might be disposed after context: ML-1438
      if (!context.isDisposed) context.startOffset() ?: request.endOffset else -1
    }
    val cancel = {
      if (!context.isDisposed) hide(false, context)
    }
    val listener = InlineSessionWiseCaretListener(expectedOffset, cancel)
    editor.caretModel.addCaretListener(listener)
    whenDisposed { editor.caretModel.removeCaretListener(listener) }
  }

  @RequiresBlockingContext
  @RequiresEdt
  private fun trace(event: InlineCompletionEventType) {
    eventListeners.getMulticaster().on(event)
  }

  @TestOnly
  suspend fun awaitExecution() {
    executor.awaitAll()
  }

  companion object {
    private val LOG = thisLogger()
    val KEY = Key.create<InlineCompletionHandler>("inline.completion.handler")

    fun getOrNull(editor: Editor) = editor.getUserData(KEY)
  }
}
