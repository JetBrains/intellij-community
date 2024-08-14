// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.listeners.InlineCompletionTypingTracker
import com.intellij.codeInsight.inline.completion.listeners.InlineSessionWiseCaretListener
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionLogs
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.ShownEvents.FinishType
import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSessionManager
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionVariant
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionVariantsComputer
import com.intellij.codeInsight.inline.completion.tooltip.onboarding.InlineCompletionOnboardingListener
import com.intellij.codeInsight.inline.completion.utils.SafeInlineCompletionExecutor
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
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
import org.jetbrains.annotations.ApiStatus
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

  private val completionState = InlineCompletionState()

  init {
    addEventListener(InlineCompletionUsageTracker.Listener()) // todo remove
    addEventListener(InlineCompletionLogs.Listener(editor))
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

    if (completionState.isInvokingEvent) {
      LOG.trace("Cannot process inline event $event: another event is being processed right now.")
      return
    }

    completionState.isInvokingEvent = true
    try {
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
        guardCaretModifications()
      }

      executor.switchJobSafely(newSession::assignJob) {
        invokeRequest(request, newSession)
      }
    }
    finally {
      completionState.isInvokingEvent = false
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
    traceBlocking(InlineCompletionEventType.Insert)

    val elements = context.state.elements.map { it.element }
    val textToInsert = context.textToInsert()
    val insertEnvironment = InlineCompletionInsertEnvironment(editor, session.request.file, TextRange.from(offset, textToInsert.length))
    context.copyUserDataTo(insertEnvironment)
    hide(context, FinishType.SELECTED)

    editor.document.insertString(offset, textToInsert)
    editor.caretModel.moveToOffset(insertEnvironment.insertedRange.endOffset)
    PsiDocumentManager.getInstance(session.request.file.project).commitDocument(editor.document)
    session.provider.insertHandler.afterInsertion(insertEnvironment, elements)
    editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
    traceBlocking(InlineCompletionEventType.AfterInsert)

    LookupManager.getActiveLookup(editor)?.hideLookup(false) //TODO: remove this
  }

  @RequiresEdt
  @RequiresBlockingContext
  fun hide(context: InlineCompletionContext, finishType: FinishType = FinishType.OTHER) {
    ThreadingAssertions.assertEventDispatchThread()
    LOG.assertTrue(!context.isDisposed)
    traceBlocking(InlineCompletionEventType.Hide(finishType, context.isCurrentlyDisplaying()))

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
      ensureDocumentAndFileSynced(request.file.project, request.document)
      var variants = request(session.provider, request).getVariants()
      if (variants.size > InlineCompletionSuggestion.MAX_VARIANTS_NUMBER) {
        val provider = session.provider
        LOG.warn("$provider gave too many variants: ${variants.size} > ${InlineCompletionSuggestion.MAX_VARIANTS_NUMBER}.")
        variants = variants.take(InlineCompletionSuggestion.MAX_VARIANTS_NUMBER)
      }
      if (variants.isEmpty()) {
        withContext(Dispatchers.EDT) {
          coroutineToIndicator {
            traceBlocking(InlineCompletionEventType.NoVariants)
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
    traceBlocking(InlineCompletionEventType.Completion(cause, isActive))
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
    else if (!completionState.ignoreDocumentChanges) {
      sessionManager.invalidate()
    }
  }

  /**
   * All the document events (except typings) that appear while executing [block] do not clear the current session
   * and do not change anything in the state.
   *
   * Intended to be used to customly change a document when reacting to events.
   *
   * **This API is highly experimental**.
   */
  @ApiStatus.Experimental
  @RequiresEdt
  fun <T> withIgnoringDocumentChanges(block: () -> T): T {
    ThreadingAssertions.assertEventDispatchThread()
    val currentCustomDocumentChangesAllowed = completionState.ignoreDocumentChanges
    completionState.ignoreDocumentChanges = true
    return try {
      block()
    } finally {
      check(completionState.ignoreDocumentChanges) {
        "The state of disabling document changes tracker is switched outside."
      }
      completionState.ignoreDocumentChanges = currentCustomDocumentChangesAllowed
    }
  }

  @ApiStatus.Experimental
  @ApiStatus.Internal
  @RequiresEdt
  fun setIgnoringDocumentChanges(value: Boolean) {
    ThreadingAssertions.assertEventDispatchThread()
    completionState.ignoreDocumentChanges = value
  }

  /**
   * By default, any caret movement to the non-expected position clears an inline completion session.
   * The expected position is defined by [InlineCompletionRequest.endOffset].
   *
   * Some completion updates, like 'accept only the next word', cannot guess the next expected position,
   * because this update is defined by implementation details of a particular provider.
   * Therefore, with this method, any caret movement will update the expected offset of the inline completion.
   */
  @ApiStatus.Experimental
  @RequiresEdt
  internal fun <T> withIgnoringCaretMovement(block: () -> T): T {
    ThreadingAssertions.assertEventDispatchThread()
    if (completionState.ignoreCaretMovement) {
      return block()
    }
    completionState.ignoreCaretMovement = true
    return try {
      block()
    } finally {
      check(completionState.ignoreCaretMovement) {
        "The state of disabling caret movement tracker is switched outside."
      }
      completionState.ignoreCaretMovement = false
    }
  }

  private suspend fun request(
    provider: InlineCompletionProvider,
    request: InlineCompletionRequest
  ): InlineCompletionSuggestion {
    withContext(Dispatchers.EDT) {
      trace(InlineCompletionEventType.Request(System.currentTimeMillis(), request, provider::class.java))
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

  private suspend fun ensureDocumentAndFileSynced(project: Project, document: Document) {
    val documentManager = PsiDocumentManager.getInstance(project)
    val isCommitted = readAction { documentManager.isCommitted(document) }
    if (isCommitted) {
      // We do not need one big readAction: it's enough to have them synced at this moment
      return
    }
    coroutineToIndicator {
      // documentManager.commitAllDocuments/commitDocument takes too much EDT and non-cancellable: performance tests fail
      // constrainedReadAction takes too much time to finish (because no explicit call of 'commit')
      // This method is the best choice I've found: cancellable, doesn't occupy EDT, fast
      documentManager.commitAndRunReadAction { }
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
        when (result) {
          UpdateSessionResult.Invalidated -> hide(session.context, FinishType.INVALIDATED)
          UpdateSessionResult.Emptied -> hide(session.context, FinishType.TYPED)
          UpdateSessionResult.Succeeded -> Unit
        }
      }
    }
  }

  @RequiresEdt
  @RequiresBlockingContext
  private fun getVariantsComputer(
    variants: List<InlineCompletionVariant>,
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
                trace(InlineCompletionEventType.Empty(variantIndex))
                isEmpty.set(true)
              }
              .withIndex()
              .collect { (elementIndex, element) ->
                ensureActive()
                trace(InlineCompletionEventType.Computed(variantIndex, element, elementIndex))
                coroutineToIndicator { WriteIntentReadAction.run<Nothing?> { elementComputed(variantIndex, elementIndex, element) } }
                allVariantsEmpty.set(false)
              }
          }

          if (isSuccess) {
            trace(InlineCompletionEventType.VariantComputed(variantIndex))
          }

          if ((!isSuccess || isEmpty.get()) && allVariantsEmpty.get()) {
            if (variantIndex < variants.size - 1) {
              coroutineToIndicator { forceNextVariant() }
            }
            else {
              trace(InlineCompletionEventType.NoVariants)
            }
          }
        }
      }

      override fun elementShown(variantIndex: Int, elementIndex: Int, element: InlineCompletionElement) {
        ThreadingAssertions.assertEventDispatchThread()
        context.renderElement(element, context.expectedStartOffset)
        traceBlocking(InlineCompletionEventType.Show(variantIndex, element, elementIndex))
      }

      override fun disposeCurrentVariant() {
        ThreadingAssertions.assertEventDispatchThread()
        context.clear()
      }

      override fun beforeVariantSwitched(fromVariantIndex: Int, toVariantIndex: Int, explicit: Boolean) {
        ThreadingAssertions.assertEventDispatchThread()
        traceBlocking(InlineCompletionEventType.VariantSwitched(fromVariantIndex, toVariantIndex, explicit))
      }

      override fun variantChanged(variantIndex: Int, old: List<InlineCompletionElement>, new: List<InlineCompletionElement>) {
        ThreadingAssertions.assertEventDispatchThread()
        val oldText = old.joinToString("") { it.text }
        val newText = new.joinToString("") { it.text }
        traceBlocking(InlineCompletionEventType.Change(variantIndex, new, oldText.length - newText.length))
      }

      override fun variantInvalidated(variantIndex: Int) {
        ThreadingAssertions.assertEventDispatchThread()
        traceBlocking(InlineCompletionEventType.Invalidated(variantIndex))
      }

      override fun dataChanged() {
        currentVariant().data.copyUserDataTo(context)
      }

      override fun dispose() {
        super.dispose()
        job.cancel()
      }
    }
  }

  @RequiresEdt
  private fun InlineCompletionSession.guardCaretModifications() {
    val listener = object : InlineSessionWiseCaretListener() {
      override var completionOffset: Int
        get() = if (!context.isDisposed) context.expectedStartOffset else -1
        set(value) {
          if (!context.isDisposed) context.expectedStartOffset = value
        }

      override val mode: Mode
        get() = if (completionState.ignoreCaretMovement) Mode.ADAPTIVE else Mode.PROHIBIT_MOVEMENT

      override fun cancel() {
        if (!context.isDisposed) hide(context, FinishType.CARET_CHANGED)
      }
    }
    editor.caretModel.addCaretListener(listener, this)
  }

  @RequiresBlockingContext
  @RequiresEdt
  private fun traceBlocking(event: InlineCompletionEventType) {
    ThreadingAssertions.assertEventDispatchThread()
    eventListeners.getMulticaster().on(event)
  }

  @RequiresEdt
  private suspend fun trace(event: InlineCompletionEventType) {
    coroutineToIndicator { traceBlocking(event) }
  }

  @TestOnly
  suspend fun awaitExecution() {
    ThreadingAssertions.assertEventDispatchThread()
    executor.awaitAll()
  }

  private class InlineCompletionState(
    var ignoreDocumentChanges: Boolean = false,
    var ignoreCaretMovement: Boolean = false,
    var isInvokingEvent: Boolean = false
  )

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
