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
import javax.swing.Action

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

      invokeAndWaitIfNeeded {
        createConfirmSdkDownloadFixDialog(actionsWithFix, actionsWithoutFix).showAndGet()
      }
    }

    private fun createConfirmSdkDownloadFixDialog(
      actions: Map<UnknownSdkFix, SuggestedFixAction>,
      actionsWithoutFix: List<UnknownSdkFix>
    ) = object : DialogWrapper(project) {
      init {
        title = ProjectBundle.message("dialog.title.resolving.sdks")
        init()

        myOKAction.putValue(Action.NAME, ProjectBundle.message("dialog.button.download.sdks"))
        myCancelAction.putValue(Action.NAME, ProjectBundle.message("dialog.button.open.settings"))
      }

      override fun createCenterPanel() = panel {
        noteRow(errorMessage)

        for ((_, fix) in actions) {
          row {
            checkBox(text = fix.checkboxActionText,
                     isSelected = true,
                     comment = fix.checkboxActionTooltip
                     )
          }
        }

        for (fix in actionsWithoutFix) {
          row {
            label(fix.notificationText)
          }
        }
      }
    }
  }
}
