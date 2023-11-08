// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.listeners.InlineSessionWiseCaretListener
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.ShownEvents.FinishType
import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSessionManager
import com.intellij.codeInsight.inline.completion.tooltip.onboarding.InlineCompletionOnboardingListener
import com.intellij.codeInsight.inline.completion.utils.SafeInlineCompletionExecutor
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.EventDispatcher
import com.intellij.util.application
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEmpty
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.errorIfNotMessage

/**
 * Use [InlineCompletion] for acquiring, installing and uninstalling [InlineCompletionHandler].
 */
class InlineCompletionHandler(
  scope: CoroutineScope,
  val editor: Editor,
  private val parentDisposable: Disposable
) {
  private val executor = SafeInlineCompletionExecutor(scope)
  private val eventListeners = EventDispatcher.create(InlineCompletionEventListener::class.java)
  private val sessionManager = createSessionManager()
  private val typingTracker = InlineCompletionTypingTracker(parentDisposable)

  init {
    addEventListener(InlineCompletionUsageTracker.Listener())
    InlineCompletionOnboardingListener.createIfOnboarding(editor)?.let(::addEventListener)
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

  @Deprecated(
    message = "Direct invocations of DocumentChange are forbidden. Use [onDocumentEvent].",
    ReplaceWith("onDocumentEvent(..., event.editor)"),
    level = DeprecationLevel.ERROR
  )
  @ScheduledForRemoval
  fun invoke(@Suppress("UNUSED_PARAMETER") event: InlineCompletionEvent.DocumentChange) {
    throw UnsupportedOperationException("Direct `DocumentChange` events are not supported anymore.")
  }

  fun invoke(event: InlineCompletionEvent.LookupChange) = invokeEvent(event)
  fun invoke(event: InlineCompletionEvent.LookupCancelled) = invokeEvent(event)
  fun invoke(event: InlineCompletionEvent.DirectCall) = invokeEvent(event)

  @RequiresEdt
  fun invokeEvent(event: InlineCompletionEvent) {
    ThreadingAssertions.assertEventDispatchThread()
    LOG.trace("Start processing inline event $event")

    val request = event.toRequest() ?: return
    if (editor != request.editor) {
      LOG.warn("Request has an inappropriate editor. Another editor was expected. Will not be invoked.")
      return
    }

    if (sessionManager.updateSession(request)) {
      return
    }

    val provider = getProvider(event) ?: return

    // At this point, the previous session must be removed, otherwise, `init` will throw.
    val newSession = InlineCompletionSession.init(editor, provider, request, parentDisposable).apply {
      sessionManager.sessionCreated(this)
      guardCaretModifications(request)
    }

    executor.switchJobSafely(newSession::assignJob) {
      invokeRequest(request, newSession)
    }
  }

  @RequiresEdt
  @RequiresWriteLock
  @RequiresBlockingContext
  fun insert() {
    val session = InlineCompletionSession.getOrNull(editor) ?: return
    val context = session.context
    val offset = context.startOffset() ?: return
    trace(InlineCompletionEventType.Insert)

    val elements = context.state.elements.map { it.element }
    val textToInsert = context.textToInsert()
    val insertEnvironment = InlineCompletionInsertEnvironment(editor, session.request.file, TextRange.from(offset, textToInsert.length))
    context.copyUserDataTo(insertEnvironment)
    hide(context, FinishType.SELECTED)

    editor.document.insertString(offset, textToInsert)
    editor.caretModel.moveToOffset(insertEnvironment.insertedRange.endOffset)
    PsiDocumentManager.getInstance(session.request.file.project).commitDocument(editor.document)
    session.provider.insertHandler.afterInsertion(insertEnvironment, elements)

    LookupManager.getActiveLookup(editor)?.hideLookup(false) //TODO: remove this
  }

  @RequiresEdt
  @RequiresBlockingContext
  fun hide(context: InlineCompletionContext, finishType: FinishType = FinishType.OTHER) {
    LOG.assertTrue(!context.isDisposed)
    trace(InlineCompletionEventType.Hide(finishType))

    InlineCompletionSession.remove(editor)
    sessionManager.sessionRemoved()
  }

  fun cancel(finishType: FinishType = FinishType.OTHER) {
    executor.cancel()
    application.invokeAndWait {
      InlineCompletionContext.getOrNull(editor)?.let {
        hide(it, finishType)
      }
    }
  }

  private suspend fun invokeRequest(request: InlineCompletionRequest, session: InlineCompletionSession) {
    currentCoroutineContext().ensureActive()

    val context = session.context
    val offset = request.endOffset

    val suggestion = try {
      request(session.provider, request)
    }
    catch (e: Throwable) {
      LOG.errorIfNotMessage(e)
      InlineCompletionSuggestion.empty()
    }

    // If you write a test and observe an infinite hang here, set [UsefulTestCase.runInDispatchThread] to false.
    withContext(Dispatchers.EDT) {
      suggestion.suggestionFlow.flowOn(Dispatchers.Default)
        .onEmpty {
          coroutineToIndicator {
            trace(InlineCompletionEventType.Empty)
            hide(context, FinishType.EMPTY)
          }
        }
        .onCompletion {
          val isActive = currentCoroutineContext().isActive
          coroutineToIndicator { complete(isActive, it, context, suggestion) }
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
    cause: Throwable?,
    context: InlineCompletionContext,
    suggestion: InlineCompletionSuggestion,
  ) {
    trace(InlineCompletionEventType.Completion(cause, isActive))
    if (!suggestion.isUserDataEmpty) {
      suggestion.copyUserDataTo(context)
    }

    if (cause != null && !context.isDisposed) {
      hide(context, FinishType.ERROR)
      return
    }
  }

  /**
   * @see InlineCompletionTypingTracker.allowTyping
   * @see onDocumentEvent
   */
  @RequiresEdt
  @RequiresBlockingContext
  internal fun allowTyping(event: TypingEvent) {
    typingTracker.allowTyping(event)
  }

  /**
   * If [documentEvent] offers the same as the last [allowTyping], then it creates [InlineCompletionEvent.DocumentChange] and
   * invokes it. Otherwise, [documentEvent] is considered as 'non-typing' and a current session is invalidated.
   * No new session is started in such a case.
   *
   * @see allowTyping
   * @see InlineCompletionTypingTracker.getDocumentChangeEvent
   */
  @RequiresEdt
  @RequiresBlockingContext
  internal fun onDocumentEvent(documentEvent: DocumentEvent, editor: Editor) {
    val event = typingTracker.getDocumentChangeEvent(documentEvent, editor)
    if (event != null) {
      invokeEvent(event)
    }
    else {
      sessionManager.invalidate()
    }
  }

  private suspend fun request(provider: InlineCompletionProvider, request: InlineCompletionRequest): InlineCompletionSuggestion {
    withContext(Dispatchers.EDT) {
      coroutineToIndicator {
        trace(InlineCompletionEventType.Request(System.currentTimeMillis(), request, provider::class.java))
      }
    }
    return provider.getSuggestion(request)
  }

  private fun getProvider(event: InlineCompletionEvent): InlineCompletionProvider? {
    if (application.isUnitTestMode && testProvider != null) {
      return testProvider?.takeIf { it.isEnabled(event) }
    }

    return InlineCompletionProvider.extensions().firstOrNull {
      try {
        it.isEnabled(event)
      }
      catch (e: Throwable) {
        LOG.errorIfNotMessage(e)
        false
      }
    }?.also {
      LOG.trace("Selected inline provider: $it")
    }
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
          is UpdateSessionResult.Overtyped -> {
            trace(InlineCompletionEventType.Change(result.overtypedLength))
            editor.inlayModel.execute(true) {
              context.clear()
              result.newElements.forEach { context.renderElement(it, context.endOffset() ?: result.newOffset) }
            }
          }
          is UpdateSessionResult.Same -> Unit
          UpdateSessionResult.Invalidated -> {
            hide(session.context, FinishType.INVALIDATED)
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
      if (!context.isDisposed) hide(context, FinishType.CARET_CHANGED)
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

    private var testProvider: InlineCompletionProvider? = null

    @TestOnly
    fun registerTestHandler(provider: InlineCompletionProvider) {
      testProvider = provider
    }

    @TestOnly
    fun registerTestHandler(provider: InlineCompletionProvider, disposable: Disposable) {
      registerTestHandler(provider)
      disposable.whenDisposed { unRegisterTestHandler() }
    }

    @TestOnly
    fun unRegisterTestHandler() {
      testProvider = null
    }
  }
}
