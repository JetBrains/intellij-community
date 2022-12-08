// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("TrustedProjects")
@file:ApiStatus.Experimental

package com.intellij.ide.impl

import com.intellij.ide.IdeBundle
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.file.CanonicalPathUtil.toNioPath
import com.intellij.openapi.file.NioFileUtil.isAncestor
import com.intellij.openapi.file.VirtualFileUtil.toNioPathOrNull
import com.intellij.openapi.project.*
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.FileUtil.getLocationRelativeToUserHome
import com.intellij.util.ThreeState
import com.intellij.util.containers.addIfNotNull
import com.intellij.util.messages.Topic
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Path
import java.util.function.Consumer

/**
 * Shows the "Trust project?" dialog, if the user wasn't asked yet if they trust this project,
 * and sets the project trusted state according to the user choice.
 *
 * @return false if the user chose not to open (link) the project at all;
 *   true otherwise, i.e. if the user chose to open (link) the project either in trust or in the safe mode,
 *   or if the confirmation wasn't shown because the project trust state was already known.
 */
fun confirmOpeningOrLinkingUntrustedProject(
  projectRoot: Path,
  project: Project?,
  @NlsContexts.DialogTitle title: String,
  @NlsContexts.DialogMessage message: String,
  @NlsContexts.Button trustButtonText: String,
  @NlsContexts.Button distrustButtonText: String,
  @NlsContexts.Button cancelButtonText: String
): Boolean {
  if (isProjectTrusted(listOf(projectRoot), project)) {
    setProjectTrusted(true, listOf(projectRoot), project)
    return true
  }

  val doNotAskOption = projectRoot.parent?.let(::createDoNotAskOptionForLocation)
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
    setProjectTrusted(true, listOf(projectRoot), project)
  }
  if (openChoice == OpenUntrustedProjectChoice.OPEN_IN_SAFE_MODE) {
    setProjectTrusted(false, listOf(projectRoot), project)
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
      val path = getLocationRelativeToUserHome(projectLocationPath, false)
      return IdeBundle.message("untrusted.project.warning.trust.location.checkbox", path)
    }
  }
}

