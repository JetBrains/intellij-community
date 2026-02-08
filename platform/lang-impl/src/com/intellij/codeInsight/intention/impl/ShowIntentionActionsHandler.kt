// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.IntentionsUI
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.codeInsight.intention.IntentionSource
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewEditor
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewUnsupportedOperationException
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInspection.SuppressIntentionActionFromFix
import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.featureStatistics.FeatureUsageTrackerImpl
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.injected.editor.DocumentWindow
import com.intellij.injected.editor.EditorWindow
import com.intellij.internal.statistic.IntentionFUSCollector
import com.intellij.lang.LangBundle
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.ModCommandService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiFileRange
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtilBase
import com.intellij.psi.stubs.StubInconsistencyReporter.SourceOfCheck
import com.intellij.psi.stubs.StubTextInconsistencyException
import com.intellij.util.SlowOperations
import com.intellij.util.ThreeState
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.Callable

private val LOG = logger<ShowIntentionActionsHandler>()

open class ShowIntentionActionsHandler : CodeInsightActionHandler {
  companion object {
    @ApiStatus.Internal
    @JvmStatic
    @RequiresEdt
    fun calcCachedIntentions(
      project: Project,
      editor: Editor,
      file: PsiFile,
    ): CachedIntentions {
      ThreadingAssertions.assertEventDispatchThread()
      check(!ApplicationManager.getApplication().isWriteAccessAllowed()) { "must not wait for intentions inside write action" }
      return ApplicationManager.getApplication().runWriteIntentReadAction(ThrowableComputable {
        val prioritizedRunnable = ThrowableComputable<CachedIntentions, RuntimeException> {
          ProgressManager.getInstance().computePrioritized(ThrowableComputable {
            DaemonCodeAnalyzerImpl.waitForLazyQuickFixesUnderCaret(project, editor)
            ApplicationManager.getApplication().runReadAction(ThrowableComputable {
              CachedIntentions.createAndUpdateActions(project, file, editor, ShowIntentionsPass.getActionsToShow(editor, file))
            })
          })
        }

        val process = if (DumbService.getInstance(project).isAlternativeResolveEnabled) {
          ThrowableComputable {
            DumbService.getInstance(project).computeWithAlternativeResolveEnabled(prioritizedRunnable)
          }
        }
        else {
          prioritizedRunnable
        }

        val progressTitle = CodeInsightBundle.message("progress.title.searching.for.context.actions")
        ProgressManager.getInstance().runProcessWithProgressSynchronously(process, progressTitle, true, project)
      })
    }

    @Deprecated("Use {@link #availableFor(PsiFile, Editor, int, IntentionAction)} instead.")
    @JvmStatic
    fun availableFor(psiFile: PsiFile, editor: Editor, action: IntentionAction): Boolean {
      return availableFor(psiFile, editor, editor.getCaretModel().offset, action)
    }

    @JvmStatic
    fun availableFor(psiFile: PsiFile, editor: Editor, offset: Int, action: IntentionAction): Boolean {
      if (!psiFile.isValid() || editor.isViewer()) {
        return false
      }

      try {
        val project = psiFile.getProject()
        val action = IntentionActionDelegate.unwrap(action)

        if (action is SuppressIntentionActionFromFix) {
          val shouldBeAppliedToInjectionHost = action.isShouldBeAppliedToInjectionHost()
          if (editor is EditorWindow && shouldBeAppliedToInjectionHost == ThreeState.YES) {
            return false
          }
          if (editor !is EditorWindow && shouldBeAppliedToInjectionHost == ThreeState.NO) {
            return false
          }
        }

        if (action is PsiElementBaseIntentionAction) {
          if (!action.checkFile(psiFile)) {
            return false
          }
          val leaf = psiFile.findElementAt(offset)
          if (leaf == null || !action.isAvailable(project, editor, leaf)) {
            return false
          }
        }
        else {
          if (ApplicationManager.getApplication().isDispatchThread()) {
            val modCommand = action.asModCommandAction()
            if (modCommand != null) {
              val actionContext = ActionContext.from(editor, psiFile)
              val computable = ThrowableComputable<Boolean, RuntimeException> {
                  ReadAction.nonBlocking(Callable { modCommand.getPresentation(actionContext) != null })
                    .expireWith(project)
                    .executeSynchronously()
                }
              return ProgressManager.getInstance().runProcessWithProgressSynchronously(
                computable, LangBundle.message("command.check.availability.for", modCommand.getFamilyName()), true, project)
            }
          }
          return action.isAvailable(project, editor, psiFile)
        }
      }
      catch (_: IndexNotReadyException) {
        return false
      }
      catch (_: IntentionPreviewUnsupportedOperationException) {
        // check action availability can be invoked on a mock editor and may produce exceptions
        return false
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (e: Throwable) {
        // avoid breaking highlighting when an exception is thrown from an intention
        LOG.error(e)
        return false
      }
      return true
    }

    @ApiStatus.Internal
    @JvmStatic
    fun chooseBetweenHostAndInjected(
      hostFile: PsiFile,
      hostEditor: Editor,
      hostOffset: Int,
      predicate: (PsiFile, Editor, Int) -> Boolean,
    ): Pair<PsiFile, Editor>? {
      val injectedFile = InjectedLanguageUtilBase.findInjectedPsiNoCommit(hostFile, hostOffset)
      return chooseBetweenHostAndInjected(
        hostFile = hostFile,
        hostEditor = hostEditor,
        hostOffset = hostOffset,
        injectedFile = injectedFile,
        predicate = predicate,
      )
    }

    @JvmStatic
    fun chooseBetweenHostAndInjected(
      hostFile: PsiFile,
      hostEditor: Editor,
      hostOffset: Int,
      injectedFile: PsiFile?,
      predicate: (PsiFile, Editor, Int) -> Boolean,
    ): Pair<PsiFile, Editor>? {
      try {
        var editorToApply: Editor? = null
        var fileToApply: PsiFile? = null

        if (injectedFile != null && hostEditor !is IntentionPreviewEditor) {
          val injectedEditor = InjectedLanguageUtil.getInjectedEditorForInjectedFile(hostEditor, injectedFile)
          if (hostEditor !== injectedEditor && injectedEditor is EditorWindow) {
            val injectedOffset = injectedEditor.logicalPositionToOffset(
              injectedEditor.hostToInjected(hostEditor.offsetToLogicalPosition(hostOffset)))
            if (predicate(injectedFile, injectedEditor, injectedOffset)) {
              editorToApply = injectedEditor
              fileToApply = injectedFile
            }
          }
        }

        if (editorToApply == null && predicate(hostFile, hostEditor, hostOffset)) {
          editorToApply = hostEditor
          fileToApply = hostFile
        }
        return if (editorToApply == null) null else Pair(fileToApply, editorToApply)
      }
      catch (e: IntentionPreviewUnsupportedOperationException) {
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          throw e
        }
        return null
      }
    }

    /**
     * Chooses a file and editor between host and injected files for the given intention action and invokes the action within a command.
     */
    @JvmStatic
    fun chooseActionAndInvoke(
      hostFile: PsiFile,
      hostEditor: Editor?,
      action: IntentionAction,
      @NlsContexts.Command commandName: @NlsContexts.Command String,
      source: IntentionSource,
    ): Boolean {
      return chooseActionAndInvoke(
        hostFile = hostFile,
        hostEditor = hostEditor,
        action = action,
        commandName = commandName,
        fixOffset = -1,
        source = source,
      )
    }

    /**
     * Chooses a file and editor between host and injected files for the given intention action and invokes the action within a command.
     */
    @JvmOverloads
    @JvmStatic
    fun chooseActionAndInvoke(
      hostFile: PsiFile,
      hostEditor: Editor?,
      action: IntentionAction,
      @NlsContexts.Command commandName: @NlsContexts.Command String,
      fixOffset: Int = -1,
      source: IntentionSource = IntentionSource.OTHER,
    ): Boolean {
      (FeatureUsageTracker.getInstance() as FeatureUsageTrackerImpl).fixesStats.registerInvocation()

      SlowOperations.startSection(SlowOperations.ACTION_PERFORM).use {
        val project = hostFile.getProject()
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val commandAction = action.asModCommandAction()
        if (commandAction != null) {
          invokeCommandAction(hostFile, hostEditor, commandName, commandAction, fixOffset, source)
        }
        else {
          val pair = chooseFileForAction(hostFile, hostEditor, action) ?: return false
          CommandProcessor.getInstance().executeCommand(
            project,
            {
              var maybeInjectedFixOffset = fixOffset
              val psiFile = pair.first
              val editor = pair.second
              if (maybeInjectedFixOffset != -1) {
                val document = editor.getDocument()
                if (document is DocumentWindow) {
                  maybeInjectedFixOffset = document.hostToInjected(fixOffset)
                }
              }
              invokeIntention(action, editor, psiFile, maybeInjectedFixOffset, source)
            },
            commandName,
            null
          )
          checkPsiTextConsistency(hostFile)
        }
      }
      return true
    }

    private fun checkPsiTextConsistency(hostFile: PsiFile) {
      if (Registry.`is`("ide.check.stub.text.consistency") ||
          ApplicationManager.getApplication().isUnitTestMode() && !ApplicationManagerEx.isInStressTest()) {
        if (hostFile.isValid()) {
          StubTextInconsistencyException.checkStubTextConsistency(hostFile, SourceOfCheck.DeliberateAdditionalCheckInIntentions)
        }
      }
    }

    @JvmStatic
    fun chooseFileForAction(
      hostFile: PsiFile,
      hostEditor: Editor?,
      action: IntentionAction,
    ): Pair<PsiFile, Editor>? {
      if (hostEditor == null) {
        return Pair(hostFile, null)
      }

      return chooseBetweenHostAndInjected(
        hostFile = hostFile,
        hostEditor = hostEditor,
        hostOffset = hostEditor.getCaretModel().offset,
        predicate = { psiFile, editor, offset -> availableFor(psiFile, editor, offset, action) }
      )
    }
  }

