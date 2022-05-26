// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation.actions

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.navigation.CtrlMouseData
import com.intellij.codeInsight.navigation.CtrlMouseInfo
import com.intellij.codeInsight.navigation.actions.GotoDeclarationOnlyHandler2.gotoDeclaration
import com.intellij.codeInsight.navigation.impl.*
import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.find.actions.ShowUsagesAction.showUsages
import com.intellij.find.actions.TargetVariant
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil.underModalProgress
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.TestOnly

object GotoDeclarationOrUsageHandler2 : CodeInsightActionHandler {

  override fun startInWriteAction(): Boolean = false

  private fun gotoDeclarationOrUsages(project: Project, editor: Editor, file: PsiFile, offset: Int): GTDUActionData? {
    return fromGTDProviders(project, editor, offset)?.toGTDUActionData()
           ?: gotoDeclarationOrUsages(file, offset)
  }

  @JvmStatic
  fun getCtrlMouseInfo(editor: Editor, file: PsiFile, offset: Int): CtrlMouseInfo? {
    return gotoDeclarationOrUsages(file.project, editor, file, offset)?.ctrlMouseInfo()
  }

  @JvmStatic
  fun getCtrlMouseData(editor: Editor, file: PsiFile, offset: Int): CtrlMouseData? {
    return gotoDeclarationOrUsages(file.project, editor, file, offset)?.ctrlMouseData()
  }

  override fun invoke(project: Project, editor: Editor, file: PsiFile) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.goto.declaration")
    if (navigateToLookupItem(project)) {
      return
    }
    if (EditorUtil.isCaretInVirtualSpace(editor)) {
      return
    }

    val offset = editor.caretModel.offset
    try {
      val actionResult: GTDUActionResult? = underModalProgress(
        project,
        CodeInsightBundle.message("progress.title.resolving.reference")
      ) {
        gotoDeclarationOrUsages(project, editor, file, offset)?.result()
      }
      when (actionResult) {
        null -> notifyNowhereToGo(project, editor, file, offset)
        is GTDUActionResult.GTD -> {
          GTDUCollector.recordPerformed(GTDUCollector.GTDUChoice.GTD)
          gotoDeclaration(project, editor, actionResult.navigationActionResult)
        }
        is GTDUActionResult.SU -> {
          GTDUCollector.recordPerformed(GTDUCollector.GTDUChoice.SU)
          showUsages(project, editor, file, actionResult.targetVariants)
        }
      }
    }
    catch (e: IndexNotReadyException) {
      DumbService.getInstance(project).showDumbModeNotification(
        CodeInsightBundle.message("message.navigation.is.not.available.here.during.index.update")
      )
    }
  }

  private fun showUsages(project: Project, editor: Editor, file: PsiFile, searchTargets: List<TargetVariant>) {
    require(searchTargets.isNotEmpty())
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PSI_FILE, file)
      .add(CommonDataKeys.EDITOR, editor)
      .add(PlatformCoreDataKeys.CONTEXT_COMPONENT, editor.contentComponent)
      .build()
    try {
      showUsages(project, dataContext, searchTargets)
    }
    catch (e: IndexNotReadyException) {
      DumbService.getInstance(project).showDumbModeNotification(
        CodeInsightBundle.message("message.navigation.is.not.available.here.during.index.update")
      )
    }
  }

  @TestOnly
  enum class GTDUOutcome {
    GTD,
    SU,
    ;
  }

  @TestOnly
  @JvmStatic
  fun testGTDUOutcome(editor: Editor, file: PsiFile, offset: Int): GTDUOutcome? {
    return when (gotoDeclarationOrUsages(file.project, editor, file, offset)?.result()) {
      null -> null
      is GTDUActionResult.GTD -> GTDUOutcome.GTD
      is GTDUActionResult.SU -> GTDUOutcome.SU
    }
  }
}
