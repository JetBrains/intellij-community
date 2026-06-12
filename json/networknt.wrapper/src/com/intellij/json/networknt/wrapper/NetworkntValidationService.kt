// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.networknt.wrapper

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion
import com.jetbrains.jsonSchema.impl.JsonValidationError
import com.jetbrains.jsonSchema.impl.jsonSchemaTracer
import com.networknt.schema.InvalidSchemaRefException
import com.networknt.schema.Schema
import com.networknt.schema.SpecificationVersion
import com.networknt.schema.path.NodePath
import com.networknt.schema.path.PathType
import java.util.concurrent.CancellationException

/**
 * High-level validation service that orchestrates the networknt validation pipeline.
 *
 * Pipeline:
 * 1. Resolve schema file → networknt Schema (via NetworkntSchemaService)
 * 2. Convert PSI tree → Jackson 3 JsonNode (via PsiToJsonNodeConverter)
 * 3. Build PsiLocationIndex for PSI element lookup
 * 4. Validate instance against schema (with cancellation support)
 * 5. Map errors → IntelliJ JsonValidationError (via NetworkntErrorMapper)
 *
 * This is a PROJECT-level service.
 */
@Service(Service.Level.PROJECT)
class NetworkntValidationService(private val project: Project) {

  companion object {
    private val LOG = Logger.getInstance(NetworkntValidationService::class.java)

    @JvmStatic
    fun getInstance(project: Project): NetworkntValidationService =
      project.getService(NetworkntValidationService::class.java)
  }

  /**
   * Validates a JSON/YAML instance against a schema and re-throws [java.util.concurrent.CancellationException]
   * instead of catching it. Use this when networknt is the primary validation engine — the inspection
   * framework needs PCE to propagate for proper rescheduling.
   */
  fun validateForAnnotation(
    schemaFile: VirtualFile,
    walker: JsonLikePsiWalker,
    rootElement: PsiElement,
    schemaVersion: JsonSchemaVersion,
  ): Map<PsiElement, JsonValidationError> {
    val networkntVersion = mapSchemaVersion(schemaVersion)
    val instanceFile = rootElement.containingFile?.virtualFile

    return jsonSchemaTracer.spanBuilder("networknt.validate")
      .setAttribute("file", instanceFile?.name ?: "unknown")
      .setAttribute("schema", schemaFile.name)
      .use { parentSpan ->
        val schemaService = NetworkntSchemaService.getInstance(project)
        val schema = jsonSchemaTracer.spanBuilder("networknt.schemaLoad").use {
          schemaService.getNetworkntSchema(schemaFile, networkntVersion)
        }

        val instanceNode = jsonSchemaTracer.spanBuilder("networknt.psiConvert").use {
          try {
            convertPsiToJsonNode(walker, rootElement)
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            LOG.warn("convertPsiToJsonNode failed: ${e.message}")
            null
          }
        }

        if (instanceNode == null) {
          parentSpan.setAttribute("error_count", 0L)
          return@use emptyMap()
        }

        val locationIndex = jsonSchemaTracer.spanBuilder("networknt.indexBuild").use {
          PsiLocationIndex.build(walker, rootElement)
        }

        val errors: Collection<com.networknt.schema.Error> = jsonSchemaTracer.spanBuilder("networknt.validation").use {
          try {
            schema.validate(instanceNode) { executionContext ->
              executionContext.executionConfig { configBuilder ->
                configBuilder.cancellationChecker { ProgressManager.checkCanceled() }
              }
            }
          } catch (e: CancellationException) {
            throw e
          } catch (e: InvalidSchemaRefException) {
            LOG.warn("Unresolvable \$ref in schema '${schemaFile.name}': ${e.message}")
            emptySet()
          } catch (e: Exception) {
            LOG.error("Failed to validate with networknt", e)
            emptySet()
          }
        }
        val rawErrorCap = NetworkntErrorMapper.getRawErrorCap()
        if (errors.size > rawErrorCap) {
          LOG.debug("networknt returned ${errors.size} raw errors for '${instanceFile?.name ?: "unknown"}' " +
                   "(cap=$rawErrorCap), switching to safe mode — composition branch errors will be skipped")
        }

        val errorMapper = NetworkntErrorMapper(locationIndex)
        val mapped = jsonSchemaTracer.spanBuilder("networknt.errorMapping").use {
          try {
            errorMapper.mapErrors(errors)
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            LOG.error("Failed to map networknt errors", e)
            emptyMap()
          }
        }

        // Branch-extension pass: validate non-default values of multi-value JsonPropertyAdapters
        // (currently only JS conditional `cond ? then : else` produces these) against their
        // sub-schema. Mirrors the legacy validator's per-value loop in
        // JsonSchemaComplianceChecker.doAnnotate. The main pass already covers the first branch
        // (PsiToJsonNodeConverter / PsiLocationIndex take values.firstOrNull); this pass adds
        // errors that come from branches the main pass did not see.
        val finalMapped = jsonSchemaTracer.spanBuilder("networknt.branchValidation").use {
          extendWithBranchValidation(walker, rootElement, schema, mapped)
        }

        parentSpan.setAttribute("error_count", finalMapped.size.toLong())
        finalMapped
      }
  }

