// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.build.productLayout.generator

import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import org.jetbrains.intellij.build.productLayout.ContentModule
import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.collectPluginizedModuleSets
import org.jetbrains.intellij.build.productLayout.moduleSetPluginModuleName
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.GenerationMode
import org.jetbrains.intellij.build.productLayout.pipeline.ModuleSetPluginsOutput
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import org.jetbrains.intellij.build.productLayout.resolveModuleSetPluginId
import org.jetbrains.intellij.build.productLayout.stats.FileChangeStatus
import org.jetbrains.intellij.build.productLayout.stats.ModuleSetPluginFileResult
import org.jetbrains.intellij.build.productLayout.util.FileUpdateStrategy
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name

private const val COMMUNITY_MODULE_SET_PLUGIN_GENERATED_ROOT: String = "community/module-set-plugins/generated"
private const val ULTIMATE_MODULE_SET_PLUGIN_GENERATED_ROOT: String = "module-set-plugins/generated"
private const val MODULE_SET_PLUGIN_MAIN_MODULE_NAME: String = "intellij.moduleSet.plugin.main"
private val MODULE_SET_PLUGIN_LEGACY_GENERATED_ROOTS: List<String> = listOf(
  "community/platform/platform-resources/generated/module-set-plugins",
)
private const val MODULE_SET_PLUGIN_LEGACY_BUNDLE_PATH: String = "resources/messages/ModuleSetPluginsBundle.properties"
private const val MODULE_SET_PLUGIN_XML_PATH: String = "resources/META-INF/plugin.xml"
private const val MODULE_SET_PLUGIN_CONTENT_YAML_PATH: String = "plugin-content.yaml"
private const val PROJECT_DIR_MACRO: String = "$" + "PROJECT_DIR" + "$"
private const val MODULE_DIR_MACRO: String = "$" + "MODULE_DIR" + "$"
private val FILEPATH_ATTRIBUTE_REGEX = Regex("\\bfilepath=\"([^\"]+)\"")

internal object ModuleSetPluginGenerator : PipelineNode {
  override val id get() = NodeIds.MODULE_SET_PLUGINS
  override val produces get() = setOf(Slots.MODULE_SET_PLUGINS)

  override suspend fun execute(ctx: ComputeContext) {
    val model = ctx.model
    if (model.generationMode == GenerationMode.UPDATE_SUPPRESSIONS) {
      ctx.publish(
        Slots.MODULE_SET_PLUGINS,
        ModuleSetPluginsOutput(files = emptyList(), managedWrapperRoots = emptyMap(), legacyGeneratedRoots = emptySet())
      )
      return
    }

    ctx.publish(
      Slots.MODULE_SET_PLUGINS,
      generateModuleSetPlugins(
        projectRoot = model.projectRoot,
        strategy = model.generatedArtifactWritePolicy,
        communityModuleSets = model.discovery.communityModuleSets,
        ultimateModuleSets = model.discovery.ultimateModuleSets,
        generationMode = model.generationMode,
      )
    )
  }
}

internal data class ModuleSetPluginCleanupResult(
  val files: List<ModuleSetPluginFileResult>,
  val emptyDirectoryCandidates: Set<Path>,
)

