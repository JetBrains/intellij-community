// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "GrazieInspection", "GrazieStyle")

package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.TargetName
import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue
import org.jetbrains.intellij.build.productLayout.MODULE_SET_PREFIX
import org.jetbrains.intellij.build.productLayout.buildContentBlocksAndChainMapping
import org.jetbrains.intellij.build.productLayout.discovery.PluginContentInfo
import org.jetbrains.intellij.build.productLayout.discovery.findProductPropertiesSourceFile
import org.jetbrains.intellij.build.productLayout.model.ModuleSourceInfo
import org.jetbrains.intellij.build.productLayout.model.error.PluginDependencyError
import org.jetbrains.intellij.build.productLayout.model.error.ProposedPatch
import org.jetbrains.intellij.build.productLayout.model.error.ValidationError
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.GenerationModel
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import org.jetbrains.intellij.build.productLayout.util.FileUpdateStrategy
import org.jetbrains.intellij.build.productLayout.validator.rule.isStructurallyAllowed
import java.nio.file.Files
import java.nio.file.Path

/**
 * Structural validation for plugin content modules.
 *
 * Purpose: Ensure loading-mode constraints are respected within a plugin.
 * Inputs: `Slots.CONTENT_MODULE_PLAN`, plugin graph.
 * Output: `PluginDependencyError` with structural violations only.
 * Auto-fix: Updates non-DSL plugin.xml loading for violations.
 *
 * Glossary: docs/validators/README.md.
 * Spec: docs/validators/plugin-content-structure.md.
 */
internal object PluginContentStructureValidator : PipelineNode {
  override val id get() = NodeIds.PLUGIN_CONTENT_STRUCTURE_VALIDATION

  // Requires CONTENT_MODULE_PLAN to ensure graph has module dependency edges populated.
  override val requires: Set<DataSlot<*>> get() = setOf(Slots.CONTENT_MODULE_PLAN)

  override suspend fun execute(ctx: ComputeContext) {
    val model = ctx.model

    val autoAddedModulesByPluginId = model.dslTestPluginDependencyChains.mapValues { it.value.keys }
    val autoAddLoadingMode = model.config.dslTestPluginAutoAddLoadingMode
    val structuralErrors = validatePluginContentStructure(
      pluginGraph = model.pluginGraph,
      autoAddedModulesByPluginId = autoAddedModulesByPluginId,
      autoAddLoadingMode = autoAddLoadingMode,
    )

    val errorsWithPatches = structuralErrors.map { error ->
      if (error !is PluginDependencyError || error.structuralViolations.isEmpty()) {
        return@map error
      }

      val pluginInfo = model.pluginContentCache.getOrExtract(error.pluginName)
      val proposedPatches = when {
        pluginInfo?.isDslDefined == true -> buildDslStructuralViolationPatches(
          pluginName = error.pluginName,
          violations = error.structuralViolations,
          model = model,
        )
        pluginInfo != null -> listOfNotNull(buildStructuralViolationPatch(
          pluginName = error.pluginName,
          violations = error.structuralViolations,
          pluginContentInfo = pluginInfo,
          projectRoot = model.projectRoot,
        ))
        else -> emptyList()
      }

      if (proposedPatches.isEmpty()) error else error.copy(proposedPatches = proposedPatches)
    }

    // Apply structural violation fixes for non-DSL plugins
    val fixedPlugins = LinkedHashSet<TargetName>()
    for (error in errorsWithPatches.filterIsInstance<PluginDependencyError>()) {
      val violations = error.structuralViolations
      if (violations.isEmpty()) continue

      val pluginInfo = model.pluginContentCache.getOrExtract(error.pluginName)
      val isDslDefined = model.pluginGraph.query { plugin(error.pluginName.value)?.isDslDefined == true }
      if (!isDslDefined) {
        fixStructuralViolations(
          violations = violations,
          pluginContentInfo = pluginInfo,
          strategy = model.xmlWritePolicy,
        )
        fixedPlugins.add(error.pluginName)
      }
    }

    ctx.emitErrors(errorsWithPatches.filterNot { error ->
      error is PluginDependencyError && error.pluginName in fixedPlugins
    })
  }
}