  override fun invoke(project: Project, editor: Editor, psiFile: PsiFile) {
    invoke(project = project, editor = editor, file = psiFile, showFeedbackOnEmptyMenu = false)
  }

  fun invoke(project: Project, editor: Editor, file: PsiFile, showFeedbackOnEmptyMenu: Boolean) {
    var editor = editor
    var file = file
    val start = System.currentTimeMillis()
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    if (editor is EditorWindow) {
      editor = editor.getDelegate()
      file = InjectedLanguageManager.getInstance(file.getProject()).getTopLevelFile(file)
    }

    val lookup = LookupManager.getActiveLookup(editor)
    if (lookup != null) {
      lookup.showElementActions(null)
      return
    }

    if (!LightEdit.owns(project)) {
      val codeAnalyzer = DaemonCodeAnalyzer.getInstance(project)
      // let auto import complete
      CommandProcessor.getInstance().runUndoTransparentAction { codeAnalyzer.autoImportReferenceAtCursor(editor, file) }
    }

    IntentionsUI.getInstance(project).hide()

    if (HintManagerImpl.getInstanceImpl().performCurrentQuestionAction()) {
      return
    }

    // intentions check isWritable before modification: if (!file.isWritable()) return;
    val state = TemplateManagerImpl.getTemplateState(editor)
    if (state != null && !state.isFinished) {
      CommandProcessor.getInstance().executeCommand(project, { state.gotoEnd(false) },
                                                    LangBundle.message("command.name.finish.template"), null)
    }

    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE)
    showIntentionHint(project, editor, file, showFeedbackOnEmptyMenu)
    val elapsed = System.currentTimeMillis() - start
    IntentionFUSCollector.reportPopupDelay(project, elapsed, file.getFileType())
  }

  protected open fun showIntentionHint(
    project: Project,
    editor: Editor,
    file: PsiFile,
    showFeedbackOnEmptyMenu: Boolean,
  ) {
    val cachedIntentions = calcCachedIntentions(project, editor, file)
    cachedIntentions.wrapAndUpdateGutters()
    if (cachedIntentions.getAllActions().isEmpty()) {
      showEmptyMenuFeedback(editor, showFeedbackOnEmptyMenu)
    }
    else {
      editor.getScrollingModel().runActionOnScrollingFinished {
        IntentionHintComponent.showIntentionHint(project, file, editor, true, cachedIntentions)
      }
    }
  }

  override fun startInWriteAction(): Boolean = false
}

