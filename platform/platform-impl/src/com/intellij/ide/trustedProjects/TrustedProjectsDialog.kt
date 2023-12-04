// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DuplicatedCode")

package com.intellij.ide.trustedProjects

import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.OpenUntrustedProjectChoice
import com.intellij.ide.impl.TRUSTED_PROJECTS_HELP_TOPIC
import com.intellij.ide.impl.TrustedPathsSettings
import com.intellij.ide.impl.TrustedProjectsStatistics
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.ThreeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

object TrustedProjectsDialog {

  private val LOG = logger<TrustedProjects>()

  /**
   * Shows the "Trust project?" dialog, if the user wasn't asked yet if they trust this project,
   * and sets the project trusted state according to the user choice.
   *
   * @return false if the user chose not to open (link) the project at all;
   *   true otherwise, i.e. if the user chose to open (link) the project either in trust or in the safe mode,
   *   or if the confirmation wasn't shown because the project trust state was already known.
   */
  suspend fun confirmOpeningOrLinkingUntrustedProjectAsync(
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
      TrustedProjects.setProjectTrusted(locatedProject, true)
    }
    if (projectTrustedState != ThreeState.UNSURE) {
      return true
    }

    val doNotAskOption = projectRoot.parent?.let(TrustedProjectsDialog::createDoNotAskOptionForLocation)
    val choice = withContext(Dispatchers.EDT) {
      MessageDialogBuilder.Message(title, message)
        .buttons(trustButtonText, distrustButtonText, cancelButtonText)
        .defaultButton(trustButtonText)
        .focusedButton(distrustButtonText)
        .doNotAsk(doNotAskOption)
        .asWarning()
        .help(TRUSTED_PROJECTS_HELP_TOPIC)
        .show()
    }

    val openChoice = when (choice) {
      trustButtonText -> OpenUntrustedProjectChoice.TRUST_AND_OPEN
      distrustButtonText -> OpenUntrustedProjectChoice.OPEN_IN_SAFE_MODE
      cancelButtonText, null -> OpenUntrustedProjectChoice.CANCEL
      else -> {
        LOG.error("Illegal choice $choice")
        return false
      }
    }

    if (openChoice == OpenUntrustedProjectChoice.TRUST_AND_OPEN) {
      TrustedProjects.setProjectTrusted(locatedProject, true)
    }
    if (openChoice == OpenUntrustedProjectChoice.OPEN_IN_SAFE_MODE) {
      TrustedProjects.setProjectTrusted(locatedProject, false)
    }

    TrustedProjectsStatistics.NEW_PROJECT_OPEN_OR_IMPORT_CHOICE.log(openChoice)

    return openChoice != OpenUntrustedProjectChoice.CANCEL
  }

  private fun createDoNotAskOptionForLocation(projectLocation: Path): DoNotAskOption {
    val projectLocationPath = projectLocation.toString()
    return object : DoNotAskOption.Adapter() {
      override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
        if (isSelected && exitCode == Messages.YES) {
          TrustedProjectsStatistics.TRUST_LOCATION_CHECKBOX_SELECTED.log()
          service<TrustedPathsSettings>().addTrustedPath(projectLocationPath)
        }
      }

      override fun getDoNotShowMessage(): String {
        val path = FileUtil.getLocationRelativeToUserHome(projectLocationPath, false)
        return IdeBundle.message("untrusted.project.warning.trust.location.checkbox", path)
      }
    }
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
  fun confirmOpeningOrLinkingUntrustedProject(
    projectRoot: Path,
    project: Project?,
    @NlsContexts.DialogTitle title: String,
    @NlsContexts.DialogMessage message: String,
    @NlsContexts.Button trustButtonText: String,
    @NlsContexts.Button distrustButtonText: String,
    @NlsContexts.Button cancelButtonText: String
  ): Boolean {
    val locatedProject = TrustedProjectsLocator.locateProject(projectRoot, project)
    val projectTrustedState = TrustedProjects.getProjectTrustedState(locatedProject)
    if (projectTrustedState == ThreeState.YES) {
      TrustedProjects.setProjectTrusted(locatedProject, true)
    }
    if (projectTrustedState != ThreeState.UNSURE) {
      return true
    }

    val doNotAskOption = projectRoot.parent?.let(TrustedProjectsDialog::createDoNotAskOptionForLocation)
    val choice = invokeAndWaitIfNeeded {
      MessageDialogBuilder.Message(title, message)
        .buttons(trustButtonText, distrustButtonText, cancelButtonText)
        .defaultButton(trustButtonText)
        .focusedButton(distrustButtonText)
        .doNotAsk(doNotAskOption)
        .asWarning()
        .help(TRUSTED_PROJECTS_HELP_TOPIC)
        .show()
    }

    val openChoice = when (choice) {
      trustButtonText -> OpenUntrustedProjectChoice.TRUST_AND_OPEN
      distrustButtonText -> OpenUntrustedProjectChoice.OPEN_IN_SAFE_MODE
      cancelButtonText, null -> OpenUntrustedProjectChoice.CANCEL
      else -> {
        LOG.error("Illegal choice $choice")
        return false
      }
    }

    if (openChoice == OpenUntrustedProjectChoice.TRUST_AND_OPEN) {
      TrustedProjects.setProjectTrusted(locatedProject, true)
    }
    if (openChoice == OpenUntrustedProjectChoice.OPEN_IN_SAFE_MODE) {
      TrustedProjects.setProjectTrusted(locatedProject, false)
    }

    TrustedProjectsStatistics.NEW_PROJECT_OPEN_OR_IMPORT_CHOICE.log(openChoice)

    return openChoice != OpenUntrustedProjectChoice.CANCEL
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