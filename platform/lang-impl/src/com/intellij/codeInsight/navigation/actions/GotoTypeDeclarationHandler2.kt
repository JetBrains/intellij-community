// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation.actions

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.navigation.CtrlMouseInfo
import com.intellij.codeInsight.navigation.impl.*
import com.intellij.openapi.actionSystem.ex.ActionUtil.underModalProgress
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.ui.list.createTargetPopup

internal object GotoTypeDeclarationHandler2 : CodeInsightActionHandler {

  override fun startInWriteAction(): Boolean = false

  override fun invoke(project: Project, editor: Editor, file: PsiFile) {
    val offset = editor.caretModel.offset
    val dumbService = DumbService.getInstance(project)
    val result: GTTDActionResult? = try {
      underModalProgress(project, CodeInsightBundle.message("progress.title.resolving.reference")) {
        dumbService.computeWithAlternativeResolveEnabled<GTTDActionResult?, Throwable> {
          handleLookup(project, editor, offset)
          ?: gotoTypeDeclaration(file, offset)?.result()
        }
      }
    }
    catch (e: IndexNotReadyException) {
      dumbService.showDumbModeNotification(CodeInsightBundle.message("message.navigation.is.not.available.here.during.index.update"))
      return
    }
    if (result == null) {
      return
    }
    gotoTypeDeclaration(editor, file, result)
  }

  private fun gotoTypeDeclaration(editor: Editor, file: PsiFile, actionResult: GTTDActionResult) {
    when (actionResult) {
      is GTTDActionResult.SingleTarget -> {
        gotoTarget(editor, file, actionResult.navigatable())
      }
      is GTTDActionResult.MultipleTargets -> {
        val popup = createTargetPopup(
          CodeInsightBundle.message("choose.type.popup.title"),
          actionResult.targets, SingleTargetWithPresentation::presentation
        ) { (navigatable, _) ->
          gotoTarget(editor, file, navigatable())
        }
        popup.showInBestPositionFor(editor)
      }
    }
  }

  private fun handleLookup(project: Project, editor: Editor, offset: Int): GTTDActionResult? {
    val fromLookup = TargetElementUtil.getTargetElementFromLookup(project) ?: return null
    return result(elementTypeTargets(editor, offset, listOf(fromLookup)))
  }

  @JvmStatic
  fun getCtrlMouseInfo(file: PsiFile, offset: Int): CtrlMouseInfo? {
    return gotoTypeDeclaration(file, offset)?.ctrlMouseInfo()
  }
}