  /**
   * Adds errors from non-default branches of every multi-value [JsonPropertyAdapter] in the
   * tree (today, JS conditional `cond ? then : else` flattened by
   * [JSJsonPropertyAdapter.createValueAdaptersByType]). For each slot we rebuild the full
   * instance with that one slot pinned to a chosen branch and re-run validation; errors
   * attribute to the branch PSI through the rebuilt [PsiLocationIndex].
   *
   * Limitation: coverage is per-slot, not Cartesian. With two parallel slots A and B, we run
   * (A=alt, B=default) and (A=default, B=alt), but never (A=alt, B=alt). Errors that only
   * surface when several conditionals take their non-default branch simultaneously are
   * therefore missed. Matches legacy semantics for the common case (independent ternaries on
   * primitive enum/type checks) and was deliberate to keep the cost additive in the slot
   * count rather than multiplicative; revisit if a real configuration ever hits a true joint
   * dependency. [collectBranchSlots] also walks every branch when collecting, so the same
   * subtree can contribute duplicate slots — bounded by [maxSubValidations] but worth
   * deduplicating if the cap starts firing on real inputs.
   */
  private fun extendWithBranchValidation(
    walker: JsonLikePsiWalker,
    rootElement: PsiElement,
    schema: Schema,
    mainErrors: Map<PsiElement, JsonValidationError>,
  ): Map<PsiElement, JsonValidationError> {
    val slots = collectBranchSlots(walker, rootElement)
    if (slots.isEmpty()) return mainErrors

    val maxSubValidations = Registry.intValue("json.schema.networknt.max.branch.subvalidations", 32)
    val merged = mainErrors.toMutableMap()
    var subCount = 0

    outer@ for (slot in slots) {
      // Skip the first branch — main pass already validated it.
      for (branch in slot.branches.drop(1)) {
        if (subCount >= maxSubValidations) break@outer
        subCount++

        // Per-branch validation: rebuild the WHOLE instance JsonNode and the WHOLE
        // PsiLocationIndex with this slot's property pinned to the chosen branch (selector
        // override). networknt then validates against the full schema as usual; errors at
        // the slot's path naturally attribute to the branch's PSI through the rebuilt index.
        // This avoids navigating through composition keywords (oneOf/anyOf/allOf/$ref) to
        // compute a sub-schema for the property — networknt does that itself during validation.
        val selector: PropertyValueSelector = { prop ->
          if (prop.delegate === slot.propertyDelegate) branch
          else prop.values.firstOrNull()
        }

        val branchTree = try {
          convertPsiToJsonNode(walker, rootElement, selector)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          LOG.debug("Branch convertPsiToJsonNode failed at ${slot.path}: ${e.message}")
          null
        } ?: continue

        val branchIndex = PsiLocationIndex.build(walker, rootElement, selector)

        val branchErrors = try {
          schema.validate(branchTree) { executionContext ->
            executionContext.executionConfig { configBuilder ->
              configBuilder.cancellationChecker { ProgressManager.checkCanceled() }
            }
          }
        } catch (e: CancellationException) {
          throw e
        } catch (e: InvalidSchemaRefException) {
          LOG.debug("Branch validation hit unresolvable \$ref at ${slot.path}: ${e.message}")
          continue
        } catch (e: Exception) {
          LOG.debug("Branch validation failed at ${slot.path}: ${e.message}")
          continue
        }

        val branchMapped = try {
          NetworkntErrorMapper(branchIndex).mapErrors(branchErrors)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          LOG.debug("Branch error mapping failed: ${e.message}")
          continue
        }

        // Only add errors whose target PSI sits inside the branch subtree — errors at unrelated
        // PSIs are duplicates of the main pass (and live at the same PSI element identity).
        // putIfAbsent makes this safe but limiting saves memory and keeps the merged map clean.
        val branchPsi = branch.delegate
        for ((psi, err) in branchMapped) {
          if (PsiTreeUtil.isAncestor(branchPsi, psi, false)) {
            merged.putIfAbsent(psi, err)
          }
        }
      }
    }

    return merged
  }

