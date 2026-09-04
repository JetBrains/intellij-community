package com.intellij.platform.ide.nonModalWelcomeScreen.actions

import com.intellij.ide.actions.WelcomeSaveFileAction
import com.intellij.ide.welcomeScreen.WelcomeUtils
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionWrapper
import com.intellij.openapi.actionSystem.ex.ActionRuntimeRegistrar
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehavior
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.nonModalWelcomeScreen.leftPanel.WELCOME_SCREEN_IS_SHOWN
import com.intellij.platform.ide.nonModalWelcomeScreen.leftPanel.WelcomeScreenLeftTabActionNew
import com.intellij.util.PlatformUtils
import org.intellij.lang.annotations.Language

internal class WelcomeScreenAwareActionsCustomizer : ActionConfigurationCustomizer, ActionConfigurationCustomizer.LightCustomizeStrategy {
  override suspend fun customize(actionRegistrar: ActionRuntimeRegistrar) {
    actionRegistrar.run {
      replaceExistingAction("CloseProject") { WelcomeScreenAwareCloseProjectAction() }
      replaceExistingAction("CloseAllProjects") { WelcomeScreenAwareCloseAllProjectsAction() }
      replaceExistingAction("RenameProject") { hideActionOnWelcomeScreen(it) }
      replaceExistingAction("NewDir") { hideActionOnWelcomeScreen(it) }
      replaceExistingAction("NewFile") { WelcomeScreenProxyAction(it, CreateEmptyFileAction()) }
      if (!ApplicationManager.getApplication().isUnitTestMode) {
        replaceExistingAction("SaveAll") { WelcomeFileProxyAction(it, WelcomeSaveFileAction()) }
        replaceExistingAction("SaveDocument") { WelcomeFileProxyAction(it, WelcomeSaveFileAction()) }
      }
      if (!PlatformUtils.isPyCharm() && !PlatformUtils.isDataGrip()) {
        replaceExistingAction("NewElement") { WelcomeScreenProxyAction(it, WelcomeScreenLeftTabActionNew(), false) }
      }

      // Hide project view toolbar actions
      replaceExistingAction("SelectInProjectView") { hideActionOnWelcomeScreen(it) }
      replaceExistingAction("ExpandRecursively") { hideActionOnWelcomeScreen(it) }
      replaceExistingAction("ExpandAll") { hideActionOnWelcomeScreen(it) }
      replaceExistingAction("CollapseAll") { hideActionOnWelcomeScreen(it) }
    }
  }
}

private fun ActionRuntimeRegistrar.replaceExistingAction(
  @Language("devkit-action-id") actionId: String,
  newActionProducer: (oldAction: AnAction) -> AnAction,
) {
  val oldAction = getUnstubbedAction(actionId) ?: return
  val newAction = newActionProducer(oldAction)
  val presentation = oldAction.templatePresentation
  replaceAction(actionId, newAction)
  newAction.templatePresentation.copyFrom(presentation)
}

private fun hideActionOnWelcomeScreen(action: AnAction): AnAction {
  return if (action is ActionRemoteBehaviorSpecification && action.getBehavior() != null) {
    WelcomeScreenHiddenActionWithRemoteSpec(action)
  }
  else {
    WelcomeScreenHiddenAction(action)
  }
}

internal class WelcomeScreenHiddenActionWithRemoteSpec<T>(val actionWithSpec: T) : WelcomeScreenHiddenAction(actionWithSpec),
                                                                                   ActionRemoteBehaviorSpecification
  where T : AnAction, T : ActionRemoteBehaviorSpecification {
  override fun getBehavior(): ActionRemoteBehavior? = actionWithSpec.getBehavior()
}

internal open class WelcomeScreenHiddenAction(action: AnAction) : AnActionWrapper(action) {
  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project != null && e.getData(WELCOME_SCREEN_IS_SHOWN) == true) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    super.update(e)
  }
}

internal open class WelcomeScreenProxyAction(
  val action: AnAction,
  val welcomeScreenBehaviour: AnAction,
  private val isVisible: Boolean = true,
) : DumbAwareAction(action.templatePresentation.text) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    if (project != null && isWelcomeAction(project, e)) {
      welcomeScreenBehaviour.actionPerformed(e)
      return
    }
    action.actionPerformed(e)
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project != null && isWelcomeAction(project, e)) {
      e.presentation.isVisible = isVisible
      welcomeScreenBehaviour.update(e)
      return
    }
    action.update(e)
  }

  protected open fun isWelcomeAction(project: Project, e: AnActionEvent): Boolean = e.getData(WELCOME_SCREEN_IS_SHOWN) == true

  override fun getActionUpdateThread(): ActionUpdateThread {
    return action.actionUpdateThread
  }
}

internal class WelcomeFileProxyAction(action: AnAction, welcomeScreenBehaviour: AnAction) :
  WelcomeScreenProxyAction(action, welcomeScreenBehaviour) {
  override fun isWelcomeAction(project: Project, e: AnActionEvent): Boolean {
    return WelcomeUtils.isWelcomeProject(project) // TODO: check welcome file??
  }
}