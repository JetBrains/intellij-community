// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.preview

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
import com.intellij.diff.fragments.LineFragment
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.model.SideEffectGuard
import com.intellij.model.SideEffectGuard.SideEffectNotAllowedException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtilBase
import java.io.IOException
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
    val actionMetaData = IntentionsMetadataService.getInstance().getMetaData().singleOrNull {
      md -> IntentionActionDelegate.unwrap(md.action).javaClass === originalAction.javaClass
    } ?: return IntentionPreviewInfo.EMPTY
    return try {
      IntentionPreviewInfo.Html(actionMetaData.description.text.replace(HTML_COMMENT_REGEX, ""))
    }
    catch(ex: IOException) {
      IntentionPreviewInfo.EMPTY
    }
  }

  private fun tryCreateDiffContent(): IntentionPreviewInfo? {
    try {
      return SideEffectGuard.computeWithoutSideEffects<IntentionPreviewInfo?, Exception> { generatePreview() }
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
    var info: IntentionPreviewInfo = IntentionPreviewInfo.EMPTY
    var fileToCopy = action.getElementToMakeWritable(origFile) ?. containingFile ?: origFile
    val psiFileCopy: PsiFile
    val editorCopy: IntentionPreviewEditor
    val anotherFile = fileToCopy != origFile
    if (!anotherFile) {
      val fileFactory = PsiFileFactory.getInstance(project)
      if (origFile != originalFile) { // injection
        val manager = InjectedLanguageManager.getInstance(project)
        fileToCopy = fileFactory.createFileFromText(origFile.name, origFile.fileType, manager.getUnescapedText(origFile))
      }
      psiFileCopy = IntentionPreviewUtils.obtainCopyForPreview(fileToCopy)
      editorCopy = IntentionPreviewEditor(psiFileCopy, originalEditor.settings)
      setupEditor(editorCopy, origFile, origEditor)
    } else {
      psiFileCopy = IntentionPreviewUtils.obtainCopyForPreview(fileToCopy)
      editorCopy = IntentionPreviewEditor(psiFileCopy, originalEditor.settings)
    }
    originalEditor.document.setReadOnly(true)
    ProgressManager.checkCanceled()
    IntentionPreviewUtils.previewSession(editorCopy) {
      PostprocessReformattingAspect.getInstance(project)
        .postponeFormattingInside { info = action.generatePreview(project, editorCopy, psiFileCopy) }
    }
    if (info == IntentionPreviewInfo.FALLBACK_DIFF && fileToCopy == origFile) {
      info = generateFallbackDiff(editorCopy, psiFileCopy)
    }
    val manager = PsiDocumentManager.getInstance(project)
    manager.commitDocument(editorCopy.document)
    manager.doPostponedOperationsAndUnblockDocument(editorCopy.document)
    val comparisonManager = ComparisonManager.getInstance()
    return when (val result = info) {
      IntentionPreviewInfo.DIFF,
      IntentionPreviewInfo.DIFF_NO_TRIM -> {
        val document = psiFileCopy.viewProvider.document
        val policy = if (info == IntentionPreviewInfo.DIFF) ComparisonPolicy.TRIM_WHITESPACES else ComparisonPolicy.DEFAULT
        IntentionPreviewDiffResult(
          psiFile = psiFileCopy,
          origFile = fileToCopy,
          policy = policy,
          fileName = if (anotherFile) psiFileCopy.name else null,
          normalDiff = !anotherFile,
          lineFragments = comparisonManager.compareLines(fileToCopy.text, document.text, policy, DumbProgressIndicator.INSTANCE))
      }
      IntentionPreviewInfo.EMPTY, IntentionPreviewInfo.FALLBACK_DIFF -> null
      is IntentionPreviewInfo.CustomDiff -> {
        val fileFactory = PsiFileFactory.getInstance(project)
        IntentionPreviewDiffResult(
          fileFactory.createFileFromText("__dummy__", result.fileType(), result.modifiedText()),
          fileFactory.createFileFromText("__dummy__", result.fileType(), result.originalText()),
          comparisonManager.compareLines(result.originalText(), result.modifiedText(),
                                         ComparisonPolicy.TRIM_WHITESPACES, DumbProgressIndicator.INSTANCE),
          fileName = result.fileName(),
          normalDiff = false,
          policy = ComparisonPolicy.TRIM_WHITESPACES)
      }
      else -> result
    }
  }

  private fun generateFallbackDiff(editorCopy: IntentionPreviewEditor, psiFileCopy: PsiFile): IntentionPreviewInfo {
    // Fallback algorithm for intention actions that don't support preview normally
    // works only for registered intentions (not for compilation or inspection quick-fixes)
    if (!action.startInWriteAction()) return IntentionPreviewInfo.EMPTY
    if (action.getElementToMakeWritable(originalFile)?.containingFile !== originalFile) return IntentionPreviewInfo.EMPTY
    val action = findCopyIntention(project, editorCopy, psiFileCopy, action) ?: return IntentionPreviewInfo.EMPTY
    val unwrapped = IntentionActionDelegate.unwrap(action)
    val cls = (if (unwrapped is QuickFixWrapper) unwrapped.fix else unwrapped)::class.java
    val loader = cls.classLoader
    val thirdParty = loader is PluginAwareClassLoader && PluginManagerCore.isDevelopedByJetBrains(loader.pluginDescriptor)
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
      val selectionModel = origEditor.selectionModel
      val start = mapInjectedOffsetToUnescaped(origFile, selectionModel.selectionStart)
      val end = if (selectionModel.selectionEnd == selectionModel.selectionStart) start
      else
        mapInjectedOffsetToUnescaped(origFile, selectionModel.selectionEnd)
      selection = TextRange(start, end)
      val caretModel = origEditor.caretModel
      caretOffset = when (caretModel.offset) {
        selectionModel.selectionStart -> start
        selectionModel.selectionEnd -> end
        else -> mapInjectedOffsetToUnescaped(origFile, caretModel.offset)
      }
    }
    else {
      selection = TextRange(originalEditor.selectionModel.selectionStart, originalEditor.selectionModel.selectionEnd)
      caretOffset = originalEditor.caretModel.offset
    }
    editorCopy.caretModel.moveToOffset(caretOffset)
    editorCopy.selectionModel.setSelection(selection.startOffset, selection.endOffset)
  }

  private fun mapInjectedOffsetToUnescaped(injectedFile: PsiFile, injectedOffset: Int): Int {
    var unescapedOffset = 0
    var escapedOffset = 0
    injectedFile.accept(object : PsiRecursiveElementWalkingVisitor() {
      override fun visitElement(element: PsiElement) {
        val leafText = InjectedLanguageUtilBase.getUnescapedLeafText(element, false)
        if (leafText != null) {
          unescapedOffset += leafText.length
          escapedOffset += element.textLength
          if (escapedOffset >= injectedOffset) {
            unescapedOffset -= escapedOffset - injectedOffset
            stopWalking()
          }
        }
        super.visitElement(element)
      }
    })
    return unescapedOffset
  }
}

private val HTML_COMMENT_REGEX = Regex("<!--.+-->")

private fun getFixes(cachedIntentions: CachedIntentions): Sequence<IntentionActionWithTextCaching> {
  return sequenceOf<IntentionActionWithTextCaching>()
    .plus(cachedIntentions.intentions)
    .plus(cachedIntentions.inspectionFixes)
    .plus(cachedIntentions.errorFixes)
}

private fun findCopyIntention(project: Project,
                              editorCopy: Editor,
                              psiFileCopy: PsiFile,
                              originalAction: IntentionAction): IntentionAction? {
  val actionsToShow = ShowIntentionsPass.getActionsToShow(editorCopy, psiFileCopy, false)
  val cachedIntentions = CachedIntentions.createAndUpdateActions(project, psiFileCopy, editorCopy, actionsToShow)
  return getFixes(cachedIntentions).find { it.text == originalAction.text }?.action
}

internal data class IntentionPreviewDiffResult(val psiFile: PsiFile,
                                               val origFile: PsiFile,
                                               val lineFragments: List<LineFragment>,
                                               val normalDiff: Boolean = true,
                                               val fileName: String? = null,
                                               val policy: ComparisonPolicy): IntentionPreviewInfo
