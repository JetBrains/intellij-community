// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.analysis.AnalysisBundle.message
import com.intellij.codeInsight.completion.command.CompletionCommand
import com.intellij.codeInsight.completion.command.HighlightInfoLookup
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler.availableFor
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.InspectionEngine
import com.intellij.codeInspection.InspectionEngine.inspectEx
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemDescriptorBase
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper
import com.intellij.codeInspection.ex.InspectionProfileWrapper
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.codeInspection.ex.QuickFixWrapper.wrap
import com.intellij.concurrency.currentThreadContext
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.jobToIndicator
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import com.intellij.psi.util.PsiTreeUtil
import kotlinx.coroutines.job
import org.jetbrains.annotations.Nls
import javax.swing.Icon

class DirectInspectionFixCompletionCommand(
  val inspectionId: String,
  override val presentableName: @Nls String,
  override val priority: Int?,
  override val icon: Icon?,
  override val highlightInfo: HighlightInfoLookup,
  private val targetOffset: Int,
  private val previewProvider: () -> IntentionPreviewInfo?,
) : CompletionCommand() {

  override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
    if (editor == null) return
    //todo merge with error finder
    val injectedLanguageManager = InjectedLanguageManager.getInstance(psiFile.project)
    val topLevelFile = injectedLanguageManager.getTopLevelFile(psiFile)
    val topLevelEditor = InjectedLanguageEditorUtil.getTopLevelEditor(editor)
    val topLevelOffset = injectedLanguageManager.injectedToHost(psiFile, targetOffset)
    val isInjected = topLevelFile != psiFile

    val action: IntentionAction? = runWithModalProgressBlocking(psiFile.project, message("scanning.scope.progress.title")) {
      val profileToUse = ProjectInspectionProfileManager.Companion.getInstance(psiFile.project).getCurrentProfile()
      val inspectionWrapper = InspectionProfileWrapper(profileToUse)
      var inspectionTool = inspectionWrapper.inspectionProfile.getInspectionTool(inspectionId, psiFile.project)
      if (inspectionTool == null) return@runWithModalProgressBlocking null
      if (inspectionTool is GlobalInspectionToolWrapper) {
        inspectionTool = inspectionTool.sharedLocalInspectionToolWrapper
      }
      if (inspectionTool !is LocalInspectionToolWrapper) return@runWithModalProgressBlocking null
      val lineRange = getLineRange(topLevelFile, topLevelOffset)
      val indicator = EmptyProgressIndicator()
      val inspectionResult = readAction {
        jobToIndicator(currentThreadContext().job, indicator) {
          if (!isInjected) {
            inspectEx(listOf(inspectionTool), topLevelFile, lineRange, lineRange, true, isInjected, true,
                      indicator,
                      fun(_: LocalInspectionToolWrapper, _: ProblemDescriptor): Boolean {
                        return true
                      })
          }
          else {
            val textRange = getLineRange(psiFile, targetOffset)
            InspectionEngine.inspectElements(listOf(inspectionTool), psiFile, textRange, true, true, indicator,
                                             PsiTreeUtil.collectElements(psiFile) { it.textRange.intersects(textRange) }.toList(), fun(_: LocalInspectionToolWrapper, _: ProblemDescriptor): Boolean {
              return true
            })
          }
        }
      }
      val result = readAction {
        for (entry: MutableMap.MutableEntry<LocalInspectionToolWrapper?, List<ProblemDescriptor?>?> in inspectionResult.entries) {
          val descriptors = entry.value ?: continue
          for (descriptor in descriptors) {
            if (descriptor !is ProblemDescriptorBase) continue
            val descriptorRange = descriptor.textRange ?: continue
            if (!isInjected && highlightInfo.range != descriptorRange) continue
            if (isInjected && highlightInfo.range != injectedLanguageManager.injectedToHost(psiFile, descriptorRange)) continue
            val fixes = descriptor.fixes ?: continue
            for (i in 0..fixes.size - 1) {
              val intentionAction = wrap(descriptor, i)
              if (intentionAction.text == presentableName && availableFor(psiFile, editor, targetOffset, intentionAction)) {
                return@readAction intentionAction
              }
            }
          }
        }
        null
      }
      return@runWithModalProgressBlocking result
    }
    if (action == null) return
    val marker = editor.document.createRangeMarker(offset, offset)
    val targetMarker = editor.document.createRangeMarker(targetOffset, targetOffset)
    editor.caretModel.moveToOffset(targetOffset)
    ShowIntentionActionsHandler.chooseActionAndInvoke(topLevelFile, topLevelEditor, action, presentableName)
    if (targetMarker.isValid && targetMarker.startOffset != editor.caretModel.offset) {
      //probably, intention moves the cursor
      return
    }
    if (marker.isValid) {
      editor.caretModel.moveToOffset(marker.endOffset)
    }
    else {
      editor.caretModel.moveToOffset(offset)
    }
  }

  override fun getPreview(): IntentionPreviewInfo {
    return previewProvider() ?: IntentionPreviewInfo.EMPTY
  }
}