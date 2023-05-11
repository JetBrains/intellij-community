// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.preview

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.codeInsight.intention.impl.CachedIntentions
import com.intellij.codeInsight.intention.impl.IntentionActionWithTextCaching
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.codeInsight.intention.impl.config.IntentionsMetadataService
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.codeInspection.ex.QuickFixWrapper
import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.ModCommandAction.ActionContext
import com.intellij.model.SideEffectGuard
import com.intellij.model.SideEffectGuard.SideEffectNotAllowedException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.util.LocalTimeCounter
import java.io.IOException
import java.lang.ref.Reference
import java.util.concurrent.Callable

internal class IntentionPreviewComputable(private val project: Project,
                                          private val action: IntentionAction,
                                          private val originalFile: PsiFile,
                                          private val originalEditor: Editor) : Callable<IntentionPreviewInfo> {
  override fun call(): IntentionPreviewInfo {
    val diffContent = tryCreateDiffContent()
    if (diffContent != null) {
      return diffContent
    }
    return tryCreateFallbackDescriptionContent()
  }

  private fun tryCreateFallbackDescriptionContent(): IntentionPreviewInfo {
    val originalAction = IntentionActionDelegate.unwrap(action)
    val actionMetaData = IntentionsMetadataService.getInstance().getMetaData().singleOrNull { md ->
      IntentionActionDelegate.unwrap(md.action).javaClass === originalAction.javaClass
    } ?: return IntentionPreviewInfo.EMPTY
    return try {
      IntentionPreviewInfo.Html(actionMetaData.description.text.replace(HTML_COMMENT_REGEX, ""))
    }
    catch (ex: IOException) {
      IntentionPreviewInfo.EMPTY
    }
  }

  private fun tryCreateDiffContent(): IntentionPreviewInfo? {
    try {
      return generatePreview()
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: SideEffectNotAllowedException) {
      val wrapper = RuntimeException(e.message)
      wrapper.stackTrace = e.stackTrace
      logger<IntentionPreviewComputable>().error("Side effect occurred on invoking the intention '${action.text}' on a copy of the file",
                                                 wrapper)
      return null
    }
    catch (e: Exception) {
      logger<IntentionPreviewComputable>().error("Exceptions occurred on invoking the intention '${action.text}' on a copy of the file.", e)
      return null
    }
  }

  fun generatePreview(): IntentionPreviewInfo? {
    if (project.isDisposed) return null
    val origPair = ShowIntentionActionsHandler.chooseFileForAction(originalFile, originalEditor, action) ?: return null
    ProgressManager.checkCanceled()
    val writable = originalEditor.document.isWritable
    try {
      return invokePreview(origPair.first, origPair.second)
    }
    finally {
      originalEditor.document.setReadOnly(!writable)
    }
  }

  private fun invokePreview(origFile: PsiFile, origEditor: Editor): IntentionPreviewInfo? {
    var info: IntentionPreviewInfo = getModActionPreview(origFile, origEditor)
    if (info != IntentionPreviewInfo.EMPTY) return info
    var fileToCopy = action.getElementToMakeWritable(origFile)?.containingFile ?: origFile
    val psiFileCopy: PsiFile
    val editorCopy: IntentionPreviewEditor
    val anotherFile = fileToCopy != origFile
    if (!anotherFile) {
      val fileFactory = PsiFileFactory.getInstance(project)
      if (origFile != originalFile) { // injection
        val manager = InjectedLanguageManager.getInstance(project)
        fileToCopy = fileFactory.createFileFromText(origFile.name, origFile.fileType, manager.getUnescapedText(origFile),
                                                    LocalTimeCounter.currentTime(), true)
      }
      psiFileCopy = IntentionPreviewUtils.obtainCopyForPreview(fileToCopy, origFile)
      editorCopy = IntentionPreviewEditor(psiFileCopy, originalEditor.settings)
      setupEditor(editorCopy, origFile, origEditor)
    }
    else {
      psiFileCopy = IntentionPreviewUtils.obtainCopyForPreview(fileToCopy)
      editorCopy = IntentionPreviewEditor(psiFileCopy, originalEditor.settings)
    }
    originalEditor.document.setReadOnly(true)
    ProgressManager.checkCanceled()
    // force settings initialization, as it may spawn EDT action which is not allowed inside generatePreview()
    val settings = CodeStyle.getSettings(editorCopy)
    IntentionPreviewUtils.previewSession(editorCopy) {
      PostprocessReformattingAspect.getInstance(project).postponeFormattingInside {
        info = SideEffectGuard.computeWithoutSideEffects<IntentionPreviewInfo?, Exception> {
          action.generatePreview(project, editorCopy, psiFileCopy)
        }
      }
    }
    if (info == IntentionPreviewInfo.FALLBACK_DIFF && fileToCopy == origFile) {
      info = SideEffectGuard.computeWithoutSideEffects<IntentionPreviewInfo?, Exception> { generateFallbackDiff(editorCopy, psiFileCopy) }
    }
    Reference.reachabilityFence(settings)
    val manager = PsiDocumentManager.getInstance(project)
    manager.commitDocument(editorCopy.document)
    manager.doPostponedOperationsAndUnblockDocument(editorCopy.document)
    return convertResult(info, psiFileCopy, fileToCopy, anotherFile)
  }
  
  private fun convertResult(info: IntentionPreviewInfo,
                            copyFile: PsiFile,
                            origFile: PsiFile,
                            anotherFile: Boolean): IntentionPreviewInfo? {
    return when (info) {
      IntentionPreviewInfo.DIFF,
      IntentionPreviewInfo.DIFF_NO_TRIM -> {
        val document = copyFile.viewProvider.document
        val policy = if (info == IntentionPreviewInfo.DIFF) ComparisonPolicy.TRIM_WHITESPACES else ComparisonPolicy.DEFAULT
        val text = origFile.text
        IntentionPreviewDiffResult(
          fileType = copyFile.fileType,
          newText = document.text,
          origText = text,
          policy = policy,
          fileName = if (anotherFile) copyFile.name else null,
          normalDiff = !anotherFile,
          lineFragments = ComparisonManager.getInstance().compareLines(text, document.text, policy,
                                                                       DumbProgressIndicator.INSTANCE))
      }
      is IntentionPreviewInfo.Diff -> {
        IntentionPreviewDiffResult(
          fileType = origFile.fileType,
          newText = info.modifiedText(),
          origText = info.originalText(),
          policy = ComparisonPolicy.DEFAULT,
          fileName = null,
          normalDiff = true,
          lineFragments = ComparisonManager.getInstance().compareLines(
            info.originalText(), info.modifiedText(), ComparisonPolicy.DEFAULT, DumbProgressIndicator.INSTANCE))
      }
      IntentionPreviewInfo.EMPTY, IntentionPreviewInfo.FALLBACK_DIFF -> null
      is IntentionPreviewInfo.CustomDiff -> IntentionPreviewDiffResult.fromCustomDiff(info)
      else -> info
    }
  }

  private fun getModActionPreview(origFile: PsiFile, origEditor: Editor): IntentionPreviewInfo {
    val unwrapped = ModCommandAction.unwrap(action) ?: return IntentionPreviewInfo.EMPTY
    val info = SideEffectGuard.computeWithoutSideEffects<IntentionPreviewInfo, Exception> 
      { unwrapped.generatePreview(ActionContext.from(origEditor, origFile)) }
    return convertResult(info, origFile, origFile, false) ?: IntentionPreviewInfo.EMPTY
  }

  private fun generateFallbackDiff(editorCopy: IntentionPreviewEditor, psiFileCopy: PsiFile): IntentionPreviewInfo {
    // Fallback algorithm for intention actions that don't support preview normally
    // works only for registered intentions (not for compilation or inspection quick-fixes)
    if (!action.startInWriteAction()) return IntentionPreviewInfo.EMPTY
    if (action.getElementToMakeWritable(originalFile)?.containingFile !== originalFile) return IntentionPreviewInfo.EMPTY
    val action = findCopyIntention(project, editorCopy, psiFileCopy, action) ?: return IntentionPreviewInfo.EMPTY
    val unwrapped = IntentionActionDelegate.unwrap(action)
    val cls = (QuickFixWrapper.unwrap(unwrapped) ?: unwrapped)::class.java
    val loader = cls.classLoader
    val thirdParty = loader !is PluginAwareClassLoader || !PluginManagerCore.isDevelopedByJetBrains(loader.pluginDescriptor)
    if (!thirdParty) {
      logger<IntentionPreviewComputable>().error("Intention preview fallback is used for action ${cls.name}|${action.familyName}")
    }
    ProgressManager.checkCanceled()
    IntentionPreviewUtils.previewSession(editorCopy) {
      PostprocessReformattingAspect.getInstance(project)
        .postponeFormattingInside { action.invoke(project, editorCopy, psiFileCopy) }
    }
    return IntentionPreviewInfo.DIFF
  }

  private fun setupEditor(editorCopy: IntentionPreviewEditor, origFile: PsiFile, origEditor: Editor) {
    ProgressManager.checkCanceled()
    val selection: TextRange
    val caretOffset: Int
    if (origFile != originalFile) { // injection
      val manager = InjectedLanguageManager.getInstance(project)
      val selectionModel = origEditor.selectionModel
      val start = manager.mapInjectedOffsetToUnescaped(origFile, selectionModel.selectionStart)
      val end = if (selectionModel.selectionEnd == selectionModel.selectionStart) start
      else
        manager.mapInjectedOffsetToUnescaped(origFile, selectionModel.selectionEnd)
      selection = TextRange(start, end)
      val caretModel = origEditor.caretModel
      caretOffset = when (caretModel.offset) {
        selectionModel.selectionStart -> start
        selectionModel.selectionEnd -> end
        else -> manager.mapInjectedOffsetToUnescaped(origFile, caretModel.offset)
      }
    }
    else {
      selection = TextRange(originalEditor.selectionModel.selectionStart, originalEditor.selectionModel.selectionEnd)
      caretOffset = originalEditor.caretModel.offset
    }
    editorCopy.caretModel.moveToOffset(caretOffset)
    editorCopy.selectionModel.setSelection(selection.startOffset, selection.endOffset)
  }
}

private val HTML_COMMENT_REGEX = Regex("<!--.+-->")

private fun getFixes(cachedIntentions: CachedIntentions): Sequence<IntentionActionWithTextCaching> {
  return sequenceOf<IntentionActionWithTextCaching>()
    .plus(cachedIntentions.intentions)
    .plus(cachedIntentions.inspectionFixes)
    .plus(cachedIntentions.errorFixes)
}

fun findCopyIntention(project: Project,
                      editorCopy: Editor,
                      psiFileCopy: PsiFile,
                      originalAction: IntentionAction): IntentionAction? {
  val actionsToShow = ShowIntentionsPass.getActionsToShow(editorCopy, psiFileCopy, false)
  val cachedIntentions = CachedIntentions.createAndUpdateActions(project, psiFileCopy, editorCopy, actionsToShow)
  return getFixes(cachedIntentions).find { it.text == originalAction.text }?.action
}
