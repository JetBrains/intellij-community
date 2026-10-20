// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.networknt.wrapper

import com.intellij.openapi.progress.ProgressManager
import com.networknt.schema.Error
import com.networknt.schema.Schema
import com.networknt.schema.SchemaContext
import com.networknt.schema.SchemaLocation
import com.networknt.schema.SpecificationVersion
import com.networknt.schema.path.NodePath
import tools.jackson.databind.JsonNode

private val unsupportedSelectorKeywords =
  listOf("allOf", "anyOf", "oneOf", "not", "if", "then", "else", "dependencies", "dependentSchemas")

private val nonValidatingRefKeywords =
  setOf($$"$ref", $$"$id", $$"$schema", $$"$defs", "definitions", $$"$comment", "title", "description")

/**
 * Hides misleading errors from `anyOf`/`oneOf` alternatives when a nested schema reference is unavailable.
 *
 * Suppresses such errors only when a property value identifies a matching branch.
 * Keeps errors in that branch. Keeps alternative errors when the branch choice is unclear.
 *
 * Examples: [NetworkntErrorMapperTest](../../../../../../tests/test/com/intellij/json/networknt/wrapper/NetworkntErrorMapperTest.kt).
 * - `test nested unresolved ref suppresses alternative repo errors`
 * - `test unresolved ref preserves a real error in the same branch`
 */
internal fun filterIndeterminateCompositionErrors(errors: Collection<Error>, schema: Schema): Collection<Error> {
  if (errors.size > NetworkntErrorMapper.getRawErrorCap()) return errors
  if (errors.any { it.evaluationPath == null || it.instanceLocation == null }) return errors
  val groups = mutableMapOf<CompositionInstance, CompositionErrors>()
  for (error in errors) {
    ProgressManager.checkCanceled()
    val parsed = parseErrorPath(error)
    for ((composition, index, schemaPath) in parsed.branches) {
      val group = groups.getOrPut(composition) { CompositionErrors() }
      group.errors.add(error)
      group.blocked = group.blocked || !parsed.complete
      val branchErrors = group.branches.getOrPut(index) { BranchErrors(schemaPath) }
      branchErrors.errors.add(error)
      if (error.keyword == $$"$ref") {
        branchErrors.hasNestedRef = true
        if (!parsed.hasOnlyPositiveApplicators || error.instanceLocation == composition.instanceLocation) {
          branchErrors.hasUnsupportedRef = true
        }
      }
      if (parsed.hasOnlyPositiveApplicators
          && (error.keyword == "enum" || error.keyword == "const" || error.keyword == "pattern")) {
        val location = error.instanceLocation
        if (location.parent == composition.instanceLocation && location.getElement(-1) is String) {
          branchErrors.selectorErrors.add(error)
        }
      }
    }
    parsed.composition?.let {
      val group = groups.getOrPut(it) { CompositionErrors() }
      group.errors.add(error)
      group.compositionErrors.add(error)
    }
  }

  val suppressed = mutableSetOf<Error>()
  for (group in groups.values) {
    ProgressManager.checkCanceled()
    if (group.blocked || group.errors.any { it.messageKey == "oneOf.indexes" }) continue
    val sources = mutableMapOf<Int, CachedSchemaNode>()
    for ((index, branch) in group.branches) {
      ProgressManager.checkCanceled()
      if (!branch.hasNestedRef || branch.hasUnsupportedRef) continue
      if (branch.selectorErrors.any { it.keyword == "enum" || it.keyword == "const" }) continue
      val candidate = resolveCachedBranch(schema, branch.schemaPath) ?: continue
      if (group.branches.values.any { sibling -> sibling.selectorErrors.any { matchesSelector(candidate, it, branch.errors) } }) {
        sources[index] = candidate
      }
    }
    val rejectedBranches = mutableSetOf<Int>()
    for ((index, candidateSchema) in sources) {
      ProgressManager.checkCanceled()
      val sourceErrors = group.branches.getValue(index).errors
      for ((siblingIndex, sibling) in group.branches) {
        if (siblingIndex in sources) continue
        if (sibling.selectorErrors.any { matchesSelector(candidateSchema, it, sourceErrors) }) {
          suppressed.addAll(sibling.errors)
          rejectedBranches.add(siblingIndex)
        }
      }
    }
    if (rejectedBranches.isNotEmpty() && group.branches.all { (index, branch) ->
        index in rejectedBranches || branch.errors.all { it.keyword == $$"$ref" }
      }) {
      suppressed.addAll(group.compositionErrors.filter { it.messageKey != "oneOf.indexes" })
    }
  }
  return errors.filter { it !in suppressed }
}

