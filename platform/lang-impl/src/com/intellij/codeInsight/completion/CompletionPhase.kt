// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.completion.CompletionPhase.CommittingDocuments.CommittingState.*
import com.intellij.codeInsight.completion.CompletionPhase.Companion.NoCompletion
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl.Companion.assertPhase
import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.ClientId.Companion.withExplicitClientId
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Pair
import com.intellij.patterns.ElementPattern
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.psi.util.PsiUtilCore
import com.intellij.ui.HintListener
import com.intellij.ui.LightweightHint
import com.intellij.util.ThreeState
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.ui.EDT
import com.intellij.util.ui.accessibility.ScreenReader
import org.jetbrains.annotations.ApiStatus
import java.awt.event.FocusEvent
import java.util.concurrent.Callable
import java.util.function.Consumer
import javax.swing.SwingUtilities
import kotlin.math.max

/**
 * Code completion lifecycle within an IntelliJ-based IDE.
 *
 * Phases:
 *  *  [NoCompletion] - no completion is running
 *  *  [CommittingDocuments] - preparing the document for completion including committing all the project documents
 *  *  [Synchronous] - completion is computing candidates synchronously.
 *  *  [BgCalculation] - inferring candidates on background
 *  *  [ItemsCalculated] - completion items have been calculated
 *  *  [EmptyAutoPopup] - completion was triggered by typing, but no completion items were found, and the lookup is not shown
 *  *  [InsertedSingleItem] - a single item was found, and it was inserted into the document
 *  *  [NoSuggestionsHint] - candidate inference has finished, but no candidates were found and a warning "no suggestions found" is shown.
 *
 *  @See CompletionServiceImpl.completionPhase
 */
