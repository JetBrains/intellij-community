// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.inspectionProfile

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.GlobalInspectionTool
import com.intellij.codeInspection.ex.*
import com.intellij.codeInspection.inspectionProfile.YamlProfileUtils.createProfileCopy
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.util.io.toNioPath
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.profile.codeInspection.BaseInspectionProfileManager
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.profile.codeInspection.PROFILE_DIR
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.scope.packageSet.*
import org.jdom.Element
import java.io.File
import java.io.Reader
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.reader

private const val SCOPE_PREFIX = "scope#"

private val LOG = logger<YamlInspectionProfileImpl>()

class YamlInspectionConfigImpl(override val inspection: String,
                               override val enabled: Boolean?,
                               override val severity: String?,
                               override val ignore: List<String>,
                               override val options: Map<String, String>) : YamlInspectionConfig

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
    for (groupRule in groupRules.asReversed().filter { it.isNotEmpty() }) {
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

  override fun findGroup(groupId: String): YamlInspectionGroup? {
    return providers.firstNotNullOfOrNull { it.findGroup(groupId) }
  }
}

class YamlInspectionProfileImpl private constructor(override val profileName: String?,
                                                    override val inspectionToolsSupplier: InspectionToolsSupplier,
                                                    override val inspectionProfileManager: BaseInspectionProfileManager,
                                                    override val baseProfile: InspectionProfileImpl,
                                                    override val configurations: List<YamlBaseConfig>,
                                                    override val groups: List<YamlInspectionGroup>,
                                                    private val groupProvider: InspectionGroupProvider) : YamlInspectionProfile, InspectionGroupProvider {

  companion object {
    @JvmStatic
    fun loadFrom(reader: Reader,
                 includeReaders: (Path) -> Reader,
                 toolsSupplier: InspectionToolsSupplier,
                 profileManager: BaseInspectionProfileManager
    ): YamlInspectionProfileImpl {
      val profile = readConfig(reader, includeReaders)
      val baseProfile = findBaseProfile(profileManager, profile.baseProfile)
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

      return YamlInspectionProfileImpl(
        profile.name,
        toolsSupplier,
        profileManager,
        baseProfile,
        configurations,
        groups,
        groupProvider)
    }

    @JvmStatic
    fun loadFrom(project: Project,
                 filePath: String = "${getDefaultProfileDirectory(project)}/profile.yaml",
                 toolsSupplier: InspectionToolsSupplier = InspectionToolRegistrar.getInstance(),
                 profileManager: BaseInspectionProfileManager = ProjectInspectionProfileManager.getInstance(project)
    ): YamlInspectionProfileImpl {
      val configFile = File(filePath).absoluteFile
      require(configFile.exists()) { "File does not exist: ${configFile.canonicalPath}" }

      val includeProvider: (Path) -> Reader = {
        val includePath = configFile.parent.toNioPath().resolve(it)
        require(includePath.exists()) { "File does not exist: ${includePath.toCanonicalPath()}" }
        includePath.reader()
      }



      return loadFrom(configFile.reader(), includeProvider, toolsSupplier, profileManager)
    }

    private fun findBaseProfile(profileManager: InspectionProfileManager, profileName: String?): InspectionProfileImpl {
      val name = profileName ?: "Default"
      return profileManager.getProfile(name, false)
             ?: throw IllegalArgumentException("Can't find base profile '$name'")
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
                                        config.options ?: emptyMap())
      }
      val groupId = config.group
      if (groupId != null) {
        return YamlGroupConfigImpl(groupId, config.enabled, config.severity, config.ignore)
      }
      throw IllegalArgumentException("Missing group or inspection id in the inspection configuration.")
    }
  }

  fun buildEffectiveProfile(): InspectionProfileImpl {
    val effectiveProfile: InspectionProfileImpl = createProfileCopy(baseProfile, inspectionToolsSupplier, inspectionProfileManager)
    effectiveProfile.name = profileName ?: "Default"
    val scopesToApply = mutableMapOf<String, MutableList<Pair<PackageSet, Boolean>>>()
    configurations.forEach { configuration ->
      val tools = findTools(configuration)
      val scopes = configuration.ignore.map { pattern ->
        createScope(pattern)
      }
      tools.asSequence().mapNotNull { tool -> effectiveProfile.getToolsOrNull(tool.shortName, null) }.forEach { inspectionTools ->
        val enabled = configuration.enabled
        if (enabled != null) {
          inspectionTools.isEnabled = enabled
          if (inspectionTools.tools.all { !it.isEnabled }) {
            inspectionTools.defaultState.isEnabled = enabled
          }
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
          YamlProfileUtils.writeXmlOptions(element, options)
          inspectionTools.defaultState.tool.tool.readSettings(element)
        }
        val toApply = scopesToApply.getOrPut(inspectionTools.shortName) { mutableListOf() }
        toApply.addAll(scopes)
      }
    }

    effectiveProfile.tools.forEach { tool ->
      val enabled = tool.isEnabled
      val toApply = scopesToApply[tool.shortName]

      if (toApply != null) {
        if (tool.isGlobalTool()) { // for global inspections yaml profile are creating only one enabled scope, it doesn't support several scopes with different severities/params.
          val oldScopes = collectOldScopes(tool)
          tool.defaultState.isEnabled = false
          tool.removeAllScopes()
          val scope = HierarchyPackageSet(toApply.reversed() + oldScopes)
          tool.prependTool(NamedScope.UnnamedScope(scope), tool.defaultState.tool, true, tool.level)
        } else {
          toApply.forEach { (packageSet, enabled) ->
            tool.prependTool(NamedScope.UnnamedScope(packageSet), tool.defaultState.tool, enabled, tool.level)
          }
        }
        tool.isEnabled = enabled // because prependTool is modifying isEnabled
      }
    }

    return effectiveProfile
  }

  private fun collectOldScopes(tool: ToolsImpl): List<Pair<PackageSet, Boolean>> {
    return tool.tools.mapNotNull {
      val packageSet = it.getScope(null)?.value
      if (packageSet is HierarchyPackageSet) {
        packageSet.packages
      } else if (packageSet != null) {
        listOf( packageSet to it.isEnabled)
      }
      else null
    }.flatten()
  }

  private fun ToolsImpl.isGlobalTool(): Boolean {
    return this.tool.tool is GlobalInspectionTool
  }

  private fun createScope(pattern: String): Pair<PackageSet, Boolean> {
    return if (pattern.startsWith("!")) {
      Pair(parsePattern(pattern.drop(1)), true)
    }
    else {
      Pair(parsePattern(pattern), false)
    }
  }

  private fun parsePattern(pattern: String): PackageSet {
    if (pattern.startsWith(SCOPE_PREFIX)) {
      val scope = pattern.drop(SCOPE_PREFIX.length)
      try {
        return PackageSetFactory.getInstance().compile(scope)
      } catch (e: ParsingException) {
        LOG.warn("Unknown scope format: $scope", e)
      }
    }

    return getGlobScope(pattern)
  }

  private fun getGlobScope(pattern: String): PackageSet {
    val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")

    val packageSet = object : AbstractPackageSet("glob:$pattern") {
      override fun contains(file: VirtualFile, project: Project, holder: NamedScopesHolder?): Boolean {
        val root = holder?.projectBaseDir ?: return false
        val relativePath = VfsUtilCore.getRelativePath(file, root,  File.separatorChar) ?: return false
        return matcher.matches(Paths.get(relativePath))
      }
    }

    return packageSet
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

  private class HierarchyPackageSet(val packages: List<Pair<PackageSet, Boolean>>)
    : AbstractPackageSet("Hierarchy:$packages") {
    override fun contains(file: VirtualFile, project: Project, holder: NamedScopesHolder?): Boolean {
      packages.forEach {(packageSet, enabled) ->
        if (packageSet is PackageSetBase) {
          if (packageSet.contains(file, project, holder)) return enabled
        }
        else {
          val psiFile = getPsiFile(file, project)
          if (psiFile != null && packageSet.contains(psiFile, holder)) return enabled
        }
      }
      return false
    }
  }
}