private fun validatePluginContentStructure(
  pluginGraph: PluginGraph,
  autoAddedModulesByPluginId: Map<PluginId, Set<ContentModuleName>>,
  autoAddLoadingMode: ModuleLoadingRuleValue,
): List<ValidationError> {
  return pluginGraph.query {
    val result = ArrayList<ValidationError>()
    plugins { plugin ->
      if (plugin.pluginIdOrNull == null) return@plugins

      val pluginName = plugin.name()
      val isTestPlugin = plugin.isTest
      val autoAddedModules = autoAddedModulesByPluginId[plugin.pluginIdOrNull] ?: emptySet()
      val skipAutoAddedStructuralCheck = isTestPlugin &&
                                         plugin.isDslDefined &&
                                         (autoAddLoadingMode == ModuleLoadingRuleValue.OPTIONAL ||
                                          autoAddLoadingMode == ModuleLoadingRuleValue.ON_DEMAND)

      val snapshot = collectPluginContentSnapshot(plugin)
      val loadingModes = snapshot.loadingModes
      val bundlingProducts = snapshot.bundlingProducts
      val contentModulesForValidation = snapshot.contentModulesForValidation(isTestPlugin)

      if (contentModulesForValidation.isEmpty()) {
        return@plugins
      }

      val contentModuleDeps = collectContentModuleDeps(contentModulesForValidation, isTestPlugin)

      val structuralViolationsByModule = LinkedHashMap<ContentModuleName, MutableSet<ContentModuleName>>()
      for ((moduleName, deps) in contentModuleDeps) {
        val contentLoading = loadingModes.get(moduleName)
        for (dep in deps) {
          if (dep !in contentModulesForValidation) continue
          if (skipAutoAddedStructuralCheck && dep in autoAddedModules) continue
          val depLoading = loadingModes.get(dep)
          if (!isStructurallyAllowed(contentLoading, depLoading)) {
            structuralViolationsByModule.computeIfAbsent(moduleName) { LinkedHashSet() }.add(dep)
          }
        }
      }

      if (structuralViolationsByModule.isEmpty()) {
        return@plugins
      }

      val modulesForInfo = structuralViolationsByModule.keys + structuralViolationsByModule.values.flatten()
      val moduleSourceInfo = buildStructuralModuleSourceInfo(
        modules = modulesForInfo,
        loadingModes = loadingModes,
        pluginName = pluginName,
        isTestPlugin = isTestPlugin,
        bundlingProducts = bundlingProducts,
      )

      result.add(PluginDependencyError(
        context = pluginName.value,
        pluginName = pluginName,
        missingDependencies = emptyMap(),
        moduleSourceInfo = moduleSourceInfo,
        unresolvedByProduct = emptyMap(),
        filteredDependencies = emptyMap(),
        structuralViolations = structuralViolationsByModule,
        ruleName = NodeIds.PLUGIN_CONTENT_STRUCTURE_VALIDATION.name,
      ))
    }
    result
  }
}

private fun buildStructuralModuleSourceInfo(
  modules: Set<ContentModuleName>,
  loadingModes: Map<ContentModuleName, ModuleLoadingRuleValue?>,
  pluginName: TargetName,
  isTestPlugin: Boolean,
  bundlingProducts: Set<String>,
): Map<ContentModuleName, ModuleSourceInfo> {
  val result = HashMap<ContentModuleName, ModuleSourceInfo>()
  for (moduleName in modules) {
    result.put(moduleName, ModuleSourceInfo(
      loadingMode = loadingModes.get(moduleName),
      sourcePlugin = pluginName,
      isTestPlugin = isTestPlugin,
      bundledInProducts = bundlingProducts,
    ))
  }
  return result
}

/**
 * Fixes structural violations by updating loading modes in plugin.xml files.
 *
 * When a REQUIRED module depends on an OPTIONAL/unspecified sibling, the fix is to
 * change the dependency's loading to REQUIRED.
 *
 * @param violations Map of content module name -> set of dependency module names with violations
 * @param pluginContentInfo Plugin content info
 * @param strategy File update strategy for generating diffs
 */
