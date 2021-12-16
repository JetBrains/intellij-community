// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.external

import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.components.*
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.util.xmlb.annotations.OptionTag
import org.jetbrains.annotations.NonNls

@State(name = "ExternalDiffSettings", storages = [Storage(DiffUtil.DIFF_CONFIG)], category = SettingsCategory.TOOLS)
class ExternalDiffSettings : BaseState(), PersistentStateComponent<ExternalDiffSettings> {
  override fun getState(): ExternalDiffSettings = this
  override fun loadState(state: ExternalDiffSettings) {
    copyFrom(state)
  }

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
    @NonNls var fileTypeName: String = DEFAULT_TOOL_NAME,
    @NonNls var diffToolName: String = BUILTIN_TOOL,
    @NonNls var mergeToolName: String = BUILTIN_TOOL
  ) {
    companion object {
      @NonNls
      const val DEFAULT_TOOL_NAME = "Default"

      @NonNls
      const val BUILTIN_TOOL = "Built-in"

      val builtinInstance = ExternalToolConfiguration()
    }
  }

  enum class ExternalToolGroup(val groupName: String) {
    DIFF_TOOL("Diff tool"),
    MERGE_TOOL("Merge tool")
  }

  data class ExternalTool(
    @NonNls var name: String = "",
    var programPath: String = "",
    var argumentPattern: String = "",
    var isMergeTrustExitCode: Boolean = false,
    var groupName: ExternalToolGroup = ExternalToolGroup.DIFF_TOOL
  )

  companion object {
    @JvmStatic
    val instance: ExternalDiffSettings
      get() = service()

    private val fileTypeManager
      get() = FileTypeManager.getInstance()

    @JvmStatic
    fun findDiffTool(fileType: FileType): ExternalTool? {
      val diffToolName = findToolConfiguration(fileType)?.diffToolName
                         ?: instance.defaultToolConfiguration.diffToolName

      if (diffToolName == ExternalToolConfiguration.BUILTIN_TOOL) return null
      val diffTools = instance.externalTools[ExternalToolGroup.DIFF_TOOL] ?: return null

      return findTool(diffTools, diffToolName)
    }

    @JvmStatic
    fun findMergeTool(fileType: FileType): ExternalTool? {
      val mergeToolName = findToolConfiguration(fileType)?.mergeToolName
                          ?: instance.defaultToolConfiguration.mergeToolName

      if (mergeToolName == ExternalToolConfiguration.BUILTIN_TOOL) return null
      val mergeTools = instance.externalTools[ExternalToolGroup.MERGE_TOOL] ?: return null

      return findTool(mergeTools, mergeToolName)
    }

    @JvmStatic
    fun isNotBuiltinDiffTool(): Boolean {
      return instance.defaultToolConfiguration.diffToolName != ExternalToolConfiguration.BUILTIN_TOOL
    }

    @JvmStatic
    fun isNotBuiltinMergeTool(): Boolean {
      return instance.defaultToolConfiguration.mergeToolName != ExternalToolConfiguration.BUILTIN_TOOL
    }

    @JvmStatic
    fun isConfigurationRegistered(externalTool: ExternalTool): Boolean {
      return instance.defaultToolConfiguration.diffToolName == externalTool.name ||
             instance.defaultToolConfiguration.mergeToolName == externalTool.name ||
             instance.externalToolsConfiguration.any { it.diffToolName == externalTool.name || it.mergeToolName == externalTool.name }
    }

    private fun findTool(tools: List<ExternalTool>, toolName: String): ExternalTool? = tools.find { it.name == toolName }

    private fun findToolConfiguration(fileType: FileType): ExternalToolConfiguration? = instance.externalToolsConfiguration.find {
      fileTypeManager.findFileTypeByName(it.fileTypeName) == fileType
    }
  }
}