sealed class CompletionPhase @ApiStatus.Internal constructor(
  @JvmField
  val indicator: CompletionProgressIndicator?
) : Disposable {

  abstract fun newCompletionStarted(time: Int, repeated: Boolean): Int

  override fun dispose() {}

  /** see doc of [CompletionPhase] */
  class CommittingDocuments private constructor(
    indicator: CompletionProgressIndicator?,
    editor: Editor,
    private val event: TypedEvent?
  ) : CompletionPhase(indicator) {
    @JvmField
    internal var replaced: Boolean = false

    private val myTracker: ActionTracker = ActionTracker(editor, this)
    private var myState: CommittingState = InProgress(1) // access available on EDT only

    fun ignoreCurrentDocumentChange() {
      myTracker.ignoreCurrentDocumentChange()
    }

    /**
     * @return `true` if the phase won't trigger the completion process.
     */
    @get:ApiStatus.Internal
    val isExpired: Boolean
      get() = myTracker.hasAnythingHappened() || myState !is InProgress

    /**
     * Several typedHandlers can request auto-popup completion during processing of a single event.
     * We need to trigger read-action for them independently because they can have different conditions for starting completion.
     */
    @RequiresEdt
    private fun addRequest() {
      when (val cur = myState) {
        Cancelled, Disposed, Success -> {
          LOG.error("Cannot add request for a finished phase: $cur")
          return
        }
        is InProgress -> {
          myState = InProgress(cur.requests + 1)
        }
      }
      LOG.trace { "Increment request count :: new myState=$myState" }
    }

    /**
     * the current request was not successful (most likely because of the failed condition), but other requests still have a chance to succeed.
     */
    @RequiresEdt
    private fun cancelThisRequest() {
      when (val cur = myState) {
        Disposed, Cancelled, Success -> {
          /* do nothing */
        }

        is InProgress -> {
          val requests = cur.requests
          if (requests > 1) {
            myState = InProgress(cur.requests - 1)
          }
          else {
            if (requests < 1) {
              LOG.error("Invalid request count: $requests")
            }
            myState = Cancelled
          }
        }
      }

      LOG.trace { "Cancel request :: new myState=$myState" }
    }

    /**
     * At least one of the requests succeeded, the completion process has been started, no more requests are necessary.
     */
    @RequiresEdt
    private fun requestCompleted() {
      LOG.trace { "Request completed" }
      LOG.assertTrue(myState is InProgress, "myState=$myState")
      myState = Success
    }

    override fun newCompletionStarted(time: Int, repeated: Boolean): Int {
      return time
    }

    override fun dispose() {
      LOG.trace { "Dispose completion phase: $this" }
      myState = Disposed
      if (!replaced && indicator != null) {
        indicator.closeAndFinish(true)
      }
    }

    override fun toString(): String {
      return "CommittingDocuments{hasIndicator=${indicator != null}}"
    }

    /**
     * InProgress(1) -> InProgress(2...Xxx) -> InProgress(1) -> Cancelled -> Disposed
     *      |--------------|------------------------|---------> Success-------^
     *
     */
    private sealed interface CommittingState {
      object Success : CommittingState
      object Cancelled : CommittingState
      object Disposed : CommittingState
      data class InProgress(val requests: Int) : CommittingState
    }

    @ApiStatus.Internal
    companion object {
      private val ourExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Completion Preparation", 1)

      @ApiStatus.Internal
      @JvmStatic
      fun create(editor: Editor, indicator: CompletionProgressIndicator?): CommittingDocuments {
        return CommittingDocuments(indicator, editor, null)
      }

      /**
       * Schedules completion process for the given [editor] with [completionType] and [condition] for the state of the file.
       * The condition is checked after committing all the documents inside a read-action.
       *
       * @param editor          editor where we start completion
       * @param completionType  completion type
       * @param condition       condition for the state of the file. If the condition is not met after committing the document, completion will not be started.
       * @param project         the current project
       * @param prevIndicator   the completion indicator, if any. The indicator exists if completion was already started and we restart it with new parameters.
       */
      @ApiStatus.Internal
      @RequiresEdt
      @JvmStatic
      fun scheduleAsyncCompletion(
        editor: Editor,
        completionType: CompletionType,
        condition: Condition<in PsiFile>?,
        project: Project,
        prevIndicator: CompletionProgressIndicator?
      ) {
        LOG.trace("Schedule async completion")

        ThreadingAssertions.assertEventDispatchThread()

        val topLevelEditor = InjectedLanguageEditorUtil.getTopLevelEditor(editor)
        val offset = topLevelEditor.getCaretModel().offset

        val phase = getCompletionPhase(prevIndicator, topLevelEditor, editor.getUserData(AUTO_POPUP_TYPED_EVENT))

        val autopopup = prevIndicator == null || prevIndicator.isAutopopupCompletion

        ReadAction
          .nonBlocking(Callable {
            prepareEditorAndDocumentsForAsyncCompletion(phase, topLevelEditor, condition, offset, autopopup, project)
          })
          .withDocumentsCommitted(project)
          .expireWith(phase)
          .finishOnUiThread(ModalityState.current(), Consumer { completionEditor: Editor? ->
            startAsyncCompletionIfNotExpired(phase, completionEditor, completionType, autopopup, project)
          })
          .submit(ourExecutor)
      }

      @RequiresReadLock
      private fun prepareEditorAndDocumentsForAsyncCompletion(
        phase: CommittingDocuments,
        topLevelEditor: Editor,
        condition: Condition<in PsiFile>?,
        offset: Int,
        autopopup: Boolean,
        project: Project,
      ): Editor? {
        if (phase.myState !is InProgress) {
          LOG.trace { "Phase is expired ${phase.myState}" }
          return null
        }

        LOG.trace { "Start non-blocking read action :: phase=${phase.replaced}" }

        // retrieve the injected file from scratch since our typing might have destroyed the old one completely
        val topLevelFile = PsiDocumentManager.getInstance(project).getPsiFile(topLevelEditor.getDocument())
        val completionEditor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(topLevelEditor, topLevelFile, offset)
        val completionFile = PsiDocumentManager.getInstance(project).getPsiFile(completionEditor.getDocument())

        if (completionFile == null || autopopup && shouldSkipAutoPopup(completionEditor, completionFile) || condition != null && !condition.value(completionFile)) {
          LOG.trace { "File is null or should skip auto popup or condition is not met :: file=$completionFile, condition=$condition" }
          return null
        }

        loadContributorsOutsideEdt(completionEditor, completionFile)
        return completionEditor
      }

      @RequiresEdt
      private fun startAsyncCompletionIfNotExpired(
        phase: CommittingDocuments,
        completionEditor: Editor?,
        completionType: CompletionType,
        autopopup: Boolean,
        project: Project,
      ) {
        LOG.trace { "Finish on UI thread :: completionEditor=$completionEditor" }

        if (phase != CompletionServiceImpl.completionPhase) {
          LOG.trace { "Phase is expired :: myPhase=${phase}, completionPhase=${CompletionServiceImpl.completionPhase} " }
          return
        }

        if (phase.myState !is InProgress) {
          LOG.trace { "Phase is expired :: myState=${phase.myState}" }
          return
        }

        if (completionEditor == null) {
          // preparation has failed for this specific request. We must cancel only this request.
          // If no other requests are pending, we can cancel the phase altogether.

          LOG.trace { "Setting NoCompletion phase :: completionEditor=$completionEditor, expirationReason=editor is null" }
          phase.cancelThisRequest()
          if (phase.myState == Cancelled) {
            CompletionServiceImpl.setCompletionPhase(NoCompletion)
          }
          return
        }

        if (phase.myTracker.hasAnythingHappened() && (phase.indicator == null || !phase.indicator.lookup.isShown)) {
          // activity has happened in the editor. We must cancel all the requests altogether.
          // but we want to do this only if lookup is not shown yet. because if it's shown, the lookup is going to handle close by itself.
          LOG.trace { "Setting NoCompletion phase :: completionEditor=$completionEditor, expirationReason=${phase.myTracker.describeChangeEvent()}" }
          phase.cancelPhase()
          CompletionServiceImpl.setCompletionPhase(NoCompletion)
          return
        }

        LOG.trace { "Starting completion phase :: completionEditor=$completionEditor" }

        phase.requestCompleted()
        val time = phase.indicator?.invocationCount ?: 0

        val customId = completionEditor.getUserData(CUSTOM_CODE_COMPLETION_ACTION_ID) ?: IdeActions.ACTION_CODE_COMPLETION
        val handler = CodeCompletionHandlerBase.createHandler(completionType, false, autopopup, false, customId)
        handler.invokeCompletion(project, completionEditor, time, false)
      }

      @RequiresEdt
      private fun getCompletionPhase(
        prevIndicator: CompletionProgressIndicator?,
        topLevelEditor: Editor,
        event: TypedEvent?
      ): CommittingDocuments {
        if (event != null) {
          val currentPhase = CompletionServiceImpl.completionPhase
          if (currentPhase is CommittingDocuments && !currentPhase.isExpired && event == currentPhase.event) {
            LOG.assertTrue(prevIndicator == currentPhase.indicator, "Indicators must match. prevIndicator=$prevIndicator, currentPhase=$currentPhase, currentPhase.indicator=${currentPhase.indicator}")

            currentPhase.addRequest()
            return currentPhase
          }
        }
        val phase = CommittingDocuments(prevIndicator, topLevelEditor, event)
        CompletionServiceImpl.setCompletionPhase(phase)
        phase.ignoreCurrentDocumentChange()
        return phase
      }

      @ApiStatus.Internal
      @RequiresBackgroundThread
      @JvmStatic
      fun loadContributorsOutsideEdt(editor: Editor, file: PsiFile) {
        ThreadingAssertions.assertBackgroundThread()
        CompletionContributor.forLanguage(PsiUtilCore.getLanguageAtOffset(file, editor.getCaretModel().offset))
      }

      @ApiStatus.Internal
      @JvmStatic
      fun shouldSkipAutoPopup(editor: Editor, psiFile: PsiFile): Boolean {
        val offset = editor.getCaretModel().offset
        val psiOffset = max(0, offset - 1)

        val elementAt = psiFile.findElementAt(psiOffset) ?: return true

        val language = PsiUtilCore.findLanguageFromElement(elementAt)

        for (confidence in CompletionConfidenceEP.forLanguage(language)) {
          try {
            val result = confidence.shouldSkipAutopopup(editor, elementAt, psiFile, offset)
            if (result != ThreeState.UNSURE) {
              LOG.debug { "$confidence has returned shouldSkipAutopopup=$result" }
              return result == ThreeState.YES
            }
          }
          catch (e: IndexNotReadyException) {
            LOG.debug(e)
            return true
          }
        }
        return false
      }
    }

    @RequiresEdt
    private fun cancelPhase() {
      when (myState) {
        Cancelled, Disposed, Success -> {}
        is InProgress -> myState = Cancelled
      }
    }
  }

  /** see doc of [CompletionPhase] */
  class Synchronous internal constructor(indicator: CompletionProgressIndicator) : CompletionPhase(indicator) {
    override fun newCompletionStarted(time: Int, repeated: Boolean): Int {
      assertPhase(NoCompletion.javaClass) // will fail and log valuable info
      CompletionServiceImpl.setCompletionPhase(NoCompletion)
      return time
    }
  }

  /** see doc of [CompletionPhase] */
  class BgCalculation internal constructor(indicator: CompletionProgressIndicator) : CompletionPhase(indicator) {
    @JvmField
    internal var modifiersChanged: Boolean = false
    private val ownerId = ClientId.current

    init {
      restartOnWriteAction()
      cancelOnEditorLoseFocus(indicator)
    }

    private fun restartOnWriteAction() {
      ApplicationManager.getApplication().addApplicationListener(object : ApplicationListener {
        override fun beforeWriteActionStart(action: Any) {
          if (!indicator!!.lookup.isLookupDisposed && !indicator.isCanceled) {
            withExplicitClientId(ownerId) {
              indicator.cancel()
              if (EDT.isCurrentThreadEdt()) {
                indicator.scheduleRestart()
              }
              else {
                // this branch is possible because completion can be canceled on background write action
                ApplicationManager.getApplication().invokeLater(
                  /* runnable = */ { indicator.scheduleRestart() },

                  // since we break the synchronous execution here, it is possible that some other EDT event finishes completion before us
                  // in this case, the current indicator becomes obsolete, and we don't need to reschedule the session anymore

                  /* expired = */ { CompletionServiceImpl.currentCompletionProgressIndicator != indicator }
                )
              }
            }
          }
        }
      }, this)
    }

    private fun cancelOnEditorLoseFocus(indicator: CompletionProgressIndicator) {
      if (indicator.isAutopopupCompletion) {
        // lookup is not visible, we have to check ourselves if the editor retains focus
        (indicator.editor as EditorEx).addFocusListener(object : FocusChangeListener {
          override fun focusLost(editor: Editor, event: FocusEvent) {
            // When ScreenReader is active, the lookup gets focus on show, and we should not close it.
            if (ScreenReader.isActive() &&
                event.getOppositeComponent() != null &&  // Check the opposite is in the lookup ancestor
                SwingUtilities.getWindowAncestor(event.getOppositeComponent()) === SwingUtilities.getWindowAncestor(indicator.lookup.component)
            ) {
              return
            }
            indicator.closeAndFinish(true)
          }
        }, this)
      }
    }

    override fun newCompletionStarted(time: Int, repeated: Boolean): Int {
      indicator!!.closeAndFinish(false)
      return indicator.nextInvocationCount(time, repeated)
    }
  }

  /** see doc of [CompletionPhase] */
  class ItemsCalculated internal constructor(indicator: CompletionProgressIndicator) : CompletionPhase(indicator) {
    override fun newCompletionStarted(time: Int, repeated: Boolean): Int {
      requireNotNull(indicator) { "`ItemsCalculated#indicator` is not-null as its constructor accepts not-null `indicator`" }.closeAndFinish(false)
      return indicator.nextInvocationCount(time, repeated)
    }
  }

  abstract class ZombiePhase internal constructor(indicator: CompletionProgressIndicator?) : CompletionPhase(indicator) {
    internal fun expireOnAnyEditorChange(editor: Editor) {
      editor.getDocument().addDocumentListener(object : DocumentListener {
        override fun beforeDocumentChange(e: DocumentEvent) {
          CompletionServiceImpl.setCompletionPhase(NoCompletion)
        }
      }, this)
      editor.getSelectionModel().addSelectionListener(object : SelectionListener {
        override fun selectionChanged(e: SelectionEvent) {
          CompletionServiceImpl.setCompletionPhase(NoCompletion)
        }
      }, this)
      editor.getCaretModel().addCaretListener(object : CaretListener {
        override fun caretPositionChanged(e: CaretEvent) {
          CompletionServiceImpl.setCompletionPhase(NoCompletion)
        }
      }, this)
    }
  }

  /** see doc of [CompletionPhase] */
  class InsertedSingleItem internal constructor(
    indicator: CompletionProgressIndicator,
    @JvmField
    val restorePrefix: Runnable
  ) : ZombiePhase(indicator) {
    init {
      expireOnAnyEditorChange(indicator.editor)
    }

    override fun newCompletionStarted(time: Int, repeated: Boolean): Int {
      CompletionServiceImpl.setCompletionPhase(NoCompletion)
      if (repeated) {
        indicator!!.restorePrefix(restorePrefix)
      }
      return indicator!!.nextInvocationCount(time, repeated)
    }
  }

  /** see doc of [CompletionPhase] */
  class NoSuggestionsHint internal constructor(hint: LightweightHint?, indicator: CompletionProgressIndicator) : ZombiePhase(indicator) {
    init {
      expireOnAnyEditorChange(indicator.editor)
      if (hint != null) {
        val hintListener = HintListener { CompletionServiceImpl.setCompletionPhase(NoCompletion) }
        hint.addHintListener(hintListener)
        Disposer.register(this, Disposable { hint.removeHintListener(hintListener) })
      }
    }

    override fun newCompletionStarted(time: Int, repeated: Boolean): Int {
      CompletionServiceImpl.setCompletionPhase(NoCompletion)
      return indicator!!.nextInvocationCount(time, repeated)
    }
  }

  /** see doc of [CompletionPhase] */
  class EmptyAutoPopup internal constructor(
    editor: Editor,
    private val restartingPrefixConditions: Set<Pair<Int, ElementPattern<String>>>
  ) : ZombiePhase(null) {
    private val myTracker: ActionTracker = ActionTracker(editor, this)
    private val myEditor: Editor = editor

    fun allowsSkippingNewAutoPopup(editor: Editor, toType: Char): Boolean {
      if (myEditor === editor && !myTracker.hasAnythingHappened() && !CompletionProgressIndicator.shouldRestartCompletion(editor, restartingPrefixConditions, toType.toString())) {
        myTracker.ignoreCurrentDocumentChange()
        return true
      }
      return false
    }

    override fun newCompletionStarted(time: Int, repeated: Boolean): Int {
      CompletionServiceImpl.setCompletionPhase(NoCompletion)
      return time
    }
  }

  /** see doc of [CompletionPhase] */
  private object NoCompletionImpl: CompletionPhase(null) {
    override fun newCompletionStarted(time: Int, repeated: Boolean): Int {
      return time
    }

    override fun toString(): String {
      return "NoCompletion"
    }
  }

  @ApiStatus.Internal
  companion object {
    @ApiStatus.Internal
    @JvmField
    internal val AUTO_POPUP_TYPED_EVENT: Key<TypedEvent> = Key.create("AutoPopupTypedEvent")

    @ApiStatus.Internal
    @JvmField
    val CUSTOM_CODE_COMPLETION_ACTION_ID: Key<String> = Key.create("CodeCompletionActionID")

    @JvmField
    val NoCompletion: CompletionPhase = NoCompletionImpl
  }
}

private val LOG = Logger.getInstance(CompletionPhase::class.java)
