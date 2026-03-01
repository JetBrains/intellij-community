// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("GrazieInspection", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.PluginId
import org.jetbrains.intellij.build.productLayout.TestPluginSpec
import org.jetbrains.intellij.build.productLayout.buildContentBlocksAndChainMapping
import org.jetbrains.intellij.build.productLayout.config.ContentModuleSuppression
import org.jetbrains.intellij.build.productLayout.config.SuppressionConfig
import org.jetbrains.intellij.build.productLayout.deps.ContentModuleDependencyPlan
import org.jetbrains.intellij.build.productLayout.deps.buildAllowedMissingByModule
import org.jetbrains.intellij.build.productLayout.discovery.findProductPropertiesSourceFile
import org.jetbrains.intellij.build.productLayout.model.error.MissingContentModulePluginDependencyError
import org.jetbrains.intellij.build.productLayout.model.error.MissingContentModulePluginDependencySuppressionKind
import org.jetbrains.intellij.build.productLayout.model.error.ProposedPatch
import org.jetbrains.intellij.build.productLayout.model.error.ValidationError
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.GenerationModel
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import org.jetbrains.intellij.build.productLayout.util.DeferredFileUpdater
import org.jetbrains.intellij.build.productLayout.xml.updateXmlDependencies
import java.nio.file.Files
import java.nio.file.Path

/**
 * Content module plugin dependency validation.
 *
 * Purpose: Ensure content module XMLs declare plugin dependencies for main plugin modules from IML.
 * Inputs: `Slots.CONTENT_MODULE_PLAN`, plugin graph, suppression config.
 * Output: `MissingContentModulePluginDependencyError`.
 * Auto-fix: none.
 *
 * Glossary: docs/validators/README.md.
 * Spec: docs/validators/plugin-content-dependency.md.
 */
internal object ContentModulePluginDependencyValidator : PipelineNode {
  override val id get() = NodeIds.CONTENT_MODULE_PLUGIN_DEPENDENCY_VALIDATION

  override val requires: Set<DataSlot<*>> get() = setOf(Slots.CONTENT_MODULE_PLAN)

  override suspend fun execute(ctx: ComputeContext) {
    val model = ctx.model
    val plans = ctx.get(Slots.CONTENT_MODULE_PLAN).plans
    val dslDeclaredModules = HashSet<ContentModuleName>()
    val dslModuleAllowedMissing = HashMap<ContentModuleName, MutableSet<PluginId>>()
    for (specs in model.dslTestPluginsByProduct.values) {
      for (spec in specs) {
        val contentData = buildContentBlocksAndChainMapping(spec.spec, collectModuleSetAliases = false)
        for (contentBlock in contentData.contentBlocks) {
          for (module in contentBlock.modules) {
            dslDeclaredModules.add(module.name)
          }
        }
        val allowedByModule = buildAllowedMissingByModule(contentData)
        for ((moduleName, pluginIds) in allowedByModule) {
          dslModuleAllowedMissing.computeIfAbsent(moduleName) { LinkedHashSet() }.addAll(pluginIds)
        }
      }
    }

    val baseErrors =
      validateContentModulePluginDependencies(
        contentModulePlans = plans,
        pluginGraph = model.pluginGraph,
        allowedMissing = model.suppressionConfig.getAllowedMissingPluginsMap(),
        dslDeclaredModules = dslDeclaredModules,
        dslModuleAllowedMissing = dslModuleAllowedMissing,
      )
    val plansByModule = plans.associateBy { it.contentModuleName }
    val errorsWithPatches = baseErrors.map { error ->
      if (error !is MissingContentModulePluginDependencyError) {
        return@map error
      }

      val plan = plansByModule[error.contentModuleName] ?: return@map error
      val proposedPatches = buildProposedPatchesForMissingPluginDependency(
        error = error,
        plan = plan,
        model = model,
      )
      if (proposedPatches.isEmpty()) error else error.copy(proposedPatches = proposedPatches)
    }

    ctx.emitErrors(errorsWithPatches)
  }
}