internal fun generateModuleSetPlugins(
  projectRoot: Path,
  strategy: FileUpdateStrategy,
  communityModuleSets: List<ModuleSet>,
  ultimateModuleSets: List<ModuleSet>,
  generationMode: GenerationMode = GenerationMode.NORMAL,
): ModuleSetPluginsOutput {
  if (generationMode == GenerationMode.UPDATE_SUPPRESSIONS) {
    return ModuleSetPluginsOutput(files = emptyList(), managedWrapperRoots = emptyMap(), legacyGeneratedRoots = emptySet())
  }

  val communityGeneratedRoot = projectRoot.resolve(COMMUNITY_MODULE_SET_PLUGIN_GENERATED_ROOT)
  val ultimateGeneratedRoot = projectRoot.resolve(ULTIMATE_MODULE_SET_PLUGIN_GENERATED_ROOT)
  val communityWrappers = collectPluginWrappers(communityModuleSets, communityGeneratedRoot)
  val ultimateWrappers = collectPluginWrappers(ultimateModuleSets, ultimateGeneratedRoot)

  val duplicateWrapperModuleNames = communityWrappers.keys.intersect(ultimateWrappers.keys)
  for (moduleName in duplicateWrapperModuleNames) {
    communityWrappers.remove(moduleName)
    ultimateWrappers.remove(moduleName)
  }

  val files = ArrayList<ModuleSetPluginFileResult>()
  val emptyDirectoryCandidates = LinkedHashSet<Path>()

  for (wrapper in (communityWrappers.values + ultimateWrappers.values).sortedBy { it.moduleName }) {
    generateWrapperFiles(projectRoot, strategy, wrapper, files, emptyDirectoryCandidates)
  }

  val mainModule = generateMainModule(projectRoot, strategy, communityGeneratedRoot, communityWrappers.values.toList(), files)
  val requiredCommunityModuleNames = LinkedHashSet(communityWrappers.keys)
  if (mainModule != null) {
    requiredCommunityModuleNames.add(mainModule.moduleName)
  }

  updateModulesXml(
    projectRoot = projectRoot,
    strategy = strategy,
    rootModuleImlPaths = communityWrappers.values.map { it.imlPath } + ultimateWrappers.values.map { it.imlPath } + listOfNotNull(mainModule?.imlPath),
    communityModuleImlPaths = communityWrappers.values.map { it.imlPath } + listOfNotNull(mainModule?.imlPath),
    files = files,
  )

  return ModuleSetPluginsOutput(
    files = files,
    managedWrapperRoots = linkedMapOf(
      communityGeneratedRoot to requiredCommunityModuleNames,
      ultimateGeneratedRoot to ultimateWrappers.keys.toSet(),
    ),
    legacyGeneratedRoots = MODULE_SET_PLUGIN_LEGACY_GENERATED_ROOTS.mapTo(LinkedHashSet(), projectRoot::resolve),
    emptyDirectoryCandidates = emptyDirectoryCandidates,
  )
}

internal fun cleanupOrphanedModuleSetPluginFiles(
  projectRoot: Path,
  output: ModuleSetPluginsOutput,
  strategy: FileUpdateStrategy,
): ModuleSetPluginCleanupResult {
  val files = ArrayList<ModuleSetPluginFileResult>()
  val emptyDirectoryCandidates = LinkedHashSet<Path>()

  for ((generatedRoot, requiredModuleNames) in output.managedWrapperRoots) {
    if (!Files.exists(generatedRoot)) {
      continue
    }

    Files.newDirectoryStream(generatedRoot).use { entries ->
      for (entry in entries) {
        if (entry.name in requiredModuleNames) {
          continue
        }
        planRecursiveDelete(projectRoot, entry, strategy, files)
        emptyDirectoryCandidates.add(entry)
      }
    }
  }

  for (legacyRoot in output.legacyGeneratedRoots.sortedByDescending { it.nameCount }) {
    if (!Files.exists(legacyRoot)) {
      continue
    }
    planRecursiveDelete(projectRoot, legacyRoot, strategy, files)
    emptyDirectoryCandidates.add(legacyRoot)
  }

  return ModuleSetPluginCleanupResult(
    files = files,
    emptyDirectoryCandidates = emptyDirectoryCandidates + output.emptyDirectoryCandidates,
  )
}

internal fun cleanupGeneratedArtifactDirectories(directories: Collection<Path>) {
  for (directory in directories.distinct().sortedByDescending { it.nameCount }) {
    if (!Files.exists(directory) || !Files.isDirectory(directory)) {
      continue
    }

    val nestedDirectories = Files.walk(directory).use { stream ->
      stream
        .filter { Files.isDirectory(it) }
        .sorted(Comparator.reverseOrder())
        .toList()
    }
    for (candidate in nestedDirectories) {
      if (!Files.exists(candidate) || !Files.isDirectory(candidate)) {
        continue
      }
      Files.newDirectoryStream(candidate).use { entries ->
        if (!entries.iterator().hasNext()) {
          Files.deleteIfExists(candidate)
        }
      }
    }
  }
}

