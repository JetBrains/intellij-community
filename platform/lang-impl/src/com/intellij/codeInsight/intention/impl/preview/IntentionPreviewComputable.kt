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
    val origFile: PsiFile
    val selection: TextRange
    val caretOffset: Int
    val fileFactory = PsiFileFactory.getInstance(project)
    if (origPair.first != originalFile) {
      val manager = InjectedLanguageManager.getInstance(project)
      origFile = fileFactory.createFileFromText(
        origPair.first.name, origPair.first.fileType, manager.getUnescapedText(origPair.first))
      val selectionModel = origPair.second.selectionModel
      val start = mapInjectedOffsetToUnescaped(origPair.first, selectionModel.selectionStart)
      val end = if (selectionModel.selectionEnd == selectionModel.selectionStart) start else
        mapInjectedOffsetToUnescaped(origPair.first, selectionModel.selectionEnd)
      selection = TextRange(start, end)
      val caretModel = origPair.second.caretModel
      caretOffset = when (caretModel.offset) {
        selectionModel.selectionStart -> start
        selectionModel.selectionEnd -> end
        else -> mapInjectedOffsetToUnescaped(origPair.first, caretModel.offset)
      }
    }
    else {
      origFile = originalFile
      selection = TextRange(originalEditor.selectionModel.selectionStart, originalEditor.selectionModel.selectionEnd)
      caretOffset = originalEditor.caretModel.offset
    }
    ProgressManager.checkCanceled()
    val writable = originalEditor.document.isWritable
    try {
      val (result: IntentionPreviewInfo, psiFileCopy: PsiFile?) = invokePreview(origFile, selection, caretOffset)
      ProgressManager.checkCanceled()
      val comparisonManager = ComparisonManager.getInstance()
      return when (result) {
        IntentionPreviewInfo.DIFF,
        IntentionPreviewInfo.DIFF_NO_TRIM -> {
          val document = psiFileCopy!!.viewProvider.document
          val correctedOrigFile = psiFileCopy.originalFile
          val anotherFile = correctedOrigFile != origFile
          val policy = if (result == IntentionPreviewInfo.DIFF) ComparisonPolicy.TRIM_WHITESPACES else ComparisonPolicy.DEFAULT
          IntentionPreviewDiffResult(
            psiFile = psiFileCopy,
            origFile = correctedOrigFile,
            policy = policy,
            fileName = if (anotherFile) psiFileCopy.name else null,
            normalDiff = !anotherFile,
            lineFragments = comparisonManager.compareLines(correctedOrigFile.text, document.text, policy, DumbProgressIndicator.INSTANCE))
        }
        IntentionPreviewInfo.EMPTY, IntentionPreviewInfo.FALLBACK_DIFF -> null
        is IntentionPreviewInfo.CustomDiff -> {
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
    finally {
      originalEditor.document.setReadOnly(!writable)
    }
  }

  private fun invokePreview(origFile: PsiFile, selection: TextRange, caretOffset: Int): Pair<IntentionPreviewInfo, PsiFile?> {
    var info: IntentionPreviewInfo = IntentionPreviewInfo.EMPTY
    val fileToCopy = action.getElementToMakeWritable(origFile) ?. containingFile ?: origFile
    val psiFileCopy = IntentionPreviewUtils.obtainCopyForPreview(fileToCopy)
    val editorCopy = IntentionPreviewEditor(psiFileCopy, originalEditor.settings)
    if (fileToCopy == origFile) {
      editorCopy.caretModel.moveToOffset(caretOffset)
      editorCopy.selectionModel.setSelection(selection.startOffset, selection.endOffset)
    }
    originalEditor.document.setReadOnly(true)
    ProgressManager.checkCanceled()
    IntentionPreviewUtils.previewSession(editorCopy) {
      PostprocessReformattingAspect.getInstance(project)
        .postponeFormattingInside { info = action.generatePreview(project, editorCopy, psiFileCopy) }
    }
    if (info == IntentionPreviewInfo.FALLBACK_DIFF && fileToCopy == origFile) {
      if (!action.startInWriteAction()) return info to null
      if (action.getElementToMakeWritable(originalFile)?.containingFile !== originalFile) return info to null
      val action = findCopyIntention(project, editorCopy, psiFileCopy, action) ?: return info to null
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
      info = IntentionPreviewInfo.DIFF
    }
    val manager = PsiDocumentManager.getInstance(project)
    manager.commitDocument(editorCopy.document)
    manager.doPostponedOperationsAndUnblockDocument(editorCopy.document)
    return Pair(info, psiFileCopy)
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
