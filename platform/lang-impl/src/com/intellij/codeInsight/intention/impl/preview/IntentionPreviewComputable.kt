// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.preview

import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.codeInsight.intention.impl.CachedIntentions
import com.intellij.codeInsight.intention.impl.IntentionActionWithTextCaching
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.ex.QuickFixWrapper
import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.fragments.LineFragment
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
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
    val actionMetaData = IntentionManagerSettings.getInstance().getMetaData().singleOrNull {
      md -> IntentionActionDelegate.unwrap(md.action) === originalAction
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
      return generatePreview()
    }
    catch (e: IntentionPreviewUnsupportedOperationException) {
      return null
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Exception) {
      logger<IntentionPreviewComputable>().debug("There are exceptions on invocation the intention: '${action.text}' on a copy of the file.", e)
      return null
    }
  }

  fun generatePreview(): IntentionPreviewInfo? {
    if (project.isDisposed) return null
    val origPair = ShowIntentionActionsHandler.chooseFileForAction(originalFile, originalEditor, action) ?: return null
    val origFile: PsiFile
    val caretOffset: Int
    if (origPair.first != originalFile) {
      val manager = InjectedLanguageManager.getInstance(project)
      origFile = PsiFileFactory.getInstance(project).createFileFromText(
        origPair.first.name, origPair.first.fileType, manager.getUnescapedText(origPair.first))
      caretOffset = mapInjectedOffsetToUnescaped(origPair.first, origPair.second.caretModel.offset) 
    }
    else {
      origFile = originalFile
      caretOffset = originalEditor.caretModel.offset
    }
    val psiFileCopy = origFile.copy() as PsiFile
    ProgressManager.checkCanceled()
    val editorCopy = IntentionPreviewEditor(psiFileCopy, caretOffset, originalEditor.settings)

    val writable = originalEditor.document.isWritable
    try {
      originalEditor.document.setReadOnly(true)
      ProgressManager.checkCanceled()
      var result = action.generatePreview(project, editorCopy, psiFileCopy)
      if (result == IntentionPreviewInfo.FALLBACK_DIFF) {
        if (action.getElementToMakeWritable(originalFile)?.containingFile !== originalFile) return null
        // Use fallback algorithm only if invokeForPreview is not explicitly overridden
        // in this case, the absence of diff could be intended, thus should not be logged as error
        val action = findCopyIntention(project, editorCopy, psiFileCopy, action) ?: return null
        val unwrapped = IntentionActionDelegate.unwrap(action)
        val actionClass = (if (unwrapped is QuickFixWrapper) unwrapped.fix else unwrapped)::class.qualifiedName
        logger<IntentionPreviewComputable>().error("Intention preview fallback is used for action $actionClass|${action.familyName}")
        action.invoke(project, editorCopy, psiFileCopy)
        result = IntentionPreviewInfo.DIFF
      }
      ProgressManager.checkCanceled()
      return when (result) {
        IntentionPreviewInfo.DIFF -> {
          PostprocessReformattingAspect.getInstance(project).doPostponedFormatting(psiFileCopy.viewProvider)
          IntentionPreviewDiffResult(
            psiFile = psiFileCopy,
            origFile = origFile,
            lineFragments = ComparisonManager.getInstance()
              .compareLines(origFile.text, editorCopy.document.text, ComparisonPolicy.TRIM_WHITESPACES, DumbProgressIndicator.INSTANCE))
        }
        IntentionPreviewInfo.EMPTY -> null
        else -> result
      }
    }
    finally {
      originalEditor.document.setReadOnly(!writable)
    }
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
                                               val lineFragments: List<LineFragment>): IntentionPreviewInfo