private fun collectPluginWrappers(moduleSets: List<ModuleSet>, generatedRoot: Path): LinkedHashMap<String, ModuleSetPluginWrapper> {
  val wrappers = LinkedHashMap<String, ModuleSetPluginWrapper>()
  for (moduleSet in collectPluginizedModuleSets(moduleSets)) {
    val moduleName = moduleSetPluginModuleName(moduleSet.name).value
    val contentModules = collectPluginContentModules(moduleSet)
    val moduleDir = generatedRoot.resolve(moduleName)
    wrappers[moduleName] = ModuleSetPluginWrapper(
      moduleSet = moduleSet,
      moduleName = moduleName,
      moduleDir = moduleDir,
      imlPath = moduleDir.resolve("$moduleName.iml"),
      pluginXmlPath = moduleDir.resolve(MODULE_SET_PLUGIN_XML_PATH),
      pluginContentYamlPath = moduleDir.resolve(MODULE_SET_PLUGIN_CONTENT_YAML_PATH),
      contentModules = contentModules,
    )
  }
  return wrappers
}

private data class ModuleSetPluginWrapper(
  val moduleSet: ModuleSet,
  val moduleName: String,
  val moduleDir: Path,
  val imlPath: Path,
  val pluginXmlPath: Path,
  val pluginContentYamlPath: Path,
  val contentModules: List<ContentModule>,
)

private data class ModuleSetPluginMainModule(
  val moduleName: String,
  val imlPath: Path,
)

private data class ModulesXmlTarget(
  val modulesXmlPath: Path,
  val modulesRoot: Path,
  val managedGeneratedRoots: List<Path>,
  val moduleImlPaths: List<Path>,
)

private data class ModuleEntry(
  val filepath: String,
  val line: String,
)

private fun generateWrapperFiles(
  projectRoot: Path,
  strategy: FileUpdateStrategy,
  wrapper: ModuleSetPluginWrapper,
  files: MutableList<ModuleSetPluginFileResult>,
  emptyDirectoryCandidates: MutableSet<Path>,
) {
  recordFileResult(projectRoot, files, wrapper.imlPath, strategy.updateIfChanged(wrapper.imlPath, renderWrapperIml()))
  recordFileResult(projectRoot, files, wrapper.pluginXmlPath, strategy.updateIfChanged(wrapper.pluginXmlPath, renderPluginXml(wrapper.moduleSet, wrapper.contentModules)))
  recordFileResult(projectRoot, files, wrapper.pluginContentYamlPath, strategy.updateIfChanged(wrapper.pluginContentYamlPath, renderPluginContentYaml(wrapper)))
  cleanupLegacyBundleArtifacts(projectRoot, strategy, wrapper, files, emptyDirectoryCandidates)
}

private fun generateMainModule(
  projectRoot: Path,
  strategy: FileUpdateStrategy,
  generatedRoot: Path,
  wrappers: List<ModuleSetPluginWrapper>,
  files: MutableList<ModuleSetPluginFileResult>,
): ModuleSetPluginMainModule? {
  val wrappersToAdd = wrappers.filter { wrapper ->
    requireNotNull(wrapper.moduleSet.pluginSpec).addToMainModule
  }
  if (wrappersToAdd.isEmpty()) {
    return null
  }

  val moduleName = MODULE_SET_PLUGIN_MAIN_MODULE_NAME
  val imlPath = generatedRoot.resolve(moduleName).resolve("$moduleName.iml")

  val runtimeDependencies = LinkedHashSet<String>()
  for (wrapper in wrappersToAdd.sortedBy { it.moduleName }) {
    runtimeDependencies.add(wrapper.moduleName)
    for (contentModule in wrapper.contentModules.sortedBy { it.name.value }) {
      runtimeDependencies.add(contentModule.name.value)
    }
  }

  recordFileResult(projectRoot, files, imlPath, strategy.updateIfChanged(imlPath, renderMainModuleIml(runtimeDependencies.toList().sorted())))
  return ModuleSetPluginMainModule(moduleName = moduleName, imlPath = imlPath)
}