private data class CompositionInstance(val evaluationPath: NodePath, val instanceLocation: NodePath)

private data class CompositionBranch(
  val composition: CompositionInstance,
  val index: Int,
  val schemaPath: SchemaStep,
)

private data class SchemaStep(val parent: SchemaStep?, val keyword: String, val argument: Any?)

private class BranchErrors(val schemaPath: SchemaStep) {
  val errors = mutableListOf<Error>()
  val selectorErrors = mutableListOf<Error>()
  var hasNestedRef = false
  var hasUnsupportedRef = false
}

private class CompositionErrors {
  val errors = mutableListOf<Error>()
  val compositionErrors = mutableListOf<Error>()
  val branches = mutableMapOf<Int, BranchErrors>()
  var blocked = false
}

private class ParsedErrorPath {
  val branches = mutableListOf<CompositionBranch>()
  var composition: CompositionInstance? = null
  var complete = false
  var hasOnlyPositiveApplicators = true
}

private fun parseErrorPath(error: Error): ParsedErrorPath {
  val result = ParsedErrorPath()
  val evaluation = generateSequence(error.evaluationPath) { it.parent }.toList().asReversed()
  val instance = generateSequence(error.instanceLocation) { it.parent }.toList().asReversed()
  if (evaluation.isEmpty() || instance.isEmpty()) return result
  var schemaIndex = 1
  var instanceIndex = 0
  var schemaPath: SchemaStep? = null

  fun descend(property: Boolean, expected: Any? = null): Boolean {
    val next = instance.getOrNull(++instanceIndex)?.getElement(-1) ?: return false
    return (if (property) next is String else next is Int) && (expected == null || next == expected)
  }

  while (schemaIndex < evaluation.lastIndex) {
    val keywordPath = evaluation[schemaIndex]
    val keyword = keywordPath.getElement(-1) as? String ?: return result
    val argument = evaluation.getOrNull(schemaIndex + 1)?.getElement(-1)
    var schemaArgument: Any? = null
    var branchIndex: Int? = null
    when (keyword) {
      "properties" -> {
        if (argument !is String || !descend(property = true, expected = argument)) return result
        schemaArgument = argument
        schemaIndex += 2
      }
      "patternProperties" -> {
        if (argument !is String || !descend(property = true)) return result
        schemaArgument = argument
        schemaIndex += 2
      }
      "items" -> {
        if (argument is Int && argument < 0) return result
        if (!descend(property = false, expected = argument as? Int)) return result
        schemaArgument = argument as? Int
        schemaIndex += if (argument is Int) 2 else 1
      }
      "prefixItems" -> {
        if (argument !is Int || argument < 0 || !descend(property = false, expected = argument)) return result
        schemaArgument = argument
        schemaIndex += 2
      }
      "additionalProperties", "unevaluatedProperties" -> {
        if (!descend(property = true)) return result
        schemaIndex++
      }
      "additionalItems", "unevaluatedItems" -> {
        if (!descend(property = false)) return result
        schemaIndex++
      }
      "allOf", "anyOf", "oneOf" -> {
        if (argument !is Int || argument < 0) return result
        schemaArgument = argument
        if (keyword != "allOf") branchIndex = argument
        schemaIndex += 2
      }
      $$"$ref" -> schemaIndex++
      $$"$dynamicRef", $$"$recursiveRef" -> return result
      "not", "if", "then", "else" -> {
        result.hasOnlyPositiveApplicators = false
        schemaIndex++
      }
      "dependencies", "dependentSchemas" -> {
        if (argument !is String) return result
        schemaArgument = argument
        result.hasOnlyPositiveApplicators = false
        schemaIndex += 2
      }
      "contains" -> {
        if (!descend(property = false)) return result
        result.hasOnlyPositiveApplicators = false
        schemaIndex++
      }
      else -> return result
    }
    schemaPath = SchemaStep(schemaPath, keyword, schemaArgument)
    if (branchIndex != null) {
      result.branches.add(CompositionBranch(
        CompositionInstance(keywordPath, instance[instanceIndex]), branchIndex, schemaPath,
      ))
    }
  }
  if (schemaIndex != evaluation.lastIndex || instanceIndex != instance.lastIndex) return result
  val terminalKeyword = evaluation.last().getElement(-1)
  val containsBounds = terminalKeyword == "contains" && (error.keyword == "minContains" || error.keyword == "maxContains")
  if (terminalKeyword != error.keyword && !containsBounds) return result
  result.complete = true
  if (error.keyword == "anyOf" || error.keyword == "oneOf") {
    result.composition = CompositionInstance(evaluation.last(), instance.last())
  }
  return result
}

