// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.configurationStore.OLD_NAME_CONVERTER
import com.intellij.icons.AllIcons
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.Presentation
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.profile.codeInspection.PROFILE_DIR
import com.intellij.project.isDirectoryBased
import com.intellij.project.stateStore
import org.jetbrains.annotations.ApiStatus

/**
 * Disables an inspection in the current inspection profile, for the file of the action context.
 *
 * Unlike [DisableInspectionToolAction], this action does not change the profile in memory.
 * It produces a [ModCommand] that changes the profile file with [ProfileFile.writeTools].
 *
 * The inspection is disabled the same way as [ToolsImpl.disableTool] does it: in the first scope that contains the file,
 * or in the default state otherwise. Without scopes, the whole inspection is disabled.
 *
 * @param profileFileLocator finds the file of the current profile, or gives `null` when the action is not available
 */
@ApiStatus.Internal
class DisableInspectionToolModCommandAction(
  private val toolId: String,
  private val profileFileLocator: (Project, InspectionProfileImpl) -> ProfileFile?,
) : ModCommandAction, DumbAware {
  /**
   * Creates the action for the project profile of a directory-based project.
   * The action is not available for an application profile.
   */
  constructor(toolId: String) : this(toolId, ::findProjectProfileFile)

  constructor(key: HighlightDisplayKey) : this(key.shortName)

  override fun getFamilyName(): String = DisableInspectionToolAction.getNameText()

  override fun getPresentation(context: ActionContext): Presentation? {
    val project = context.project()
    val profile = InspectionProjectProfileManager.getInstance(project).currentProfile
    val profileFile = profileFileLocator(project, profile) ?: return null
    if (!profileFile.canWrite()) return null
    val toolWrapper = profile.getInspectionTool(toolId, project)
    if (toolWrapper != null && toolWrapper.defaultLevel.isNonSwitchable) return null
    val tools = profile.getToolsOrNull(toolId, project)
    if (tools != null && !tools.isEnabled(context.file())) return null
    return Presentation.of(familyName).withIcon(AllIcons.Actions.Cancel)
  }

  override fun perform(context: ActionContext): ModCommand {
    val project = context.project()
    val profile = InspectionProjectProfileManager.getInstance(project).currentProfile
    val profileFile = profileFileLocator(project, profile) ?: return ModCommand.nop()
    val tools = profile.getToolsOrNull(toolId, project) ?: return ModCommand.nop()
    if (!tools.isEnabled(context.file())) return ModCommand.nop()
    val disabledTools = tools.copy(project)
    disabledTools.disableTool(context.file())
    return profileFile.writeTools(listOf(disabledTools))
  }
}

private fun findProjectProfileFile(project: Project, profile: InspectionProfileImpl): ProfileFile? {
  if (!profile.isProjectLevel || !project.isDirectoryBased) return null
  val configDir = project.stateStore.directoryStorePath ?: return null
  val path = configDir.resolve(PROFILE_DIR).resolve(OLD_NAME_CONVERTER(profile.name) + ".xml")
  return ProfileFile(path, profile.name)
}
