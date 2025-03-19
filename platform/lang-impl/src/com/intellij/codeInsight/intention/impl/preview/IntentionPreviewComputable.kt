// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.diagnostic.PluginException
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.modcommand.ActionContext
import com.intellij.model.SideEffectGuard
import com.intellij.model.SideEffectGuard.SideEffectNotAllowedException
import com.intellij.openapi.diagnostic.ReportingClassSubstitutor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.util.LocalTimeCounter
import com.intellij.util.applyIf
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.IOException
import java.lang.ref.Reference

@Internal
class IntentionPreviewComputable(
  private val project: Project,
  private val action: IntentionAction,
  private val originalFile: PsiFile,
  private val originalEditor: Editor,
  private val fixOffset: Int,
) {
  fun call(): IntentionPreviewInfo = tryCreateDiffContent() ?: tryCreateFallbackDescriptionContent()

  private fun tryCreateFallbackDescriptionContent(): IntentionPreviewInfo {
    val originalAction = IntentionActionDelegate.unwrap(action)
    val actionMetaData = IntentionsMetadataService.getInstance().getMetaData().singleOrNull { md ->
      IntentionActionDelegate.unwrap(md.action).javaClass === originalAction.javaClass
    } ?: return IntentionPreviewInfo.EMPTY
    try {
      return IntentionPreviewInfo.Html(actionMetaData.description.getText().replace(HTML_COMMENT_REGEX, ""))
    }
    catch (_: IOException) {
      return IntentionPreviewInfo.EMPTY
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
      logger<IntentionPreviewComputable>().error("Side effect occurred on invoking the intention '${action.text}'" +
                                                 " (${ReportingClassSubstitutor.getClassToReport(action)}) on a copy of the file",
                                                 wrapper)
      return null
    }
    catch (e: Exception) {
      logger<IntentionPreviewComputable>().error("Exceptions occurred on invoking the intention '${action.text}'" +
                                                 " (${ReportingClassSubstitutor.getClassToReport(action)}) on a copy of the file.", e)
      return null
    }
  }

  fun generatePreview(): IntentionPreviewInfo? {
    if (project.isDisposed) return null
    val origPair = ShowIntentionActionsHandler.chooseFileForAction(originalFile, originalEditor, action) ?: return null
    ProgressManager.checkCanceled()
    return invokePreview(origPair.first, origPair.second)
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
    if (fixOffset >= 0) {
      editorCopy.caretModel.moveToOffset(fixOffset)
    }
    ProgressManager.checkCanceled()
    // force settings initialization, as it may spawn EDT action which is not allowed inside generatePreview()
    val settings = CodeStyle.getSettings(editorCopy)
    IntentionPreviewUtils.previewSession(editorCopy) {
      PostprocessReformattingAspect.getInstance(project).postponeFormattingInside {
        info = SideEffectGuard.computeWithoutSideEffects {
          action.generatePreview(project, editorCopy, psiFileCopy)
        }
      }
    }
    if (info == IntentionPreviewInfo.FALLBACK_DIFF && fileToCopy == origFile) {
      info = SideEffectGuard.computeWithoutSideEffects { generateFallbackDiff(editorCopy, psiFileCopy) }
    }
    Reference.reachabilityFence(settings)
    val manager = PsiDocumentManager.getInstance(project)
    if (!psiFileCopy.viewProvider.isEventSystemEnabled) {
      manager.commitDocument(editorCopy.document)
      manager.doPostponedOperationsAndUnblockDocument(editorCopy.document)
    }
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
        IntentionPreviewDiffResult.create(
          fileType = copyFile.fileType,
          updatedText = document.text,
          origText = text,
          fileName = if (anotherFile) copyFile.name else null,
          normalDiff = !anotherFile,
          policy = policy)
      }
      IntentionPreviewInfo.EMPTY, IntentionPreviewInfo.FALLBACK_DIFF -> null
      else -> info
    }
  }

  private fun getModActionPreview(origFile: PsiFile, origEditor: Editor): IntentionPreviewInfo {
    val unwrapped = action.asModCommandAction() ?: return IntentionPreviewInfo.EMPTY
    var info: IntentionPreviewInfo = IntentionPreviewInfo.EMPTY
    val context = ActionContext.from(origEditor, origFile).applyIf(fixOffset >= 0) { withOffset(fixOffset) }
    IntentionPreviewUtils.previewSession(origEditor) {
      info = unwrapped.generatePreview(context)
    }
    return convertResult(info, origFile, origFile, false) ?: IntentionPreviewInfo.EMPTY
  }

  private fun generateFallbackDiff(editorCopy: IntentionPreviewEditor, psiFileCopy: PsiFile): IntentionPreviewInfo {
    // Fallback algorithm for intention actions that don't support preview normally
    // works only for registered intentions (not for compilation or inspection quick-fixes)
    if (!action.startInWriteAction()) return IntentionPreviewInfo.EMPTY
    if (action.getElementToMakeWritable(originalFile)?.containingFile !== originalFile) return IntentionPreviewInfo.EMPTY
    val action = findCopyIntention(project, editorCopy, psiFileCopy, action) ?: return IntentionPreviewInfo.EMPTY
    val cls = ReportingClassSubstitutor.getClassToReport(action)
    val loader = cls.classLoader
    if (loader is PluginAwareClassLoader && PluginManagerCore.isDevelopedByJetBrains(loader.pluginDescriptor)) {
      logger<IntentionPreviewComputable>().error(
        PluginException("Intention preview fallback is used for action ${cls.name}|${action.familyName}", loader.pluginId))
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
    val caretOffset: Int
    val selectionStart: Int
    val selectionEnd: Int
    if (origFile != originalFile) { // injection
      val manager = InjectedLanguageManager.getInstance(project)
      val selectionModel = origEditor.selectionModel
      selectionStart = manager.mapInjectedOffsetToUnescaped(origFile, selectionModel.selectionStart)
      selectionEnd = if (selectionModel.selectionEnd == selectionModel.selectionStart) {
        selectionStart
      }
      else {
        manager.mapInjectedOffsetToUnescaped(origFile, selectionModel.selectionEnd)
      }
      val caretModel = origEditor.caretModel
      caretOffset = when (caretModel.offset) {
        selectionModel.selectionStart -> selectionStart
        selectionModel.selectionEnd -> selectionEnd
        else -> manager.mapInjectedOffsetToUnescaped(origFile, caretModel.offset)
      }
    }
    else {
      selectionStart = originalEditor.selectionModel.selectionStart
      selectionEnd = originalEditor.selectionModel.selectionEnd
      caretOffset = originalEditor.caretModel.offset
    }
    editorCopy.caretModel.moveToOffset(caretOffset)
    editorCopy.selectionModel.setSelection(selectionStart, selectionEnd)
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
  val actionsToShow = ShowIntentionsPass.getActionsToShow(editorCopy, psiFileCopy)
  val cachedIntentions = CachedIntentions.createAndUpdateActions(project, psiFileCopy, editorCopy, actionsToShow)
  return getFixes(cachedIntentions).find { it.text == originalAction.text }?.action
}
