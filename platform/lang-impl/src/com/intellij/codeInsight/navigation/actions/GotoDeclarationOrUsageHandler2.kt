// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation.actions

import com.intellij.CommonBundle
import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.navigation.CtrlMouseData
import com.intellij.codeInsight.navigation.actions.GotoDeclarationOnlyHandler2.Companion.gotoDeclaration
import com.intellij.codeInsight.navigation.impl.GTDUActionData
import com.intellij.codeInsight.navigation.impl.GTDUActionResult
import com.intellij.codeInsight.navigation.impl.fromGTDProviders
import com.intellij.codeInsight.navigation.impl.gotoDeclarationOrUsages
import com.intellij.codeInsight.navigation.impl.toGTDUActionData
import com.intellij.find.FindUsagesSettings
import com.intellij.ide.IdeBundle
import com.intellij.ide.trustedProjects.TrustedFiles
import com.intellij.find.actions.ShowUsagesAction.showUsages
import com.intellij.find.actions.TargetVariant
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil.underModalProgress
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.DumbModeBlockedFunctionality
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.platform.ide.navigation.NavigationOptions
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.Callable

private val NAVIGATION_CONFIRMED_IN_UNTRUSTED_FILE = Key.create<Boolean>("navigation.confirmed.in.untrusted.file")

/**
 * Navigation from a file opened in the safe mode (see [TrustedFiles]) resolves references and can open other files,
 * so it requires an explicit one-time (per file, per session) confirmation.
 */
private fun confirmNavigationFromUntrustedFile(project: Project, psiFile: PsiFile): Boolean {
  val virtualFile = psiFile.virtualFile ?: return true
  if (TrustedFiles.isTrusted(virtualFile, project)) {
    return true
  }
  if (virtualFile.getUserData(NAVIGATION_CONFIRMED_IN_UNTRUSTED_FILE) == true) {
    return true
  }
  val answer = MessageDialogBuilder.yesNo(
    IdeBundle.message("untrusted.file.navigation.dialog.title"),
    IdeBundle.message("untrusted.file.navigation.dialog.text", virtualFile.name))
    .yesText(IdeBundle.message("untrusted.file.navigation.dialog.navigate.button"))
    .noText(CommonBundle.getCancelButtonText())
    .asWarning()
    .ask(project)
  if (answer) {
    virtualFile.putUserData(NAVIGATION_CONFIRMED_IN_UNTRUSTED_FILE, true)
  }
  return answer
}

@ApiStatus.Internal
class GotoDeclarationOrUsageHandler2 internal constructor(
  private val reporter: GotoDeclarationReporter?,
  private val navigationOptions: NavigationOptions,
) : CodeInsightActionHandler {

  @ApiStatus.Internal
  companion object {

    private fun gotoDeclarationOrUsages(project: Project, editor: Editor, file: PsiFile, offset: Int): GTDUActionData? {
      return fromGTDProviders(project, editor, offset)?.toGTDUActionData()
             ?: gotoDeclarationOrUsages(file, offset)
    }

    @JvmStatic
    fun getCtrlMouseData(editor: Editor, file: PsiFile, offset: Int): CtrlMouseData? {
      return gotoDeclarationOrUsages(file.project, editor, file, offset)?.ctrlMouseData()
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

    @TestOnly
    @JvmStatic
    fun testGTDUOutcomeInNonBlockingReadAction(editor: Editor, file: PsiFile, offset: Int): GTDUOutcome? {
      val callable = Callable {
        testGTDUOutcome(editor, file, offset)
      }
      return ReadAction.nonBlocking(callable).submit(AppExecutorUtil.getAppExecutorService()).get()
    }
  }

  override fun startInWriteAction(): Boolean = false

  override fun invoke(project: Project, editor: Editor, psiFile: PsiFile) {
    if (navigateToLookupItem(project, editor)) {
      return
    }
    if (EditorUtil.isCaretInVirtualSpace(editor)) {
      return
    }
    if (!confirmNavigationFromUntrustedFile(project, psiFile)) {
      return
    }

    val offset = editor.caretModel.offset
    try {
      PsiUtilCore.ensureValid(psiFile)
      val actionResult: GTDUActionResult? = underModalProgress(
        project,
        CodeInsightBundle.message("progress.title.resolving.reference")
      ) {
        PsiUtilCore.ensureValid(psiFile)
        gotoDeclarationOrUsages(project, editor, psiFile, offset)?.result()
      }
      when (actionResult) {
        null -> {
          reporter?.reportDeclarationSearchFinished(GotoDeclarationReporter.DeclarationsFound.NONE)
          notifyNowhereToGo(project, editor, psiFile, offset)
        }
        is GTDUActionResult.GTD -> {
          GTDUCollector.recordPerformed(GTDUCollector.GTDUChoice.GTD)
          gotoDeclaration(project, editor, actionResult.navigationActionResult, reporter, navigationOptions)
        }
        is GTDUActionResult.SU -> {
          reporter?.reportDeclarationSearchFinished(GotoDeclarationReporter.DeclarationsFound.NONE)
          GTDUCollector.recordPerformed(GTDUCollector.GTDUChoice.SU)
          showUsages(project, editor, psiFile, actionResult.targetVariants)
        }
      }
    }
    catch (_: IndexNotReadyException) {
      DumbService.getInstance(project).showDumbModeNotificationForFunctionality(
        CodeInsightBundle.message("message.navigation.is.not.available.here.during.index.update"),
        DumbModeBlockedFunctionality.GotoDeclarationOrUsage
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
      showUsages(project,
                 searchTargets,
                 JBPopupFactory.getInstance().guessBestPopupLocation(editor),
                 editor,
                 FindUsagesOptions.findScopeByName(project, dataContext, FindUsagesSettings.getInstance().getDefaultScopeName()))
    }
    catch (_: IndexNotReadyException) {
      DumbService.getInstance(project).showDumbModeNotificationForFunctionality(
        CodeInsightBundle.message("message.navigation.is.not.available.here.during.index.update"),
        DumbModeBlockedFunctionality.GotoDeclarationOrUsage
      )
    }
  }

  @ApiStatus.Internal
  @TestOnly
  enum class GTDUOutcome {
    GTD,
    SU,
    ;
  }
}
