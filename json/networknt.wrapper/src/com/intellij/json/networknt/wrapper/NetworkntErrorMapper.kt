// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.networknt.wrapper

import com.intellij.json.JsonBundle
import com.intellij.json.networknt.wrapper.NetworkntErrorMapper.Companion.getRawErrorCap
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.jetbrains.jsonSchema.extension.JsonErrorPriority
import com.jetbrains.jsonSchema.impl.JsonValidationError
import com.jetbrains.jsonSchema.impl.JsonValidationError.DuplicateArrayItemIssueData
import com.jetbrains.jsonSchema.impl.JsonValidationError.FixableIssueKind
import com.jetbrains.jsonSchema.impl.JsonValidationError.MissingMultiplePropsIssueData
import com.jetbrains.jsonSchema.impl.JsonValidationError.MissingPropertyIssueData
import com.jetbrains.jsonSchema.impl.JsonValidationError.ProhibitedPropertyIssueData
import com.jetbrains.jsonSchema.impl.JsonValidationError.TypeMismatchIssueData
import com.networknt.schema.Error
import org.jetbrains.annotations.NonNls
import tools.jackson.databind.JsonNode

/**
 * Converts networknt validation errors to IntelliJ JsonValidationError format.
 *
 * Responsibilities:
 * - Map networknt keyword to FixableIssueKind + JsonErrorPriority
 * - Generate localized messages via JsonBundle
 * - Reconstruct IssueData for quick fixes using fork-enriched Error API
 * - Use PsiLocationIndex to find target PSI elements
 * - Handle oneOf/anyOf errors: collect types from all branches, produce merged message (ADR-001 Variant 3)
 * - Group required/dependentRequired errors per object into a single MissingMultiplePropsIssueData
 */
