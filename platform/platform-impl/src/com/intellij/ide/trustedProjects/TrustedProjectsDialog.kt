// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.trustedProjects

import com.intellij.diagnostic.WindowsDefenderChecker
import com.intellij.diagnostic.WindowsDefenderCheckerActivity
import com.intellij.diagnostic.WindowsDefenderStatisticsCollector
import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.OpenUntrustedProjectChoice
import com.intellij.ide.impl.TRUSTED_PROJECTS_HELP_TOPIC
import com.intellij.ide.impl.TrustedPathsSettings
import com.intellij.ide.impl.TrustedProjectsStatistics
import com.intellij.ide.trustedProjects.impl.TrustedProjectStartupDialog
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.ThreeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

object TrustedProjectsDialog {
  /**
   * Shows the "Trust project" dialog if the user wasn't asked yet if they trust this project
   * and sets the project trusted state according to the user's choice.
   *
   * @return `false` if the user chose not to open (link) the project at all;
   *   `true` otherwise, i.e., if the user chose to open (link) the project either in trust or in the safe mode,
   *   or if the confirmation wasn't shown because the project trust state was already known.
   */
  suspend fun confirmOpeningOrLinkingUntrustedProject(
    projectRoot: Path,
    project: Project?,
    title: @NlsContexts.DialogTitle String,
    message: @NlsContexts.DialogMessage String = IdeBundle.message("untrusted.project.open.dialog.text", ApplicationInfo.getInstance().fullApplicationName),
    trustButtonText: @NlsContexts.Button String = IdeBundle.message("untrusted.project.dialog.trust.button"),
    distrustButtonText: @NlsContexts.Button String = IdeBundle.message("untrusted.project.open.dialog.distrust.button"),
    cancelButtonText: @NlsContexts.Button String = IdeBundle.message("untrusted.project.open.dialog.cancel.button")
  ): Boolean {
    val locatedProject = TrustedProjectsLocator.locateProject(projectRoot, project)
    val projectTrustedState = TrustedProjects.getProjectTrustedState(locatedProject)
    if (projectTrustedState == ThreeState.YES) {
      TrustedProjects.setProjectTrusted(locatedProject, isTrusted = true)
    }
    if (projectTrustedState != ThreeState.UNSURE) {
      return true
    }

    val dialog = TrustedProjectStartupDialog.showAndGet(
      project, projectRoot,
      title, message, trustButtonText, distrustButtonText, cancelButtonText
    )
    val pathsToExclude = dialog.defenderExcludePaths
    val openChoice = dialog.openChoice

    if (openChoice == OpenUntrustedProjectChoice.TRUST_AND_OPEN) {
      TrustedProjects.setProjectTrusted(locatedProject, true)
      if (projectRoot.parent != null && dialog.isTrustAll) {
        val projectLocationPath = projectRoot.parent.toString()
        TrustedProjectsStatistics.TRUST_LOCATION_CHECKBOX_SELECTED.log()
        service<TrustedPathsSettings>().addTrustedPath(projectLocationPath)
      }
    }
    else if (openChoice == OpenUntrustedProjectChoice.OPEN_IN_SAFE_MODE) {
      TrustedProjects.setProjectTrusted(locatedProject, false)
    }

    if (openChoice != OpenUntrustedProjectChoice.CANCEL && pathsToExclude.isNotEmpty()) {
      val checker = serviceAsync<WindowsDefenderChecker>()
      val defenderTrustDir = dialog.defenderTrustFolder
      if (openChoice == OpenUntrustedProjectChoice.TRUST_AND_OPEN && defenderTrustDir != null) {
        checker.markProjectPath(projectRoot, /*skip =*/ false)
        WindowsDefenderStatisticsCollector.excludedFromTrustDialog(dialog.isTrustAll)
        if (defenderTrustDir != projectRoot) {
          (pathsToExclude as MutableList<Path>).apply {
            remove(projectRoot)
            add(0, defenderTrustDir)
          }
        }
        WindowsDefenderCheckerActivity.runAndNotify(project) {
          checker.excludeProjectPaths(project, projectRoot, pathsToExclude)
        }
      }
      else {
        checker.markProjectPath(projectRoot, /*skip =*/ true)
      }
    }

    TrustedProjectsStatistics.NEW_PROJECT_OPEN_OR_IMPORT_CHOICE.log(openChoice)
    if (pathsToExclude.isNotEmpty()) {
      WindowsDefenderStatisticsCollector.checkboxShownInTrustDialog()
    }

    return openChoice != OpenUntrustedProjectChoice.CANCEL
  }

  suspend fun confirmLoadingUntrustedProjectAsync(
    project: Project,
    title: @NlsContexts.DialogTitle String = IdeBundle.message("untrusted.project.general.dialog.title"),
    message: @NlsContexts.DialogMessage String = IdeBundle.message("untrusted.project.open.dialog.text", ApplicationInfoEx.getInstanceEx().fullApplicationName),
    trustButtonText: @NlsContexts.Button String = IdeBundle.message("untrusted.project.dialog.trust.button"),
    distrustButtonText: @NlsContexts.Button String = IdeBundle.message("untrusted.project.dialog.distrust.button"),
  ): Boolean {
    val locatedProject = TrustedProjectsLocator.locateProject(project)
    if (TrustedProjects.isProjectTrusted(locatedProject)) {
      TrustedProjects.setProjectTrusted(locatedProject, true)
      return true
    }

    val answer = withContext(Dispatchers.EDT) {
      MessageDialogBuilder.yesNo(title, message)
        .yesText(trustButtonText)
        .noText(distrustButtonText)
        .asWarning()
        .help(TRUSTED_PROJECTS_HELP_TOPIC)
        .ask(project)
    }

    TrustedProjects.setProjectTrusted(locatedProject, answer)

    TrustedProjectsStatistics.LOAD_UNTRUSTED_PROJECT_CONFIRMATION_CHOICE.log(project, answer)

    return answer
  }

  @JvmStatic
  @ApiStatus.Obsolete
  fun confirmLoadingUntrustedProject(
    project: Project,
    title: @NlsContexts.DialogTitle String = IdeBundle.message("untrusted.project.general.dialog.title"),
    message: @NlsContexts.DialogMessage String = IdeBundle.message("untrusted.project.open.dialog.text", ApplicationInfoEx.getInstanceEx().fullApplicationName),
    trustButtonText: @NlsContexts.Button String = IdeBundle.message("untrusted.project.dialog.trust.button"),
    distrustButtonText: @NlsContexts.Button String = IdeBundle.message("untrusted.project.dialog.distrust.button"),
  ): Boolean {
    val locatedProject = TrustedProjectsLocator.locateProject(project)
    if (TrustedProjects.isProjectTrusted(locatedProject)) {
      TrustedProjects.setProjectTrusted(locatedProject, true)
      return true
    }

    val answer = invokeAndWaitIfNeeded {
      MessageDialogBuilder.yesNo(title, message)
        .yesText(trustButtonText)
        .noText(distrustButtonText)
        .asWarning()
        .help(TRUSTED_PROJECTS_HELP_TOPIC)
        .ask(project)
    }

    TrustedProjects.setProjectTrusted(locatedProject, answer)

    TrustedProjectsStatistics.LOAD_UNTRUSTED_PROJECT_CONFIRMATION_CHOICE.log(project, answer)

    return answer
  }
}