private fun showEmptyMenuFeedback(editor: Editor, showFeedbackOnEmptyMenu: Boolean) {
  if (showFeedbackOnEmptyMenu) {
    HintManager.getInstance().showInformationHint(editor, LangBundle.message("hint.text.no.context.actions.available.at.this.location"))
  }
}

private fun invokeIntention(
  action: IntentionAction,
  editor: Editor?,
  file: PsiFile,
  fixOffset: Int,
  source: IntentionSource,
) {
  IntentionFUSCollector.record(file.getProject(), action, file.getLanguage(), editor, fixOffset, source)
  val elementToMakeWritable = action.getElementToMakeWritable(file)
  if (elementToMakeWritable != null && !FileModificationService.getInstance().preparePsiElementsForWrite(elementToMakeWritable)) {
    return
  }

  var originalOffset: SmartPsiFileRange? = null
  if (editor != null && fixOffset >= 0) {
    originalOffset = SmartPointerManager.getInstance(file.getProject())
      .createSmartPsiFileRangePointer(file, TextRange.from(editor.getCaretModel().getOffset(), 0))
    editor.getCaretModel().moveToOffset(fixOffset)
  }
  try {
    if (action.startInWriteAction()) {
      ApplicationManager.getApplication().runWriteAction { action.invoke(file.getProject(), editor, file) }
    }
    else {
      action.invoke(file.getProject(), editor, file)
    }
  }
  finally {
    if (originalOffset?.getRange() != null &&
        editor!!.getCaretModel().offset == fixOffset &&
        TemplateManager.getInstance(file.getProject()).getActiveTemplate(editor) == null) {
      editor.getCaretModel().moveToOffset(originalOffset.getRange()!!.getStartOffset())
    }
  }
}

private fun invokeCommandAction(
  hostFile: PsiFile,
  hostEditor: Editor?,
  @NlsContexts.Command commandName: @NlsContexts.Command String,
  commandAction: ModCommandAction, fixOffset: Int,
  source: IntentionSource,
) {
  val contextAndCommand = ModCommandService.getInstance().chooseFileAndPerform(hostFile, hostEditor, commandAction, fixOffset) ?: return
  val context = contextAndCommand.context
  val project = context.project
  IntentionFUSCollector.record(project, commandAction, context.file.getLanguage(), hostEditor, fixOffset, source)
  CommandProcessor.getInstance().executeCommand(project, { contextAndCommand.executeInteractively(hostEditor) }, commandName, null)
}
