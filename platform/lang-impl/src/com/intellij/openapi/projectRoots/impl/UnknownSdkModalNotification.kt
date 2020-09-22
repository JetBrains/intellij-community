// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl

import com.intellij.icons.AllIcons
import com.intellij.ide.nls.NlsMessages
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.projectRoots.impl.UnknownSdkBalloonNotification.FixedSdksNotification
import com.intellij.openapi.projectRoots.impl.UnknownSdkEditorNotification.FixableSdkNotification
import com.intellij.openapi.projectRoots.impl.UnknownSdkFix.SuggestedFixAction
import com.intellij.openapi.projectRoots.impl.UnknownSdkTracker.ShowStatusCallback
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ex.MultiLineLabel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.layout.*
import org.jetbrains.annotations.Nls
import javax.swing.Action
import javax.swing.SwingConstants

@Service
class UnknownSdkModalNotification(
  private val project: Project
) {
  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<UnknownSdkModalNotification>()
  }

  sealed class Outcome {
    object NO_CHANGES : Outcome()
    object CONFIGURED : Outcome()
    class OpenProjectStructureDialog(val forSdkName: String? = null): Outcome()
  }

  interface UnknownSdkModalNotificationHandler : ShowStatusCallback {
    val outcome: Outcome
  }

  fun newModalHandler(@Nls errorMessage: String) : UnknownSdkModalNotificationHandler = object : UnknownSdkTracker.ShowStatusCallbackAdapter(project), UnknownSdkModalNotificationHandler {
    override lateinit var outcome: Outcome

    override fun notifySdks(fixed: FixedSdksNotification, actions: FixableSdkNotification) {
      //this was fixed automatically, it could hopefully be enough
      mySdkBalloonNotification.notifyFixedSdks(fixed)

      //nothing to do, so it's done!
      if (actions.isEmpty) {
        outcome = Outcome.NO_CHANGES
        return
      }

      val actionsWithFix = actions.infos.mapNotNull {
        val fix = it.suggestedFixAction
        if (fix != null) it to fix else null
      }.toMap()

      val actionsWithoutFix = actions.infos.filter { it.suggestedFixAction == null }

      val isOk = invokeAndWaitIfNeeded {
        createConfirmSdkDownloadFixDialog(actionsWithFix, actionsWithoutFix).showAndGet()
      }

      if (isOk) {
        outcome = Outcome.CONFIGURED
        //TODO: run the configuration, and also join same downlaods
      }

      if (actionsWithoutFix.isNotEmpty()) {
        outcome = Outcome.OpenProjectStructureDialog()
      }
    }

    private fun createConfirmSdkDownloadFixDialog(
      actions: Map<UnknownSdkFix, SuggestedFixAction>,
      actionsWithoutFix: List<UnknownSdkFix>
    ) = object : DialogWrapper(project) {
      init {
        title = ProjectBundle.message("dialog.title.resolving.sdks")
        setResizable(false)
        init()

        val okMessage = when {
          actionsWithoutFix.isEmpty() -> ProjectBundle.message("dialog.button.download.sdks")
          else -> ProjectBundle.message("dialog.button.download.sdksAndOpenDialog")
        }

        myOKAction.putValue(Action.NAME, okMessage)
        myCancelAction.putValue(Action.NAME, ProjectBundle.message("dialog.button.open.settings"))
      }

      override fun createCenterPanel() = panel {
        noteRow(errorMessage)

        if (actions.isNotEmpty()) {
          row {
            label(ProjectBundle.message("dialog.text.resolving.sdks.suggestions"))
          }

          for ((_, fix) in actions) {
            row(ProjectBundle.message("dialog.section.bullet")) {
              val label = label(fix.checkboxActionText)
              fix.checkboxActionTooltip?.let {
                label.comment(it)
              }
            }
          }
        }

        if (actionsWithoutFix.isNotEmpty()) {
          val description = ProjectBundle.message(
            "dialog.text.resolving.sdks.unknowns",
            NlsMessages.formatAndList(actionsWithoutFix.mapNotNull { it.sdkTypeAndNameText }.toSortedSet()))

          row {
            val label = MultiLineLabel(description)
            label.icon = AllIcons.General.Warning
            component(label)
          }
        }
      }
    }
  }
}
