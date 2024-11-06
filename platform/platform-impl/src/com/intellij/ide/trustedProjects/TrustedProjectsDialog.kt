// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.trustedProjects

import com.intellij.diagnostic.WindowsDefenderChecker
import com.intellij.diagnostic.WindowsDefenderCheckerActivity
import com.intellij.diagnostic.WindowsDefenderStatisticsCollector
import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.OpenUntrustedProjectChoice
import com.intellij.ide.impl.TRUSTED_PROJECTS_HELP_TOPIC
import com.intellij.ide.impl.TrustedPathsSettings
import com.intellij.ide.impl.TrustedProjectsStatistics
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.ThreeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

object TrustedProjectsDialog {
  /**
   * Shows the "Trust project?" dialog, if the user wasn't asked yet if they trust this project,
   * and sets the project trusted state according to the user choice.
   *
   * @return false if the user chose not to open (link) the project at all;
   *   true otherwise, i.e. if the user chose to open (link) the project either in trust or in the safe mode,
   *   or if the confirmation wasn't shown because the project trust state was already known.
   */
  suspend fun confirmOpeningOrLinkingUntrustedProject(
    projectRoot: Path,
    project: Project?,
    @NlsContexts.DialogTitle title: String,
    @NlsContexts.DialogMessage message: String = IdeBundle.message("untrusted.project.open.dialog.text", ApplicationInfo.getInstance().fullApplicationName),
    @NlsContexts.Button trustButtonText: String = IdeBundle.message("untrusted.project.dialog.trust.button"),
    @NlsContexts.Button distrustButtonText: String = IdeBundle.message("untrusted.project.open.dialog.distrust.button"),
    @NlsContexts.Button cancelButtonText: String = IdeBundle.message("untrusted.project.open.dialog.cancel.button")
  ): Boolean {
    val locatedProject = TrustedProjectsLocator.locateProject(projectRoot, project)
    val projectTrustedState = TrustedProjects.getProjectTrustedState(locatedProject)
    if (projectTrustedState == ThreeState.YES) {
      TrustedProjects.setProjectTrusted(locatedProject = locatedProject, isTrusted = true)
    }
    if (projectTrustedState != ThreeState.UNSURE) {
      return true
    }

    val pathsToExclude = getDefenderExcludePaths(project, projectRoot)
    val dialog = withContext(Dispatchers.EDT) {
      val dialog = TrustedProjectStartupDialog(
        project, projectRoot, pathsToExclude, title, message, trustButtonText, distrustButtonText, cancelButtonText
      )
      writeIntentReadAction {
        dialog.show()
      }
      dialog
    }
    val openChoice = dialog.getOpenChoice()

    if (openChoice == OpenUntrustedProjectChoice.TRUST_AND_OPEN) {
      TrustedProjects.setProjectTrusted(locatedProject, true)
      if (projectRoot.parent != null && dialog.isTrustAll()) {
        val projectLocationPath = projectRoot.parent.toString()
        TrustedProjectsStatistics.TRUST_LOCATION_CHECKBOX_SELECTED.log()
        service<TrustedPathsSettings>().addTrustedPath(projectLocationPath)
      }
    }
    if (openChoice == OpenUntrustedProjectChoice.OPEN_IN_SAFE_MODE) {
      TrustedProjects.setProjectTrusted(locatedProject, false)
    }

    TrustedProjectsStatistics.NEW_PROJECT_OPEN_OR_IMPORT_CHOICE.log(openChoice)

    if (openChoice == OpenUntrustedProjectChoice.TRUST_AND_OPEN) {
      dialog.getDefenderTrustFolder()?.let { defenderTrustDir ->
        WindowsDefenderStatisticsCollector.excludedFromTrustDialog(dialog.isTrustAll())
        val checker = serviceAsync<WindowsDefenderChecker>()
        if (project == null) {
          checker.markProjectPath(projectRoot)
        }
        (pathsToExclude as MutableList<Path>).add(0, defenderTrustDir)
        WindowsDefenderCheckerActivity.runAndNotify(project) {
          checker.excludeProjectPaths(project, projectRoot, pathsToExclude)
        }
      }
    }

    return openChoice != OpenUntrustedProjectChoice.CANCEL
  }

  private suspend fun getDefenderExcludePaths(project: Project?, projectPath: Path): List<Path> {
    if (SystemInfo.isWindows) {
      val checker = serviceAsync<WindowsDefenderChecker>()
      if (
        !checker.isUnderDownloads(projectPath) &&
        !checker.isStatusCheckIgnored(project) &&
        checker.isRealTimeProtectionEnabled == true
      ) {
        val paths = checker.filterDevDrivePaths(checker.getPathsToExclude(project, projectPath)).toMutableList()
        if (paths.isEmpty()) {
          logger<TrustedProjectsDialog>().info("all paths are on a DevDrive")
        }
        paths.remove(projectPath) // a project directory is not needed for the dialog and might be changed by it
        return paths
      }
    }
    return emptyList()
  }
  
  suspend fun confirmLoadingUntrustedProjectAsync(
    project: Project,
    @NlsContexts.DialogTitle title: String,
    @NlsContexts.DialogMessage message: String,
    @NlsContexts.Button trustButtonText: String,
    @NlsContexts.Button distrustButtonText: String
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

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use async method instead")
  fun confirmLoadingUntrustedProject(
    project: Project,
    @NlsContexts.DialogTitle title: String,
    @NlsContexts.DialogMessage message: String,
    @NlsContexts.Button trustButtonText: String,
    @NlsContexts.Button distrustButtonText: String
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