private fun matchesSelector(candidate: CachedSchemaNode, error: Error, branchErrors: Collection<Error>): Boolean {
  val actual = error.instanceNode?.takeIf { it.isString } ?: return false
  val property = error.instanceLocation.getElement(-1) as? String ?: return false
  if (branchErrors.any { rejectsSelectorProperty(it, error.instanceLocation, property) }) return false
  val propertySchema = candidate.node.get("properties")?.get(property) ?: return false
  if (propertySchema.has($$"$schema")) return false
  if (hasUnsupportedSelectorContext(propertySchema)) return false
  if (propertySchema.has($$"$ref") || propertySchema.has($$"$dynamicRef") || propertySchema.has($$"$recursiveRef")) return false
  val constant = propertySchema.get("const")
  val version = candidate.context.dialect.specificationVersion
  if (version != null && version.order >= SpecificationVersion.DRAFT_6.order
      && constant != null && constant.isString && constant == actual) return true
  val enumeration = propertySchema.get("enum") ?: return false
  return enumeration.isArray && enumeration.any { it.isString && it == actual }
}

private fun rejectsSelectorProperty(error: Error, location: NodePath, property: String): Boolean {
  if (error.keyword == $$"$ref") return false
  if (error.instanceLocation == location) return true
  return error.instanceLocation == location.parent && error.property == property
         && (error.keyword == "propertyNames" || error.keyword == "additionalProperties" || error.keyword == "unevaluatedProperties")
}

private data class CachedSchemaNode(val node: JsonNode, val location: SchemaLocation, val context: SchemaContext)

private fun Schema.cachedNode(): CachedSchemaNode = CachedSchemaNode(schemaNode, schemaLocation, schemaContext)

private fun resolveCachedBranch(schema: Schema, path: SchemaStep): CachedSchemaNode? {
  var current = schema.cachedNode()
  for ((_, keyword, argument) in generateSequence(path) { it.parent }.toList().asReversed()) {
    ProgressManager.checkCanceled()
    if (keyword == $$"$ref") {
      current = resolveCachedReference(current) ?: return null
      continue
    }
    var node = current.node.get(keyword) ?: return null
    var location = current.location.append(keyword)
    when (argument) {
      is String -> {
        node = node.get(argument) ?: return null
        location = location.append(argument)
      }
      is Int -> {
        node = node.get(argument) ?: return null
        location = location.append(argument)
      }
    }
    val id = current.context.resolveSchemaId(node)
    if (id != null) {
      val resource = current.context.schemaResources[SchemaLocation.resolve(location, id)] ?: return null
      if (resource.schemaNode !== node) return null
      current = resource.cachedNode()
    }
    else {
      if (node.has($$"$schema")) return null
      current = CachedSchemaNode(node, location, current.context)
    }
  }
  val visited = mutableSetOf<SchemaLocation>()
  while (visited.add(current.location)) {
    ProgressManager.checkCanceled()
    if (current.node.has($$"$ref")) {
      val version = current.context.dialect.specificationVersion ?: return null
      if (version.order > SpecificationVersion.DRAFT_7.order
          && current.node.properties().any { it.key !in nonValidatingRefKeywords }) return null
      current = resolveCachedReference(current) ?: return null
    }
    else {
      if (hasUnsupportedSelectorContext(current.node)) return null
      if (current.node.has($$"$dynamicRef") || current.node.has($$"$recursiveRef")) return null
      return current
    }
  }
  return null
}

private fun hasUnsupportedSelectorContext(node: JsonNode): Boolean = unsupportedSelectorKeywords.any(node::has)

private fun resolveCachedReference(current: CachedSchemaNode): CachedSchemaNode? {
  val ref = current.node.get($$"$ref")?.takeIf { it.isString }?.asString() ?: return null
  val version = current.context.dialect.specificationVersion ?: return null
  if (version.order <= SpecificationVersion.DRAFT_7.order
      && current.context.resolveSchemaId(current.node) != null) return null
  val location = SchemaLocation.resolve(current.location, ref)
  val target = current.context.schemaReferences[location] ?: current.context.schemaResources[location] ?: return null
  return target.cachedNode()
}