/**
 * Validates that content module XML files declare all plugin dependencies from IML.
 *
 * Catches the case where a content module has a compile dependency on a main plugin module
 * in its `.iml` file, but the generated XML doesn't have the corresponding `<plugin id="..."/>` dependency.
 *
 * @param contentModulePlans Results from content module dependency planning
 * @param pluginGraph Graph used to resolve containing plugins for modules
 * @param allowedMissing Map of content module name to set of allowed missing plugin IDs
 * @return List of validation errors for modules with missing plugin deps
 */
private fun validateContentModulePluginDependencies(
  contentModulePlans: List<ContentModuleDependencyPlan>,
  pluginGraph: PluginGraph,
  allowedMissing: Map<ContentModuleName, Set<PluginId>> = emptyMap(),
  dslDeclaredModules: Set<ContentModuleName> = emptySet(),
  dslModuleAllowedMissing: Map<ContentModuleName, Set<PluginId>> = emptyMap(),
): List<ValidationError> {
  if (contentModulePlans.isEmpty()) {
    return emptyList()
  }

  return pluginGraph.query {
    val errors = ArrayList<ValidationError>()

    fun collectContainingPluginIds(moduleName: ContentModuleName): Set<PluginId> {
      val moduleNode = contentModule(moduleName)
                       ?: return emptySet()
      val ids = HashSet<PluginId>()
      moduleNode.owningPlugins { plugin ->
        val pluginId = plugin.pluginIdOrNull ?: return@owningPlugins
        ids.add(pluginId)
      }
      return ids
    }

    for (plan in contentModulePlans) {
      val moduleName = plan.contentModuleName

      val writtenPluginDeps = plan.writtenPluginDependencies.toHashSet()
      val allJpsPluginDeps = plan.allJpsPluginDependencies

      val isDslDeclaredModule = moduleName in dslDeclaredModules
      val allowedForModule = if (isDslDeclaredModule) {
        dslModuleAllowedMissing.get(moduleName) ?: emptySet()
      }
      else {
        allowedMissing.get(moduleName) ?: emptySet()
      }

      // Planner suppressions represent intentionally omitted XML plugin deps
      // (including --update-suppressions auto-capture) and must not fail validation.
      val plannerSuppressedForModule = plan.suppressedPlugins

      val candidateMissing = allJpsPluginDeps - writtenPluginDeps - allowedForModule - plannerSuppressedForModule
      if (candidateMissing.isEmpty()) {
        continue
      }

      // Exclude ALL containing plugins - content modules have an implicit dependency on them
      val missingPluginDeps = candidateMissing - collectContainingPluginIds(moduleName)
      if (missingPluginDeps.isNotEmpty()) {
        val suppressionKind = if (isDslDeclaredModule) {
          MissingContentModulePluginDependencySuppressionKind.DSL_MODULE_ALLOWED_MISSING
        }
        else {
          MissingContentModulePluginDependencySuppressionKind.SUPPRESSIONS_JSON
        }
        errors.add(MissingContentModulePluginDependencyError(
          context = plan.contentModuleName.value,
          contentModuleName = plan.contentModuleName,
          missingPluginIds = missingPluginDeps,
          suppressionKind = suppressionKind,
        ))
      }
    }

    errors
  }
}

private fun buildProposedPatchesForMissingPluginDependency(
  error: MissingContentModulePluginDependencyError,
  plan: ContentModuleDependencyPlan,
  model: GenerationModel,
): List<ProposedPatch> {
  val patches = ArrayList<ProposedPatch>(2)
  buildContentModuleDescriptorPatch(error = error, plan = plan, projectRoot = model.projectRoot)?.let(patches::add)

  when (error.suppressionKind) {
    MissingContentModulePluginDependencySuppressionKind.DSL_MODULE_ALLOWED_MISSING -> {
      (buildDslAllowedMissingPatch(error, model) ?: buildDslAllowedMissingSnippetPatch(error)).let(patches::add)
    }

    MissingContentModulePluginDependencySuppressionKind.SUPPRESSIONS_JSON -> {
      if (!model.updateSuppressions) {
        (buildSuppressionsJsonPatch(error, model) ?: buildSuppressionsJsonSnippetPatch(error)).let(patches::add)
      }
    }
  }

  return patches
}

