package com.intellij.ide.actions

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.WindowManagerEx

class ToggleZenModeAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
    companion object {
        private fun isFullScreenApplicable() = WindowManager.getInstance().isFullScreenSupportedInCurrentOS
        private fun Project.getFrame() = WindowManagerEx.getInstanceEx().findFrameHelper(this)

        fun isZenModeEnabled(project: Project): Boolean {
            if (!DistractionFreeModeController.isDistractionFreeModeEnabled())
                return false
            if (isFullScreenApplicable()) {
                val frame = project.getFrame()
                if (frame != null && !frame.isInFullScreen)
                    return false
            }
            return true
        }
    }

    private val toggleDistractionFreeModeAction = ToggleDistractionFreeModeAction()

    override fun update(e: AnActionEvent) {
        if (!isFullScreenApplicable()) {
            // If the full screen mode is not applicable, the action is identical to the distraction free mode,
            // and should be hidden to reduce confusion.
            e.presentation.isVisible = false
            return
        }

        val project = e.project
        if (project == null) {
            e.presentation.isEnabled = false
            return
        }

        e.presentation.text =
          if (isZenModeEnabled(project)) ActionsBundle.message("action.ToggleZenMode.exit")
          else ActionsBundle.message("action.ToggleZenMode.enter")
    }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        applyZenMode(e, !isZenModeEnabled(project))
    }

    private fun applyZenMode(e: AnActionEvent, state: Boolean) {
        val project = e.project ?: return

        if (DistractionFreeModeController.isDistractionFreeModeEnabled() != state)
            toggleDistractionFreeModeAction.actionPerformed(e)

        if (isFullScreenApplicable()) {
            val frame = project.getFrame()
            if (frame != null && frame.isInFullScreen != state)
                frame.toggleFullScreen(state)
        }
    }
}