  /**
   * Maps IntelliJ's JsonSchemaVersion to networknt's SpecificationVersion.
   */
  private fun mapSchemaVersion(version: JsonSchemaVersion): SpecificationVersion {
    return when (version) {
      JsonSchemaVersion.SCHEMA_4 -> SpecificationVersion.DRAFT_4
      JsonSchemaVersion.SCHEMA_6 -> SpecificationVersion.DRAFT_6
      JsonSchemaVersion.SCHEMA_7 -> SpecificationVersion.DRAFT_7
      JsonSchemaVersion.SCHEMA_2019_09 -> SpecificationVersion.DRAFT_2019_09
      JsonSchemaVersion.SCHEMA_2020_12 -> SpecificationVersion.DRAFT_2020_12
      else -> SpecificationVersion.DRAFT_7 // Default fallback to draft-07
    }
  }
}

/**
 * A property whose [JsonPropertyAdapter.values] returned more than one adapter — currently this
 * only happens for JS conditional expressions (`cond ? then : else`), which the JS adapter
 * flattens into a list of branch values.
 *
 * Each non-first branch is validated by rebuilding the whole instance with
 * [propertyDelegate]'s value pinned to that branch via the [PropertyValueSelector]. The
 * recorded [path] is informational only — branch validation routes through the full schema.
 */
private data class BranchSlot(
  val path: NodePath,
  val propertyDelegate: PsiElement,
  val branches: List<JsonValueAdapter>,
)

/**
 * Walks the PSI tree in [JsonValueAdapter] terms and returns every property whose adapter has
 * more than one value (i.e. JS-conditional branches). Recursion descends into every branch so
 * nested conditionals living inside a non-default branch are still found.
 */
private fun collectBranchSlots(walker: JsonLikePsiWalker, rootElement: PsiElement): List<BranchSlot> {
  // Mirror PsiToJsonNodeConverter's PsiFile handling so the same root semantics apply.
  val effectiveRoot = if (rootElement is PsiFile) {
    walker.getRoots(rootElement)?.firstOrNull() ?: rootElement
  } else {
    rootElement
  }
  val rootAdapter = walker.createValueAdapter(effectiveRoot) ?: return emptyList()
  val slots = mutableListOf<BranchSlot>()
  collectSlotsRecursive(rootAdapter, NodePath(PathType.JSON_POINTER), slots)
  return slots
}

private fun collectSlotsRecursive(
  adapter: JsonValueAdapter,
  currentPath: NodePath,
  slots: MutableList<BranchSlot>,
) {
  if (!adapter.shouldCheckAsValue()) return
  when {
    adapter.isObject -> {
      val obj = adapter.asObject ?: return
      for (prop in obj.propertyList) {
        val name = prop.name ?: continue
        val propPath = currentPath.append(name)
        val values = prop.values.toList()
        if (values.size > 1) {
          slots += BranchSlot(propPath, prop.delegate, values)
        }
        for (value in values) {
          collectSlotsRecursive(value, propPath, slots)
        }
      }
    }
    adapter.isArray -> {
      val arr = adapter.asArray ?: return
      arr.elements.forEachIndexed { idx, el ->
        collectSlotsRecursive(el, currentPath.append(idx), slots)
      }
    }
  }
}