private fun buildContentModuleDescriptorPatch(
  error: MissingContentModulePluginDependencyError,
  plan: ContentModuleDependencyPlan,
  projectRoot: Path,
): ProposedPatch? {
  val descriptorPath = plan.descriptorPath
  val descriptorContent = plan.descriptorContent.ifEmpty {
    if (Files.exists(descriptorPath)) Files.readString(descriptorPath) else return null
  }

  val updater = DeferredFileUpdater(projectRoot)
  updateXmlDependencies(
    path = descriptorPath,
    content = descriptorContent,
    moduleDependencies = plan.existingXmlModuleDependencies.map { it.value }.sorted(),
    pluginDependencies = (plan.existingXmlPluginDependencies + error.missingPluginIds).map { it.value }.sorted(),
    allowInsideSectionRegion = false,
    strategy = updater,
  )

  val diff = updater.getDiffs().firstOrNull { it.path == descriptorPath } ?: return null
  val patchPath = toPatchPath(projectRoot, descriptorPath)
  val patch = buildWholeFilePatch(
    relativePath = patchPath,
    oldContent = diff.actualContent,
    newContent = diff.expectedContent,
  ) ?: return null

  return ProposedPatch(
    title = "Add missing plugin dependencies to $patchPath",
    patch = patch,
  )
}

private fun buildSuppressionsJsonPatch(
  error: MissingContentModulePluginDependencyError,
  model: GenerationModel,
): ProposedPatch? {
  val suppressionsPath = model.config.suppressionConfigPath ?: return null
  val oldContent = if (Files.exists(suppressionsPath)) Files.readString(suppressionsPath) else ""

  val existingSuppression = model.suppressionConfig.contentModules[error.contentModuleName] ?: ContentModuleSuppression()
  val updatedSuppression = existingSuppression.copy(
    suppressPlugins = existingSuppression.suppressPlugins + error.missingPluginIds,
  )
  val updatedConfig = model.suppressionConfig.copy(
    contentModules = model.suppressionConfig.contentModules + (error.contentModuleName to updatedSuppression),
  )
  val newContent = SuppressionConfig.serializeToString(updatedConfig)
  if (newContent == oldContent) {
    return null
  }

  val patchPath = toPatchPath(model.projectRoot, suppressionsPath)
  val patch = buildWholeFilePatch(
    relativePath = patchPath,
    oldContent = oldContent,
    newContent = newContent,
  ) ?: return null

  return ProposedPatch(
    title = "Add suppressPlugins for ${error.contentModuleName.value} in $patchPath",
    patch = patch,
  )
}

private data class DslModuleDeclaration(
  val sourceFile: String,
  val startLine: Int,
  val declarationText: String,
)

private data class DslCallMatch(
  val startOffset: Int,
  val endOffsetInclusive: Int,
)

private fun buildDslAllowedMissingPatch(
  error: MissingContentModulePluginDependencyError,
  model: GenerationModel,
): ProposedPatch? {
  val declaration = findDslModuleDeclaration(
    model = model,
    contentModuleName = error.contentModuleName,
  ) ?: return null

  val updatedDeclaration = withAllowedMissingPluginIds(
    declarationText = declaration.declarationText,
    missingPluginIds = error.missingPluginIds,
  ) ?: return null
  if (updatedDeclaration == declaration.declarationText) {
    return null
  }

  val oldLines = splitContentLines(declaration.declarationText)
  val newLines = splitContentLines(updatedDeclaration)

  return ProposedPatch(
    title = "Add allowedMissingPluginIds for ${error.contentModuleName.value} in ${declaration.sourceFile}",
    patch = buildBlockReplacementPatch(
      relativePath = declaration.sourceFile,
      startLine = declaration.startLine,
      oldLines = oldLines,
      newLines = newLines,
    ),
  )
}

private fun findDslModuleDeclaration(
  model: GenerationModel,
  contentModuleName: ContentModuleName,
): DslModuleDeclaration? {
  val candidateSourceFiles = collectDslCandidateSourceFiles(model, contentModuleName)
  if (candidateSourceFiles.isEmpty()) {
    return null
  }

  val declarations = ArrayList<DslModuleDeclaration>()
  for (sourceFile in candidateSourceFiles) {
    val sourcePath = model.projectRoot.resolve(sourceFile)
    if (!Files.exists(sourcePath)) {
      continue
    }

    val fileContent = Files.readString(sourcePath)
    for (match in findDslModuleCallMatches(fileContent, contentModuleName)) {
      declarations.add(
        DslModuleDeclaration(
          sourceFile = sourceFile,
          startLine = lineNumberAtOffset(fileContent, match.startOffset),
          declarationText = fileContent.substring(match.startOffset, match.endOffsetInclusive + 1),
        )
      )
    }
  }

  return when (declarations.size) {
    0 -> null
    1 -> declarations.single()
    else -> {
      val withoutAllowed = declarations.filterNot { it.declarationText.contains("allowedMissingPluginIds") }
      if (withoutAllowed.size == 1) withoutAllowed.single() else null
    }
  }
}

