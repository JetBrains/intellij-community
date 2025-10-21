// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.analysis.AnalysisBundle.message
import com.intellij.codeInsight.completion.command.CompletionCommand
import com.intellij.codeInsight.completion.command.HighlightInfoLookup
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator
import com.intellij.codeInsight.daemon.impl.HighlightInfo.IntentionActionDescriptor
import com.intellij.codeInsight.daemon.impl.HighlightVisitorBasedInspection.runAnnotatorsInGeneralHighlighting
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass.addAvailableFixesForGroups
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.quickfix.LazyQuickFixUpdater
import com.intellij.concurrency.currentThreadContext
import com.intellij.injected.editor.DocumentWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.jobToIndicator
import com.intellij.openapi.util.ProperTextRange
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import com.intellij.psi.util.PsiModificationTracker
import kotlinx.coroutines.job
import org.jetbrains.annotations.Nls
import javax.swing.Icon

internal class DirectErrorFixCompletionCommand(
  override val presentableName: @Nls String,
  override val priority: Int?,
  override val icon: Icon?,
  override val highlightInfo: HighlightInfoLookup,
  private val myOffset: Int? = null,
  private val previewProvider: () -> IntentionPreviewInfo?,
) : CompletionCommand() {

  override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
    if (editor == null) return
    val injectedLanguageManager = InjectedLanguageManager.getInstance(psiFile.project)
    val topLevelEditor = InjectedLanguageEditorUtil.getTopLevelEditor(editor)
    val topLevelPsiFile = injectedLanguageManager.getTopLevelFile(psiFile)
    var topLevelOffset = offset
    if (topLevelPsiFile != psiFile) {
      topLevelOffset = (psiFile.fileDocument as? DocumentWindow)?.hostToInjected(offset) ?: 0
    }

    myOffset?.let {
      topLevelEditor.caretModel.moveToOffset(myOffset)
    }
    val action: IntentionAction? = runWithModalProgressBlocking(topLevelPsiFile.project, message("scanning.scope.progress.title")) {
      val indicator = DaemonProgressIndicator()
      readAction {
        jobToIndicator(currentThreadContext().job, indicator) {
          //todo merge with error finder
          val fileDocument = topLevelPsiFile.fileDocument
          val lineNumber = fileDocument.getLineNumber(topLevelOffset)
          val startLineNumber = maxOf(0, lineNumber - 2)
          val endLineNumber = minOf(fileDocument.lineCount - 1, lineNumber + 2)
          val startOffset = fileDocument.getLineStartOffset(startLineNumber)
          val endOffset = fileDocument.getLineEndOffset(endLineNumber)
          val highlightings = runAnnotatorsInGeneralHighlighting(topLevelPsiFile,
                                                                 startOffset,
                                                                 endOffset,
                                                                 ProperTextRange(startOffset, endOffset),
                                                                 true, true, true)
          val lazyQuickFixUpdater = LazyQuickFixUpdater.getInstance(psiFile.project)
          for (info in highlightings) {
            if (info.hasLazyQuickFixes()) {
              info.updateLazyFixesPsiTimeStamp(PsiModificationTracker.getInstance(psiFile.project).modificationCount);
              lazyQuickFixUpdater.waitQuickFixesSynchronously(psiFile, editor, info)
            }
            if (!(info.startOffset == highlightInfo.range.startOffset && info.endOffset == highlightInfo.range.endOffset)) {
              continue
            }
            val fixes: MutableList<IntentionActionDescriptor> = ArrayList()
            addAvailableFixesForGroups(info, topLevelEditor, topLevelPsiFile, fixes, -1, topLevelOffset, false)
            for (fix in fixes) {
              var currentName = fix.action.text
              if (currentName.startsWith("<html>") && currentName.endsWith("</html>")) {
                currentName = currentName.substring(6, currentName.length - 7)
              }
              if (currentName == presentableName) {
                return@jobToIndicator fix.action
              }
            }
          }
          return@jobToIndicator null
        }
      }
    }
    if (action == null) return
    ShowIntentionActionsHandler.chooseActionAndInvoke(topLevelPsiFile, topLevelEditor, action, presentableName)
  }

  override fun getPreview(): IntentionPreviewInfo {
    return previewProvider() ?: IntentionPreviewInfo.EMPTY
  }
}