fun confirmLoadingUntrustedProject(
  project: Project,
  @NlsContexts.DialogTitle title: String,
  @NlsContexts.DialogMessage message: String,
  @NlsContexts.Button trustButtonText: String,
  @NlsContexts.Button distrustButtonText: String
): Boolean {
  if (project.isTrusted()) {
    project.setTrusted(true)
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

fun Project.isTrusted() = isProjectTrusted(getProjectRoots(), project = this)

fun Project.setTrusted(isTrusted: Boolean) = setProjectTrusted(isTrusted, getProjectRoots(), project = this)

fun Project.getTrustedState() = getProjectTrustedState(getProjectRoots(), project = this)

fun isProjectTrusted(projectRoots: List<Path>, project: Project?): Boolean {
  return getProjectTrustedState(projectRoots, project = project) == ThreeState.YES
}

fun getProjectTrustedState(projectRoots: List<Path>, project: Project?): ThreeState {
  val explicitTrustedState = getExplicitTrustedProjectState(projectRoots)
  if (explicitTrustedState != ThreeState.UNSURE) {
    return explicitTrustedState
  }
  val implicitTrustedState = getImplicitTrustedProjectState(projectRoots, project)
  if (implicitTrustedState != ThreeState.UNSURE) {
    return implicitTrustedState
  }
  if (project != null) {
    @Suppress("DEPRECATION")
    val legacyTrustedState = project.service<TrustedProjectSettings>().trustedState
    if (legacyTrustedState != ThreeState.UNSURE) {
      // we were asking about this project in the previous IDE version => migrate
      for (projectRoot in projectRoots) {
        TrustedPaths.getInstance().setProjectPathTrusted(projectRoot, legacyTrustedState.toBoolean())
      }
      return legacyTrustedState
    }
  }
  return ThreeState.UNSURE
}

fun setProjectTrusted(isTrusted: Boolean, projectRoots: List<Path>, project: Project?) {
  val oldExplicitTrustedState = getProjectTrustedState(projectRoots, project)
  for (projectRoot in projectRoots) {
    TrustedPaths.getInstance().setProjectPathTrusted(projectRoot, isTrusted)
  }
  val newExplicitTrustedState = getProjectTrustedState(projectRoots, project)
  if (project != null) {
    val syncPublisher = ApplicationManager.getApplication().messageBus.syncPublisher(TrustStateListener.TOPIC)
    if (oldExplicitTrustedState != newExplicitTrustedState) {
      when (isTrusted) {
        true -> syncPublisher.onProjectTrusted(project)
        else -> syncPublisher.onProjectUntrusted(project)
      }
    }
  }
}

private fun getExplicitTrustedProjectState(projectRoots: List<Path>): ThreeState {
  val trustedPaths = TrustedPaths.getInstance()
  val trustedStates = projectRoots.map { trustedPaths.getProjectPathTrustedState(it) }
  return mergeTrustedProjectStates(trustedStates)
}

@ApiStatus.Internal
fun isTrustedCheckDisabled() = ApplicationManager.getApplication().isUnitTestMode ||
                               ApplicationManager.getApplication().isHeadlessEnvironment ||
                               ApplicationManagerEx.isInIntegrationTest() ||
                               java.lang.Boolean.getBoolean("idea.trust.all.projects")

private fun isTrustedCheckDisabledForProduct(): Boolean = java.lang.Boolean.getBoolean("idea.trust.disabled")

private fun getImplicitTrustedProjectState(projectRoots: List<Path>, project: Project?): ThreeState {
  if (isTrustedCheckDisabled() || isTrustedCheckDisabledForProduct()) {
    return ThreeState.YES
  }
  if (LightEdit.owns(project)) {
    return ThreeState.YES
  }
  val trustedPaths = TrustedPathsSettings.getInstance()
  if (projectRoots.all { trustedPaths.isPathTrusted(it) }) {
    TrustedProjectsStatistics.PROJECT_IMPLICITLY_TRUSTED_BY_PATH.log(project)
    return ThreeState.YES
  }
  return ThreeState.UNSURE
}

private fun mergeTrustedProjectStates(states: List<ThreeState>): ThreeState {
  return states.fold(ThreeState.YES) { acc, it ->
    when {
      acc == ThreeState.UNSURE -> ThreeState.UNSURE
      it == ThreeState.UNSURE -> ThreeState.UNSURE
      acc == ThreeState.NO -> ThreeState.NO
      it == ThreeState.NO -> ThreeState.NO
      else -> ThreeState.YES
    }
  }
}

@ApiStatus.Internal
fun Project.getProjectRoots(): List<Path> {
  val projectRoots = ArrayList<Path>()
  projectRoots.addIfNotNull(basePath?.toNioPath())
  for (module in modules) {
    for (contentRoot in module.rootManager.contentRoots) {
      val contentPath = contentRoot.toNioPathOrNull()
      projectRoots.addIfNotNull(contentPath)
    }
  }
  return filterProjectRoots(projectRoots)
}

@ApiStatus.Internal
@VisibleForTesting
fun filterProjectRoots(roots: List<Path>): List<Path> {
  @Suppress("ComplexRedundantLet")
  return roots
    .let { filterProjectRootsOneWave(it) }.reversed()
    .let { filterProjectRootsOneWave(it) }.reversed()
}

private fun filterProjectRootsOneWave(roots: List<Path>): List<Path> {
  val result = ArrayList(roots)
  var index = 0
  while (index < result.size) {
    val root = result[index]
    val iterator = result.listIterator(index + 1)
    while (iterator.hasNext()) {
      val child = iterator.next()
      if (root.isAncestor(child, strict = false)) {
        iterator.remove()
      }
    }
    index++
  }
  return result
}

@JvmOverloads
@ApiStatus.Internal
@Deprecated("Use Project.isTrusted instead")
fun isProjectImplicitlyTrusted(projectDir: Path?, project: Project? = null): Boolean {
  return getImplicitTrustedProjectState(listOfNotNull(projectDir), project) == ThreeState.YES
}

/**
 * Per-project "is this project trusted" setting from the previous version of the trusted API.
 * It shouldn't be used and is kept for migration purposes only.
 */
@State(name = "Trusted.Project.Settings", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
@Service(Service.Level.PROJECT)
@ApiStatus.Internal
@Suppress("DEPRECATION")
@Deprecated("Use TrustedPaths instead")
internal class TrustedProjectSettings : SimplePersistentStateComponent<TrustedProjectSettings.State>(State()) {
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
 * Consider using the helper method [onceWhenProjectTrusted] which accepts a lambda.
 */
@ApiStatus.Experimental
interface TrustStateListener {

  /**
   * Executed when the project becomes trusted.
   */
  fun onProjectTrusted(project: Project) {
  }

  /**
   * Executed when the project becomes in safe mode.
   */
  fun onProjectUntrusted(project: Project) {
  }

  /**
   * Executed when the user clicks to the "Trust Project" button in the [editor notification][UntrustedProjectEditorNotificationPanel].
   * Use this method if you need to know that the project has become trusted exactly because the user has clicked to that button.
   *
   * NB: [onProjectTrusted] is also called in this case, and most probably you want to use that method.
   */
  fun onProjectTrustedFromNotification(project: Project) {
  }

  companion object {
    @JvmField
    @Topic.AppLevel
    val TOPIC = Topic(TrustStateListener::class.java, Topic.BroadcastDirection.NONE)
  }
}

/**
 * Adds a one-time listener of the project's trust state change: when the project becomes trusted, the listener is called and disconnected.
 */
@JvmOverloads
fun onceWhenProjectTrusted(parentDisposable: Disposable? = null, listener: (Project) -> Unit) {
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
fun onceWhenProjectTrusted(parentDisposable: Disposable? = null, listener: Consumer<Project>) =
  onceWhenProjectTrusted(parentDisposable, listener::accept)

@JvmOverloads
@Deprecated("Use onceWhenProjectTrusted instead", ReplaceWith("onceWhenProjectTrusted(parentDisposable, listener)"))
fun whenProjectTrusted(parentDisposable: Disposable? = null, listener: (Project) -> Unit) =
  onceWhenProjectTrusted(parentDisposable, listener)

@JvmOverloads
@Deprecated("Use onceWhenProjectTrusted instead", ReplaceWith("onceWhenProjectTrusted(parentDisposable, listener::accept)"))
fun whenProjectTrusted(parentDisposable: Disposable? = null, listener: Consumer<Project>) =
  onceWhenProjectTrusted(parentDisposable, listener::accept)

const val TRUSTED_PROJECTS_HELP_TOPIC = "Project_security"

private val LOG = Logger.getInstance("com.intellij.ide.impl.TrustedProjects")
