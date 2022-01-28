// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("TrustedProjects")
@file:ApiStatus.Experimental

package com.intellij.ide.impl

import com.intellij.ide.IdeBundle
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.FileUtil.getLocationRelativeToUserHome
import com.intellij.util.ThreeState
import com.intellij.util.messages.Topic
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.Consumer
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
fun confirmOpeningAndSetProjectTrustedStateIfNeeded(projectFileOrDir: Path): Boolean {
  return invokeAndWaitIfNeeded {
    val projectDir = if (projectFileOrDir.isDirectory()) projectFileOrDir else projectFileOrDir.parent
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

private fun confirmOpeningUntrustedProject(projectDir: Path): OpenUntrustedProjectChoice = confirmOpeningUntrustedProject(
  projectDir,
  IdeBundle.message("untrusted.project.open.dialog.title", projectDir.fileName),
  IdeBundle.message("untrusted.project.open.dialog.text", ApplicationNamesInfo.getInstance().fullProductName),
  IdeBundle.message("untrusted.project.dialog.trust.button"),
  IdeBundle.message("untrusted.project.open.dialog.distrust.button"),
  IdeBundle.message("untrusted.project.open.dialog.cancel.button")
)

private fun confirmOpeningUntrustedProject(
  projectDir: Path,
  @NlsContexts.DialogTitle title: String,
  @NlsContexts.DialogMessage message: String,
  @NlsContexts.Button trustButtonText: String,
  @NlsContexts.Button distrustButtonText: String,
  @NlsContexts.Button cancelButtonText: String
): OpenUntrustedProjectChoice = invokeAndWaitIfNeeded {
  if (isProjectImplicitlyTrusted(projectDir)) {
    return@invokeAndWaitIfNeeded OpenUntrustedProjectChoice.TRUST_AND_OPEN
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
      return@invokeAndWaitIfNeeded OpenUntrustedProjectChoice.CANCEL
    }
  }
  TrustedProjectsStatistics.NEW_PROJECT_OPEN_OR_IMPORT_CHOICE.log(openChoice)
  return@invokeAndWaitIfNeeded openChoice
}

fun confirmLoadingUntrustedProject(
  project: Project,
  @NlsContexts.DialogTitle title: String,
  @NlsContexts.DialogMessage message: String,
  @NlsContexts.Button trustButtonText: String,
  @NlsContexts.Button distrustButtonText: String
): Boolean = invokeAndWaitIfNeeded {
  if (isProjectImplicitlyTrusted(project)) {
    project.setTrusted(true)
    return@invokeAndWaitIfNeeded true
  }

  val answer = MessageDialogBuilder.yesNo(title, message)
    .yesText(trustButtonText)
    .noText(distrustButtonText)
    .asWarning()
    .help(TRUSTED_PROJECTS_HELP_TOPIC)
    .ask(project)
  project.setTrusted(answer)
  TrustedProjectsStatistics.LOAD_UNTRUSTED_PROJECT_CONFIRMATION_CHOICE.log(project, answer)
  return@invokeAndWaitIfNeeded answer
}

@ApiStatus.Internal
enum class OpenUntrustedProjectChoice {
  TRUST_AND_OPEN,
  OPEN_IN_SAFE_MODE,
  CANCEL;
}

fun Project.isTrusted() = LightEdit.owns(this) || getTrustedState () == ThreeState.YES

@ApiStatus.Internal
fun Project.getTrustedState(): ThreeState {
  val projectPath = basePath
  if (projectPath != null) {
    val explicit = TrustedPaths.getInstance().getProjectPathTrustedState(Paths.get(projectPath))
    if (explicit != ThreeState.UNSURE) {
      return explicit
    }
  }

  if (isProjectImplicitlyTrusted(this)) {
    return ThreeState.YES
  }

  @Suppress("DEPRECATION")
  return this.service<TrustedProjectSettings>().trustedState
}

fun Project.setTrusted(value: Boolean) {
  val projectPath = basePath
  if (projectPath != null) {
    val path = Paths.get(projectPath)
    val trustedPaths = TrustedPaths.getInstance()
    val oldValue = trustedPaths.getProjectPathTrustedState(path)
    trustedPaths.setProjectPathTrusted(path, value)

    if (value && oldValue != ThreeState.YES) {
      ApplicationManager.getApplication().messageBus.syncPublisher(TrustStateListener.TOPIC).onProjectTrusted(this)
    }
  }
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
      val path = getLocationRelativeToUserHome(projectLocationPath, false)
      return IdeBundle.message("untrusted.project.warning.trust.location.checkbox", path)
    }
  }
}