private fun collectPluginContentModules(moduleSet: ModuleSet): List<ContentModule> {
  val result = LinkedHashMap<String, ContentModule>()

  fun visit(current: ModuleSet) {
    if (current !== moduleSet && current.pluginSpec != null) {
      return
    }
    for (module in current.modules) {
      result.putIfAbsent(module.name.value, module)
    }
    for (nested in current.nestedSets) {
      visit(nested)
    }
  }

  visit(moduleSet)
  return result.values.toList()
}

private fun renderWrapperIml(): String {
  return buildString {
    appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
    appendLine("<module type=\"JAVA_MODULE\" version=\"4\">")
    appendLine("  <component name=\"NewModuleRootManager\" inherit-compiler-output=\"true\">")
    appendLine("    <exclude-output />")
    appendLine("    <content url=\"file://$MODULE_DIR_MACRO\">")
    appendLine("      <sourceFolder url=\"file://$MODULE_DIR_MACRO/resources\" type=\"java-resource\" />")
    appendLine("    </content>")
    appendLine("    <orderEntry type=\"inheritedJdk\" />")
    appendLine("    <orderEntry type=\"sourceFolder\" forTests=\"false\" />")
    appendLine("  </component>")
    appendLine("</module>")
  }.removeSuffix("\n")
}

private fun renderMainModuleIml(runtimeDependencies: List<String>): String {
  return buildString {
    appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
    appendLine("<module type=\"JAVA_MODULE\" version=\"4\">")
    appendLine("  <component name=\"NewModuleRootManager\" inherit-compiler-output=\"true\">")
    appendLine("    <exclude-output />")
    appendLine("    <orderEntry type=\"inheritedJdk\" />")
    appendLine("    <orderEntry type=\"sourceFolder\" forTests=\"false\" />")
    for (moduleName in runtimeDependencies) {
      appendLine("    <orderEntry type=\"module\" module-name=\"$moduleName\" scope=\"RUNTIME\" />")
    }
    appendLine("  </component>")
    appendLine("</module>")
  }.removeSuffix("\n")
}

private fun renderPluginXml(moduleSet: ModuleSet, contentModules: List<ContentModule>): String {
  val pluginId = resolveModuleSetPluginId(moduleSet).value
  val displayName = toDisplayName(moduleSet.name)
  val description = "Generated plugin wrapper for module set ${moduleSet.name}."
  val aliases = collectAliases(moduleSet)
  val sortedModules = contentModules.sortedBy { it.name.value }

  return buildString {
    appendLine("<!-- DO NOT EDIT: This file is auto-generated from moduleSet(\"${moduleSet.name}\") -->")
    appendLine("<!-- To regenerate, run: `Generate Product Layouts` or `bazel run //platform/buildScripts:plugin-model-tool` -->")
    appendLine("<idea-plugin implementation-detail=\"true\">")
    appendLine("  <id>$pluginId</id>")
    appendLine("  <name>$displayName</name>")
    appendLine("  <description>$description</description>")
    appendLine("  <vendor>JetBrains</vendor>")

    for (alias in aliases) {
      appendLine("  <module value=\"${alias.value}\"/>")
    }

    appendLine("  <content namespace=\"jetbrains\">")
    for (module in sortedModules) {
      append("    <module name=\"${module.name.value}\"")
      when (module.loading) {
        ModuleLoadingRuleValue.EMBEDDED -> append(" loading=\"embedded\"")
        ModuleLoadingRuleValue.REQUIRED -> append(" loading=\"required\"")
        else -> Unit
      }
      appendLine("/>")
    }
    appendLine("  </content>")
    appendLine("</idea-plugin>")
  }
}

private fun renderPluginContentYaml(wrapper: ModuleSetPluginWrapper): String {
  val contentEntries = wrapper.contentModules
    .map { contentModule ->
      val jarPath = "lib/modules/${contentModule.name.value}.jar"
      jarPath to contentModule.name.value
    }
    .sortedBy { (jarPath, _) -> jarPath }

  return buildString {
    appendLine("- name: lib/${moduleSetPluginJarFileName(wrapper.moduleSet.name)}")
    appendLine("  modules:")
    appendLine("  - name: ${wrapper.moduleName}")
    for ((jarPath, moduleName) in contentEntries) {
      appendLine("- name: $jarPath")
      appendLine("  contentModules:")
      appendLine("  - name: $moduleName")
    }
  }.removeSuffix("\n")
}