private fun fixStructuralViolations(
  violations: Map<ContentModuleName, Set<ContentModuleName>>,
  pluginContentInfo: PluginContentInfo?,
  strategy: FileUpdateStrategy,
) {
  if (violations.isEmpty() || pluginContentInfo == null) {
    return
  }

  val pluginXmlPath = pluginContentInfo.pluginXmlPath
  val currentContent = pluginContentInfo.pluginXmlContent.ifEmpty {
    if (Files.exists(pluginXmlPath)) Files.readString(pluginXmlPath) else return
  }

  // Apply fixes - deps are in values (map is: module -> deps)
  val depsToFix = violations.values.flatMapTo(HashSet()) { deps -> deps.map { it.value } }
  val fixedContent = applyLoadingModeFixes(currentContent, depsToFix)

  strategy.writeIfChanged(pluginXmlPath, currentContent, fixedContent)
}

private fun buildStructuralViolationPatch(
  pluginName: TargetName,
  violations: Map<ContentModuleName, Set<ContentModuleName>>,
  pluginContentInfo: PluginContentInfo,
  projectRoot: Path,
): ProposedPatch? {
  if (violations.isEmpty()) return null

  val depsToFix = violations.values.flatMapTo(LinkedHashSet()) { deps -> deps.map { it.value } }
  if (depsToFix.isEmpty()) return null

  val pluginXmlPath = pluginContentInfo.pluginXmlPath
  val currentContent = pluginContentInfo.pluginXmlContent.ifEmpty {
    if (Files.exists(pluginXmlPath)) Files.readString(pluginXmlPath) else return null
  }

  val fixedContent = applyLoadingModeFixes(currentContent, depsToFix)
  if (fixedContent == currentContent) return null

  val relativePath = try {
    projectRoot.relativize(pluginXmlPath).toString().replace('\\', '/')
  }
  catch (_: IllegalArgumentException) {
    pluginXmlPath.toString().replace('\\', '/')
  }
  val patch = buildLineReplacementPatch(relativePath, currentContent, fixedContent) ?: return null

  return ProposedPatch(
    title = "${pluginName.value} ($relativePath)",
    patch = patch,
  )
}

private fun buildDslStructuralViolationPatches(
  pluginName: TargetName,
  violations: Map<ContentModuleName, Set<ContentModuleName>>,
  model: GenerationModel,
): List<ProposedPatch> {
  val (productName, testPluginSpec) = findDslTestPluginSpec(pluginName, model) ?: return emptyList()
  val product = model.discovery.products.firstOrNull { it.name == productName } ?: return emptyList()
  val productProperties = product.properties ?: return emptyList()

  val sourceFile = try {
    findProductPropertiesSourceFile(
      buildModules = product.config.modules,
      productPropertiesClass = productProperties.javaClass,
      outputProvider = model.outputProvider,
      projectRoot = model.projectRoot,
    )
  }
  catch (_: Exception) {
    return emptyList()
  }

  val sourcePath = model.projectRoot.resolve(sourceFile)
  if (!Files.exists(sourcePath)) return emptyList()

  val patches = ArrayList<ProposedPatch>()
  val modulesToFix = violations.values.flatten().mapTo(LinkedHashSet()) { it.value }
  if (modulesToFix.isEmpty()) return emptyList()

  val contentData = buildContentBlocksAndChainMapping(testPluginSpec.spec, collectModuleSetAliases = false)
  val currentLoading = HashMap<ContentModuleName, ModuleLoadingRuleValue>()
  for (block in contentData.contentBlocks) {
    for (module in block.modules) {
      currentLoading.put(module.name, module.loading)
    }
  }

  val additionalModulesByName = testPluginSpec.spec.additionalModules.associateBy { it.name }
  val directModulesToFix = LinkedHashSet<String>()
  val moduleSetsToFix = LinkedHashMap<String, MutableSet<String>>()

  for (moduleName in modulesToFix) {
    val contentName = ContentModuleName(moduleName)
    val loading = currentLoading.get(contentName)
    if (loading == ModuleLoadingRuleValue.REQUIRED || loading == ModuleLoadingRuleValue.EMBEDDED) {
      continue
    }

    if (additionalModulesByName.containsKey(contentName)) {
      directModulesToFix.add(moduleName)
      continue
    }

    val chain = contentData.moduleToSetChainMapping.get(contentName) ?: continue
    if (chain.isEmpty()) continue
    val setName = chain.last().removePrefix(MODULE_SET_PREFIX)
    moduleSetsToFix.computeIfAbsent(setName) { LinkedHashSet() }.add(moduleName)
  }

  val directPatch = buildRequiredModulePatch(
    sourceFile = sourceFile,
    sourcePath = sourcePath,
    modulesToFix = directModulesToFix,
    title = "${pluginName.value} ($sourceFile)",
  )
  if (directPatch != null) {
    patches.add(directPatch)
  }

  if (moduleSetsToFix.isNotEmpty()) {
    for ((moduleSetName, modules) in moduleSetsToFix) {
      val moduleSetSourceFile = findModuleSetSourceFile(moduleSetName, model) ?: continue
      val moduleSetSourcePath = model.projectRoot.resolve(moduleSetSourceFile)
      if (!Files.exists(moduleSetSourcePath)) continue
      val patch = buildRequiredModulePatch(
        sourceFile = moduleSetSourceFile,
        sourcePath = moduleSetSourcePath,
        modulesToFix = modules,
        title = "$moduleSetName ($moduleSetSourceFile)",
        functionName = moduleSetFunctionName(moduleSetName),
      )
      if (patch != null) {
        patches.add(patch)
      }
    }
  }

  return patches
}

