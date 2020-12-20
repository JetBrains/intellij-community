// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.withPushPop
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.projectRoots.impl.UnknownSdkTracker.ShowStatusCallback
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.openapi.ui.DialogWrapper
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
    fun openProjectStructureDialogIfNeeded()
  }

  val noSettingsDialogSuggested = object: Outcome {
    override val shouldOpenProjectStructureDialog: Boolean = false
    override fun openProjectStructureDialogIfNeeded() {}
  }

  private val OPEN_DIALOG = object : Outcome {
    override val shouldOpenProjectStructureDialog: Boolean = true

    override fun openProjectStructureDialogIfNeeded() {
      if (!shouldOpenProjectStructureDialog) return
      val service = ProjectSettingsService.getInstance(project)
      service.openProjectSettings()
    }
  }

  interface UnknownSdkModalNotificationHandler : ShowStatusCallback {
    val outcome: Outcome
  }

  fun newModalHandler(@Nls dialogTitle: String,
                      @Nls detailedMessage: String?
  ): UnknownSdkModalNotificationHandler = object : ShowStatusCallback, UnknownSdkModalNotificationHandler {
    override var outcome: Outcome = noSettingsDialogSuggested

    override fun showStatus(allActions: List<UnknownSdkFix>, indicator: ProgressIndicator) {
      outcome = kotlin.runCatching { showStatusImpl(dialogTitle, detailedMessage, allActions, indicator) }.getOrElse { noSettingsDialogSuggested }
    }
  }

  private fun showStatusImpl(@Nls dialogTitle: String,
                             @Nls detailedMessage: String?,
                             allActions: List<UnknownSdkFix>,
                             indicator: ProgressIndicator) : Outcome {
    if (allActions.isEmpty()) return noSettingsDialogSuggested

    val actions = UnknownSdkTracker
      .getInstance(project)
      .applyAutoFixesAndNotify(allActions, indicator)

    val actionsWithFix = actions.mapNotNull { it.suggestedFixAction }
    val actionsWithoutFix = actions.filter { it.suggestedFixAction == null }

    if (actionsWithFix.isEmpty()) {
      //nothing to do. We fallback to the default behaviour because there is nothing we can do better
      return noSettingsDialogSuggested
    }

    val isOk = invokeAndWaitIfNeeded {
      createConfirmSdkDownloadFixDialog(dialogTitle, detailedMessage, actionsWithFix, actionsWithoutFix).showAndGet()
    }

    if (isOk) {
      val suggestionsApplied = applySuggestions(actionsWithFix, indicator)
      if (!suggestionsApplied) return OPEN_DIALOG
    }

    if (actionsWithoutFix.isNotEmpty()) {
      return OPEN_DIALOG
    }

    return noSettingsDialogSuggested
  }

  private fun createConfirmSdkDownloadFixDialog(
    @Nls dialogTitle: String,
    @Nls detailedMessage: String?,
    actions: List<UnknownSdkFixAction>,
    actionsWithoutFix: List<UnknownSdkFix>
  ) = object : DialogWrapper(project) {
    init {
      require(actions.isNotEmpty()) { "There must be fix suggestions! " }

      title = dialogTitle
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
      detailedMessage?.let {
        row {
          label(it)
        }
      }

      row {
        label(ProjectBundle.message("dialog.text.resolving.sdks.suggestions"))
      }

      if (actions.isNotEmpty()) {
        actions.sortedBy { it.actionDetailedText.toLowerCase() }.forEach {
          row(ProjectBundle.message("dialog.section.bullet")) {
            val label = label(it.actionDetailedText)
            it.actionTooltipText?.let {
              label.comment(it)
            }
          }
        }
      }

      if (actionsWithoutFix.isNotEmpty()) {
        row {
          label(ProjectBundle.message("dialog.text.resolving.sdks.unknowns"))
        }

        actionsWithoutFix.sortedBy { it.notificationText.toLowerCase() }.forEach {
          row(ProjectBundle.message("dialog.section.bullet")) {
            label(it.notificationText)
          }
        }
      }
    }
  }

  private fun applySuggestions(suggestions: List<UnknownSdkFixAction>, indicator: ProgressIndicator): Boolean {
    if (suggestions.isEmpty()) return true

    var isFailed = false
    for (suggestion in suggestions) {
      try {
        indicator.withPushPop {
          suggestion.applySuggestionBlocking(indicator)
        }
      }
      catch (t: Throwable) {
        isFailed = true
        if (t is ControlFlowException) break
        LOG.warn("Failed to apply suggestion $suggestion. ${t.message}", t)
      }
    }
    return !isFailed
  }
}