private fun moduleSetPluginJarFileName(moduleSetName: String): String {
  return "moduleSet-plugin-${moduleSetName.replace('.', '-')}" + ".jar"
}

private fun cleanupLegacyBundleArtifacts(
  projectRoot: Path,
  strategy: FileUpdateStrategy,
  wrapper: ModuleSetPluginWrapper,
  files: MutableList<ModuleSetPluginFileResult>,
  emptyDirectoryCandidates: MutableSet<Path>,
) {
  val legacyBundleFile = wrapper.moduleDir.resolve(MODULE_SET_PLUGIN_LEGACY_BUNDLE_PATH)
  if (Files.exists(legacyBundleFile)) {
    strategy.delete(legacyBundleFile)
    files.add(ModuleSetPluginFileResult(relativePath = relativePath(projectRoot, legacyBundleFile), status = FileChangeStatus.DELETED))
  }

  legacyBundleFile.parent?.let(emptyDirectoryCandidates::add)
}

private fun toDisplayName(moduleSetName: String): String {
  val withSpaces = moduleSetName
    .replace('.', ' ')
    .replace('-', ' ')
    .replace('_', ' ')
    .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
  return withSpaces
    .split(Regex("\\s+"))
    .filter { it.isNotEmpty() }
    .joinToString(" ") { token ->
      token.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}

private fun collectAliases(moduleSet: ModuleSet): List<PluginId> {
  val result = LinkedHashSet<PluginId>()

  fun visit(current: ModuleSet) {
    val alias = current.alias
    if (alias != null) {
      result.add(alias)
    }
    for (nested in current.nestedSets) {
      if (nested.pluginSpec != null) {
        continue
      }
      visit(nested)
    }
  }

  visit(moduleSet)
  return result.toList().sortedBy { it.value }
}

private fun updateModulesXml(
  projectRoot: Path,
  strategy: FileUpdateStrategy,
  rootModuleImlPaths: List<Path>,
  communityModuleImlPaths: List<Path>,
  files: MutableList<ModuleSetPluginFileResult>,
) {
  val communityGeneratedRoot = projectRoot.resolve(COMMUNITY_MODULE_SET_PLUGIN_GENERATED_ROOT)
  val ultimateGeneratedRoot = projectRoot.resolve(ULTIMATE_MODULE_SET_PLUGIN_GENERATED_ROOT)
  val legacyGeneratedRoots = MODULE_SET_PLUGIN_LEGACY_GENERATED_ROOTS.map(projectRoot::resolve)
  val targets = listOf(
    ModulesXmlTarget(
      modulesXmlPath = projectRoot.resolve(".idea/modules.xml"),
      modulesRoot = projectRoot,
      managedGeneratedRoots = listOf(communityGeneratedRoot, ultimateGeneratedRoot) + legacyGeneratedRoots,
      moduleImlPaths = rootModuleImlPaths,
    ),
    ModulesXmlTarget(
      modulesXmlPath = projectRoot.resolve("community/.idea/modules.xml"),
      modulesRoot = projectRoot.resolve("community"),
      managedGeneratedRoots = listOf(communityGeneratedRoot) + legacyGeneratedRoots,
      moduleImlPaths = communityModuleImlPaths,
    ),
  )
  for (target in targets) {
    updateModulesXmlFile(projectRoot, target, strategy, files)
  }
}

private fun updateModulesXmlFile(
  projectRoot: Path,
  target: ModulesXmlTarget,
  strategy: FileUpdateStrategy,
  files: MutableList<ModuleSetPluginFileResult>,
) {
  if (!Files.exists(target.modulesXmlPath) || !Files.isDirectory(target.modulesRoot)) {
    return
  }

  val currentContent = Files.readString(target.modulesXmlPath)
  val lines = Files.readAllLines(target.modulesXmlPath)
  val modulesStart = lines.indexOfFirst { it.trim() == "<modules>" }
  if (modulesStart == -1) {
    return
  }
  val modulesEnd = lines.indexOfFirst { it.trim() == "</modules>" }
  if (modulesEnd == -1 || modulesEnd <= modulesStart) {
    return
  }

  val moduleIndent = lines
    .subList(modulesStart + 1, modulesEnd)
    .firstOrNull { it.contains("<module ") }
    ?.takeWhile { it == ' ' || it == '\t' }
    ?: "      "

  val existingEntries = lines
    .subList(modulesStart + 1, modulesEnd)
    .mapNotNull(::parseModuleEntry)

  val generatedPrefixes = collectGeneratedPrefixes(target)
  val mergedEntries = LinkedHashMap<String, ModuleEntry>()
  for (entry in existingEntries) {
    if (generatedPrefixes.any { entry.filepath.startsWith(it) }) {
      continue
    }
    mergedEntries.putIfAbsent(entry.filepath, entry)
  }

  for (moduleImlPath in target.moduleImlPaths) {
    val requiredPath = "$PROJECT_DIR_MACRO/${target.modulesRoot.relativize(moduleImlPath).invariantSeparatorsPathString}"
    mergedEntries[requiredPath] = ModuleEntry(
      filepath = requiredPath,
      line = "$moduleIndent<module fileurl=\"file://$requiredPath\" filepath=\"$requiredPath\" />",
    )
  }

  val sortedEntries = mergedEntries.values.sortedWith(compareBy({ moduleNameFromFilepath(it.filepath) }, { it.filepath }))
  val updatedLines = ArrayList<String>(lines.size - (modulesEnd - modulesStart - 1) + sortedEntries.size)
  updatedLines.addAll(lines.subList(0, modulesStart + 1))
  updatedLines.addAll(sortedEntries.map { it.line })
  updatedLines.addAll(lines.subList(modulesEnd, lines.size))

  val updatedContent = updatedLines.joinToString(separator = "\n")
  recordFileResult(projectRoot, files, target.modulesXmlPath, strategy.writeIfChanged(target.modulesXmlPath, currentContent, updatedContent))
}

private fun collectGeneratedPrefixes(target: ModulesXmlTarget): List<String> {
  return target.managedGeneratedRoots
    .map { generatedRoot ->
      val relativeGeneratedRoot = target.modulesRoot.relativize(generatedRoot)
      "$PROJECT_DIR_MACRO/${relativeGeneratedRoot.invariantSeparatorsPathString}/"
    }
    .distinct()
}

private fun parseModuleEntry(line: String): ModuleEntry? {
  if (!line.contains("<module ")) {
    return null
  }
  val filepath = FILEPATH_ATTRIBUTE_REGEX.find(line)?.groupValues?.get(1) ?: return null
  return ModuleEntry(filepath = filepath, line = line)
}

private fun moduleNameFromFilepath(filepath: String): String {
  return filepath.substringAfterLast('/').removeSuffix(".iml")
}

private fun planRecursiveDelete(
  projectRoot: Path,
  path: Path,
  strategy: FileUpdateStrategy,
  files: MutableList<ModuleSetPluginFileResult>,
) {
  if (!Files.exists(path)) {
    return
  }

  if (Files.isRegularFile(path)) {
    strategy.delete(path)
    files.add(ModuleSetPluginFileResult(relativePath = relativePath(projectRoot, path), status = FileChangeStatus.DELETED))
    return
  }

  Files.walk(path).use { stream ->
    val filePaths = stream
      .filter { Files.isRegularFile(it) }
      .sorted(Comparator.comparing { it.invariantSeparatorsPathString })
      .toList()
    for (filePath in filePaths) {
      strategy.delete(filePath)
      files.add(ModuleSetPluginFileResult(relativePath = relativePath(projectRoot, filePath), status = FileChangeStatus.DELETED))
    }
  }
}

private fun recordFileResult(
  projectRoot: Path,
  files: MutableList<ModuleSetPluginFileResult>,
  path: Path,
  status: FileChangeStatus,
) {
  files.add(ModuleSetPluginFileResult(relativePath = relativePath(projectRoot, path), status = status))
}

private fun relativePath(projectRoot: Path, path: Path): String {
  return projectRoot.relativize(path).invariantSeparatorsPathString
}
