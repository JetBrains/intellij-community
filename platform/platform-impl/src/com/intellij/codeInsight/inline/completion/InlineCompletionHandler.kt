// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.listeners.InlineSessionWiseCaretListener
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionEventListener
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionEventType
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker
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
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import com.intellij.util.EventDispatcher
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Experimental
class InlineCompletionHandler(scope: CoroutineScope) {
  private val executor = SafeInlineCompletionExecutor(scope)
  private val eventListeners = EventDispatcher.create(InlineCompletionEventListener::class.java)

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

  @RequiresEdt
  fun invoke(event: InlineCompletionEvent.DocumentChange) = invokeEvent(event)

  @RequiresEdt
  fun invoke(event: InlineCompletionEvent.CaretMove) = invokeEvent(event)

  @RequiresEdt
  fun invoke(event: InlineCompletionEvent.LookupChange) = invokeEvent(event)

  @RequiresEdt
  fun invoke(event: InlineCompletionEvent.DirectCall) = invokeEvent(event)

  @RequiresEdt
  private fun invokeEvent(event: InlineCompletionEvent) {
    if (!application.isDispatchThread) {
      LOG.error("Cannot run inline completion handler outside of EDT.")
      return
    }

    LOG.trace("Start processing inline event $event")

    val provider = getProvider(event)
    val request = event.toRequest() ?: return
    if (updateContextOrInvalidate(request, provider) || provider == null) {
      return
    }

    // At this point, the previous session must be removed, otherwise, `init` will throw.
    val newSession = InlineCompletionSession.init(request.editor, provider)
    newSession.guardCaretModifications(request)
    executor.switchJobSafely(newSession::assignJob) {
      val newRequest = actualizeRequestOrNull(request)
      if (newRequest != null) {
        LOG.assertTrue(newRequest.editor === request.editor)
        withContext(Dispatchers.EDT) {
          newSession.guardCaretModifications(newRequest)
        }
      }
      invokeRequest(newRequest ?: request, newSession)
    }
  }

  private suspend fun invokeRequest(request: InlineCompletionRequest, session: InlineCompletionSession) {
    currentCoroutineContext().ensureActive()

    val context = session.context
    val editor = request.editor
    val offset = request.endOffset

    val resultFlow = try {
      request(session.provider, request) // .flowOn(Dispatchers.IO)
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

  suspend fun request(provider: InlineCompletionProvider, request: InlineCompletionRequest): Flow<InlineCompletionElement> {
    withContext(Dispatchers.EDT) {
      coroutineToIndicator {
        trace(InlineCompletionEventType.Request(System.currentTimeMillis(), request, provider::class.java))
      }
    }
    return provider.getProposals(request)
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
    element.render(editor, lastOffset ?: startOffset)
    state.addElement(element)
  }

  @RequiresEdt
  @RequiresBlockingContext
  fun insert(editor: Editor) {
    val context = InlineCompletionContext.getOrNull(editor) ?: return
    trace(InlineCompletionEventType.Insert)

    val offset = context.lastOffset ?: return
    val currentCompletion = context.lineToInsert
    hide(editor, false, context)

    editor.document.insertString(offset, currentCompletion)
    editor.caretModel.moveToOffset(offset + currentCompletion.length)

    LookupManager.getActiveLookup(editor)?.hideLookup(false) //TODO: remove this
  }

  @RequiresEdt
  @RequiresBlockingContext
  fun hide(editor: Editor, explicit: Boolean, context: InlineCompletionContext) {
    LOG.assertTrue(!context.isInvalidated)
    if (context.isCurrentlyDisplayingInlays) {
      trace(InlineCompletionEventType.Hide(explicit))
    }

    InlineCompletionSession.remove(editor)
  }

  fun cancel(editor: Editor) {
    executor.cancel()
    application.invokeAndWait {
      hide(editor, false)
    }
  }

  @RequiresEdt
  @RequiresBlockingContext
  fun complete(isActive: Boolean, editor: Editor, cause: Throwable?, context: InlineCompletionContext) {
    trace(InlineCompletionEventType.Completion(cause, isActive))
    if (cause != null && !context.isInvalidated) {
      hide(editor, false, context)
    }
  }

  @RequiresEdt
  private fun hide(editor: Editor, explicit: Boolean) {
    InlineCompletionContext.getOrNull(editor)?.let {
      hide(editor, explicit, it)
    }
  }

  /**
   * @return `true` if update was successful. Otherwise, [hide] is invoked to invalidate the current context.
   */
  @RequiresEdt
  @RequiresBlockingContext
  private fun updateContextOrInvalidate(
    request: InlineCompletionRequest,
    provider: InlineCompletionProvider?
  ): Boolean {
    val session = InlineCompletionSession.getOrNull(request.editor) ?: return false
    if (provider == null && !session.context.isCurrentlyDisplayingInlays) {
      return true // Fast fall to not slow down editor
    }
    if ((provider != null && session.provider != provider) || session.provider.requiresInvalidation(request.event)) {
      session.invalidate()
      return false
    }

    val context = session.context
    val result = updateContext(context, request.event)
    when (result) {
      is UpdateContextResult.Changed -> {
        context.editor.inlayModel.execute(true) {
          context.clear()
          trace(InlineCompletionEventType.Change(result.truncateTyping))
          result.newElements.forEach { context.renderElement(it, request.endOffset) }
        }
      }
      is UpdateContextResult.Same -> Unit
      is UpdateContextResult.Invalidated -> session.invalidate()
    }
    return result != UpdateContextResult.Invalidated
  }

  @RequiresEdt
  private fun InlineCompletionSession.invalidate() {
    hide(context.editor, false, context)
  }

  /**
   * IDE inserts a paired quote/bracket and moves a caret without any event. It requires us to update request.
   *
   * It cannot be invoked when a request is constructed. It must be called after, in order to get
   * actualized information about offset from EDT.
   */
  private suspend fun actualizeRequestOrNull(request: InlineCompletionRequest): InlineCompletionRequest? {
    if (request.event !is InlineCompletionEvent.DocumentChange) {
      return null
    }

    // ML-1237, ML-1281, ML-1232
    // TODO should not go to EDT but it helps us wait for an actual caret position
    val offsetDelta = withContext(Dispatchers.EDT) { request.editor.caretModel.offset - request.endOffset }
    if (offsetDelta == 0) {
      return null
    }

    return request.copy(
      startOffset = request.startOffset + offsetDelta,
      endOffset = request.endOffset + offsetDelta
    )
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
    ReplaceWith("invoke(InlineCompletionEvent.CaretMove(event))"),
    DeprecationLevel.ERROR
  )
  fun invoke(event: EditorMouseEvent) {
    return invoke(InlineCompletionEvent.CaretMove(event))
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