private fun collectDslCandidateSourceFiles(
  model: GenerationModel,
  contentModuleName: ContentModuleName,
): List<String> {
  val candidates = LinkedHashSet<String>()
  val productsByName = model.discovery.products.associateBy { it.name }
  for ((productName, specs) in model.dslTestPluginsByProduct) {
    if (specs.none { specContainsModule(it, contentModuleName) }) {
      continue
    }

    val product = productsByName[productName] ?: continue
    val properties = product.properties ?: continue
    val sourceFile = try {
      findProductPropertiesSourceFile(
        buildModules = product.config.modules,
        productPropertiesClass = properties.javaClass,
        outputProvider = model.outputProvider,
        projectRoot = model.projectRoot,
      )
    }
    catch (_: Exception) {
      continue
    }

    val sourcePath = model.projectRoot.resolve(sourceFile)
    if (Files.exists(sourcePath)) {
      candidates.add(sourceFile.replace('\\', '/'))
    }
  }

  for ((sourceObj, _) in model.discovery.moduleSetSources.values) {
    findDslSourceFileByClassName(
      projectRoot = model.projectRoot,
      className = sourceObj.javaClass.name,
    )?.let(candidates::add)
  }

  return candidates.toList()
}

private fun findDslSourceFileByClassName(projectRoot: Path, className: String): String? {
  val packageName = className.substringBeforeLast('.', "")
  val simpleName = className.substringAfterLast('.')
  val relativePath = if (packageName.isEmpty()) {
    "$simpleName.kt"
  }
  else {
    "${packageName.replace('.', '/')}/$simpleName.kt"
  }

  val roots = listOf(
    projectRoot.resolve("community/platform/build-scripts/src"),
    projectRoot.resolve("platform/buildScripts/src"),
    projectRoot.resolve("rider/build/src"),
    projectRoot.resolve("python/build/src"),
  )

  for (root in roots) {
    val candidate = root.resolve(relativePath)
    if (Files.exists(candidate)) {
      return projectRoot.relativize(candidate).toString().replace('\\', '/')
    }
  }

  return null
}

private fun findDslModuleCallMatches(
  fileContent: String,
  contentModuleName: ContentModuleName,
): List<DslCallMatch> {
  val callStartRegex = Regex("""(?m)^[ \t]*(?:module|requiredModule|embeddedModule)\s*\(""")
  val matches = ArrayList<DslCallMatch>()
  var searchIndex = 0
  while (searchIndex < fileContent.length) {
    val startMatch = callStartRegex.find(fileContent, searchIndex) ?: break
    val openParenIndex = fileContent.indexOf('(', startMatch.range.first)
    if (openParenIndex < 0) {
      searchIndex = startMatch.range.last + 1
      continue
    }

    val closeParenIndex = findMatchingParen(fileContent, openParenIndex)
    if (closeParenIndex == null) {
      searchIndex = startMatch.range.last + 1
      continue
    }

    val declarationText = fileContent.substring(startMatch.range.first, closeParenIndex + 1)
    if (isModuleDeclarationCallForModule(declarationText, contentModuleName.value)) {
      matches.add(DslCallMatch(startMatch.range.first, closeParenIndex))
    }
    searchIndex = closeParenIndex + 1
  }
  return matches
}

private fun specContainsModule(
  spec: TestPluginSpec,
  contentModuleName: ContentModuleName,
): Boolean {
  val contentData = buildContentBlocksAndChainMapping(spec.spec, collectModuleSetAliases = false)
  for (block in contentData.contentBlocks) {
    if (block.modules.any { it.name == contentModuleName }) {
      return true
    }
  }
  return false
}

