// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("TrustedProjects")
@file:ApiStatus.Experimental

package com.intellij.ide.impl

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.FileUtil.getLocationRelativeToUserHome
import com.intellij.util.SystemProperties
import com.intellij.util.ThreeState
import com.intellij.util.messages.Topic
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isDirectory

/**
 * Shows the "Trust this project?" dialog, if the user wasn't asked yet if they trust this project,
 * and sets the project trusted state according to the user choice.
 *
 * @return false if the user chose not to open the project at all;
 *   true otherwise, i.e. if the user chose to open the project either in trust or in the safe mode,
 *   or if the confirmation wasn't shown because the project trust state was already known.
 */
@ApiStatus.Internal
fun confirmOpeningAndSetProjectTrustedStateIfNeeded(projectDir: Path): Boolean {
  return invokeAndWaitIfNeeded {
    val trustedPaths = TrustedPaths.getInstance()
    val trustedState = trustedPaths.getProjectPathTrustedState(projectDir)
    if (trustedState == ThreeState.UNSURE) {
      val openingUntrustedProjectChoice = confirmOpeningUntrustedProject(projectDir)
      when (openingUntrustedProjectChoice) {
        OpenUntrustedProjectChoice.TRUST_AND_OPEN -> trustedPaths.setProjectPathTrusted(projectDir, true)
        OpenUntrustedProjectChoice.OPEN_IN_SAFE_MODE -> trustedPaths.setProjectPathTrusted(projectDir, false)
        OpenUntrustedProjectChoice.CANCEL -> return@invokeAndWaitIfNeeded false
      }
    }
    true
  }
}

fun confirmOpeningUntrustedProject(projectFileOrDir: Path): OpenUntrustedProjectChoice = confirmOpeningUntrustedProject(
  projectFileOrDir,
  IdeBundle.message("untrusted.project.open.dialog.title"),
  IdeBundle.message("untrusted.project.open.dialog.text", ApplicationInfoEx.getInstanceEx().fullApplicationName),
  IdeBundle.message("untrusted.project.dialog.trust.button"),
  IdeBundle.message("untrusted.project.open.dialog.distrust.button"),
  IdeBundle.message("untrusted.project.open.dialog.cancel.button")
)

fun confirmOpeningUntrustedProject(
  projectFileOrDir: Path,
  @NlsContexts.DialogTitle title: String,
  @NlsContexts.DialogMessage message: String,
  @NlsContexts.Button trustButtonText: String,
  @NlsContexts.Button distrustButtonText: String,
  @NlsContexts.Button cancelButtonText: String
): OpenUntrustedProjectChoice {
  val projectDir = if (projectFileOrDir.isDirectory()) projectFileOrDir else projectFileOrDir.parent
  if (isProjectImplicitlyTrusted(projectDir)) {
    return OpenUntrustedProjectChoice.TRUST_AND_OPEN
  }

  val choice = MessageDialogBuilder.Message(title, message)
    .buttons(trustButtonText, distrustButtonText, cancelButtonText)
    .defaultButton(trustButtonText)
    .focusedButton(distrustButtonText)
    .doNotAsk(createDoNotAskOptionForLocation(projectDir.parent))
    .asWarning()
    .help(TRUSTED_PROJECTS_HELP_TOPIC)
    .show()

  val openChoice = when (choice) {
    trustButtonText -> OpenUntrustedProjectChoice.TRUST_AND_OPEN
    distrustButtonText -> OpenUntrustedProjectChoice.OPEN_IN_SAFE_MODE
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

@ApiStatus.Internal
enum class OpenUntrustedProjectChoice {
  TRUST_AND_OPEN,
  OPEN_IN_SAFE_MODE,
  CANCEL;
}

fun Project.isTrusted() = getTrustedState() == ThreeState.YES

fun Project.getTrustedState(): ThreeState {
  val explicit = this.service<TrustedProjectSettings>().trustedState
  if (explicit != ThreeState.UNSURE) {
    return explicit
  }
  if (isProjectImplicitlyTrusted(this)) {
    return ThreeState.YES
  }
  val projectPath = basePath
  if (projectPath != null) {
    return TrustedPaths.getInstance().getProjectPathTrustedState(Paths.get(projectPath))
  }
  return ThreeState.UNSURE
}

fun Project.setTrusted(value: Boolean) {
  val projectPath = basePath
  if (projectPath != null) {
    val path = Paths.get(projectPath)
    val trustedPaths = TrustedPaths.getInstance()
    val oldValue = trustedPaths.getProjectPathTrustedState(path)
    trustedPaths.setProjectPathTrusted(path, value)

    if (value && oldValue != ThreeState.YES) {
      ApplicationManager.getApplication().messageBus.syncPublisher(TrustChangeNotifier.TOPIC).projectTrusted(this)
    }
  }
}

private fun createDoNotAskOptionForLocation(projectLocation: Path): DialogWrapper.DoNotAskOption {
  val projectLocationPath = projectLocation.toString()
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
