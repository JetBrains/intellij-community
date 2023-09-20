// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.logs.InlineCompletionEventListener
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionEventType
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import com.intellij.util.EventDispatcher
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEmpty
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

    executor.switchJobSafely {
      val newRequest = actualizeRequestOrNull(request)
      if (newRequest != null) {
        withContext(Dispatchers.EDT) {
          InlineCompletionContext.getOrNull(newRequest.editor)?.let { context ->
            hide(newRequest.editor, false, context)
          }
        }
      }
      invokeRequest(newRequest ?: request, provider)
    }
  }

  private suspend fun invokeRequest(request: InlineCompletionRequest, provider: InlineCompletionProvider) {
    currentCoroutineContext().ensureActive()

    val editor = request.editor
    val offset = request.endOffset

    val modificationStamp = request.document.modificationStamp
    val resultFlow = request(provider, request) // .flowOn(Dispatchers.IO)

    val context = InlineCompletionSession.getOrInit(editor, provider).context

    // If you write a test and observe an infinite hang here, set [UsefulTestCase.runInDispatchThread] to false.
    withContext(Dispatchers.EDT) {
      resultFlow
        .onEmpty {
          trace(InlineCompletionEventType.Empty)
          InlineCompletionSession.remove(editor)
        }
        .onCompletion {
          complete(currentCoroutineContext().isActive, editor, it, context)
        }
        .collectIndexed { index, it ->
          ensureActive()

          if (modificationStamp != request.document.modificationStamp) {
            cancel()
            return@collectIndexed
          }

          showInlineElement(it, index, offset, context)
        }
    }
  }

  suspend fun request(provider: InlineCompletionProvider, request: InlineCompletionRequest): Flow<InlineCompletionElement> {
    trace(InlineCompletionEventType.Request(System.currentTimeMillis(), request, provider::class.java))
    return provider.getProposals(request)
  }

  @RequiresEdt
  private fun showInlineElement(
    element: InlineCompletionElement,
    index: Int,
    offset: Int,
    context: InlineCompletionContext
  ) {
    trace(InlineCompletionEventType.Show(element, index))
    context.renderElement(element, offset)
  }

  @RequiresEdt
  private fun InlineCompletionContext.renderElement(element: InlineCompletionElement, startOffset: Int) {
    element.render(editor, lastOffset ?: startOffset)
    state.addElement(element)
  }

  @RequiresEdt
  fun insert(editor: Editor) {
    val context = InlineCompletionContext.getOrNull(editor) ?: return
    trace(InlineCompletionEventType.Insert)

    val offset = context.lastOffset ?: return
    val currentCompletion = context.lineToInsert

    editor.document.insertString(offset, currentCompletion)
    editor.caretModel.moveToOffset(offset + currentCompletion.length)

    LookupManager.getActiveLookup(editor)?.hideLookup(false) //TODO: remove this
    hide(editor, false, context)
  }

  @RequiresEdt
  fun hide(editor: Editor, explicit: Boolean, context: InlineCompletionContext) {
    if (context.isCurrentlyDisplayingInlays) {
      trace(InlineCompletionEventType.Hide(explicit))
    }

    InlineCompletionSession.remove(editor)
  }

  fun cancel(editor: Editor) {
    executor.cancel()
    application.invokeAndWait {
      InlineCompletionContext.getOrNull(editor)?.let { context ->
        hide(editor, false, context)
      }
    }
  }

  fun complete(isActive: Boolean, editor: Editor, cause: Throwable?, context: InlineCompletionContext) {
    trace(InlineCompletionEventType.Completion(cause, isActive))
    if (cause != null) {
      hide(editor, false, context)
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
    if (context.isCurrentlyDisplayingInlays) {
      hide(context.editor, false, context)
    }
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
