// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.listeners.InlineCompletionTypingTracker
import com.intellij.codeInsight.inline.completion.listeners.InlineSessionWiseCaretListener
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.ComputedEvents.FinishType
import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSessionManager
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionVariantsComputer
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.flow.withIndex
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.errorIfNotMessage
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

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
    ThreadingAssertions.assertEventDispatchThread()
    ThreadingAssertions.assertWriteAccess()

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
    ThreadingAssertions.assertEventDispatchThread()
    LOG.assertTrue(!context.isDisposed)
    trace(InlineCompletionEventType.Hide(finishType, context.isCurrentlyDisplaying()))

    InlineCompletionSession.remove(editor)
    sessionManager.sessionRemoved()
  }

  @RequiresBlockingContext
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

    val result = Result.runCatching {
      val variants = request(session.provider, request).getVariants()
      if (variants.isEmpty()) {
        withContext(Dispatchers.EDT) {
          coroutineToIndicator {
            trace(InlineCompletionEventType.NoVariants)
          }
        }
        return@runCatching
      }

      coroutineScope {
        // If you write a test and observe an infinite hang here, set [UsefulTestCase.runInDispatchThread] to false.
        withContext(Dispatchers.EDT) {
          val variantsComputer = coroutineToIndicator {
            getVariantsComputer(variants, context, this@coroutineScope)
          }
          session.assignVariants(variantsComputer)
        }
      }

      // TODO remove

      //withContext(Dispatchers.EDT) {
      //  variant.elements.flowOn(Dispatchers.Default)
      //    .onEmpty {
      //      coroutineToIndicator {
      //        trace(InlineCompletionEventType.Empty)
      //        hide(context, FinishType.EMPTY)
      //      }
      //    }
      //    .onCompletion {
      //      val data = variant.data
      //      if (it == null && !data.isUserDataEmpty) {
      //        data.copyUserDataTo(context)
      //      }
      //    }
      //    .collectIndexed { index, it ->
      //      ensureActive()
      //      showInlineElement(it, index, offset, context)
      //    }
      //}
    }

    val exception = result.exceptionOrNull()
    val isActive = coroutineContext.isActive

    // Another request is waiting outside of EDT, so no deadlock
    withContext(NonCancellable) {
      withContext(Dispatchers.EDT) {
        coroutineToIndicator {
          complete(isActive, exception, context)
        }
      }
      exception?.let(LOG::errorIfNotMessage)
    }
  }

  @RequiresEdt
  @RequiresBlockingContext
  private fun complete(
    isActive: Boolean,
    cause: Throwable?,
    context: InlineCompletionContext
  ) {
    if (cause != null && !context.isDisposed) {
      hide(context, FinishType.ERROR)
    }
    if (!context.isDisposed && context.state.elements.isEmpty()) {
      hide(context, FinishType.EMPTY)
    }
    trace(InlineCompletionEventType.Completion(cause, isActive))
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

  private suspend fun request(
    provider: InlineCompletionProvider,
    request: InlineCompletionRequest
  ): InlineCompletionSuggestion {
    withContext(Dispatchers.EDT) {
      traceAsync(InlineCompletionEventType.Request(System.currentTimeMillis(), request, provider::class.java))
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
  @RequiresBlockingContext
  private fun InlineCompletionContext.renderElement(element: InlineCompletionElement, startOffset: Int) {
    val presentable = element.toPresentable()
    presentable.render(editor, endOffset() ?: startOffset)
    state.addElement(presentable)
  }

  private fun createSessionManager(): InlineCompletionSessionManager {
    return object : InlineCompletionSessionManager() {
      override fun onUpdate(session: InlineCompletionSession, result: UpdateSessionResult) {
        ThreadingAssertions.assertEventDispatchThread()

        val context = session.context
        when (result) {
          is UpdateSessionResult.Changed -> {
            trace(InlineCompletionEventType.Change(-1, result.overtypedLength)) // TODO correct index
            editor.inlayModel.execute(true) {
              context.clear()
              result.newElements.forEach { context.renderElement(it, context.endOffset() ?: result.newOffset) }
            }
            if (context.textToInsert().isEmpty()) {
              hide(context, FinishType.TYPED)
            }
          }
          UpdateSessionResult.Same -> Unit
          UpdateSessionResult.Invalidated -> {
            hide(context, FinishType.INVALIDATED)
          }
          UpdateSessionResult.Emptied -> {
            hide(context, FinishType.TYPED)
          }
        }
      }
    }
  }

  @RequiresEdt
  @RequiresBlockingContext
  private fun getVariantsComputer(
    variants: List<InlineCompletionSuggestion.Variant>,
    context: InlineCompletionContext,
    scope: CoroutineScope
  ): InlineCompletionVariantsComputer {
    return object : InlineCompletionVariantsComputer(variants) {
      private val job = scope.launch(Dispatchers.EDT) {
        val allVariantsEmpty = AtomicBoolean(true)
        for ((variantIndex, variant) in variants.withIndex()) {
          val isEmpty = AtomicBoolean(false)
          val isSuccess = variantComputing(variantIndex) {
            variant.elements.flowOn(Dispatchers.Default)
              .onEmpty {
                traceAsync(InlineCompletionEventType.Empty(variantIndex))
                isEmpty.set(true)
              }
              .withIndex()
              .collect { (elementIndex, element) ->
                ensureActive()
                traceAsync(InlineCompletionEventType.Computed(variantIndex, element, elementIndex))
                // TODO make without coroutineToIndicator
                coroutineToIndicator { elementComputed(variantIndex, elementIndex, element) }
                allVariantsEmpty.set(false)
              }
          }

          if (isSuccess) {
            traceAsync(InlineCompletionEventType.VariantComputed(variantIndex))
          }

          if ((!isSuccess || isEmpty.get()) && allVariantsEmpty.get()) {
            if (variantIndex < variants.size - 1) {
              coroutineToIndicator { forceNextVariant() }
            }
            else {
              traceAsync(InlineCompletionEventType.NoVariants)
            }
          }
        }
      }

      override fun elementShown(variantIndex: Int, elementIndex: Int, element: InlineCompletionElement) {
        ThreadingAssertions.assertEventDispatchThread()
        context.renderElement(element, context.expectedStartOffset)
        trace(InlineCompletionEventType.Show(variantIndex, element, elementIndex))
      }

      override fun disposeCurrentVariant() {
        ThreadingAssertions.assertEventDispatchThread()
        context.clear()
      }

      override fun beforeVariantSwitched(fromVariantIndex: Int, toVariantIndex: Int, explicit: Boolean) {
        ThreadingAssertions.assertEventDispatchThread()
        trace(InlineCompletionEventType.VariantSwitched(fromVariantIndex, toVariantIndex, explicit))
      }

      override fun variantChanged(variantIndex: Int, oldText: String, newText: String) {
        ThreadingAssertions.assertEventDispatchThread()
        trace(InlineCompletionEventType.Change(variantIndex, oldText.length - newText.length))
      }

      override fun variantInvalidated(variantIndex: Int) {
        ThreadingAssertions.assertEventDispatchThread()
        trace(InlineCompletionEventType.Invalidated(variantIndex))
      }

      override fun dataChanged() {
        currentVariant().data.copyUserDataTo(context) // TODO
      }

      override fun dispose() {
        super.dispose()
        job.cancel()
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
    ThreadingAssertions.assertEventDispatchThread()
    eventListeners.getMulticaster().on(event)
  }

  @RequiresEdt
  private suspend fun traceAsync(event: InlineCompletionEventType) {
    coroutineToIndicator { trace(event) }
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