private fun findDslTestPluginSpec(
  pluginName: TargetName,
  model: GenerationModel,
): Pair<String, org.jetbrains.intellij.build.productLayout.TestPluginSpec>? {
  val pluginId = PluginId(pluginName.value)
  for ((productName, specs) in model.dslTestPluginsByProduct) {
    val match = specs.firstOrNull { it.pluginId == pluginId } ?: continue
    return productName to match
  }
  return null
}

private fun buildRequiredModulePatch(
  sourceFile: String,
  sourcePath: Path,
  modulesToFix: Set<String>,
  title: String,
  functionName: String? = null,
): ProposedPatch? {
  if (modulesToFix.isEmpty()) return null

  val originalLines = Files.readAllLines(sourcePath)
  val updatedLines = originalLines.toMutableList()
  val targetRange = functionName?.let { findFunctionBlockRange(originalLines, it) }

  var changed = false
  for (moduleName in modulesToFix) {
    val lineIndex = findModuleCallLineIndex(originalLines, moduleName, targetRange) ?: continue
    val line = updatedLines[lineIndex]
    if (line.contains("requiredModule(") || line.contains("embeddedModule(")) {
      continue
    }
    updatedLines[lineIndex] = replaceModuleCallWithRequired(line)
    changed = true
  }

  if (!changed) return null

  val updatedContent = updatedLines.joinToString("\n")
  val originalContent = originalLines.joinToString("\n")
  val normalizedPath = sourceFile.replace('\\', '/')
  val patch = buildLineReplacementPatch(normalizedPath, originalContent, updatedContent) ?: return null

  return ProposedPatch(
    title = title,
    patch = patch,
  )
}

private fun findModuleCallLineIndex(
  lines: List<String>,
  moduleName: String,
  range: IntRange?,
): Int? {
  val pattern = Regex("\\bmodule\\s*\\(\\s*\"${Regex.escape(moduleName)}\"")
  val indices = lines.withIndex()
    .filter { (index, line) ->
      if (range != null && index !in range) return@filter false
      pattern.containsMatchIn(line)
    }
    .map { it.index }
  return if (indices.size == 1) indices.single() else null
}

private fun findFunctionBlockRange(lines: List<String>, functionName: String): IntRange? {
  val pattern = Regex("\\bfun\\s+$functionName\\b")
  val start = lines.indexOfFirst { pattern.containsMatchIn(it) }
  if (start < 0) return null

  var braceCount = 0
  var started = false
  for (i in start until lines.size) {
    for (ch in lines[i]) {
      if (ch == '{') {
        braceCount++
        started = true
      }
      else if (ch == '}') {
        braceCount--
      }
    }
    if (started && braceCount == 0) {
      return start..i
    }
  }
  return null
}