private fun findMatchingParen(text: String, openParenIndex: Int): Int? {
  var depth = 0
  var index = openParenIndex
  while (index < text.length) {
    val ch = text[index]

    if (ch == '"') {
      index = skipStringLiteral(text, index)
      if (index < 0) {
        return null
      }
      continue
    }

    if (ch == '/' && index + 1 < text.length) {
      val next = text[index + 1]
      if (next == '/') {
        index = skipLineComment(text, index + 2)
        continue
      }
      if (next == '*') {
        index = skipBlockComment(text, index + 2)
        if (index < 0) {
          return null
        }
        continue
      }
    }

    when (ch) {
      '(' -> depth++
      ')' -> {
        depth--
        if (depth == 0) {
          return index
        }
      }
    }
    index++
  }
  return null
}

private fun skipStringLiteral(text: String, quoteIndex: Int): Int {
  var index = quoteIndex + 1
  var escaped = false
  while (index < text.length) {
    val ch = text[index]
    if (escaped) {
      escaped = false
      index++
      continue
    }
    if (ch == '\\') {
      escaped = true
      index++
      continue
    }
    if (ch == '"') {
      return index + 1
    }
    index++
  }
  return -1
}

private fun skipLineComment(text: String, startIndex: Int): Int {
  var index = startIndex
  while (index < text.length && text[index] != '\n') {
    index++
  }
  return index
}

private fun skipBlockComment(text: String, startIndex: Int): Int {
  var index = startIndex
  while (index + 1 < text.length) {
    if (text[index] == '*' && text[index + 1] == '/') {
      return index + 2
    }
    index++
  }
  return -1
}

private fun isModuleDeclarationCallForModule(declarationText: String, moduleName: String): Boolean {
  val openParenIndex = declarationText.indexOf('(')
  if (openParenIndex < 0) {
    return false
  }

  var index = skipWhitespaceAndComments(declarationText, openParenIndex + 1)
  if (index >= declarationText.length || declarationText[index] != '"') {
    return false
  }
  index++

  val parsedName = StringBuilder()
  var escaped = false
  while (index < declarationText.length) {
    val ch = declarationText[index]
    if (escaped) {
      parsedName.append(ch)
      escaped = false
      index++
      continue
    }

    if (ch == '\\') {
      escaped = true
      index++
      continue
    }

    if (ch == '"') {
      return parsedName.toString() == moduleName
    }

    parsedName.append(ch)
    index++
  }

  return false
}

private fun skipWhitespaceAndComments(text: String, startIndex: Int): Int {
  var index = startIndex
  while (index < text.length) {
    val ch = text[index]
    if (ch.isWhitespace()) {
      index++
      continue
    }

    if (ch == '/' && index + 1 < text.length) {
      val next = text[index + 1]
      if (next == '/') {
        index = skipLineComment(text, index + 2)
        continue
      }
      if (next == '*') {
        index = skipBlockComment(text, index + 2)
        if (index < 0) {
          return text.length
        }
        continue
      }
    }

    break
  }
  return index
}

private fun withAllowedMissingPluginIds(
  declarationText: String,
  missingPluginIds: Set<PluginId>,
): String? {
  val missingIds = missingPluginIds.map { it.value }.sorted()
  if (missingIds.isEmpty()) {
    return declarationText
  }

  val existingAllowedRegex = Regex(
    """allowedMissingPluginIds\s*=\s*listOf\(([^)]*)\)""",
    setOf(RegexOption.DOT_MATCHES_ALL),
  )
  val existingAllowed = existingAllowedRegex.find(declarationText)
  if (existingAllowed != null) {
    val existingIds = Regex(""""([^"]+)"""")
      .findAll(existingAllowed.groupValues[1])
      .map { it.groupValues[1] }
      .toSet()
    val mergedIds = (existingIds + missingIds).sorted()
    val replacement = "allowedMissingPluginIds = listOf(${mergedIds.joinToString { "\"$it\"" }})"
    return declarationText.replaceRange(existingAllowed.range, replacement)
  }

  val insertionText = "allowedMissingPluginIds = listOf(${missingIds.joinToString { "\"$it\"" }})"
  if (!declarationText.contains('\n')) {
    val closeParenIndex = declarationText.lastIndexOf(')')
    if (closeParenIndex < 0) {
      return null
    }
    return declarationText.substring(0, closeParenIndex) + ", $insertionText" + declarationText.substring(closeParenIndex)
  }

  val lines = splitContentLines(declarationText).toMutableList()
  val closingLineIndex = lines.indexOfLast { it.contains(')') }
  if (closingLineIndex < 0) {
    return null
  }

  val previousNonBlankLine = (closingLineIndex - 1 downTo 0).firstOrNull { lines[it].trim().isNotEmpty() }
  if (previousNonBlankLine != null && !lines[previousNonBlankLine].trimEnd().endsWith(',')) {
    lines[previousNonBlankLine] = lines[previousNonBlankLine].trimEnd() + ","
  }

  val argumentIndent = if (previousNonBlankLine != null) {
    lines[previousNonBlankLine].takeWhile { ch -> ch == ' ' || ch == '\t' }
  }
  else {
    val closingIndent = lines[closingLineIndex].takeWhile { ch -> ch == ' ' || ch == '\t' }
    "$closingIndent  "
  }

  lines.add(closingLineIndex, "$argumentIndent$insertionText,")
  return lines.joinToString("\n")
}

