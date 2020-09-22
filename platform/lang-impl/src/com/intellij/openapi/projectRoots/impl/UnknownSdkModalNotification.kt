// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.projectRoots.impl.UnknownSdkBalloonNotification.FixedSdksNotification
import com.intellij.openapi.projectRoots.impl.UnknownSdkEditorNotification.FixableSdkNotification
import com.intellij.openapi.projectRoots.impl.UnknownSdkFix.SuggestedFixAction
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.layout.*
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import javax.swing.Action
import javax.swing.JPanel

@Service
class UnknownSdkModalNotification(
  private val project: Project
) {
  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<UnknownSdkModalNotification>()
  }

  enum class Outcome {
    NO_CHANGES,
    CONFIGURED
  }

  fun newModalHandler(@Nls errorMessage: String) = object : UnknownSdkTracker.ShowStatusCallbackAdapter(project) {
    override fun notifySdks(fixed: FixedSdksNotification, actions: FixableSdkNotification) {
      //this was fixed automatically, it could hopefully be enough
      mySdkBalloonNotification.notifyFixedSdks(fixed)

      //nothing to do, so it's done!
      if (actions.isEmpty) return

      val actionsWithFix = actions.infos.mapNotNull {
        val fix = it.suggestedFixAction
        if (fix != null) it to fix else null
      }.toMap()

      val actionsWithoutFix = actions.infos.filter { it.suggestedFixAction == null }

      if (actionsWithoutFix.isNotEmpty()) {
        //TODO: handle additional message that we cannot fix that one, so ProjectStructure dialog has to be open
        "".toString()
      }

      val confirmActionText = when {
        actionsWithFix.size == 1 -> actionsWithFix.values.single().actionText
        else -> actionsWithFix.values.map { it.actionKindText }.distinct().sorted().joinToString(", ") //TODO: how to join?
      }

      invokeAndWaitIfNeeded {
        createConfirmSdkDownloadFixDialog(actionsWithFix, confirmActionText).showAndGet()
      }
    }

    private fun createConfirmSdkDownloadFixDialog(actions: Map<UnknownSdkFix, SuggestedFixAction>,
                                                  @Nls confirmActionText : String
    ) = object : DialogWrapper(project) {
      init {
        title = ProjectBundle.message("dialog.title.resolving.sdks")
        init()

        myOKAction.putValue(Action.NAME, ProjectBundle.message("dialog.button.download.sdks"))
        myCancelAction.putValue(Action.NAME, ProjectBundle.message("dialog.button.open.settings"))
      }

      override fun createCenterPanel() = panel {
        noteRow(errorMessage)

        val mainMessages = actions.map { it.key.notificationText }.distinct().sorted()

        for ((info, fix) in actions) {
          val control = info.createNotificationPanel(project)
          row {
            val wrap = JPanel(BorderLayout())
            wrap.add(control, BorderLayout.CENTER)
            wrap.minimumSize = control.preferredSize
            wrap(CCFlags.grow)
          }
        }
      }
    }
  }
}
