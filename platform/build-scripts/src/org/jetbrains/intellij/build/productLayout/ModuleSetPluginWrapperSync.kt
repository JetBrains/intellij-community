// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.build.productLayout

import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.io.path.createDirectories
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

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

fun syncModuleSetPluginsOnDisk(
  projectRoot: Path,
  communityModuleSets: List<ModuleSet>,
  ultimateModuleSets: List<ModuleSet> = emptyList(),
) {
  val communityGeneratedRoot = projectRoot.resolve(COMMUNITY_MODULE_SET_PLUGIN_GENERATED_ROOT)
  val ultimateGeneratedRoot = projectRoot.resolve(ULTIMATE_MODULE_SET_PLUGIN_GENERATED_ROOT)
  val communityWrappers = collectPluginWrappers(communityModuleSets, communityGeneratedRoot)
  val ultimateWrappers = collectPluginWrappers(ultimateModuleSets, ultimateGeneratedRoot)

  val duplicateWrapperModuleNames = communityWrappers.keys.intersect(ultimateWrappers.keys)
  check(duplicateWrapperModuleNames.isEmpty()) {
    "Module-set plugin wrappers must be unique across community and ultimate registries: ${duplicateWrapperModuleNames.sorted().joinToString()}"
  }

  val ultimateWrappersAddedToMainModule = ultimateWrappers.values
    .filter { requireNotNull(it.moduleSet.pluginSpec).addToMainModule }
    .map { it.moduleSet.name }
    .sorted()
  check(ultimateWrappersAddedToMainModule.isEmpty()) {
    "Ultimate module-set plugin wrappers cannot use addToMainModule=true while intellij.moduleSet.plugin.main remains community-only: ${ultimateWrappersAddedToMainModule.joinToString()}"
  }

  for (wrapper in communityWrappers.values + ultimateWrappers.values) {
    syncWrapperFiles(wrapper)
  }

  val mainModule = syncMainModuleFile(generatedRoot = communityGeneratedRoot, wrappers = communityWrappers.values.toList())
  val requiredCommunityModuleNames = LinkedHashSet(communityWrappers.keys)
  if (mainModule != null) {
    requiredCommunityModuleNames.add(mainModule.moduleName)
  }

  cleanupOrphanWrappers(communityGeneratedRoot, requiredCommunityModuleNames)
  cleanupOrphanWrappers(ultimateGeneratedRoot, ultimateWrappers.keys)
  cleanupLegacyGeneratedRoots(projectRoot)
  syncModulesXml(
    projectRoot = projectRoot,
    rootModuleImlPaths = communityWrappers.values.map { it.imlPath } + ultimateWrappers.values.map { it.imlPath } + listOfNotNull(mainModule?.imlPath),
    communityModuleImlPaths = communityWrappers.values.map { it.imlPath } + listOfNotNull(mainModule?.imlPath),
  )
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
  val moduleDir: Path,
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

private fun syncWrapperFiles(wrapper: ModuleSetPluginWrapper) {
  wrapper.moduleDir.createDirectories()
  wrapper.pluginXmlPath.parent.createDirectories()

  writeIfChanged(wrapper.imlPath, renderWrapperIml())
  writeIfChanged(wrapper.pluginXmlPath, renderPluginXml(wrapper.moduleSet, wrapper.contentModules))
  writeIfChanged(wrapper.pluginContentYamlPath, renderPluginContentYaml(wrapper))
  cleanupLegacyBundleArtifacts(wrapper)
}

private fun syncMainModuleFile(generatedRoot: Path, wrappers: List<ModuleSetPluginWrapper>): ModuleSetPluginMainModule? {
  val wrappersToAdd = wrappers.filter { wrapper ->
    requireNotNull(wrapper.moduleSet.pluginSpec).addToMainModule
  }
  if (wrappersToAdd.isEmpty()) {
    return null
  }

  val moduleName = MODULE_SET_PLUGIN_MAIN_MODULE_NAME
  val moduleDir = generatedRoot.resolve(moduleName)
  val mainModule = ModuleSetPluginMainModule(
    moduleName = moduleName,
    moduleDir = moduleDir,
    imlPath = moduleDir.resolve("$moduleName.iml"),
  )

  val runtimeDependencies = LinkedHashSet<String>()
  for (wrapper in wrappersToAdd.sortedBy { it.moduleName }) {
    runtimeDependencies.add(wrapper.moduleName)
    for (contentModule in wrapper.contentModules.sortedBy { it.name.value }) {
      runtimeDependencies.add(contentModule.name.value)
    }
  }

  mainModule.moduleDir.createDirectories()
  writeIfChanged(mainModule.imlPath, renderMainModuleIml(runtimeDependencies.toList().sorted()))
  return mainModule
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

private fun cleanupLegacyBundleArtifacts(wrapper: ModuleSetPluginWrapper) {
  val legacyBundleFile = wrapper.moduleDir.resolve(MODULE_SET_PLUGIN_LEGACY_BUNDLE_PATH)
  if (Files.exists(legacyBundleFile)) {
    Files.delete(legacyBundleFile)
  }

  val messagesDir = legacyBundleFile.parent
  if (messagesDir != null && Files.isDirectory(messagesDir)) {
    Files.newDirectoryStream(messagesDir).use { entries ->
      if (!entries.iterator().hasNext()) {
        Files.delete(messagesDir)
      }
    }
  }
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

private fun cleanupOrphanWrappers(generatedRoot: Path, requiredModuleNames: Set<String>) {
  if (!Files.exists(generatedRoot)) {
    return
  }

  Files.newDirectoryStream(generatedRoot).use { entries ->
    for (entry in entries) {
      if (entry.name !in requiredModuleNames) {
        deleteRecursively(entry)
      }
    }
  }
}

private fun cleanupLegacyGeneratedRoots(projectRoot: Path) {
  for (legacyRoot in MODULE_SET_PLUGIN_LEGACY_GENERATED_ROOTS) {
    deleteRecursively(projectRoot.resolve(legacyRoot))
  }
}

private fun syncModulesXml(projectRoot: Path, rootModuleImlPaths: List<Path>, communityModuleImlPaths: List<Path>) {
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
    syncModulesXmlFile(target = target)
  }
}

private fun syncModulesXmlFile(target: ModulesXmlTarget) {
  if (!Files.exists(target.modulesXmlPath) || !Files.isDirectory(target.modulesRoot)) {
    return
  }

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

  val sortedEntries = mergedEntries.values.sortedWith(
    compareBy({ moduleNameFromFilepath(it.filepath) }, { it.filepath }),
  )
  val updatedLines = ArrayList<String>(lines.size - (modulesEnd - modulesStart - 1) + sortedEntries.size)
  updatedLines.addAll(lines.subList(0, modulesStart + 1))
  updatedLines.addAll(sortedEntries.map { it.line })
  updatedLines.addAll(lines.subList(modulesEnd, lines.size))

  writeIfChanged(target.modulesXmlPath, updatedLines.joinToString(separator = "\n"))
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

private fun deleteRecursively(path: Path) {
  if (!Files.exists(path)) {
    return
  }
  Files.walk(path).use { stream ->
    stream.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
  }
}

private fun writeIfChanged(path: Path, newContent: String) {
  val existing = if (Files.exists(path)) path.readText() else null
  if (existing == newContent) {
    return
  }
  path.parent?.createDirectories()
  path.writeText(newContent)
}