private fun lineNumberAtOffset(content: String, offset: Int): Int {
  if (offset <= 0) {
    return 1
  }
  return content.asSequence().take(offset).count { it == '\n' } + 1
}

private fun buildSuppressionsJsonSnippetPatch(error: MissingContentModulePluginDependencyError): ProposedPatch {
  val snippet = "\"${error.contentModuleName.value}\": { \"suppressPlugins\": [${error.missingPluginIds.sortedBy { it.value }.joinToString { "\"${it.value}\"" }}] }"
  return ProposedPatch(
    title = "Suppression snippet for suppressions.json",
    patch = snippet,
  )
}

private fun buildDslAllowedMissingSnippetPatch(error: MissingContentModulePluginDependencyError): ProposedPatch {
  val snippet = "module(\"${error.contentModuleName.value}\", allowedMissingPluginIds = listOf(${error.missingPluginIds.sortedBy { it.value }.joinToString { "\"${it.value}\"" }}))"
  return ProposedPatch(
    title = "Suppression snippet for DSL module declaration",
    patch = snippet,
  )
}

private fun buildBlockReplacementPatch(
  relativePath: String,
  startLine: Int,
  oldLines: List<String>,
  newLines: List<String>,
): String {
  return buildUnifiedPatch(
    relativePath = relativePath,
    oldStart = startLine,
    oldLines = oldLines,
    newStart = startLine,
    newLines = newLines,
  )
}

private fun buildWholeFilePatch(
  relativePath: String,
  oldContent: String,
  newContent: String,
): String? {
  if (oldContent == newContent) {
    return null
  }

  val oldLines = splitContentLines(oldContent)
  val newLines = splitContentLines(newContent)
  val oldStart = if (oldLines.isEmpty()) 0 else 1
  val newStart = if (newLines.isEmpty()) 0 else 1

  return buildUnifiedPatch(
    relativePath = relativePath,
    oldStart = oldStart,
    oldLines = oldLines,
    newStart = newStart,
    newLines = newLines,
  )
}

private fun buildUnifiedPatch(
  relativePath: String,
  oldStart: Int,
  oldLines: List<String>,
  newStart: Int,
  newLines: List<String>,
): String {
  return buildString {
    appendLine("--- a/$relativePath")
    appendLine("+++ b/$relativePath")
    appendLine("@@ -$oldStart,${oldLines.size} +$newStart,${newLines.size} @@")
    for (line in oldLines) {
      appendLine("-$line")
    }
    for (line in newLines) {
      appendLine("+$line")
    }
  }.trimEnd()
}

private fun splitContentLines(content: String): List<String> {
  if (content.isEmpty()) {
    return emptyList()
  }

  val normalized = content.replace("\r\n", "\n").replace("\r", "\n")
  return normalized.split('\n')
}

private fun toPatchPath(projectRoot: Path, path: Path): String {
  val normalizedRoot = projectRoot.toAbsolutePath().normalize()
  val normalizedPath = if (path.isAbsolute) path.normalize() else normalizedRoot.resolve(path).normalize()
  return try {
    normalizedRoot.relativize(normalizedPath).toString().replace('\\', '/')
  }
  catch (_: IllegalArgumentException) {
    normalizedPath.toString().replace('\\', '/')
  }
}
