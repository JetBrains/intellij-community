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
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiFile
import com.intellij.util.EventDispatcher
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@ApiStatus.Experimental
class InlineCompletionHandler(private val scope: CoroutineScope) {
  private val runningJob: AtomicReference<Job?> = AtomicReference(null)
  private var lastInvocationTime = 0L
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

  fun invoke(event: InlineCompletionEvent.DocumentChange) = invokeEvent(event)
  fun invoke(event: InlineCompletionEvent.CaretMove) = invokeEvent(event)
  fun invoke(event: InlineCompletionEvent.LookupChange) = invokeEvent(event)
  fun invoke(event: InlineCompletionEvent.DirectCall) = invokeEvent(event)

  private fun invokeEvent(event: InlineCompletionEvent) {
    LOG.trace("Start processing inline event $event")
    if (isMuted.get()) {
      LOG.trace("Muted")
      return
    }

    val provider = getProvider(event) ?: return

    if (updateContextOrInvalidate(request)) {
      runningJob.getAndSet(null)?.cancel()
      return
    }

    LOG.trace("Schedule new job")
    runningJob.getAndUpdate {
      scope.launch {
        invokeRequest(provider, event)
      }
    }?.cancel()
  }

  private suspend fun invokeRequest(provider: InlineCompletionProvider, event: InlineCompletionEvent) {
    val request = event.toRequest() ?: return

    val editor = request.editor
    val offset = request.endOffset

    lastInvocationTime = System.currentTimeMillis()
    val modificationStamp = request.document.modificationStamp
    val resultFlow = request(provider, request) // .flowOn(Dispatchers.IO)

    val context = InlineCompletionContext.getOrInit(editor)

    // If you write a test and observe an infinite hang here, set [UsefulTestCase.runInDispatchThread] to false.
    withContext(Dispatchers.EDT) {
      resultFlow
        .onStart { isShowing.set(true) }
        .onEmpty {
          trace(InlineCompletionEventType.Empty)
        }
        .onCompletion {
          complete(currentCoroutineContext().isActive, editor, it, context)
        }
        .collectIndexed { index, it ->
          ensureActive()

          if (!isShowing.get() || modificationStamp != request.document.modificationStamp) {
            cancel()
            return@collectIndexed
          }

          showInlineElement(editor, it, index, offset, context)
        }
    }
  }

  suspend fun request(provider: InlineCompletionProvider, request: InlineCompletionRequest): Flow<InlineCompletionElement> {
    trace(InlineCompletionEventType.Request(lastInvocationTime, request, provider::class.java))
    return provider.getProposals(request)
  }

  private fun showInlineElement(
    editor: Editor,
    element: InlineCompletionElement,
    index: Int,
    offset: Int,
    context: InlineCompletionContext = InlineCompletionContext.getOrInit(editor)
  ) {
    trace(InlineCompletionEventType.Show(element, index))
    context.renderElement(element, offset)
  }

  private fun InlineCompletionContext.renderElement(element: InlineCompletionElement, startOffset: Int) {
    withSafeMute {
      element.render(editor, lastOffset ?: startOffset)
      addElement(element)
    }
  }

  fun insert(editor: Editor, context: InlineCompletionContext = InlineCompletionContext.getOrInit(editor)) {
    trace(InlineCompletionEventType.Insert)

    withSafeMute {
      val offset = context.lastOffset ?: return@withSafeMute
      val currentCompletion = context.lineToInsert

      editor.document.insertString(offset, currentCompletion)
      editor.caretModel.moveToOffset(offset + currentCompletion.length)
    }

    LookupManager.getActiveLookup(editor)?.hideLookup(false) //TODO: remove this
    hide(editor, false, context)
  }

  fun hide(editor: Editor, explicit: Boolean, context: InlineCompletionContext) {
    if (!context.isCurrentlyDisplayingInlays) return
    trace(InlineCompletionEventType.Hide(explicit))

    withSafeMute {
      isShowing.set(false)
      InlineCompletionContext.remove(editor)
    }
  }

  fun complete(isActive: Boolean, editor: Editor, cause: Throwable?, context: InlineCompletionContext) {
    trace(InlineCompletionEventType.Completion(cause, isActive))
    isShowing.set(false)
    if (cause != null) {
      hide(editor, false, context)
    }
  }

  /**
   * @return `true` if update was successful. Otherwise, [hide] is invoked to invalidate the current context.
   */
  @RequiresBlockingContext
  private fun updateContextOrInvalidate(request: InlineCompletionRequest): Boolean {
    return InlineCompletionContext.getOrNull(request.editor)?.let { context ->
      context.updateWithEvent(request.event).also { success ->
        if (!success) {
          hide(request.editor, false, context)
        }
      }
    } ?: false
  }

  @RequiresBlockingContext
  private fun InlineCompletionContext.updateWithEvent(event: InlineCompletionEvent): Boolean {
    if (!isCurrentlyDisplayingInlays) {
      return false
    }
    return when (event) {
      is InlineCompletionEvent.LookupChange -> {
        // If we have elements to show and lookup hides/appears, then we do not need to hide gray text
        true
      }
      is InlineCompletionEvent.DocumentChange -> {
        // We may add new prefix to the gray text if it matches, and do not re-call providers
        event.getFragmentToAppendPrefix()?.let { applyPrefixAppend(it) } ?: false
      }
      else -> false
    }
  }

  @RequiresBlockingContext
  private fun InlineCompletionContext.applyPrefixAppend(fragment: CharSequence): Boolean {
    if (!lineToInsert.startsWith(fragment) || lineToInsert == fragment.toString()) {
      return false
    }
    val newElements = truncateElementsPrefix(elements, fragment.length)
    val startOffset = checkNotNull(startOffset)

    application.invokeAndWait {
      editor.inlayModel.execute(true) {
        clear()
        newElements.forEach {
          this@applyPrefixAppend.renderElement(it, startOffset)
        }
      }
    }
    return true
  }

  private fun truncateElementsPrefix(elements: List<InlineCompletionElement>, length: Int): List<InlineCompletionElement> {
    var currentLength = length
    val newFirstElementIndex = elements.indexOfFirst {
      currentLength -= it.text.length
      currentLength < 0 // Searching for the element that exceeds [length]
    }
    assert(newFirstElementIndex >= 0)
    currentLength += elements[newFirstElementIndex].text.length
    val newFirstElement = InlineCompletionElement(elements[newFirstElementIndex].text.drop(currentLength))
    return listOf(newFirstElement) + elements.drop(newFirstElementIndex + 1)
  }

  private fun InlineCompletionEvent.DocumentChange.getFragmentToAppendPrefix(): CharSequence? {
    val documentEvent = event
    val newFragment = documentEvent.newFragment
    val oldFragment = documentEvent.oldFragment

    return newFragment.takeIf { it.isNotEmpty() && oldFragment.isEmpty() }
  }

  private fun shouldShowPlaceholder(): Boolean = Registry.`is`("inline.completion.show.placeholder")

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

  companion object {
    private val LOG = thisLogger()
    val KEY = Key.create<InlineCompletionHandler>("inline.completion.handler")

    fun getOrNull(editor: Editor) = editor.getUserData(KEY)

    val isMuted: AtomicBoolean = AtomicBoolean(false)
    val isShowing: AtomicBoolean = AtomicBoolean(false)

    fun withSafeMute(block: () -> Unit) {
      mute()
      try {
        block()
      }
      finally {
        unmute()
      }
    }

    fun mute(): Unit = isMuted.set(true)
    fun unmute(): Unit = isMuted.set(false)

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