private fun replaceModuleCallWithRequired(line: String): String {
  val pattern = Regex("\\bmodule(\\s*\\()")
  return pattern.replaceFirst(line, "requiredModule$1")
}

private fun moduleSetFunctionName(setName: String): String? {
  val parts = setName.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }
  if (parts.isEmpty()) return null
  val head = parts.first().replaceFirstChar { it.lowercase() }
  val tail = parts.drop(1).joinToString("") { part ->
    part.replaceFirstChar { it.uppercase() }
  }
  return head + tail
}

private fun findModuleSetSourceFile(moduleSetName: String, model: GenerationModel): String? {
  val label = model.discovery.moduleSetsByLabel.entries.firstOrNull { (_, sets) ->
    sets.any { it.name == moduleSetName }
  }?.key ?: return null
  val sourceObj = model.discovery.moduleSetSources[label]?.first ?: return null

  val className = sourceObj.javaClass.name
  val packageName = className.substringBeforeLast('.', "")
  val simpleName = className.substringAfterLast('.')
  val relativePath = if (packageName.isEmpty()) {
    "$simpleName.kt"
  }
  else {
    "${packageName.replace('.', '/')}/$simpleName.kt"
  }

  val roots = listOf(
    model.projectRoot.resolve("community/platform/build-scripts/src"),
    model.projectRoot.resolve("platform/buildScripts/src"),
  )

  for (root in roots) {
    val candidate = root.resolve(relativePath)
    if (Files.exists(candidate)) {
      return model.projectRoot.relativize(candidate).toString().replace('\\', '/')
    }
  }

  return null
}

private fun buildLineReplacementPatch(
  relativePath: String,
  currentContent: String,
  fixedContent: String,
): String? {
  val oldLines = normalizeContentLines(currentContent)
  val newLines = normalizeContentLines(fixedContent)
  if (oldLines.size != newLines.size) return null

  val changeRanges = ArrayList<IntRange>()
  var index = 0
  while (index < oldLines.size) {
    if (oldLines[index] == newLines[index]) {
      index++
      continue
    }
    val start = index
    while (index < oldLines.size && oldLines[index] != newLines[index]) {
      index++
    }
    changeRanges.add(start until index)
  }

  if (changeRanges.isEmpty()) return null

  val patch = StringBuilder()
  patch.appendLine("--- a/$relativePath")
  patch.appendLine("+++ b/$relativePath")
  for (range in changeRanges) {
    val length = range.last - range.first + 1
    patch.appendLine("@@ -${range.first + 1},$length +${range.first + 1},$length @@")
    for (idx in range) {
      patch.appendLine("-${oldLines[idx]}")
      patch.appendLine("+${newLines[idx]}")
    }
  }
  return patch.toString().trimEnd()
}

private fun normalizeContentLines(content: String): List<String> {
  val normalized = content.replace("\r\n", "\n").replace("\r", "\n")
  return normalized.split('\n')
}

/**
 * Applies loading mode fixes to plugin.xml content by setting loading="required"
 * on modules that need it.
 *
 * Uses string-based replacement to preserve comments, CDATA sections, and formatting.
 */
internal fun applyLoadingModeFixes(content: String, modulesToFix: Set<String>): String {
  if (modulesToFix.isEmpty()) {
    return content
  }

  var result = content
  for (moduleName in modulesToFix) {
    val escapedName = Regex.escape(moduleName)

    // Case 1: Module has loading attribute - replace it with "required"
    val withLoadingPattern = Regex(
      """(<module\s+name="$escapedName"\s+)loading="[^"]*"(\s*/?>)"""
    )
    if (withLoadingPattern.containsMatchIn(result)) {
      result = withLoadingPattern.replace(result) { match ->
        "${match.groupValues[1]}loading=\"required\"${match.groupValues[2]}"
      }
      continue
    }

    // Case 2: Module has no loading attribute - add loading="required" after name
    val noLoadingPattern = Regex(
      """(<module\s+name="$escapedName")(\s*/?>)"""
    )
    result = noLoadingPattern.replace(result) { match ->
      "${match.groupValues[1]} loading=\"required\"${match.groupValues[2]}"
    }
  }
  return result
}
