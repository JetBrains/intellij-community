package com.intellij.platform.ide.nonModalWelcomeScreen.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionWrapper
import com.intellij.openapi.actionSystem.ex.ActionRuntimeRegistrar
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider.Companion.isWelcomeScreenProject
import org.intellij.lang.annotations.Language

class GoWelcomeScreenAwareActionsCustomizer : ActionConfigurationCustomizer, ActionConfigurationCustomizer.LightCustomizeStrategy {
  override suspend fun customize(actionRegistrar: ActionRuntimeRegistrar) {
    replaceActionCopyPresentation("CloseProject", GoWelcomeScreenAwareCloseProjectAction(), actionRegistrar)

    wrapExistingActionAndReplace("RenameProject", isFrontendAction = false, actionRegistrar)

    disableCreatingFilesInWelcomeProject(actionRegistrar)
    hideProjectToolbarActionsInWelcomeScreenProject(actionRegistrar)
  }

  private fun disableCreatingFilesInWelcomeProject(actionRegistrar: ActionRuntimeRegistrar) {
    wrapExistingActionAndReplace("NewFile", isFrontendAction = false, actionRegistrar)
    wrapExistingActionAndReplace("Go.NewGoFile", isFrontendAction = false, actionRegistrar)
    wrapExistingActionAndReplace("NewDir", isFrontendAction = false, actionRegistrar)
  }

  private fun hideProjectToolbarActionsInWelcomeScreenProject(actionRegistrar: ActionRuntimeRegistrar) {
    wrapExistingActionAndReplace("SelectInProjectView", isFrontendAction = true, actionRegistrar)
    wrapExistingActionAndReplace("ExpandRecursively", isFrontendAction = true, actionRegistrar)
    wrapExistingActionAndReplace("ExpandAll", isFrontendAction = true, actionRegistrar)
    wrapExistingActionAndReplace("CollapseAll", isFrontendAction = true, actionRegistrar)
  }
}

private fun wrapExistingActionAndReplace(@Language("devkit-action-id") actionId: String,
                                         isFrontendAction: Boolean,
                                         actionRegistrar: ActionRuntimeRegistrar) {
  val existingAction = actionRegistrar.getUnstubbedAction(actionId) ?: return
  val actionWrapper =
    if (isFrontendAction) {
      GoWelcomeScreenFrontendActionWrapper(existingAction)
    } else {
      GoWelcomeScreenActionWrapper(existingAction)
    }
  replaceActionCopyPresentation(actionId, actionWrapper, actionRegistrar)
}

private class GoWelcomeScreenFrontendActionWrapper(action: AnAction) : GoWelcomeScreenActionWrapper(action),
                                                                       ActionRemoteBehaviorSpecification.Frontend

private open class GoWelcomeScreenActionWrapper(action: AnAction) : AnActionWrapper(action) {
  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project != null && isWelcomeScreenProject(project)) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    super.update(e)
  }
}

private fun replaceActionCopyPresentation(@Language("devkit-action-id") actionId: String,
                                          newAction: AnAction,
                                          actionRegistrar: ActionRuntimeRegistrar) {
  val oldAction = actionRegistrar.getUnstubbedAction(actionId)
  val presentation = oldAction?.templatePresentation
  actionRegistrar.replaceAction(actionId, newAction)
  if (presentation != null) {
    newAction.templatePresentation.copyFrom(presentation)
  }
}
