// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.InspectionsBundle
import com.intellij.codeInspection.options.OptPane
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.ModEditOptions
import com.intellij.modcommand.Presentation
import com.intellij.openapi.project.DumbAware
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiFile
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus

/**
 * Edits the options of an inspection in the current inspection profile, for the file of the action context.
 *
 * The action shows the [options pane][InspectionProfileEntry.getOptionsPane] of the inspection with [ModEditOptions].
 * After the edit, it updates the changed options with [ModCommand.updateInspectionOption].
 *
 * The action is not available when the profile has no such inspection, or when the inspection has no options pane.
 */
@ApiStatus.Internal
class EditInspectionOptionsModCommandAction(private val toolId: String) : ModCommandAction, DumbAware {
  override fun getFamilyName(): String = InspectionsBundle.message("edit.options.of.reporter.inspection.family")

  override fun getPresentation(context: ActionContext): Presentation? {
    val toolWrapper = findToolWrapper(context.file()) ?: return null
    if (toolWrapper.tool.optionsPane == OptPane.EMPTY) return null
    return Presentation.of(InspectionsBundle.message("edit.inspection.options", toolWrapper.displayName))
  }

  override fun generatePreview(context: ActionContext): IntentionPreviewInfo {
    val toolWrapper = findToolWrapper(context.file()) ?: return IntentionPreviewInfo.EMPTY
    return IntentionPreviewInfo.Html(InspectionsBundle.message("edit.inspection.options.preview", toolWrapper.displayName))
  }

  override fun perform(context: ActionContext): ModCommand {
    val file = context.file()
    val toolWrapper = findToolWrapper(file) ?: return ModCommand.nop()
    if (toolWrapper.tool.optionsPane == OptPane.EMPTY) return ModCommand.nop()
    return ModEditOptions(
      InspectionsBundle.message("edit.inspection.options", toolWrapper.displayName),
      { copyTool(file) },
      false,
    ) { edited -> updateOptions(file, edited) }
  }

  private fun findToolWrapper(file: PsiFile): InspectionToolWrapper<*, *>? {
    val profile = InspectionProjectProfileManager.getInstance(file.project).currentProfile
    return profile.getInspectionTool(toolId, file)
  }

  /**
   * Creates a copy of the inspection with the current options.
   * [ModEditOptions] changes this copy, so the inspection in the profile stays the same until [updateOptions].
   */
  private fun copyTool(file: PsiFile): InspectionProfileEntry {
    val toolWrapper = checkNotNull(findToolWrapper(file)) { "Tool not found: $toolId" }
    val copy = toolWrapper.createCopy().tool
    val settings = Element("copy")
    toolWrapper.tool.writeSettings(settings)
    copy.readSettings(settings)
    return copy
  }

  private fun updateOptions(file: PsiFile, edited: InspectionProfileEntry): ModCommand {
    val inspection = findToolWrapper(file)?.tool ?: return ModCommand.nop()
    val editedController = edited.optionController
    return ModCommand.updateInspectionOption(file, inspection) { tool ->
      val controller = tool.optionController
      for (control in tool.optionsPane.allControls()) {
        val value = editedController.getOption(control.bindId()) ?: continue
        controller.setOption(control.bindId(), value)
      }
    }
  }
}