class NetworkntErrorMapper(
  private val locationIndex: PsiLocationIndex,
) {

  companion object {
    private val LOG = Logger.getInstance(NetworkntErrorMapper::class.java)

    private const val RAW_ERROR_CAP_KEY = "json.schema.networknt.raw.error.cap"

    /**
     * Threshold for raw networknt errors before composition branch pruning kicks in.
     * Not a cap on final displayed errors — all errors below this threshold are fully
     * mapped and shown to the user. Configurable via registry.
     */
    @JvmStatic
    fun getRawErrorCap(): Int = Registry.intValue(RAW_ERROR_CAP_KEY, 500)

  }

  /**
   * Maps a list of networknt errors to IntelliJ validation errors.
   * Returns a map of PsiElement → JsonValidationError.
   *
   * oneOf/anyOf branch errors arrive as flat siblings after the composition error in the list.
   * We pre-group them by instanceLocation so that mapCompositionError() can collect type
   * information from ALL branches and produce a Variant 3 merged message (ADR-001).
   *
   * required/dependentRequired errors are pre-grouped by instanceLocation (and trigger property
   * for dependentRequired) so that multiple missing properties produce a single grouped error
   * with MissingMultiplePropsIssueData — matching the old IJ validator behaviour (ADR Category 2).
   *
   * IMPORTANT: Only `required` errors that are NOT inside a oneOf/anyOf branch are pre-grouped.
   * Branch-specific required errors (evaluationPath contains "/oneOf/" or "/anyOf/") are left to
   * individual processing in order to preserve the original priority ordering — the type error from
   * the primitiveType branch must be placed before required errors from other branches, so that the
   * priority-based intersection logic in createWarnings() selects the type error over branch-specific
   * errors. Pre-grouping would insert required errors first and block the type error via putIfAbsent.
   */
  fun mapErrors(errors: Collection<Error>): Map<PsiElement, JsonValidationError> {
    val withoutUnresolved = filterUnresolvedRefErrors(errors)
    val effectiveErrorsRaw = applyErrorCap(withoutUnresolved)

    val typeBranchSuppressions = computeBranchTypeErrorSuppressions(effectiveErrorsRaw)
    val structuralBranchSuppressions = computeBranchStructuralErrorSuppressions(effectiveErrorsRaw)
    val branchSuppressions = when {
      typeBranchSuppressions.isEmpty() -> structuralBranchSuppressions
      structuralBranchSuppressions.isEmpty() -> typeBranchSuppressions
      else -> typeBranchSuppressions + structuralBranchSuppressions
    }
    val effectiveErrors = if (branchSuppressions.isEmpty()) effectiveErrorsRaw
                          else effectiveErrorsRaw.filter { it !in branchSuppressions }

    val preGrouped = preGroupErrors(effectiveErrors)

    val result = mutableMapOf<PsiElement, JsonValidationError>()
    val compositionFallbackElements = mutableListOf<PsiElement>()
    emitPreGroupedErrors(preGrouped, result)
    emitIndividualErrors(effectiveErrors, preGrouped, result, compositionFallbackElements)
    emitCompositionFallbacks(compositionFallbackElements, result)

    // Overlay synthesised Variant-3 merges on top of individual branch type errors.
    // See [shouldOverlayWithSynthMerge] for the replacement contract.
    for ((psi, synthesised) in synthesiseMissingCompositionMerges(effectiveErrors)) {
      if (shouldOverlayWithSynthMerge(result[psi])) {
        result[psi] = synthesised
      }
    }

    // Overlay synthesised enum-union merges on top of single-branch enum errors.
    // networknt emits one `enum` error per failing anyOf/oneOf branch but no composition
    // sibling, so `mapCompositionError` never runs — we reconstruct the legacy merged
    // "Value should be one of: <union>" message from the branch errors directly.
    for ((psi, synthesised) in synthesiseMissingCompositionEnumMerges(effectiveErrors)) {
      if (shouldOverlayWithSynthEnumMerge(result[psi])) {
        result[psi] = synthesised
      }
    }

    // Drop errors whose PSI target was marked as opaque by the language adapter
    // (e.g. JS `getMode()` — JSJsonForeignValueAdapter.shouldCheckAsValue() == false).
    // The converter emitted a placeholder JsonNode for these values, so networknt-level
    // errors attributed to them are false positives. See [PsiLocationIndex.isSuppressed].
    val filtered = if (result.keys.any(locationIndex::isSuppressed)) {
      result.filterKeys { psi -> !locationIndex.isSuppressed(psi) }
    } else {
      result
    }

    if (Registry.get("json.schema.networknt.show.source.prefix").asBoolean()) {
      return filtered.mapValues { (_, error) -> withSourcePrefix(error) }
    }

    return filtered
  }

  /**
   * Filters out unresolvable `$ref` errors — always, regardless of error count.
   *
   * Per JSON Schema spec discussion ([json-schema-spec#1276](https://github.com/json-schema-org/json-schema-spec/issues/1276)),
   * an unresolvable `$ref` makes the validation result indeterminate (neither valid nor invalid).
   * Showing these as errors in the editor is misleading — the user cannot fix a broken external schema.
   * We log the count so the information is available for diagnostics.
   */
  private fun filterUnresolvedRefErrors(errors: Collection<Error>): Collection<Error> {
    val refErrors = errors.filter { it.keyword == "${'$'}ref" }
    if (refErrors.isEmpty()) return errors
    LOG.debug("Filtered ${refErrors.size} unresolvable \$ref error(s) — schema references could not be resolved")
    return errors.filter { it.keyword != "${'$'}ref" }
  }

  /**
   * When raw error count exceeds [getRawErrorCap], prune oneOf/anyOf branch errors
   * to avoid CPU explosion. Errors below the threshold pass through unfiltered.
   */
  private fun applyErrorCap(errors: Collection<Error>): Collection<Error> {
    if (errors.size <= getRawErrorCap()) return errors
    return errors.filter { error ->
      val keyword = error.keyword
      keyword == "oneOf" || keyword == "anyOf" || keyword == "allOf"
      || !isInsideComposition(error)
    }
  }

  /**
   * Pre-grouping results: errors indexed by location for composition handling,
   * plus grouped required/dependentRequired errors with the set of consumed errors.
   */
  private class PreGrouped(
    val errorsByLocation: LinkedHashMap<String, MutableList<Error>>,
    val requiredGroups: LinkedHashMap<String, MutableList<Error>>,
    val dependentRequiredGroups: LinkedHashMap<String, MutableList<Error>>,
    val consumed: MutableSet<Error>,
  )

  /**
   * Pre-groups errors for batch processing:
   * - By instanceLocation (for composition sibling lookup)
   * - `required` errors NOT inside oneOf/anyOf branches (for single grouped "missing properties" message)
   * - `dependentRequired`/`dependencies` errors by (instanceLocation, triggerProperty)
   *
   * Required errors inside compositions are NOT pre-grouped — they fall through to [mapRequiredError]
   * and rely on natural ordering (type errors come first and win the putIfAbsent race).
   */
  private fun preGroupErrors(errors: Collection<Error>): PreGrouped {
    val errorsByLocation = LinkedHashMap<String, MutableList<Error>>()
    val requiredGroups = LinkedHashMap<String, MutableList<Error>>()
    val dependentRequiredGroups = LinkedHashMap<String, MutableList<Error>>()

    for (error in errors) {
      val locationKey = error.instanceLocation?.toString() ?: ""
      errorsByLocation.getOrPut(locationKey) { mutableListOf() }.add(error)

      when {
        error.keyword == "required" && !isInsideComposition(error) -> {
          val evalKey = error.evaluationPath?.toString() ?: ""
          requiredGroups.getOrPut("$locationKey\u0000$evalKey") { mutableListOf() }.add(error)
        }
        error.keyword == "dependentRequired" || error.keyword == "dependencies" -> {
          val triggerKey = error.property ?: ""
          dependentRequiredGroups.getOrPut("$locationKey\u0000$triggerKey") { mutableListOf() }.add(error)
        }
      }
    }

    return PreGrouped(errorsByLocation, requiredGroups, dependentRequiredGroups, mutableSetOf())
  }

  /**
   * Emits pre-grouped required and dependentRequired errors into [result],
   * marking consumed errors in [preGrouped].
   */
  private fun emitPreGroupedErrors(
    preGrouped: PreGrouped,
    result: MutableMap<PsiElement, JsonValidationError>,
  ) {
    for ((_, group) in preGrouped.requiredGroups) {
      val mapped = mapGroupedRequiredErrors(group) ?: continue
      mergeOrInsertRequiredProps(result, mapped.first, mapped.second)
      preGrouped.consumed.addAll(group)
    }
    for ((_, group) in preGrouped.dependentRequiredGroups) {
      val mapped = mapGroupedDependencyErrors(group) ?: continue
      result.putIfAbsent(mapped.first, mapped.second)
      preGrouped.consumed.addAll(group)
    }
  }

  /**
   * Inserts `candidate` at `psiElement` in `result`. If a [MissingMultiplePropsIssueData] error
   * already exists at that PSI element, merges the two missing-property lists into one
   * [JsonValidationError] keeping a richer per-property data when available.
   *
   * Needed for allOf-required cases: multiple allOf branches can each emit a separate
   * `required` group at the same instance location (different evaluationPath pre-group keys),
   * and a plain `putIfAbsent` would silently drop every group after the first.
   */
  private fun mergeOrInsertRequiredProps(
    result: MutableMap<PsiElement, JsonValidationError>,
    psiElement: PsiElement,
    candidate: JsonValidationError,
  ) {
    val existing = result.putIfAbsent(psiElement, candidate) ?: return
    val existingData = existing.issueData as? MissingMultiplePropsIssueData ?: return
    val candidateData = candidate.issueData as? MissingMultiplePropsIssueData ?: return

    val merged = LinkedHashMap<String, MissingPropertyIssueData>()
    for (item in existingData.myMissingPropertyIssues) merged[item.propertyName] = item
    for (item in candidateData.myMissingPropertyIssues) {
      val prev = merged[item.propertyName]
      merged[item.propertyName] = if (prev == null) item else pickRicher(prev, item)
    }
    val existingList = existingData.myMissingPropertyIssues.toList()
    if (merged.values.toList() == existingList) return

    val mergedData = MissingMultiplePropsIssueData(merged.values.toList())
    val mergedMessage = JsonBundle.message(
      "schema.validation.missing.required.property.or.properties",
      mergedData.getMessage(true)
    )
    result[psiElement] = JsonValidationError(
      mergedMessage,
      existing.fixableIssueKind,
      mergedData,
      existing.priority,
    )
  }

  private fun pickRicher(a: MissingPropertyIssueData, b: MissingPropertyIssueData): MissingPropertyIssueData {
    fun richness(d: MissingPropertyIssueData) =
      (if (d.propertyType != null) 1 else 0) +
      (if (d.defaultValue != null) 1 else 0) +
      (if (d.enumItemsCount > 0) 1 else 0)
    return if (richness(b) > richness(a)) b else a
  }

  /**
   * Main error loop: processes each error not already consumed by pre-grouping.
   * oneOf/anyOf errors are handled via [mapCompositionError] with sibling branch collection;
   * all other errors are dispatched to [mapSingleError].
   */
  private fun emitIndividualErrors(
    errors: Collection<Error>,
    preGrouped: PreGrouped,
    result: MutableMap<PsiElement, JsonValidationError>,
    compositionFallbackElements: MutableList<PsiElement>,
  ) {
    for (error in errors) {
      if (error in preGrouped.consumed) continue
      val keyword = error.keyword ?: continue

      if (keyword == "oneOf" || keyword == "anyOf") {
        val locationKey = error.instanceLocation?.toString() ?: ""
        val siblings = preGrouped.errorsByLocation[locationKey] ?: emptyList<Error>()
        val branchErrors = siblings.filter { sibling ->
          sibling !== error
          && sibling.keyword != "oneOf" && sibling.keyword != "anyOf" && sibling.keyword != "allOf"
          && isBranchErrorOf(error, sibling)
        }

        val mapped = mapCompositionError(error, branchErrors)
        if (mapped != null) {
          result.putIfAbsent(mapped.first, mapped.second)
          if (branchErrors.isNotEmpty() && branchErrors.all { it.keyword == "type" }) {
            preGrouped.consumed.addAll(branchErrors)
          }
        }
        else {
          val psiElement = locationIndex.resolve(error.instanceLocation ?: continue) ?: continue
          compositionFallbackElements.add(psiElement)
        }
        continue
      }

      val mapped = mapSingleError(error)
      for ((element, validationError) in mapped) {
        result.putIfAbsent(element, validationError)
      }
    }
  }

  private fun emitCompositionFallbacks(
    compositionFallbackElements: List<PsiElement>,
    result: MutableMap<PsiElement, JsonValidationError>,
  ) {
    if (compositionFallbackElements.isEmpty()) return
    val fallbackMessage = JsonBundle.message("schema.validation.not.matching.any.of")
    for (element in compositionFallbackElements) {
      result.putIfAbsent(element, JsonValidationError(
        fallbackMessage,
        FixableIssueKind.None,
        null,
        JsonErrorPriority.LOW_PRIORITY
      ))
    }
  }

  /**
   * Maps a single networknt error to one or more IntelliJ errors.
   * Returns empty list if the error should be skipped.
   */
  private fun mapSingleError(error: Error): List<Pair<PsiElement, JsonValidationError>> {
    val keyword = error.keyword ?: return emptyList()

    // Handle composition keywords specially
    when (keyword) {
      "oneOf", "anyOf" -> return listOfNotNull(mapCompositionError(error))
      "allOf" -> return listOfNotNull(mapAllOfError(error))
    }

    // uniqueItems can produce multiple errors (one per duplicate)
    if (keyword == "uniqueItems") return mapUniqueItemsError(error)

    // additionalItems targets a specific array element by index
    if (keyword == "additionalItems") return listOfNotNull(mapAdditionalItemsError(error))

    // Map individual keyword errors — single result wrapped in list
    val mapped = when (keyword) {
      "type" -> mapTypeError(error)
      "required" -> mapRequiredError(error)
      "additionalProperties" -> mapAdditionalPropertiesError(error)
      "enum" -> mapEnumError(error)
      "const" -> mapConstError(error)
      "not" -> mapNotError(error)
      "minLength", "maxLength", "pattern", "format" -> mapStringError(error)
      "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "multipleOf" -> mapNumericError(error)
      "minItems", "maxItems" -> mapArrayLengthError(error)
      "contains" -> mapContainsError(error)
      "minProperties", "maxProperties" -> mapObjectSizeError(error)
      "dependencies", "dependentRequired" -> mapDependencyError(error)
      "unevaluatedProperties" -> mapUnevaluatedPropertiesError(error)
      "propertyNames" -> mapPropertyNamesError(error)
      else -> mapGenericError(error)
    }
    return listOfNotNull(mapped)
  }

  // === Complex keyword mappings (with IssueData) ===

  private fun mapTypeError(error: Error): Pair<PsiElement, JsonValidationError>? {
    val instancePath = error.instanceLocation ?: return null
    val psiElement = locationIndex.resolve(instancePath) ?: return null

    // TypeValidator/UnionTypeValidator set arguments[0]=actualType, arguments[1]=expectedType(s)
    // arguments[1] is "number" for single type, or "[number, string]" for union types
    val expectedTypeStr = error.arguments?.getOrNull(1)?.toString() ?: ""
    val expectedTypes = parseExpectedTypes(expectedTypeStr)

    val actualTypeStr = error.arguments?.getOrNull(0)?.toString()
    val actualType = actualTypeStr?.let { parseSchemaType(it) } ?: detectActualType(error.instanceNode)

    val issueData = TypeMismatchIssueData(expectedTypes)
    // When this error is a top-level oneOf/anyOf branch type-mismatch (evalPath ends exactly
    // at `/oneOf/N/type` or `/anyOf/N/type`), the user-facing message must use the composition
    // form ("Required one of: …") even when the branch happens to expect a single type. Without
    // this, a leaked branch error wins the putIfAbsent race against the parent composition's
    // merged message and we regress to "Required: …" — which contradicts ADR-001 Variant 3.
    val isComposition = isTopLevelBranchTypeError(error.evaluationPath?.toString())
    val message = buildTypeErrorMessage(expectedTypes, actualType, isComposition)

    return psiElement to JsonValidationError(
      message,
      FixableIssueKind.ProhibitedType,
      issueData,
      JsonErrorPriority.TYPE_MISMATCH
    )
  }

  /**
   * Maps a single `required` error for a property that is inside a oneOf/anyOf branch.
   * (Top-level required errors are handled by mapGroupedRequiredErrors() via pre-grouping.)
   *
   * For branch-specific required errors we intentionally do NOT look up the property schema —
   * the branch may have a single-item enum (e.g. "type": {"enum": ["business"]}), which would
   * cause getPropertyNameWithComment() to append "= business" to the display name. In a oneOf
   * context this is misleading because the value is branch-specific, not universally applicable.
   * Using null/zero for defaultValue/enumCount reproduces the pre-existing IJ behaviour.
   */
  private fun mapRequiredError(error: Error): Pair<PsiElement, JsonValidationError>? {
    val instancePath = error.instanceLocation ?: return null
    val psiElement = locationIndex.resolve(instancePath) ?: return null
    val missingPropName = error.property ?: return null

    val missingPropData = MissingPropertyIssueData(missingPropName, null, null, 0)
    val issueData = MissingMultiplePropsIssueData(listOf(missingPropData))
    val message = JsonBundle.message("schema.validation.missing.required.property.or.properties", issueData.getMessage(true))

    return psiElement to JsonValidationError(
      message,
      FixableIssueKind.MissingProperty,
      issueData,
      JsonErrorPriority.MISSING_PROPS
    )
  }

  /**
   * Maps one or more `required` errors at the same instanceLocation into a single
   * IntelliJ error with [MissingMultiplePropsIssueData] listing all missing properties.
   *
   * networknt emits one [Error] per missing property; this method groups them so the
   * UI shows "Missing required properties 'a', 'b'" instead of two separate errors.
   */
  private fun mapGroupedRequiredErrors(errors: List<Error>): Pair<PsiElement, JsonValidationError>? {
    val first = errors.firstOrNull() ?: return null
    val instancePath = first.instanceLocation ?: return null
    val psiElement = locationIndex.resolve(instancePath) ?: return null

    val missingPropDataList = errors.mapNotNull { error ->
      val missingPropName = error.property ?: return@mapNotNull null
      // Use getPropertySchema() which correctly reads from parentSchemaNode.get("properties")
      val propertySchema = error.getPropertySchema(missingPropName)
      val type = propertySchema?.get("type")?.asText()?.let { parseSchemaType(it) }
      // Extract default value as a JVM primitive so AddMissingPropertyFix.formatDefaultValue()
      // can handle it — the tools.jackson.databind.JsonNode type is not recognized by that method
      // which expects com.fasterxml.jackson.databind.JsonNode.
      val (defaultValue, enumCount) = extractDefaultAndEnumCount(propertySchema)
      MissingPropertyIssueData(missingPropName, type, defaultValue, enumCount)
    }

    if (missingPropDataList.isEmpty()) return null

    val issueData = MissingMultiplePropsIssueData(missingPropDataList)
    val message = JsonBundle.message("schema.validation.missing.required.property.or.properties", issueData.getMessage(true))

    return psiElement to JsonValidationError(
      message,
      FixableIssueKind.MissingProperty,
      issueData,
      JsonErrorPriority.MISSING_PROPS
    )
  }

  private fun mapAdditionalPropertiesError(error: Error): Pair<PsiElement, JsonValidationError>? {
    val propertyName = error.property ?: return null
    val parentPath = error.instanceLocation ?: return null

    // Target the property NAME element (the key that's not allowed)
    val psiElement = locationIndex.resolvePropertyName(parentPath, propertyName) ?: return null

    // Get valid property names from schema for typo detection
    // For Phase 0, skip typo detection to avoid Jackson API complexity
    // TODO: Add typo detection in Phase 1 when we have the enriched Error API
    val typoCandidates = emptyList<String>()

    val issueData = ProhibitedPropertyIssueData(propertyName, typoCandidates)
    val messageKey = if (typoCandidates.isEmpty()) {
      "json.schema.annotation.not.allowed.property"
    } else {
      "json.schema.annotation.not.allowed.property.possibly.typo"
    }
    val message = JsonBundle.message(messageKey, propertyName)

    return psiElement to JsonValidationError(
      message,
      FixableIssueKind.ProhibitedProperty,
      issueData,
      JsonErrorPriority.LOW_PRIORITY
    )
  }

  private fun mapUniqueItemsError(error: Error): List<Pair<PsiElement, JsonValidationError>> {
    val arrayNode = error.instanceNode ?: return emptyList()
    val duplicateIndices = findDuplicateIndices(arrayNode)

    if (duplicateIndices.isEmpty()) return emptyList()

    val instancePath = error.instanceLocation ?: return emptyList()
    val issueData = DuplicateArrayItemIssueData(duplicateIndices)
    val message = JsonBundle.message("schema.validation.not.unique")

    return duplicateIndices.toList().mapNotNull { index ->
      val itemPath = instancePath.append(index)
      val psiElement = locationIndex.resolve(itemPath) ?: return@mapNotNull null
      psiElement to JsonValidationError(
        message,
        FixableIssueKind.DuplicateArrayItem,
        issueData,
        JsonErrorPriority.TYPE_MISMATCH
      )
    }
  }

  private fun mapAdditionalItemsError(error: Error): Pair<PsiElement, JsonValidationError>? {
    val arrayPath = error.instanceLocation ?: return null
    val index = error.index ?: return null
    val itemPath = arrayPath.append(index)
    val psiElement = locationIndex.resolve(itemPath) ?: return null

    val message = JsonBundle.message("schema.validation.array.no.extra")

    return psiElement to JsonValidationError(
      message,
      FixableIssueKind.None,
      null,
      JsonErrorPriority.LOW_PRIORITY
    )
  }

  // === Simple keyword mappings (FixableIssueKind.None) ===

  private fun mapEnumError(error: Error): Pair<PsiElement, JsonValidationError>? {
    val instancePath = error.instanceLocation ?: return null
    val psiElement = locationIndex.resolve(instancePath) ?: return null

    val enumValues = formatEnumValues(error.schemaNode)

    val message = JsonBundle.message("schema.validation.enum.mismatch", enumValues)

    return psiElement to JsonValidationError(
      message,
      FixableIssueKind.NonEnumValue,
      null,
      JsonErrorPriority.MEDIUM_PRIORITY
    )
  }

  private fun mapConstError(error: Error): Pair<PsiElement, JsonValidationError>? {
    val instancePath = error.instanceLocation ?: return null
    val psiElement = locationIndex.resolve(instancePath) ?: return null

    val schemaNode = error.schemaNode
    val constValue = if (schemaNode != null) formatJsonValue(schemaNode) else ""
    val message = JsonBundle.message("schema.validation.enum.mismatch", constValue)

    return psiElement to JsonValidationError(
      message,
      FixableIssueKind.NonEnumValue,
      null,
      JsonErrorPriority.MEDIUM_PRIORITY
    )
  }

  private fun mapNotError(error: Error): Pair<PsiElement, JsonValidationError>? {
    val instancePath = error.instanceLocation ?: return null
    val psiElement = locationIndex.resolve(instancePath) ?: return null

    val message = JsonBundle.message("schema.validation.against.not")

    return psiElement to JsonValidationError(
      message,
      FixableIssueKind.None,
      null,
      JsonErrorPriority.NOT_SCHEMA
    )
  }

  private fun mapStringError(error: Error): Pair<PsiElement, JsonValidationError>? {
    val instancePath = error.instanceLocation ?: return null
    val psiElement = locationIndex.resolve(instancePath) ?: return null

    val message = when (error.keyword) {
      "minLength" -> {
        val min = error.arguments?.getOrNull(0) ?: 0
        JsonBundle.message("schema.validation.string.shorter.than", min)
      }
      "maxLength" -> {
        val max = error.arguments?.getOrNull(0) ?: 0
        JsonBundle.message("schema.validation.string.longer.than", max)
      }
      "pattern" -> {
        val pattern = error.arguments?.getOrNull(0)?.toString() ?: ""
        JsonBundle.message("schema.validation.string.violates.pattern", pattern)
      }
      "format" -> {
        // Format errors: use generic message
        error.message
      }
      else -> error.message
    }

    return psiElement to JsonValidationError(
      message,
      FixableIssueKind.None,
      null,
      JsonErrorPriority.LOW_PRIORITY
    )
  }

  private fun mapNumericError(error: Error): Pair<PsiElement, JsonValidationError>? {
    val instancePath = error.instanceLocation ?: return null
    val psiElement = locationIndex.resolve(instancePath) ?: return null

    val message = when (error.keyword) {
      "minimum" -> {
        val min = error.arguments?.getOrNull(0) ?: 0
        JsonBundle.message("schema.validation.less.than.minimum", min)
      }
      "maximum" -> {
        val max = error.arguments?.getOrNull(0) ?: 0
        JsonBundle.message("schema.validation.greater.than.maximum", max)
      }
      "exclusiveMinimum" -> {
        val min = error.arguments?.getOrNull(0) ?: 0
        JsonBundle.message("schema.validation.less.than.exclusive.minimum", min)
      }
      "exclusiveMaximum" -> {
        val max = error.arguments?.getOrNull(0) ?: 0
        JsonBundle.message("schema.validation.greater.than.exclusive.maximum", max)
      }
      "multipleOf" -> {
        val multiple = error.arguments?.getOrNull(0) ?: 1
        JsonBundle.message("schema.validation.not.multiple.of", multiple)
      }
      else -> error.message
    }

    return psiElement to JsonValidationError(
      message,
      FixableIssueKind.None,
      null,
      JsonErrorPriority.LOW_PRIORITY
    )
  }

  private fun mapArrayLengthError(error: Error): Pair<PsiElement, JsonValidationError>? {
    val instancePath = error.instanceLocation ?: return null
    val psiElement = locationIndex.resolve(instancePath) ?: return null

    val message = when (error.keyword) {
      "minItems" -> {
        val min = error.arguments?.getOrNull(0) ?: 0
        JsonBundle.message("schema.validation.array.shorter.than", min)
      }
      "maxItems" -> {
        val max = error.arguments?.getOrNull(0) ?: 0
        JsonBundle.message("schema.validation.array.longer.than", max)
      }
      else -> error.message
    }

    return psiElement to JsonValidationError(
      message,
      FixableIssueKind.None,
      null,
      JsonErrorPriority.LOW_PRIORITY
    )
  }

  private fun mapContainsError(error: Error): Pair<PsiElement, JsonValidationError>? {
    val instancePath = error.instanceLocation ?: return null
    val psiElement = locationIndex.resolve(instancePath) ?: return null

    val message = JsonBundle.message("schema.validation.array.not.contains")

    return psiElement to JsonValidationError(
      message,
      FixableIssueKind.None,
      null,
      JsonErrorPriority.LOW_PRIORITY
    )
  }

  private fun mapObjectSizeError(error: Error): Pair<PsiElement, JsonValidationError>? {
    val instancePath = error.instanceLocation ?: return null
    val psiElement = locationIndex.resolve(instancePath) ?: return null

    val message = when (error.keyword) {
      "minProperties" -> {
        val min = error.arguments?.getOrNull(0) ?: 0
        JsonBundle.message("schema.validation.number.of.props.less.than", min)
      }
      "maxProperties" -> {
        val max = error.arguments?.getOrNull(0) ?: 0
        JsonBundle.message("schema.validation.number.of.props.greater.than", max)
      }
      else -> error.message
    }

    return psiElement to JsonValidationError(
      message,
      FixableIssueKind.None,
      null,
      JsonErrorPriority.LOW_PRIORITY
    )
  }

  private fun mapDependencyError(error: Error): Pair<PsiElement, JsonValidationError>? {
    return mapGroupedDependencyErrors(listOf(error))
  }

  /**
   * Maps one or more `dependentRequired`/`dependencies` errors with the same instanceLocation
   * and trigger property into a single IntelliJ error.
   *
   * networknt emits one [Error] per missing dependency; this method groups them so the
   * UI shows "Dependency is violated: properties 'bar', 'foo' must be specified, since 'quux'
   * is specified" instead of two separate errors.
   *
   * For a single missing dependency the message uses the singular `property 'X'` form.
   * For multiple missing dependencies the message uses the plural `properties 'X', 'Y'` form.
   */
  private fun mapGroupedDependencyErrors(errors: List<Error>): Pair<PsiElement, JsonValidationError>? {
    val first = errors.firstOrNull() ?: return null
    // Keep dependency errors anchored on the containing object to preserve existing highlighting behavior.
    // Multiple dependentRequired groups with different triggers may still collide on the same PsiElement;
    // fixing that requires a separate aggregation strategy, not an anchor change.
    val instancePath = first.instanceLocation ?: return null
    val psiElement = locationIndex.resolve(instancePath) ?: return null

    // error.property = trigger property (e.g. "quux")
    // error.arguments[0] = missing property (e.g. "foo" or "bar")
    val triggerProp = first.property ?: ""
    val missingProps = errors.mapNotNull { it.arguments?.getOrNull(0)?.toString() }.sorted()

    val missingDescription = if (missingProps.size == 1) {
      JsonBundle.message("schema.validation.property", "'${missingProps[0]}'")
    }
    else {
      val quotedNames = missingProps.joinToString(", ") { "'$it'" }
      JsonBundle.message("schema.validation.properties", quotedNames)
    }

    val message = JsonBundle.message("schema.validation.violated.dependency", missingDescription, triggerProp)

    val missingPropDataList = missingProps.map { propName ->
      val propertySchema = first.getPropertySchema(propName)
      val type = propertySchema?.get("type")?.asText()?.let { parseSchemaType(it) }
      val (defaultValue, enumCount) = extractDefaultAndEnumCount(propertySchema)
      MissingPropertyIssueData(propName, type, defaultValue, enumCount)
    }

    val issueData = MissingMultiplePropsIssueData(missingPropDataList)

    return psiElement to JsonValidationError(
      message,
      FixableIssueKind.MissingProperty,
      issueData,
      JsonErrorPriority.MISSING_PROPS
    )
  }

  private fun mapUnevaluatedPropertiesError(error: Error): Pair<PsiElement, JsonValidationError>? {
    val propertyName = error.property ?: return null
    val parentPath = error.instanceLocation ?: return null
    val psiElement = locationIndex.resolvePropertyName(parentPath, propertyName) ?: return null

    val issueData = ProhibitedPropertyIssueData(propertyName, emptyList())
    val message = JsonBundle.message("json.schema.annotation.not.allowed.property", propertyName)

    return psiElement to JsonValidationError(
      message,
      FixableIssueKind.ProhibitedProperty,
      issueData,
      JsonErrorPriority.LOW_PRIORITY
    )
  }

  private fun mapPropertyNamesError(error: Error): Pair<PsiElement, JsonValidationError>? {
    val propertyName = error.property ?: return null
    val parentPath = error.instanceLocation ?: return null
    val psiElement = locationIndex.resolvePropertyName(parentPath, propertyName) ?: return null

    // Reconstruct an IntelliJ-style message from the inner schema constraints.
    // error.schemaNode is the inner schema (e.g. {"minLength": 7}).
    // error.arguments[1] is the networknt-formatted inner message used to disambiguate when
    // the inner schema has multiple constraints.
    val innerSchema: JsonNode? = error.schemaNode
    val innerMessage: String? = error.arguments?.getOrNull(1)?.toString()
    val message = mapPropertyNameInnerConstraint(innerSchema, innerMessage) ?: error.message

    return psiElement to JsonValidationError(
      message,
      FixableIssueKind.None,
      null,
      JsonErrorPriority.LOW_PRIORITY
    )
  }

  private fun mapPropertyNameInnerConstraint(innerSchema: JsonNode?, innerMessage: String?): String? {
    if (innerSchema == null) return null

    class Candidate(val message: String, vararg val keywords: String)

    val minLength = innerSchema.get("minLength")
    val maxLength = innerSchema.get("maxLength")
    val pattern = innerSchema.get("pattern")
    val enumNode = innerSchema.get("enum")

    val candidates = buildList {
      if (minLength != null && minLength.isIntegralNumber)
        add(Candidate(JsonBundle.message("schema.validation.string.shorter.than", minLength.intValue()), "at least", "shorter"))
      if (maxLength != null && maxLength.isIntegralNumber)
        add(Candidate(JsonBundle.message("schema.validation.string.longer.than", maxLength.intValue()), "at most", "longer"))
      if (pattern != null && pattern.isTextual)
        add(Candidate(JsonBundle.message("schema.validation.string.violates.pattern", pattern.asText()), "pattern", "regex"))
      if (enumNode != null && enumNode.isArray)
        add(Candidate(JsonBundle.message("schema.validation.enum.mismatch", formatEnumValues(enumNode)), "enumeration", "enum"))
    }

    if (candidates.isEmpty()) return null
    if (candidates.size == 1) return candidates[0].message

    // Multiple candidates: use innerMessage to disambiguate
    if (innerMessage != null) {
      return candidates.firstOrNull { c -> c.keywords.any { kw -> innerMessage.contains(kw) } }?.message
    }

    return null
  }

  // === Composition error handling ===

  /**
   * Maps a oneOf/anyOf composition error to a single IntelliJ error.
   *
   * Implements ADR-001 Variant 3 (MVP): when ALL branch errors are type mismatches, collect
   * the expected types from every branch and produce a single "no match" message listing all
   * valid types. Falls back to existing behavior when branches have non-type errors.
   *
   * @param error        The composition error (keyword = "oneOf" or "anyOf")
   * @param branchErrors Sibling errors at the same instanceLocation that belong to failing branches
   */
  private fun mapCompositionError(error: Error, branchErrors: List<Error> = emptyList()): Pair<PsiElement, JsonValidationError>? {
    // Multi-match case: oneOf matched more than one branch (no child errors since all matching schemas passed).
    // networknt uses messageKey "oneOf.indexes" with arguments[0] = number of matches as a string.
    if (error.messageKey == "oneOf.indexes") {
      val instancePath = error.instanceLocation ?: return null
    val psiElement = locationIndex.resolve(instancePath) ?: return null
      return psiElement to JsonValidationError(
        JsonBundle.message("schema.validation.to.more.than.one"),
        FixableIssueKind.None,
        null,
        JsonErrorPriority.MEDIUM_PRIORITY
      )
    }

    // ADR-001 Variant 3: collect types from all branch errors.
    // Only applies when every branch error is a type mismatch — for non-type branches
    // (e.g. pattern, enum, required) we fall back to the existing "first branch" behavior.
    if (branchErrors.isNotEmpty() && branchErrors.all { it.keyword == "type" }) {
      val instancePath = error.instanceLocation ?: return null
    val psiElement = locationIndex.resolve(instancePath) ?: return null
      val allExpectedTypes = branchErrors
        .flatMap { branchError ->
          val expectedStr = branchError.arguments?.getOrNull(1)?.toString() ?: ""
          parseExpectedTypes(expectedStr).toList()
        }
        .distinct()

      if (allExpectedTypes.isNotEmpty()) {
        val actualTypeStr = branchErrors.firstOrNull()?.arguments?.getOrNull(0)?.toString()
        val actualType = actualTypeStr?.let { parseSchemaType(it) }
          ?: detectActualType(error.instanceNode)

        val issueData = TypeMismatchIssueData(allExpectedTypes.toTypedArray())
        val message = buildTypeErrorMessage(allExpectedTypes.toTypedArray(), actualType, isComposition = true)
        return psiElement to JsonValidationError(
          message,
          FixableIssueKind.ProhibitedType,
          issueData,
          JsonErrorPriority.TYPE_MISMATCH
        )
      }
    }

    // Non-type branch errors (pattern, enum, additionalProperties, etc.) are processed
    // individually by mapErrors(). Enum unions across branches are reconstructed by
    // [synthesiseMissingCompositionEnumMerges] since networknt does not emit a sibling
    // composition error for anyOf-of-enums; for other keywords return null so the generic
    // "Does not match" fallback is added and sibling errors are not consumed.
    return null
  }

  private fun mapAllOfError(error: Error): Pair<PsiElement, JsonValidationError>? {
    // allOf errors should be flattened - each child error is mapped separately
    val childErrors = error.details?.get("errors") as? Collection<Error> ?: emptyList()

    for (childError in childErrors) {
      val mapped = mapSingleError(childError)
      if (mapped.isNotEmpty()) return mapped.first()
    }

    return null
  }

  private fun mapGenericError(error: Error): Pair<PsiElement, JsonValidationError>? {
    val instancePath = error.instanceLocation ?: return null
    val psiElement = locationIndex.resolve(instancePath) ?: return null

    return psiElement to JsonValidationError(
      error.message,
      FixableIssueKind.None,
      null,
      JsonErrorPriority.LOW_PRIORITY
    )
  }

  // === Helper functions ===

  /**
   * Given the full error list, returns the set of branch `type` errors that should
   * be dropped because a *sibling* branch under the same composition is type-compatible
   * with the instance value. Mirrors the legacy `processSchemasVariants` pre-filter:
   * if oneOf/anyOf has a branch whose declared type matches the actual value type,
   * other branches' type mismatches are spurious and should not be shown.
   *
   * A sibling branch is considered type-compatible if EITHER
   *   a) it emits NO `type` error at the composition's instanceLocation (its top-level
   *      type check passed — the branch is evaluating deeper non-type constraints), OR
   *   b) it is not represented in the error list at all (it passed entirely).
   * Case (b) is equivalent to (a) from the mapper's point of view because we only
   * observe branches that failed.
   */
  private fun computeBranchTypeErrorSuppressions(errors: Collection<Error>): Set<Error> {
    // For every composition root we observe across all errors' evaluationPaths (at ANY
    // nesting depth), record:
    //   - branchesSeen[compRoot]            : set of branch indices that have at least one
    //                                         error whose evalPath goes through compRoot/N/...
    //   - topLevelTypeErrors[compRoot][N]   : `type` errors whose evalPath ENDS exactly at
    //                                         compRoot/N/type — i.e., the branch failed its
    //                                         OWN top-level type check (the branch is
    //                                         type-incompatible with the value).
    //
    // A branch is "type-compatible" with the value iff it is in branchesSeen but NOT in
    // topLevelTypeErrors. When any sibling branch is type-compatible, the
    // top-level-type-error branches' errors at this composition are spurious (legacy
    // pre-filter would have discarded them) and are marked for suppression.
    //
    // Iterating over ALL composition segments in the evaluationPath is essential: an error
    // from branch 1 of an outer oneOf that surfaces DEEPER (e.g., inside branch 1's own
    // anyOf) still counts as "branch 1 of the outer oneOf failed", and that must be visible
    // when judging whether branch 0 of the outer oneOf is spurious.
    val branchesSeen = mutableMapOf<String, MutableSet<Int>>()
    val topLevelTypeErrors = mutableMapOf<String, MutableMap<Int, MutableList<Error>>>()

    for (error in errors) {
      val evalPath = error.evaluationPath?.toString() ?: continue

      // Walk left-to-right through every /oneOf/ and /anyOf/ segment in the path.
      var searchFrom = 0
      while (true) {
        val oneOfIdx = evalPath.indexOf("/oneOf/", searchFrom)
        val anyOfIdx = evalPath.indexOf("/anyOf/", searchFrom)
        val kwIdx = when {
          oneOfIdx < 0 && anyOfIdx < 0 -> -1
          oneOfIdx < 0 -> anyOfIdx
          anyOfIdx < 0 -> oneOfIdx
          else -> minOf(oneOfIdx, anyOfIdx)
        }
        if (kwIdx < 0) break
        val keywordLen = if (oneOfIdx == kwIdx) "/oneOf".length else "/anyOf".length

        val compRoot = evalPath.substring(0, kwIdx + keywordLen)
        val branchStart = kwIdx + keywordLen + 1
        val nextSlash = evalPath.indexOf('/', branchStart)
        val branchSegment = if (nextSlash >= 0) evalPath.substring(branchStart, nextSlash)
                            else evalPath.substring(branchStart)
        val branchIdx = branchSegment.toIntOrNull()
        if (branchIdx == null) {
          searchFrom = kwIdx + keywordLen + 1
          continue
        }

        branchesSeen.getOrPut(compRoot) { mutableSetOf() }.add(branchIdx)

        // "Top-level type error at this composition" = keyword == "type" AND nothing
        // comes after the branch index except "/type". If the evalPath goes deeper
        // (e.g., .../oneOf/1/items/...), the branch passed its own type check and only
        // fails at a deeper level — NOT a top-level type error.
        //
        // Note: we INTENTIONALLY do not include `$ref`-redirected paths
        // (`/oneOf/N/$ref/type`) here. Those are still semantically top-level branch
        // type rejections, but mirroring that recognition into the suppression algorithm
        // changes which sibling branches count as "type-compatible" and over-prunes
        // type-merge messages on real-world `oneOf`-of-`$ref` schemas (e.g. avroSchema).
        // The narrower recognition is in [isTopLevelBranchTypeError] / [mapTypeError]
        // — that fixes the message form without affecting suppression decisions.
        val tail = if (nextSlash < 0) "" else evalPath.substring(nextSlash)
        if (error.keyword == "type" && tail == "/type") {
          topLevelTypeErrors.getOrPut(compRoot) { mutableMapOf() }
                            .getOrPut(branchIdx) { mutableListOf() }
                            .add(error)
        }

        // Advance past THIS branch index so the next iteration catches nested compositions.
        searchFrom = if (nextSlash >= 0) nextSlash else evalPath.length
      }
    }

    val toSuppress = mutableSetOf<Error>()
    // Set of composition roots where suppression fired — we use this in the second pass
    // below to also suppress the composition-level `oneOf`/`anyOf` error itself.
    val suppressedCompositions = mutableSetOf<String>()
    for ((compRoot, typeErrorBranches) in topLevelTypeErrors) {
      val allBranches = branchesSeen[compRoot] ?: continue
      val typeCompatibleBranches = allBranches - typeErrorBranches.keys
      if (typeCompatibleBranches.isNotEmpty()) {
        for (errorList in typeErrorBranches.values) toSuppress.addAll(errorList)
        suppressedCompositions.add(compRoot)
      }
    }

    // Also suppress the composition-level error itself when we dropped its type-incompatible
    // branches: the remaining type-compatible branch(es) failed deeper, their errors will
    // surface individually at their own instanceLocations, and letting the composition
    // error fall through would trigger the "Does not match any of the allowed schemas"
    // fallback in emitCompositionFallbacks — pure noise, since the deeper errors already
    // explain what went wrong.
    for (error in errors) {
      val keyword = error.keyword ?: continue
      if (keyword != "oneOf" && keyword != "anyOf") continue
      val evalPath = error.evaluationPath?.toString() ?: continue
      if (evalPath in suppressedCompositions) toSuppress.add(error)
    }

    return toSuppress
  }

  /**
   * Given the full error list, returns the set of branch `required` errors that should be
   * dropped because a sibling branch under the same composition emits a **value-scoped** error —
   * i.e. an error whose `instanceLocation` is a strict descendant of the composition's own
   * `instanceLocation`. In that case the structural "missing property" complaint at the
   * composition root is misleading UX: adding the missing property would not by itself make
   * the instance valid, since another branch is already failing at a deeper value inside the
   * same object.
   *
   * Mirrors `computeBranchTypeErrorSuppressions` in shape and is called alongside it in
   * [mapErrors]; the two suppression sets are unioned into a single filter.
   *
   * Keyed by the innermost composition root so nested compositions (oneOf inside anyOf inside
   * oneOf) are judged at the level where their branches actually diverge — same convention
   * used by [extractCompositionRoot].
   *
   * In addition to `required`, a branch-level `enum` error at the composition root is also
   * suppressed under the same condition: when a sibling branch surfaces a deeper value-scoped
   * failure, reporting "Value should be one of …" on the whole composition instance is
   * misleading UX (the user clearly intended the sibling branch and has a deeper mistake —
   * e.g. a typo'd property name). See `testWithWaySelection`.
   *
   * Branch-level `additionalProperties` / `unevaluatedProperties` errors are suppressed under a
   * SHARPER condition than `required`/`enum`: only when a sibling branch under the same
   * composition root has a value-scoped error on the SAME property the rejection targets
   * (i.e. its instanceLocation equals `rootLoc + "/" + rejectedProperty`). Suppressing them
   * unconditionally would hide legitimate "extra property" diagnostics on unrelated keys.
   * Mirrors the swagger 2.0 `oneOf:[response, jsonReference]` UX: when the user typo'd a value
   * inside `response`, the sibling `jsonReference` branch's "Property X is not allowed"
   * complaint is misleading because the user clearly meant the `response` branch.
   */
  private fun computeBranchStructuralErrorSuppressions(errors: Collection<Error>): Set<Error> {
    // For each composition root, track:
    //   compInstanceLoc[compRoot]      : the composition's OWN instanceLocation — the shortest
    //                                    instanceLocation seen for any branch error under this
    //                                    compRoot. (The composition itself is evaluated at a
    //                                    single instance node; required-at-branch errors live
    //                                    at that same instance, deeper errors extend it.)
    //   hasValueScopedError[compRoot]  : true iff any branch emits an error whose
    //                                    instanceLocation is STRICTLY deeper than compInstanceLoc.
    //   requiredErrorsAtRoot[compRoot] : `required` errors whose instanceLocation equals
    //                                    compInstanceLoc — candidates for suppression.
    //
    // An error qualifies if its evaluationPath goes through /compRoot/N/ for some numeric N.
    // A `required` error's instanceLocation equals the composition's instanceLocation because
    // `required` is an object-level keyword. A `pattern`/`enum`/etc. error on a property
    // extends the instanceLocation by "/<propName>" — strict descendant.
    val compInstanceLoc = mutableMapOf<String, String>()
    val branchErrorsByCompRoot = mutableMapOf<String, MutableList<Error>>()

    for (error in errors) {
      val evalPath = error.evaluationPath?.toString() ?: continue
      val compRoot = extractCompositionRoot(evalPath) ?: continue
      val branchIdx = extractBranchIndex(evalPath, compRoot) ?: continue
      // Not strictly required since branchIdx is non-null above, but keeps intent explicit.
      if (branchIdx < 0) continue

      val instLoc = error.instanceLocation?.toString() ?: ""
      val existing = compInstanceLoc[compRoot]
      if (existing == null || instLoc.length < existing.length) {
        compInstanceLoc[compRoot] = instLoc
      }
      branchErrorsByCompRoot.getOrPut(compRoot) { mutableListOf() }.add(error)
    }

    val toSuppress = mutableSetOf<Error>()
    val suppressedCompositions = mutableSetOf<String>()
    for ((compRoot, branchErrors) in branchErrorsByCompRoot) {
      val rootLoc = compInstanceLoc[compRoot] ?: continue
      val rootLocPrefix = if (rootLoc.isEmpty()) "/" else "$rootLoc/"
      // Collect all instance locations strictly deeper than rootLoc — used both as a flag
      // for required/enum blanket suppression and as a per-property check for
      // additionalProperties/unevaluatedProperties.
      val deeperInstanceLocs = branchErrors
        .mapNotNull { it.instanceLocation?.toString() }
        .filter { it.length > rootLoc.length && it.startsWith(rootLocPrefix) }
        .toSet()
      if (deeperInstanceLocs.isEmpty()) continue
      var anySuppressed = false
      for (err in branchErrors) {
        val loc = err.instanceLocation?.toString() ?: ""
        if (loc != rootLoc) continue
        when (err.keyword) {
          "required", "enum" -> {
            toSuppress.add(err)
            anySuppressed = true
          }
          "additionalProperties", "unevaluatedProperties" -> {
            val rejected = err.property ?: continue
            // Build the rejected property's instance location and only suppress if some
            // sibling branch reports a value-scoped error at exactly that path. This keeps
            // unrelated extra-property diagnostics intact while squashing the misleading
            // composition-root rejection on a property that the chosen sibling actually has.
            val rejectedLoc = rootLocPrefix + rejected
            if (rejectedLoc in deeperInstanceLocs) {
              toSuppress.add(err)
              anySuppressed = true
            }
          }
        }
      }
      if (anySuppressed) suppressedCompositions.add(compRoot)
    }

    // Also suppress the composition-level error itself when we dropped a branch's
    // structural `required` complaint: the surviving value-scoped error(s) already anchor
    // the diagnostic on the offending value, and letting the composition error fall
    // through would trigger the "Does not match any of the allowed schemas" fallback in
    // [emitCompositionFallbacks] — redundant noise on top of the precise deeper message.
    if (suppressedCompositions.isNotEmpty()) {
      for (error in errors) {
        val keyword = error.keyword ?: continue
        if (keyword != "oneOf" && keyword != "anyOf") continue
        val evalPath = error.evaluationPath?.toString() ?: continue
        if (evalPath in suppressedCompositions) toSuppress.add(error)
      }
    }

    return toSuppress
  }

  /**
   * Detects groups of branch `type` errors that share an instance location AND a composition
   * root in their evaluationPath, but have no `keyword = "oneOf"/"anyOf"` sibling error at the
   * same instance location to drive [mapCompositionError]. Builds a synthesised Variant-3
   * merged error for each such group — indexed by PsiElement — that the caller overlays on
   * top of the individually-mapped results via `putIfAbsent`.
   *
   * Only fires when every error in the group has `keyword == "type"` (Variant 3's precondition)
   * and there are at least TWO distinct branches represented (otherwise there's nothing to
   * merge; a single branch's type error is already emitted correctly by `mapTypeError`).
   *
   * Takes the pre-filter output as input so suppressed errors are not considered.
   */
  private fun synthesiseMissingCompositionMerges(
    errors: Collection<Error>,
  ): Map<PsiElement, JsonValidationError> {
    data class MergeKey(val compositionRoot: String, val instanceLocation: String)

    // Group type errors by (compRoot, instanceLoc).
    val groups = mutableMapOf<MergeKey, MutableList<Error>>()
    // Track which composition roots already have an explicit composition error.
    // Keyed by compositionRoot only (not instanceLocation) because networknt may emit the
    // composition error with a null or different instanceLocation than the branch type errors.
    // — we MUST NOT synthesise a merge on top of an existing composition, otherwise we'd double-emit.
    val coveredCompositionRoots = mutableSetOf<String>()

    for (error in errors) {
      val keyword = error.keyword
      val evalPath = error.evaluationPath?.toString()

      if (keyword == "oneOf" || keyword == "anyOf") {
        // The composition error itself. Its own evaluationPath ENDS with /oneOf or /anyOf,
        // not with a branch index — so extractCompositionRoot would return the same string.
        // Use it as the composition root directly: mark this root as "covered".
        // Guard: also mark any compRoot we can derive from its evalPath, to handle cases
        // where networknt emits the composition error with its own extractable compRoot.
        if (evalPath != null) coveredCompositionRoots.add(evalPath)
        continue
      }
      if (keyword != "type") continue

      evalPath ?: continue
      val instanceLoc = error.instanceLocation?.toString() ?: continue
      val compRoot = extractCompositionRoot(evalPath) ?: continue
      val branchIdx = extractBranchIndex(evalPath, compRoot) ?: continue
      // Require TOP-LEVEL branch type errors (same criterion as in Task 2). We INTENTIONALLY
      // keep the strict `rest == "/type"` form here (not [isTopLevelBranchTypeError], which
      // also accepts `$ref` redirects) because expanding it would re-merge type errors that
      // existing test suites rely on being routed through individual branch handling.
      val rest = evalPath.substring(compRoot.length + 1 + branchIdx.toString().length)
      if (rest != "/type") continue

      groups.getOrPut(MergeKey(compRoot, instanceLoc)) { mutableListOf() }.add(error)
    }

    val synthesised = mutableMapOf<PsiElement, JsonValidationError>()
    for ((key, groupErrors) in groups) {
      if (key.compositionRoot in coveredCompositionRoots) continue
      // Require distinct branches — otherwise nothing to merge.
      val distinctBranchCount = groupErrors
        .mapNotNull { extractBranchIndex(it.evaluationPath?.toString(), key.compositionRoot) }
        .toSet().size
      if (distinctBranchCount < 2) continue

      val psiElement = locationIndex.resolve(groupErrors.first().instanceLocation ?: continue) ?: continue

      val allExpectedTypes = groupErrors
        .flatMap { error ->
          val expectedStr = error.arguments?.getOrNull(1)?.toString() ?: ""
          parseExpectedTypes(expectedStr).toList()
        }
        .distinct()
      if (allExpectedTypes.isEmpty()) {
        if (LOG.isDebugEnabled) {
          LOG.debug("synthesiseMissingCompositionMerges: dropping group at ${key.compositionRoot}/${key.instanceLocation} — no parseable expected types in ${groupErrors.size} branch errors")
        }
        continue
      }

      val actualTypeStr = groupErrors.firstOrNull()?.arguments?.getOrNull(0)?.toString()
      val actualType = actualTypeStr?.let { parseSchemaType(it) }
        ?: detectActualType(groupErrors.first().instanceNode)

      val issueData = TypeMismatchIssueData(allExpectedTypes.toTypedArray())
      val message = buildTypeErrorMessage(allExpectedTypes.toTypedArray(), actualType, isComposition = true)
      // PSI-collision note: if two distinct composition roots produce synth-merged errors that
      // resolve to the SAME psiElement, the later iteration silently wins. The iteration order
      // of [groups] is insertion-order (LinkedHashMap semantics of mutableMapOf), so the result
      // is deterministic for a given error list but not semantically meaningful. In practice a
      // single PSI node never sits under two unrelated composition roots at the same time, so
      // this case does not occur in real schemas. Revisit if a regression shows up.
      synthesised[psiElement] = JsonValidationError(
        message,
        FixableIssueKind.ProhibitedType,
        issueData,
        JsonErrorPriority.TYPE_MISMATCH,
      )
    }
    return synthesised
  }

  /**
   * Whether an existing mapped error should be replaced by a freshly-synthesised Variant-3
   * composition merge. Returns true iff:
   *   - there is no existing error, OR
   *   - the existing error is a single-branch `ProhibitedType` (`TypeMismatchIssueData` with
   *     exactly one expected type) — i.e., an individual `mapTypeError` result.
   *
   * Returns false for all other existing errors — notably an already-merged Variant-3 result
   * produced by [mapCompositionError] (2+ expected types), and any non-type error
   * (required/dependency/pattern/etc.).
   */
  private fun shouldOverlayWithSynthMerge(existing: JsonValidationError?): Boolean {
    if (existing == null) return true
    if (existing.fixableIssueKind != FixableIssueKind.ProhibitedType) return false
    return (existing.issueData as? TypeMismatchIssueData)?.expectedTypes?.size == 1
  }

  /**
   * Detects groups of branch `enum` errors that share an instance location AND a composition
   * root in their evaluationPath, but have no `keyword = "oneOf"/"anyOf"` sibling at the same
   * instance location to drive [mapCompositionError]. Builds one merged "Value should be one
   * of: <union>" error per group — indexed by PsiElement — for overlay by the caller.
   *
   * Mirrors [synthesiseMissingCompositionMerges] for the `type` keyword. Fires only when every
   * error in the group has `keyword == "enum"` and there are at least TWO distinct branches
   * represented.
   *
   * Groups by the **outermost** composition root so nested anyOf/oneOf trees with enum leaves
   * collapse into a single union (matches the legacy engine's recursive flattening).
   */
  private fun synthesiseMissingCompositionEnumMerges(
    errors: Collection<Error>,
  ): Map<PsiElement, JsonValidationError> {
    data class MergeKey(val compositionRoot: String, val instanceLocation: String)
    data class Annotated(val error: Error, val outerBranchIdx: Int)

    val groups = mutableMapOf<MergeKey, MutableList<Annotated>>()
    val coveredCompositionRoots = mutableSetOf<String>()

    for (error in errors) {
      val keyword = error.keyword
      val evalPath = error.evaluationPath?.toString()

      if (keyword == "oneOf" || keyword == "anyOf") {
        if (evalPath != null) coveredCompositionRoots.add(evalPath)
        continue
      }
      if (keyword != "enum") continue

      evalPath ?: continue
      val instanceLoc = error.instanceLocation?.toString() ?: continue
      // Innermost composition hosts the enum leaf — verify the error ends exactly at
      // <innermostBranch>/enum, not deeper. Guards against accidentally merging enums
      // that belong to some non-leaf sub-schema.
      val innermostCompRoot = extractCompositionRoot(evalPath) ?: continue
      val innermostBranchIdx = extractBranchIndex(evalPath, innermostCompRoot) ?: continue
      val rest = evalPath.substring(innermostCompRoot.length + 1 + innermostBranchIdx.toString().length)
      if (rest != "/enum") continue

      // Outermost composition drives the merge group: leaves from every nesting level roll
      // up into the same union, matching the legacy engine's recursive flatten semantics.
      val outer = extractOutermostComposition(evalPath) ?: continue

      groups.getOrPut(MergeKey(outer.first, instanceLoc)) { mutableListOf() }
            .add(Annotated(error, outer.second))
    }

    val synthesised = mutableMapOf<PsiElement, JsonValidationError>()
    for ((key, annotatedErrors) in groups) {
      if (key.compositionRoot in coveredCompositionRoots) continue
      val distinctOuterBranches = annotatedErrors.map { it.outerBranchIdx }.toSet().size
      if (distinctOuterBranches < 2) continue

      val psiElement = locationIndex.resolve(annotatedErrors.first().error.instanceLocation ?: continue) ?: continue

      val allEnumValues = linkedSetOf<String>()
      for (annotated in annotatedErrors) {
        val schemaNode = annotated.error.schemaNode ?: continue
        if (!schemaNode.isArray) continue
        for (i in 0 until schemaNode.size()) {
          allEnumValues.add(formatJsonValue(schemaNode.get(i)))
        }
      }
      if (allEnumValues.isEmpty()) continue

      // Sort for stable, reader-friendly output. `allEnumValues` are formatted JSON literals
      // ("1", "\"foo\"", etc.); alphabetical sort gives natural numeric order for single-digit
      // integers and a consistent ordering for strings — mirrors the legacy engine which also
      // did not preserve branch-insertion order (see JsonSchemaReSharperHighlightingTest#test022).
      val message = JsonBundle.message("schema.validation.enum.mismatch", allEnumValues.sorted().joinToString(", "))
      synthesised[psiElement] = JsonValidationError(
        message,
        FixableIssueKind.NonEnumValue,
        null,
        JsonErrorPriority.MEDIUM_PRIORITY,
      )
    }
    return synthesised
  }

  /**
   * Whether an existing mapped error should be replaced by a freshly-synthesised enum union
   * merge. Returns true iff the existing error is absent or is a single-branch
   * [FixableIssueKind.NonEnumValue] — i.e., an individual `mapEnumError` result from one branch
   * whose message we now want to replace with the union across all branches.
   *
   * Returns false for [FixableIssueKind.ProhibitedType] (which already carries the stronger
   * type-mismatch semantics) and any other kind.
   */
  private fun shouldOverlayWithSynthEnumMerge(existing: JsonValidationError?): Boolean {
    if (existing == null) return true
    return existing.fixableIssueKind == FixableIssueKind.NonEnumValue
  }

  // DEMO FLAG: remove together with json.schema.networknt.show.source.prefix registry key
  private fun withSourcePrefix(error: JsonValidationError): JsonValidationError {
    @NonNls val prefix = "[networknt] "
    return JsonValidationError(prefix + error.message, error.fixableIssueKind, error.issueData, error.priority)
  }
}
