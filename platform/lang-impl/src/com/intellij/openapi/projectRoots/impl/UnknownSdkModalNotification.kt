// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl

import com.intellij.icons.AllIcons
import com.intellij.ide.nls.NlsMessages
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.forEachWithProgress
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.projectRoots.impl.UnknownSdkTracker.ShowStatusCallback
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ex.MultiLineLabel
import com.intellij.ui.layout.*
import org.jetbrains.annotations.Nls
import javax.swing.Action

@Service
class UnknownSdkModalNotification(
  private val project: Project
) {
  companion object {
    private val LOG = logger<UnknownSdkModalNotification>()

    @JvmStatic
    fun getInstance(project: Project) = project.service<UnknownSdkModalNotification>()
  }

  interface Outcome {
    val shouldOpenProjectStructureDialog: Boolean
  }

  private val OPEN_DIALOG = object : Outcome {
    override val shouldOpenProjectStructureDialog: Boolean = true
  }

  private val NO_DIALOG = object : Outcome {
    override val shouldOpenProjectStructureDialog: Boolean = true
  }

  interface UnknownSdkModalNotificationHandler : ShowStatusCallback {
    val outcome: Outcome
  }

  fun newModalHandler(@Nls errorMessage: String): UnknownSdkModalNotificationHandler = object : ShowStatusCallback, UnknownSdkModalNotificationHandler {
    override lateinit var outcome: Outcome

    override fun showStatus(actions: List<UnknownSdkFix>) {
      //nothing to do, so it's done!
      if (actions.isEmpty()) {
        outcome = NO_DIALOG
        return
      }

      val actionsWithFix = actions.mapNotNull {
        val fix = it.suggestedFixAction
        if (fix != null) it to fix else null
      }.toMap()

      val actionsWithoutFix = actions.filter { it.suggestedFixAction == null }

      val isOk = actionsWithFix.isNotEmpty() && invokeAndWaitIfNeeded {
        createConfirmSdkDownloadFixDialog(errorMessage, actionsWithFix, actionsWithoutFix).showAndGet()
      }

      if (isOk) {
        val success = applySuggestions(actionsWithFix.values.toList())
        if (!success) {
          outcome = OPEN_DIALOG
        }
      }

      if (actionsWithoutFix.isNotEmpty()) {
        outcome = OPEN_DIALOG
      }
    }
  }

  private fun createConfirmSdkDownloadFixDialog(
    @Nls errorMessage: String,
    actions: Map<UnknownSdkFix, UnknownSdkFixAction>,
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

  private fun applySuggestions(suggestions: List<UnknownSdkFixAction>): Boolean {
    if (suggestions.isEmpty()) return true

    var isFailed = false
    object : Task.Modal(project, "Configuring SDKs", true) {
      override fun run(outerIndicator: ProgressIndicator) {
        suggestions.forEachWithProgress(outerIndicator) { suggestion, subIndicator ->
          try {
            suggestion.applySuggestionModal(subIndicator)
          }
          catch (t: Throwable) {
            isFailed = true
            if (t is ControlFlowException) return
            LOG.warn("Failed to apply suggestion $suggestion. ${t.message}", t)
          }
        }
      }
    }.queue()
    return !isFailed
  }

}
