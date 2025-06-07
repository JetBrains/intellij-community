// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.editor.InlineCompletionEditorType
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.listeners.InlineSessionWiseCaretListener
import com.intellij.codeInsight.inline.completion.listeners.typing.InlineCompletionDocumentChangesTrackerImpl
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionLogsListener
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.ShownEvents.FinishType
import com.intellij.codeInsight.inline.completion.logs.UserFactorsListener
import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.codeInsight.inline.completion.session.InlineCompletionInvalidationListener
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSessionManager
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSessionManager.UpdateSessionResult
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionVariant
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionVariantsComputer
import com.intellij.codeInsight.inline.edit.InlineEditRequestExecutor
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.inlinePrompt.isInlinePromptShown
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.EventDispatcher
import com.intellij.util.application
import com.intellij.util.concurrency.ThreadingAssertions
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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Use [InlineCompletion] for acquiring, installing and uninstalling [InlineCompletionHandler].
 */
@ApiStatus.NonExtendable
abstract class InlineCompletionHandler @ApiStatus.Internal constructor(
  @ApiStatus.Internal
  protected val scope: CoroutineScope,

  val editor: Editor,

  @ApiStatus.Internal
  protected val parentDisposable: Disposable,
) {
  private val executor = InlineEditRequestExecutor.create(scope)
  private val eventListeners = EventDispatcher.create(InlineCompletionEventListener::class.java)
  private val completionState: InlineCompletionState = InlineCompletionState()

  @ApiStatus.Internal
  protected val sessionManager: InlineCompletionSessionManager = createSessionManager()

  @ApiStatus.Internal
  protected val invalidationListeners: EventDispatcher<InlineCompletionInvalidationListener> =
    EventDispatcher.create(InlineCompletionInvalidationListener::class.java)

  init {
    addEventListener(InlineCompletionUsageTracker.Listener()) // todo remove

    val logsListener = InlineCompletionLogsListener(editor)
    addEventListener(logsListener)
    invalidationListeners.addListener(logsListener)
    addEventListener(UserFactorsListener())

    Disposer.register(parentDisposable, /* child = */ executor)
  }

  /**
   * Frontend always starts a session. Backend never starts a session. Instead, the backend will send a notification to the frontend.
   */
  @ApiStatus.Internal
  protected abstract fun startSessionOrNull(
    request: InlineCompletionRequest,
    provider: InlineCompletionProvider
  ): InlineCompletionSession?

  @ApiStatus.Internal
  protected abstract fun doHide(context: InlineCompletionContext, finishType: FinishType)

  @ApiStatus.Internal
  protected abstract fun createSessionManager(): InlineCompletionSessionManager

  @ApiStatus.Internal
  protected abstract fun afterInsert(providerId: InlineCompletionProviderID)

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
    message = "Use general invokeEvent.",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("invokeEvent(event)"),
  )
  @ScheduledForRemoval
  fun invoke(event: InlineCompletionEvent.LookupChange): Unit = invokeEvent(event)

  @Deprecated(
    message = "Use general invokeEvent.",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("invokeEvent(event)"),
  )
  @ScheduledForRemoval
  fun invoke(event: InlineCompletionEvent.LookupCancelled): Unit = invokeEvent(event)

  @Deprecated(
    message = "Use general invokeEvent.",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("invokeEvent(event)"),
  )
  @ScheduledForRemoval
  fun invoke(event: InlineCompletionEvent.DirectCall): Unit = invokeEvent(event)

  @RequiresEdt
  fun invokeEvent(event: InlineCompletionEvent) {
    ThreadingAssertions.assertEventDispatchThread()

    if (completionState.isInvokingEvent) {
      LOG.trace("[Inline Completion] Cannot process inline event $event: another event is being processed right now.")
      return
    }

    completionState.isInvokingEvent = true
    try {
      LOG.trace("[Inline Completion] Start processing inline event $event")

      val request = event.toRequest() ?: return
      if (editor != request.editor) {
        LOG.warn("[Inline Completion] Request has an inappropriate editor. Another editor was expected. Will not be invoked.")
        return
      }

      if (isInlineCompletionSuppressedByPrompt()) {
        LOG.trace("Inline Completion is suppressed. Event $event is ignored.")
        return
      }

      if (sessionManager.updateSession(request)) {
        return
      }

      val provider = getProvider(event) ?: return

      // At this point, the previous session must be removed, otherwise, `init` will throw.
      val newSession = startSessionOrNull(request, provider) ?: return
      newSession.guardCaretModifications()

      switchAndInvokeRequest(request, newSession)
    }
    finally {
      completionState.isInvokingEvent = false
    }
  }

  @RequiresEdt
  @RequiresWriteLock
  fun insert() {
    ThreadingAssertions.assertEventDispatchThread()
    ThreadingAssertions.assertWriteAccess()

    val session = InlineCompletionSession.getOrNull(editor) ?: return
    val actualProviderId = session.provider.let { provider ->
      if (provider is RemDevAggregatorInlineCompletionProvider) provider.currentProviderId ?: provider.id else provider.id
    }
    val context = session.context
    val offset = context.startOffset() ?: return
    traceBlocking(InlineCompletionEventType.Insert)

    val elements = context.state.elements.map { it.element }
    val textToInsert = context.textToInsert()
    val insertEnvironment = InlineCompletionInsertEnvironment(
      editor = editor,
      file = session.request.file,
      insertedRange = TextRange.from(offset, textToInsert.length),
      request = session.request,
    )
    context.copyUserDataTo(insertEnvironment)
    hide(context, FinishType.SELECTED)

    editor.document.insertString(offset, textToInsert)
    editor.caretModel.moveToOffset(insertEnvironment.insertedRange.endOffset)
    PsiDocumentManager.getInstance(session.request.file.project).commitDocument(editor.document)
    session.provider.insertHandler.afterInsertion(insertEnvironment, elements)
    editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
    traceBlocking(InlineCompletionEventType.AfterInsert)

    LookupManager.getActiveLookup(editor)?.hideLookup(false) //TODO: remove this

    afterInsert(actualProviderId)
  }

  @RequiresEdt
  fun hide(context: InlineCompletionContext, finishType: FinishType = FinishType.OTHER) {
    ThreadingAssertions.assertEventDispatchThread()
    val currentSession = InlineCompletionSession.getOrNull(editor)
    if (context !== currentSession?.context) {
      LOG.error("[Inline Completion] Cannot hide a session because an invalid context is passed.")
      return
    }
    if (context.isDisposed) {
      sessionManager.removeSession()
      LOG.error("[Inline Completion] Cannot hide a session because the context is already disposed.")
      return
    }
    doHide(context, finishType)
  }

  fun cancel(finishType: FinishType = FinishType.OTHER) {
    application.invokeAndWait {
      InlineCompletionContext.getOrNull(editor)?.let {
        hide(it, finishType)
      }
    }
  }

  internal val documentChangesTracker = InlineCompletionDocumentChangesTrackerImpl(
    parentDisposable,
    sendEvent = ::invokeEvent,
    invalidateOnUnknownChange = { sessionManager.invalidate(UpdateSessionResult.Invalidated.Reason.UnclassifiedDocumentChange) }
  )

  @ApiStatus.Internal
  protected fun performHardHide(context: InlineCompletionContext, finishType: FinishType) {
    traceBlocking(InlineCompletionEventType.Hide(finishType, context.isCurrentlyDisplaying()))
    sessionManager.removeSession()
  }

  // TODO extract EP
  private fun isInlineCompletionSuppressedByPrompt(): Boolean = isInlinePromptShown(editor)

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
      withContext(Dispatchers.EDT) {
        trace(InlineCompletionEventType.SuggestionInitialized(variants.size))
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
  private fun complete(
    isActive: Boolean,
    cause: Throwable?,
    context: InlineCompletionContext,
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
    return documentChangesTracker.withIgnoringDocumentChanges(block)
  }

  @ApiStatus.Experimental
  @ApiStatus.Internal
  @RequiresEdt
  fun setIgnoringDocumentChanges(value: Boolean) {
    documentChangesTracker.ignoreDocumentChanges = value
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
    return documentChangesTracker.withIgnoringCaretMovement(block)
  }

  private suspend fun request(
    provider: InlineCompletionProvider,
    request: InlineCompletionRequest,
  ): InlineCompletionSuggestion {
    withContext(Dispatchers.EDT) {
      trace(InlineCompletionEventType.Request(System.currentTimeMillis(), request, provider::class.java))
    }
    return provider.getSuggestion(request)
  }

  @ApiStatus.Internal
  protected fun getProvider(event: InlineCompletionEvent): InlineCompletionProvider? {
    if (application.isUnitTestMode && testProvider != null) {
      return testProvider?.takeIf { it.isEnabledConsideringEventRequirements(event) }
    }

    val allProviders = InlineCompletionProvider.extensions()
    val (rdProviders, regularProviders) = allProviders.partition { it is RemDevAggregatorInlineCompletionProvider }
    val result = regularProviders.findSafely { it.isEnabledConsideringEventRequirements(event) }
                 ?: rdProviders.findSafely { it.isEnabledConsideringEventRequirements(event) }

    if (result != null) {
      LOG.trace("[Inline Completion] Selected inline provider: $result")
    }
    return result
  }

  private inline fun <T> List<T>.findSafely(filter: (T) -> Boolean): T? {
    return firstOrNull { provider ->
      try {
        filter(provider)
      }
      catch (e: Throwable) {
        LOG.errorIfNotMessage(e)
        false
      }
    }
  }

  private fun InlineCompletionProvider.isEnabledConsideringEventRequirements(event: InlineCompletionEvent): Boolean {
    if (event is InlineCompletionEvent.WithSpecificProvider) {
      // Only [RemDevAggregatorInlineCompletionProvider] can use others' events
      if (event.providerId != this@isEnabledConsideringEventRequirements.id && this !is RemDevAggregatorInlineCompletionProvider) {
        return false
      }
    }
    val editorType = InlineCompletionEditorType.get(editor)
    if (!isEditorTypeSupported(editorType)) {
      return false
    }
    return isEnabled(event)
  }

  private suspend fun ensureDocumentAndFileSynced(project: Project, document: Document) {
    val documentManager = PsiDocumentManager.getInstance(project)
    val isCommitted = readAction { documentManager.isCommitted(document) }
    if (isCommitted) {
      // We do not need one big readAction: it's enough to have them synced at this moment
      return
    }

    suspendCancellableCoroutine { continuation ->
      application.invokeLater {
        if (project.isDisposed) {
          continuation.resumeWithException(CancellationException())
        }
        else {
          documentManager.performWhenAllCommitted {
            continuation.resume(Unit)
          }
        }
      }
    }
  }

  @RequiresEdt
  private fun InlineCompletionContext.renderElement(element: InlineCompletionElement, startOffset: Int) {
    val presentable = element.toPresentable()
    presentable.render(editor, endOffset() ?: startOffset)
    state.addElement(presentable)
  }

  @RequiresEdt
  private fun getVariantsComputer(
    variants: List<InlineCompletionVariant>,
    context: InlineCompletionContext,
    scope: CoroutineScope,
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

      override fun variantChanged(event: InlineCompletionEvent, variantIndex: Int, old: List<InlineCompletionElement>, new: List<InlineCompletionElement>) {
        ThreadingAssertions.assertEventDispatchThread()
        val oldText = old.joinToString("") { it.text }
        val newText = new.joinToString("") { it.text }
        traceBlocking(InlineCompletionEventType.Change(event, variantIndex, new, oldText.length - newText.length))
      }

      override fun variantInvalidated(event: InlineCompletionEvent, variantIndex: Int) {
        ThreadingAssertions.assertEventDispatchThread()
        traceBlocking(InlineCompletionEventType.Invalidated(event, variantIndex))
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
  @ApiStatus.Internal
  protected fun InlineCompletionSession.guardCaretModifications() {
    val listener = object : InlineSessionWiseCaretListener() {
      override var completionOffset: Int
        get() = if (!context.isDisposed) context.expectedStartOffset else -1
        set(value) {
          if (!context.isDisposed) context.expectedStartOffset = value
        }

      override val mode: Mode
        get() = if (documentChangesTracker.ignoreCaretMovements) Mode.ADAPTIVE else Mode.PROHIBIT_MOVEMENT

      override fun cancel() {
        if (!context.isDisposed) hide(context, FinishType.CARET_CHANGED)
      }
    }
    editor.caretModel.addCaretListener(listener, this)
  }

  @RequiresEdt
  private fun traceBlocking(event: InlineCompletionEventType) {
    ThreadingAssertions.assertEventDispatchThread()
    WriteIntentReadAction.run {
      eventListeners.getMulticaster().on(event)
    }
  }

  @RequiresEdt
  private suspend fun trace(event: InlineCompletionEventType) {
    coroutineToIndicator { traceBlocking(event) }
  }

  // Utils for inheritors
  // -----------------------------------

  @RequiresEdt
  @ApiStatus.Internal
  protected fun switchAndInvokeRequest(request: InlineCompletionRequest, newSession: InlineCompletionSession) {
    ThreadingAssertions.assertEventDispatchThread()
    executor.switchRequest(onJobCreated = newSession::assignJob) {
      invokeRequest(request, newSession)
    }
  }

  @ApiStatus.Internal
  protected abstract class InlineCompletionSessionManagerBase(editor: Editor) : InlineCompletionSessionManager(editor) {

    abstract fun executeHide(
      context: InlineCompletionContext,
      finishType: FinishType,
      invalidatedResult: UpdateSessionResult.Invalidated?
    )

    override fun onUpdate(session: InlineCompletionSession, result: UpdateSessionResult) {
      ThreadingAssertions.assertEventDispatchThread()
      when (result) {
        UpdateSessionResult.Emptied -> executeHide(session.context, FinishType.TYPED, null)
        UpdateSessionResult.Succeeded -> Unit
        is UpdateSessionResult.Invalidated -> executeHide(session.context, result.getInvalidationFinishType(), result)
      }
    }

    private fun UpdateSessionResult.Invalidated.getInvalidationFinishType(): FinishType {
      return when (val reason = reason) {
        is UpdateSessionResult.Invalidated.Reason.Event -> {
          when (reason.event) {
            is InlineCompletionEvent.Backspace -> FinishType.BACKSPACE_PRESSED
            else -> FinishType.INVALIDATED
          }
        }
        UpdateSessionResult.Invalidated.Reason.UnclassifiedDocumentChange -> FinishType.INVALIDATED
      }
    }
  }

  // -----------------------------------

  @TestOnly
  suspend fun awaitExecution() {
    ThreadingAssertions.assertEventDispatchThread()
    executor.awaitActiveRequest()
  }

  @ApiStatus.Internal // TODO (remove?)
  private class InlineCompletionState(
    var isInvokingEvent: Boolean = false,
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