@ApiStatus.Internal
fun isTrustedCheckDisabled() = ApplicationManager.getApplication().isUnitTestMode ||
                               ApplicationManager.getApplication().isHeadlessEnvironment ||
                               java.lang.Boolean.getBoolean("idea.is.integration.test") ||
                               java.lang.Boolean.getBoolean("idea.trust.all.projects")

private fun isTrustedCheckDisabledForProduct(): Boolean = java.lang.Boolean.getBoolean("idea.trust.disabled")


private fun isProjectImplicitlyTrusted(project: Project): Boolean =
  isProjectImplicitlyTrusted(project.basePath?.let { Paths.get(it) }, project)

@JvmOverloads
@ApiStatus.Internal
fun isProjectImplicitlyTrusted(projectDir: Path?, project: Project? = null): Boolean {
  if (isTrustedCheckDisabled() || isTrustedCheckDisabledForProduct()) {
    return true
  }
  if (projectDir != null && isPathTrustedInSettings(projectDir)) {
    TrustedProjectsStatistics.PROJECT_IMPLICITLY_TRUSTED_BY_PATH.log(project)
    return true
  }
  return false
}

@ApiStatus.Internal
fun isPathTrustedInSettings(path: Path): Boolean = service<TrustedPathsSettings>().isPathTrusted(path)

/**
 * Per-project "is this project trusted" setting from the previous version of the trusted API.
 * It shouldn't be used and is kept for migration purposes only.
 */
@State(name = "Trusted.Project.Settings", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
@Service(Service.Level.PROJECT)
@ApiStatus.Internal
@Deprecated("Use TrustedPaths instead")
class TrustedProjectSettings : SimplePersistentStateComponent<TrustedProjectSettings.State>(State()) {

  class State : BaseState() {
    @get:Attribute
    var isTrusted by enum(ThreeState.UNSURE)
  }

  var trustedState: ThreeState
    get() = state.isTrusted
    set(value) {
      state.isTrusted = value
    }
}

/**
 * Listens to the change of the project trusted state, i.e. when a non-trusted project becomes trusted (the vice versa is not possible).
 *
 * Consider using the helper method [whenProjectTrusted] which accepts a lambda.
 */
@ApiStatus.Experimental
interface TrustStateListener {
  /**
   * Executed when the project becomes trusted.
   */
  @JvmDefault
  fun onProjectTrusted(project: Project) {
  }

  /**
   * Executed when the user clicks to the "Trust Project" button in the [editor notification][UntrustedProjectEditorNotificationPanel].
   * Use this method if you need to know that the project has become trusted exactly because the user has clicked to that button.
   *
   * NB: [onProjectTrusted] is also called in this case, and most probably you want to use that method.
   */
  @JvmDefault
  fun onProjectTrustedFromNotification(project: Project) {
  }

  companion object {
    @JvmField
    @Topic.AppLevel
    val TOPIC = Topic.create("Trusted project status", TrustStateListener::class.java)
  }
}

/**
 * Adds a one-time listener of the project's trust state change: when the project becomes trusted, the listener is called and disconnected.
 */
@JvmOverloads
fun whenProjectTrusted(parentDisposable: Disposable? = null, listener: (Project) -> Unit) {
  val messageBus = ApplicationManager.getApplication().messageBus
  val connection = if (parentDisposable == null) messageBus.connect() else messageBus.connect(parentDisposable)
  connection.subscribe(TrustStateListener.TOPIC, object : TrustStateListener {
    override fun onProjectTrusted(project: Project) {
      listener(project)
      connection.disconnect()
    }
  })
}

@JvmOverloads
fun whenProjectTrusted(parentDisposable: Disposable? = null, listener: Consumer<Project>) {
  whenProjectTrusted(parentDisposable) { project ->
    listener.accept(project)
  }
}

const val TRUSTED_PROJECTS_HELP_TOPIC = "Project_security"

private val LOG = Logger.getInstance("com.intellij.ide.impl.TrustedProjects")
