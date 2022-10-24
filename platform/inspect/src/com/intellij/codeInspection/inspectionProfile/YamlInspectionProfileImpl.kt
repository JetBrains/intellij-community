// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.inspectionProfile

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionToolRegistrar
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.PROFILE_DIR
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.PackageSetFactory
import org.jdom.Element
import java.io.File
import java.lang.IllegalArgumentException

class YamlInspectionConfigImpl(override val inspection: String,
                               override val enabled: Boolean?,
                               override val severity: String?,
                               override val ignore: List<String>,
                               override val options: Map<String, *>) : YamlInspectionConfig

class YamlGroupConfigImpl(override val group: String,
                          override val enabled: Boolean?,
                          override val severity: String?,
                          override val ignore: List<String>) : YamlGroupConfig

class YamlInspectionGroupImpl(override val groupId: String, val inspections: Set<String>) : YamlInspectionGroup {
  override fun includesInspection(tool: InspectionToolWrapper<*, *>): Boolean {
    return tool.shortName in inspections
  }
}

class YamlCompositeGroupImpl(override val groupId: String,
                             private val groupProvider: InspectionGroupProvider,
                             private val groupRules: List<String>) : YamlInspectionGroup {
  override fun includesInspection(tool: InspectionToolWrapper<*, *>): Boolean {
    for (groupRule in groupRules.asReversed()) {
      val groupId = groupRule.removePrefix("!")
      if (groupProvider.findGroup(groupId).includesInspection(tool)) {
        return groupId == groupRule
      }
    }
    return false
  }
}

class CompositeGroupProvider : InspectionGroupProvider {

  private val providers = mutableListOf<InspectionGroupProvider>()

  fun addProvider(groupProvider: InspectionGroupProvider) {
    providers.add(groupProvider)
  }

  override fun findGroup(groupId: String): YamlInspectionGroup {
    return object: YamlInspectionGroup {
      override val groupId: String = groupId
      override fun includesInspection(tool: InspectionToolWrapper<*, *>): Boolean {
        return providers.any { it.findGroup(groupId)?.includesInspection(tool) == true }
      }
    }
  }
}

class YamlInspectionProfileImpl private constructor(override val profileName: String?,
                                                    override val baseProfile: InspectionProfileImpl,
                                                    override val configurations: List<YamlBaseConfig>,
                                                    override val groups: List<YamlInspectionGroup>,
                                                    private val groupProvider: InspectionGroupProvider) : YamlInspectionProfile, InspectionGroupProvider {

  companion object {
    @JvmStatic
    fun loadFrom(project: Project, filepath: String = "${getDefaultProfileDirectory(project)}/profile.yaml"): YamlInspectionProfileImpl {
      val profile = readConfig(project, filepath)
      val baseProfile = findBaseProfile(project, profile.baseProfile)
      val configurations = profile.inspections.map(::createInspectionConfig)
      val groupProvider = CompositeGroupProvider()
      groupProvider.addProvider(InspectionGroupProvider.createDynamicGroupProvider())
      val groups = profile.groups.map { group -> createGroup(groupProvider, group) }
      val customGroupProvider = object : InspectionGroupProvider {
        val groupMap = groups.associateBy { group -> group.groupId }
        override fun findGroup(groupId: String): YamlInspectionGroup? {
          return groupMap[groupId]
        }
      }
      groupProvider.addProvider(customGroupProvider)
      return YamlInspectionProfileImpl(profile.name, baseProfile, configurations, groups, groupProvider)
    }

    private fun findBaseProfile(project: Project, profileName: String?): InspectionProfileImpl {
      return profileName
        ?.let { ProjectInspectionProfileManager.getInstance(project).getProfile(profileName, false) }
        ?: InspectionProfileImpl("Default")
    }

    @JvmStatic
    fun isYamlFile(filepath: String): Boolean {
      val extension = File(filepath).extension
      return extension == "yaml" || extension == "yml"
    }

    private fun createGroup(groupProvider: InspectionGroupProvider, group: YamlInspectionGroupRaw): YamlInspectionGroup {
      return if (group.groups.isNotEmpty()) {
        YamlCompositeGroupImpl(group.groupId, groupProvider, group.groups)
      }
      else {
        YamlInspectionGroupImpl(group.groupId, group.inspections.toSet())
      }
    }

    private fun getDefaultProfileDirectory(project: Project): String = "${project.basePath}/${Project.DIRECTORY_STORE_FOLDER}/$PROFILE_DIR"

    private fun createInspectionConfig(config: YamlInspectionConfigRaw): YamlBaseConfig {
      val inspectionId = config.inspection
      if (inspectionId != null) {
        return YamlInspectionConfigImpl(inspectionId, config.enabled, config.severity, config.ignore,
                                        config.options ?: emptyMap<String, Any>())
      }
      val groupId = config.group
      if (groupId != null) {
        return YamlGroupConfigImpl(groupId, config.enabled, config.severity, config.ignore)
      }
      throw IllegalArgumentException("Missing group or inspection id in the inspection configuration.")
    }
  }

  fun buildEffectiveProfile(): InspectionProfileImpl {
    val effectiveProfile: InspectionProfileImpl = InspectionProfileImpl("Default", InspectionToolRegistrar.getInstance(), baseProfile)
      .also { profile ->
        profile.initInspectionTools()
        profile.copyFrom(baseProfile)
        profile.name = profileName ?: "Default"
    }
    configurations.forEach { configuration ->
      val tools = findTools(configuration)
      val scopes = configuration.ignore.map { pattern ->
        if (pattern.startsWith("!")) {
          Pair(NamedScope.UnnamedScope(PackageSetFactory.getInstance().compile(pattern.drop(1))), true)
        }
        else {
          Pair(NamedScope.UnnamedScope(PackageSetFactory.getInstance().compile(pattern)), false)
        }
      }
      tools.asSequence().mapNotNull { tool -> effectiveProfile.getToolsOrNull(tool.shortName, null) }.forEach { inspectionTools ->
        val enabled = configuration.enabled
        if (enabled != null) {
          inspectionTools.isEnabled = enabled
        }
        val severity = HighlightDisplayLevel.find(configuration.severity)
        if (severity != null) {
          inspectionTools.tools.forEach {
            it.level = severity
          }
        }
        val options = (configuration as? YamlInspectionConfig)?.options
        if (options != null) {
          val element = Element("tool")
          ProfileMigrationUtils.writeXmlOptions(element, options)
          inspectionTools.defaultState.tool.tool.readSettings(element)
        }
        scopes.forEach { (scope, enabled) ->
          inspectionTools.prependTool(scope, inspectionTools.defaultState.tool, enabled, inspectionTools.level)
        }
      }
    }
    return effectiveProfile
  }

  private fun findTools(configuration: YamlBaseConfig): List<InspectionToolWrapper<*, *>> {
    return when (configuration) {
      is YamlGroupConfig -> baseProfile.getInspectionTools(null).filter { findGroup(configuration.group).includesInspection(it) }
      is YamlInspectionConfig -> listOfNotNull(baseProfile.getInspectionTool(configuration.inspection, null as PsiElement?))
    }
  }

  override fun findGroup(groupId: String): YamlInspectionGroup? {
    return groupProvider.findGroup(groupId)
  }
}