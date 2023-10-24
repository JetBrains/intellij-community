// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.external

import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.components.*
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.PathUtilRt
import com.intellij.util.xmlb.annotations.OptionTag
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

@State(name = "ExternalDiffSettings", storages = [Storage(DiffUtil.DIFF_CONFIG)], category = SettingsCategory.TOOLS)
class ExternalDiffSettings : BaseState(), PersistentStateComponent<ExternalDiffSettings> {
  override fun getState(): ExternalDiffSettings = this
  override fun loadState(state: ExternalDiffSettings) {
    migrateOldSettings(state)
    copyFrom(state)
  }

  override fun noStateLoaded() {
    isSettingsMigrated = true
  }

  @get:OptionTag("MIGRATE_OLD_SETTINGS")
  var isSettingsMigrated by property(false)

  @get:OptionTag("EXTERNAL_TOOLS_ENABLED")
  var isExternalToolsEnabled: Boolean by property(false)

  @get:OptionTag("EXTERNAL_TOOLS_CONFIGURATION")
  var externalToolsConfiguration: MutableList<ExternalToolConfiguration> by list()

  @get:OptionTag("EXTERNAL_TOOLS")
  var externalTools: MutableMap<ExternalToolGroup, List<ExternalTool>> by map()

  @get:OptionTag("DEFAULT_TOOL_CONFIGURATION")
  var defaultToolConfiguration: ExternalToolConfiguration by property(ExternalToolConfiguration.builtinInstance) {
    it == ExternalToolConfiguration.builtinInstance
  }

  data class ExternalToolConfiguration(
    var fileTypeName: @NlsSafe String = DEFAULT_TOOL_NAME,
    var diffToolName: @NlsSafe String = BUILTIN_TOOL,
    var mergeToolName: @NlsSafe String = BUILTIN_TOOL
  ) {
    companion object {
      const val DEFAULT_TOOL_NAME: @NonNls String = "Default"
      const val BUILTIN_TOOL: @NonNls String = "Built-in"

      val builtinInstance = ExternalToolConfiguration()
    }
  }

  enum class ExternalToolGroup(val groupName: @Nls String) {
    DIFF_TOOL(DiffBundle.message("diff.tool")),
    MERGE_TOOL(DiffBundle.message("merge.tool"))
  }

  data class ExternalTool(
    var name: @NlsSafe String = "",
    var programPath: String = "",
    var argumentPattern: String = "",
    var isMergeTrustExitCode: Boolean = false,
    var groupName: ExternalToolGroup = ExternalToolGroup.DIFF_TOOL
  )

  // OLD SETTINGS AREA
  @get:OptionTag("DIFF_ENABLED")
  var isDiffEnabled: Boolean by property(false)

  @get:OptionTag("DIFF_EXE_PATH")
  var diffExePath: String by nonNullString()

  @get:OptionTag("DIFF_PARAMETERS")
  var diffParameters: String by nonNullString("%1 %2 %3")

  @get:OptionTag("MERGE_ENABLED")
  var isMergeEnabled: Boolean by property(false)

  @get:OptionTag("MERGE_EXE_PATH")
  var mergeExePath: String by nonNullString()

  @get:OptionTag("MERGE_PARAMETERS")
  var mergeParameters: String by nonNullString("%1 %2 %3 %4")

  @get:OptionTag("MERGE_TRUST_EXIT_CODE")
  var isMergeTrustExitCode: Boolean by property(false)
  // ^^^ OLD SETTINGS AREA

  private fun nonNullString(initialValue: String = "") = property(initialValue) { it == initialValue }

  companion object {
    @JvmStatic
    val instance: ExternalDiffSettings
      get() = service()

    private val fileTypeManager
      get() = FileTypeManager.getInstance()

    @JvmStatic
    fun findDefaultDiffTool(): ExternalTool? {
      val diffToolName = instance.defaultToolConfiguration.diffToolName
      return findTool(diffToolName, ExternalToolGroup.DIFF_TOOL)
    }

    @JvmStatic
    fun findDiffTool(fileType: FileType): ExternalTool? {
      val diffToolName = findToolConfiguration(fileType)?.diffToolName
                         ?: instance.defaultToolConfiguration.diffToolName
      return findTool(diffToolName, ExternalToolGroup.DIFF_TOOL)
    }

    @JvmStatic
    fun findMergeTool(fileType: FileType): ExternalTool? {
      val mergeToolName = findToolConfiguration(fileType)?.mergeToolName
                          ?: instance.defaultToolConfiguration.mergeToolName
      return findTool(mergeToolName, ExternalToolGroup.MERGE_TOOL)
    }

    @JvmStatic
    fun isNotBuiltinDiffTool(): Boolean {
      return instance.defaultToolConfiguration.diffToolName != ExternalToolConfiguration.BUILTIN_TOOL
    }

    @JvmStatic
    fun isNotBuiltinMergeTool(): Boolean {
      return instance.defaultToolConfiguration.mergeToolName != ExternalToolConfiguration.BUILTIN_TOOL
    }

    private fun findTool(toolName: String, group: ExternalToolGroup): ExternalTool? {
      if (toolName == ExternalToolConfiguration.BUILTIN_TOOL) return null
      val tools = instance.externalTools[group] ?: return null
      return tools.find { it.name == toolName }
    }

    private fun findToolConfiguration(fileType: FileType): ExternalToolConfiguration? = instance.externalToolsConfiguration.find {
      fileTypeManager.findFileTypeByName(it.fileTypeName) == fileType
    }

    private fun migrateOldSettings(state: ExternalDiffSettings) {
      if (!state.isSettingsMigrated) {
        // load old settings
        state.isExternalToolsEnabled = state.isDiffEnabled || state.isMergeEnabled

        // save old settings
        state.defaultToolConfiguration = ExternalToolConfiguration().apply {
          if (state.diffExePath.isNotEmpty()) {
            val oldDiffTool = ExternalTool(StringUtil.capitalize(PathUtilRt.getFileName(state.diffExePath)),
                                           state.diffExePath, state.diffParameters,
                                           false, ExternalToolGroup.DIFF_TOOL)
            state.externalTools[ExternalToolGroup.DIFF_TOOL] = listOf(oldDiffTool)
            diffToolName = oldDiffTool.name
          }

          if (state.mergeExePath.isNotEmpty()) {
            val oldMergeTool = ExternalTool(StringUtil.capitalize(PathUtilRt.getFileName(state.mergeExePath)),
                                            state.mergeExePath, state.mergeParameters,
                                            state.isMergeTrustExitCode, ExternalToolGroup.MERGE_TOOL)
            state.externalTools[ExternalToolGroup.MERGE_TOOL] = listOf(oldMergeTool)
            mergeToolName = oldMergeTool.name
          }
        }

        state.isSettingsMigrated = true
      }
    }
  }
}
