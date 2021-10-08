// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("TrustedProjects")
@file:ApiStatus.Experimental

package com.intellij.ide.impl

import com.intellij.ide.IdeBundle
import com.intellij.ide.nls.NlsMessages
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
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.nio.file.Paths

fun confirmOpeningUntrustedProject(
  virtualFile: VirtualFile,
  projectTypeNames: List<String>,
): OpenUntrustedProjectChoice {
  val systemsPresentation: String = NlsMessages.formatAndList(projectTypeNames)
  return confirmOpeningUntrustedProject(
    virtualFile,
    IdeBundle.message("untrusted.project.open.dialog.title", systemsPresentation, projectTypeNames.size),
    IdeBundle.message("untrusted.project.open.dialog.text", systemsPresentation, projectTypeNames.size),
    IdeBundle.message("untrusted.project.dialog.trust.button"),
    IdeBundle.message("untrusted.project.open.dialog.distrust.button"),
    IdeBundle.message("untrusted.project.open.dialog.cancel.button")
  )
}

fun confirmOpeningUntrustedProject(
  virtualFile: VirtualFile,
  @NlsContexts.DialogTitle title: String,
  @NlsContexts.DialogMessage message: String,
  @NlsContexts.Button trustButtonText: String,
  @NlsContexts.Button distrustButtonText: String,
  @NlsContexts.Button cancelButtonText: String
): OpenUntrustedProjectChoice {
  val projectDir = if (virtualFile.isDirectory) virtualFile else virtualFile.parent
  if (isProjectImplicitlyTrusted(projectDir.toNioPath())) {
    return OpenUntrustedProjectChoice.IMPORT
  }

  val choice = MessageDialogBuilder.Message(title, message)
    .buttons(trustButtonText, distrustButtonText, cancelButtonText)
    .defaultButton(trustButtonText)
    .focusedButton(distrustButtonText)
    .doNotAsk(createDoNotAskOptionForLocation(projectDir.parent.path))
    .asWarning()
    .help(TRUSTED_PROJECTS_HELP_TOPIC)
    .show()

  val openChoice = when (choice) {
    trustButtonText -> OpenUntrustedProjectChoice.IMPORT
    distrustButtonText -> OpenUntrustedProjectChoice.OPEN_WITHOUT_IMPORTING
    cancelButtonText, null -> OpenUntrustedProjectChoice.CANCEL
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
) : Boolean {
  if (isProjectImplicitlyTrusted(project)) {
    project.setTrusted(true)
    return true
  }

  val answer = MessageDialogBuilder.yesNo(title, message)
    .yesText(trustButtonText)
    .noText(distrustButtonText)
    .asWarning()
    .help(TRUSTED_PROJECTS_HELP_TOPIC)
    .ask(project)
  project.setTrusted(answer)
  TrustedProjectsStatistics.LOAD_UNTRUSTED_PROJECT_CONFIRMATION_CHOICE.log(project, answer)
  return answer
}

@ApiStatus.Experimental
enum class OpenUntrustedProjectChoice {
  IMPORT,
  OPEN_WITHOUT_IMPORTING,
  CANCEL;
}

fun Project.isTrusted() = getTrustedState() == ThreeState.YES

fun Project.getTrustedState(): ThreeState {
  val explicit = this.service<TrustedProjectSettings>().trustedState
  if (explicit != ThreeState.UNSURE) return explicit
  return if (isProjectImplicitlyTrusted(this)) ThreeState.YES else ThreeState.UNSURE
}

fun Project.setTrusted(value: Boolean) {
  val oldValue = this.service<TrustedProjectSettings>().trustedState
  this.service<TrustedProjectSettings>().trustedState = ThreeState.fromBoolean(value)

  if (value && oldValue != ThreeState.YES) {
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

private fun isTrustedCheckDisabled() = ApplicationManager.getApplication().isUnitTestMode ||
                                       ApplicationManager.getApplication().isHeadlessEnvironment ||
                                       SystemProperties.`is`("idea.is.integration.test")

private fun isProjectImplicitlyTrusted(project: Project): Boolean =
  isProjectImplicitlyTrusted(project.basePath?.let { Paths.get(it) }, project)

@JvmOverloads
fun isProjectImplicitlyTrusted(projectDir: Path?, project : Project? = null): Boolean {
  if (isTrustedCheckDisabled()) {
    return true
  }
  if (projectDir != null && service<TrustedPathsSettings>().isPathTrusted(projectDir)) {
    TrustedProjectsStatistics.PROJECT_IMPLICITLY_TRUSTED_BY_PATH.log(project)
    return true
  }
  return false
}

@State(name = "Trusted.Project.Settings", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
@Service(Service.Level.PROJECT)
@ApiStatus.Internal
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

@ApiStatus.Experimental
fun interface TrustChangeNotifier {
  fun projectTrusted(project: Project)

  companion object {
    @JvmField
    @Topic.AppLevel
    val TOPIC = Topic.create("Trusted project status", TrustChangeNotifier::class.java)
  }
}

const val TRUSTED_PROJECTS_HELP_TOPIC = "Project_security"

private val LOG = Logger.getInstance("com.intellij.ide.impl.TrustedProjects")
