// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.CommonBundle
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.Strings
import com.intellij.openapi.wm.impl.headertoolbar.ProjectToolbarWidgetPresentable
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectItem.Companion.openProjectAndLogRecent
import com.intellij.platform.eel.provider.EelInitialization
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.BitUtil
import com.intellij.util.PathUtil
import org.jetbrains.annotations.SystemIndependent
import java.awt.event.ActionEvent
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.Icon

open class ReopenProjectAction @JvmOverloads constructor(
  projectPath: @SystemIndependent String,
  projectName: @NlsSafe String?,
  displayName: @NlsSafe String?,
  branchName: @NlsSafe String? = null,
  activationTimestamp: Long? = null,
) : AnAction(), DumbAware, LightEditCompatible, ProjectToolbarWidgetPresentable {
  private val myProjectPath: @SystemIndependent String
  private val myProjectName: @NlsSafe String?
  private val myDisplayName: @NlsSafe String?
  private val myBranchName: @NlsSafe String?
  private val myActivationTimestamp: Long?

  override val branchName: @NlsSafe String? get() = myBranchName

  var isRemoved: Boolean = false
    private set
  private var projectGroup: ProjectGroup? = null

  init {
    templatePresentation.text = IdeBundle.message("action.ReopenProject.reopen.project.text")
    templatePresentation.isApplicationScope = true
    myProjectPath = projectPath
    myProjectName = projectName
    myDisplayName = displayName
    myBranchName = branchName
    myActivationTimestamp = activationTimestamp

    if (Strings.isEmpty(projectDisplayName)) {
      logger<ReopenProjectAction>().error("Empty action text for projectName='$projectName' displayName='$displayName' path='$projectPath'")
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    presentation.setText(projectDisplayName, false)
    presentation.description = FileUtil.toSystemDependentName(myProjectPath)
    presentation.isEnabledAndVisible = true
  }

  val projectDisplayName: @NlsSafe String?
    get() {
      val s = if (myProjectPath == myDisplayName) FileUtil.getLocationRelativeToUserHome(myProjectPath) else myDisplayName!!
      if (branchName != null) {
        return IdeBundle.message("action.reopen.project.display.name.with.branch", s, branchName)
      }
      return s
    }

  override fun actionPerformed(e: AnActionEvent) {
    // force move focus to IdeFrame
    IdeEventQueue.getInstance().popupManager.closeAllPopups()

    val project = e.project

    runWithModalProgressBlocking(ModalTaskOwner.guess(), IdeBundle.message("progress.title.project.initialization")) {
      EelInitialization.runEelInitialization(myProjectPath)
    }

    val file = Path.of(myProjectPath).normalize()
    if (Files.notExists(file)) {
      if (Messages.showDialog(project, IdeBundle
          .message("message.the.path.0.does.not.exist.maybe.on.remote", FileUtil.toSystemDependentName(myProjectPath)),
                              IdeBundle.message("dialog.title.reopen.project"),
                              arrayOf(CommonBundle.getOkButtonText(), IdeBundle.message("button.remove.from.list")), 0,
                              Messages.getErrorIcon()) == 1
      ) {
        isRemoved = true
        RecentProjectsManager.getInstance().removePath(myProjectPath)
      }
      return
    }

    val options = OpenProjectTask {
      projectToClose = project
      val modifiers = e.modifiers
      forceOpenInNewFrame = BitUtil.isSet(modifiers, ActionEvent.CTRL_MASK) ||
                            BitUtil.isSet(modifiers, ActionEvent.SHIFT_MASK) ||
                            ActionPlaces.WELCOME_SCREEN == e.place ||
                            LightEdit.owns(project)
      runConfigurators = true
    }
    openProjectAndLogRecent(file = file, options = options, projectGroup = projectGroup)
  }

  val projectPath: @SystemIndependent String
    get() = myProjectPath

  val projectName: @NlsSafe String?
    get() {
      val manager = RecentProjectsManager.getInstance()
      return if (manager is RecentProjectsManagerBase) manager.getProjectName(myProjectPath) else myProjectName
    }

  override val projectNameToDisplay: @NlsSafe String
    get() {
      val manager = RecentProjectsManager.getInstance()
      return (if (manager is RecentProjectsManagerBase) manager.getDisplayName(myProjectPath) else null) ?: projectName ?: projectPath
    }

  override val projectPathToDisplay: @NlsSafe String
    get() = FileUtil.getLocationRelativeToUserHome(PathUtil.toSystemDependentName(projectPath), false)

  override val projectIcon: Icon get() = RecentProjectsManagerBase.getInstanceEx().getProjectIcon(projectPath, true, 20)

  override val providerIcon: Icon? get() = null

  override val activationTimestamp: Long? get() = myActivationTimestamp

  fun setProjectGroup(projectGroup: ProjectGroup?) {
    this.projectGroup = projectGroup
  }
}
