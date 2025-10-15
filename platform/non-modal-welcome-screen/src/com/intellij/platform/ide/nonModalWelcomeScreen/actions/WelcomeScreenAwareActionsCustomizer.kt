package com.intellij.platform.ide.nonModalWelcomeScreen.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionWrapper
import com.intellij.openapi.actionSystem.ex.ActionRuntimeRegistrar
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehavior
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider.Companion.isWelcomeScreenProject
import com.intellij.platform.ide.nonModalWelcomeScreen.leftPanel.WelcomeScreenLeftTabActionNew
import org.intellij.lang.annotations.Language

internal class WelcomeScreenAwareActionsCustomizer : ActionConfigurationCustomizer, ActionConfigurationCustomizer.LightCustomizeStrategy {
  override suspend fun customize(actionRegistrar: ActionRuntimeRegistrar) {
    actionRegistrar.run {
      replaceExistingAction("CloseProject") { WelcomeScreenAwareCloseProjectAction() }
      replaceExistingAction("RenameProject") { hideActionOnWelcomeScreen(it) }
      replaceExistingAction("NewDir") { hideActionOnWelcomeScreen(it) }
      replaceExistingAction("NewFile") { WelcomeScreenProxyAction(it, CreateEmptyFileAction()) }
      replaceExistingAction("NewElement") { WelcomeScreenProxyAction(it, WelcomeScreenLeftTabActionNew()) }

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

private class WelcomeScreenHiddenActionWithRemoteSpec<T>(val actionWithSpec: T) : WelcomeScreenHiddenAction(actionWithSpec),
                                                                                  ActionRemoteBehaviorSpecification
  where T : AnAction, T : ActionRemoteBehaviorSpecification {
  override fun getBehavior(): ActionRemoteBehavior? = actionWithSpec.getBehavior()
}

private open class WelcomeScreenHiddenAction(action: AnAction) : AnActionWrapper(action) {
  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project != null && isWelcomeScreenProject(project)) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    super.update(e)
  }
}

private class WelcomeScreenProxyAction(val action: AnAction?, val welcomeScreenBehaviour: AnAction) : DumbAwareAction(action?.templatePresentation?.text) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    if (project != null && isWelcomeScreenProject(project)) {
      welcomeScreenBehaviour.actionPerformed(e)
      return
    }
    action?.actionPerformed(e)
  }
}
