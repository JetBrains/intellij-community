// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("TrustedProjects")

package com.intellij.ide.impl

import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.TrustedCheckResult.NotTrusted
import com.intellij.ide.impl.TrustedCheckResult.Trusted
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.FileUtil.getLocationRelativeToUserHome
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.SystemProperties
import com.intellij.util.ThreeState
import com.intellij.util.messages.Topic
import com.intellij.util.xmlb.annotations.Attribute
import java.nio.file.Path
import java.nio.file.Paths


fun confirmOpeningUntrustedProject(
  virtualFile: VirtualFile,
  @NlsContexts.DialogTitle title: String,
  @NlsContexts.DialogMessage message: String,
  @NlsContexts.Button trustButtonText: String,
  @NlsContexts.Button distrustButtonText: String,
  @NlsContexts.Button cancelButtonText: String,
) = confirmOpeningUntrustedProject(virtualFile) {
  MessageDialogBuilder.yesNoCancel(title, message)
    .yesText(trustButtonText)
    .noText(distrustButtonText)
    .cancelText(cancelButtonText)
}

fun confirmOpeningUntrustedProject(
  virtualFile: VirtualFile,
  createDialog: () -> MessageDialogBuilder.YesNoCancel
): OpenUntrustedProjectChoice {
  val projectDir = if (virtualFile.isDirectory) virtualFile else virtualFile.parent
  val trustedCheckResult = getImplicitTrustedCheckResult(projectDir.toNioPath())
  if (trustedCheckResult is Trusted) {
    return OpenUntrustedProjectChoice.IMPORT
  }

  val choice = createDialog()
    .doNotAsk(createDoNotAskOptionForLocation(projectDir.parent.path))
    .asWarning()
    .show(project = null)

  val openChoice = when (choice) {
    Messages.YES -> OpenUntrustedProjectChoice.IMPORT
    Messages.NO -> OpenUntrustedProjectChoice.OPEN_WITHOUT_IMPORTING
    Messages.CANCEL -> OpenUntrustedProjectChoice.CANCEL
    else -> {
      LOG.error("Illegal choice $choice")
      return OpenUntrustedProjectChoice.CANCEL
    }
  }
  TrustedProjectsStatistics.NEW_PROJECT_OPEN_OR_IMPORT_CHOICE.log(openChoice)
  return openChoice
}

fun confirmLoadingUntrustedProject(
  project: Project,
  @NlsContexts.DialogTitle title: String,
  @NlsContexts.DialogMessage message: String,
  @NlsContexts.Button trustButtonText: String,
  @NlsContexts.Button distrustButtonText: String
) = confirmLoadingUntrustedProject(project) {
  MessageDialogBuilder.yesNo(title, message)
    .yesText(trustButtonText)
    .noText(distrustButtonText)
}

fun confirmLoadingUntrustedProject(project: Project, createDialog: () -> MessageDialogBuilder.YesNo): Boolean {
  val trustedCheckResult = getImplicitTrustedCheckResult(project)
  if (trustedCheckResult is Trusted) {
    project.setTrusted(true)
    return true
  }

  val answer = createDialog()
    .asWarning()
    .ask(project)
  project.setTrusted(answer)
  TrustedProjectsStatistics.LOAD_UNTRUSTED_PROJECT_CONFIRMATION_CHOICE.log(project, answer)
  return answer
}

enum class OpenUntrustedProjectChoice {
  IMPORT,
  OPEN_WITHOUT_IMPORTING,
  CANCEL;
}

fun Project.isTrusted() = this.service<TrustedProjectSettings>().trustedState == ThreeState.YES

fun Project.getTrustedState() = this.service<TrustedProjectSettings>().trustedState

fun Project.getExplicitTrustedStateOrByHostAndLocation(): ThreeState {
  val explicit = getTrustedState()
  if (explicit != ThreeState.UNSURE) return explicit

  return if (getImplicitTrustedCheckResult(this) is Trusted) {
    ThreeState.YES
  }
  else {
    ThreeState.UNSURE
  }
}

fun Project.setTrusted(value: Boolean) {
  this.service<TrustedProjectSettings>().trustedState = ThreeState.fromBoolean(value)
  if(value) {
    ApplicationManager.getApplication().messageBus.syncPublisher(TrustChangeNotifier.TOPIC).projectTrusted(this)
  }
}

fun createDoNotAskOptionForLocation(projectLocationPath: String): DialogWrapper.DoNotAskOption {
  return object : DialogWrapper.DoNotAskOption.Adapter() {
    override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
      if (isSelected && exitCode == Messages.YES) {
        TrustedProjectsStatistics.TRUST_LOCATION_CHECKBOX_SELECTED.log()
        service<TrustedPathsSettings>().addTrustedPath(projectLocationPath)
      }
    }

    override fun getDoNotShowMessage(): String {
      val path = getLocationRelativeToUserHome(projectLocationPath, false)
      return IdeBundle.message("untrusted.project.warning.trust.location.checkbox", path)
    }
  }
}

fun isProjectImplicitlyTrusted(projectDir: Path?): Boolean {
  return getImplicitTrustedCheckResult(projectDir) is Trusted
}

private fun isTrustedCheckDisabled() = ApplicationManager.getApplication().isUnitTestMode ||
                                       ApplicationManager.getApplication().isHeadlessEnvironment ||
                                       SystemProperties.`is`("idea.is.integration.test")

private sealed class TrustedCheckResult {
  object Trusted: TrustedCheckResult()
  class NotTrusted(val url: String?): TrustedCheckResult()
}

private fun getImplicitTrustedCheckResult(project: Project): TrustedCheckResult =
  getImplicitTrustedCheckResult(project.basePath?.let { Paths.get(it) }, project)

private fun getImplicitTrustedCheckResult(projectDir: Path?, project: Project? = null): TrustedCheckResult {
  if (isTrustedCheckDisabled()) {
    return Trusted
  }
  if (projectDir != null && service<TrustedPathsSettings>().isPathTrusted(projectDir)) {
    TrustedProjectsStatistics.PROJECT_IMPLICITLY_TRUSTED_BY_PATH.log(project)
    return Trusted
  }
  return NotTrusted(null)
}

@State(name = "Trusted.Project.Settings", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
@Service(Service.Level.PROJECT)
class TrustedProjectSettings : SimplePersistentStateComponent<TrustedProjectSettings.State>(State()) {

  class State : BaseState() {
    @get:Attribute
    var isTrusted by enum(ThreeState.UNSURE)

    @get:Attribute
    var hasCheckedIfOldProject by property(false)
  }

  var trustedState: ThreeState
    get() = if (isTrustedCheckDisabled()) ThreeState.YES else state.isTrusted
    set(value) {
      state.isTrusted = value
    }

  var hasCheckedIfOldProject: Boolean
    get() = state.hasCheckedIfOldProject
    set(value) {
      state.hasCheckedIfOldProject = value
    }
}

interface TrustChangeNotifier {
  fun projectTrusted(project: Project)

  companion object {
    @JvmField
    @Topic.AppLevel
    val TOPIC = Topic.create("Trusted project status", TrustChangeNotifier::class.java)
  }
}

const val TRUSTED_PROJECTS_HELP_TOPIC = "Project_security"

private val LOG = Logger.getInstance("com.intellij.ide.impl.TrustedProjects")

