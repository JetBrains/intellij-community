package com.intellij.platform.ide.nonModalWelcomeScreen.actions

import com.intellij.ide.actions.WelcomeFilesRootType
import com.intellij.ide.actions.saveWelcomeFileAs
import com.intellij.ide.welcomeScreen.WelcomeUtils
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionWrapper
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ex.ActionRuntimeRegistrar
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehavior
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.nonModalWelcomeScreen.leftPanel.WELCOME_SCREEN_IS_SHOWN
import com.intellij.platform.ide.nonModalWelcomeScreen.leftPanel.WelcomeScreenLeftTabActionNew
import com.intellij.util.PlatformUtils
import org.intellij.lang.annotations.Language

internal class WelcomeScreenAwareActionsCustomizer : ActionConfigurationCustomizer, ActionConfigurationCustomizer.LightCustomizeStrategy {
  override suspend fun customize(actionRegistrar: ActionRuntimeRegistrar) {
    actionRegistrar.run {
      replaceExistingAction("RenameProject") { hideActionOnWelcomeScreen(it) }
      if (!ApplicationManager.getApplication().isUnitTestMode) {
        replaceExistingAction("SaveAll") { WelcomeFileProxyAction(it) }
        replaceExistingAction("SaveDocument") { WelcomeFileProxyAction(it) }
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

internal class WelcomeProjectNewActionsCustomizer : ActionConfigurationCustomizer, ActionConfigurationCustomizer.LightCustomizeStrategy {
  override suspend fun customize(actionRegistrar: ActionRuntimeRegistrar) {
    actionRegistrar.run {
      replaceExistingAction("NewDir") { WelcomeProjectHiddenAction(it) }
      replaceExistingAction("NewFile") { WelcomeProjectHiddenAction(it) }
      replaceExistingAction("NewScratchFile") { WelcomeProjectHiddenAction(it) }
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

internal class WelcomeProjectHiddenAction(action: AnAction) : AnActionWrapper(action) {
  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project != null && WelcomeUtils.isWelcomeProject(project)) {
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

/**
 * Gives `SaveDocument` and `SaveAll` the "Save File" behavior for a Home file.
 */
internal class WelcomeFileProxyAction(action: AnAction) : WelcomeScreenProxyAction(action, WelcomeSaveFileAction()) {
  override fun isWelcomeAction(project: Project, e: AnActionEvent): Boolean {
    return (welcomeScreenBehaviour as WelcomeSaveFileAction).isWelcomeFile(project, e)
  }
}

/**
 * The "Save File" behavior that [WelcomeFileProxyAction] gives to `SaveDocument` and `SaveAll` for a Home file.
 * It is not a registered action. Do not register it, and do not add it to a group.
 *
 * It finds a Home file by its path under the Home files root. The path is correct only where the Home files are:
 * on the backend and in the monolith. A split-mode client runs `SaveDocument` and `SaveAll` on the backend,
 * so the check holds there.
 *
 * The editor tab menu has a frontend action instead: `FrontendWelcomeSaveFileAction` in the frontend module.
 * It is registered under the id `WelcomeSaveFileAction`. It finds a Home file by the modified marker,
 * which the frontend also sets on a remote Home file, and saves the file through the welcome files RPC.
 */
private class WelcomeSaveFileAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    val project = event.project
    event.presentation.isEnabledAndVisible = project != null && isWelcomeFile(project, event)
    event.presentation.text = ActionsBundle.message("action.WelcomeSaveFileAction.text")
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    val file = getFile(project, event) ?: return
    saveWelcomeFileAs(project, file, true)
  }

  fun isWelcomeFile(project: Project, event: AnActionEvent): Boolean = getFile(project, event) != null

  private fun getFile(project: Project, event: AnActionEvent): VirtualFile? {
    if (!WelcomeUtils.isWelcomeProject(project)) {
      return null
    }
    val document = event.getData(CommonDataKeys.EDITOR)?.document
    val file = if (document == null) {
      event.getData(EditorWindow.DATA_KEY)?.getContextFile() ?: return null
    }
    else {
      FileDocumentManager.getInstance().getFile(document) ?: return null
    }
    if (!WelcomeFilesRootType.Util.instance.containsFile(file)) {
      return null
    }
    return file
  }
}