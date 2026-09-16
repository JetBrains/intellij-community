@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.dev

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.devDist.CanonicalJarRecipe
import org.jetbrains.intellij.build.devDist.PluginPackingPlan
import java.nio.file.FileSystems

@ApiStatus.Internal
data class DevPluginArtifactKind(
  @JvmField val id: String,
  @JvmField val kind: String,
)

/** Supplies the selected raw roots and ordered library references without physical paths. Archive roots denote files. */
@ApiStatus.Internal
data class DevPluginPlanCatalogue(
  @JvmField val artifacts: List<DevPluginArtifactKind>,
  @JvmField val libraries: List<DevPluginLibrary> = emptyList(),
)

@ApiStatus.Internal
fun DevPluginArtifactCatalogue.toPlanCatalogue(): DevPluginPlanCatalogue {
  return DevPluginPlanCatalogue(artifacts.map { DevPluginArtifactKind(it.id, it.kind) }, libraries)
}

/**
 * The raw and prepared inputs of one plan. [inputs] is the ordered remainder input set, prepared outputs included. The
 * preparer writes the catalogue in this order. [remainderInputs] is [inputs] without the prepared outputs.
 * [preparationInputs] is the ordered raw input set of the preparations that run a Kotlin action.
 * A Go-executed operation ([isGoExecutedOperation]) has no action: its raw inputs are remainder inputs.
 */
@ApiStatus.Internal
data class DevPluginInputDerivation(
  @JvmField val inputs: List<String>,
  @JvmField val preparationInputs: List<String>,
  @JvmField val remainderInputs: List<String>,
)

/** Derives the input ownership from the selected assets and the operations without reading payloads or running preparation actions. */
@ApiStatus.Internal
fun deriveDevPluginInputs(
  plan: PluginPackingPlan,
  catalogue: DevPluginPlanCatalogue,
  operations: List<DevPluginPreparationOperation>,
): DevPluginInputDerivation {
  require(plan.preparations.map { it.id }.distinct().size == plan.preparations.size) { "Duplicate preparation IDs" }
  val outputs = plan.preparations.flatMap { it.outputs }
  require(outputs.distinct().size == outputs.size) { "Duplicate preparation outputs" }
  require(plan.requiredInputs.distinct().size == plan.requiredInputs.size) { "Duplicate required inputs" }
  val inputs = InputCatalogue(catalogue)
  require(outputs.none { inputs.contains(it) }) { "A preparation output aliases a raw catalogue ID" }
  val prepared = operations.flatMap { operation -> operation.declaredOutputs().map { it to operation } }.toMap()
  val assets = deriveDevPluginExecutionAssets(plan)
  val independent = assets.filter { it.producer == "independent" }.mapTo(HashSet(), DevPluginExecutionAsset::artifact)
  require(catalogue.artifacts.none { it.id in independent } && catalogue.libraries.none { it.id in independent } &&
          outputs.none { it in independent }) { "Independent artifacts must not be preparation inputs or outputs" }
  val requiredRawInputs = plan.requiredInputs.flatMapTo(LinkedHashSet()) { input ->
    inputs.library(input)?.files?.map(DevPluginReference::artifact) ?: listOf(input)
  }
  require(
    catalogue.artifacts.mapTo(HashSet(), DevPluginArtifactKind::id) == requiredRawInputs &&
    catalogue.libraries.all { it.id in plan.requiredInputs }) {
    "Plugin '${plan.plugin}' has stale preparation inputs: expected=$requiredRawInputs"
  }

  val usedInputs = LinkedHashSet<String>()
  val goExecuted = operations.filter(::isGoExecutedOperation).mapTo(HashSet(), DevPluginPreparationOperation::id)
  // The remainder reads the raw inputs of a Go-executed operation in place of its output.
  fun use(input: String) {
    val operation = prepared.get(input)
    if (operation != null && operation.id in goExecuted) {
      operation.sourceReferences().mapTo(usedInputs, DevPluginReference::artifact)
    }
    else {
      usedInputs.add(input)
    }
  }
  for (planned in plan.assets) {
    if (planned.artifact != null) continue
    val asset = planned.asset
    when {
      asset.kind == "tree" -> {
        val input = asset.inputs.single()
        require(input in prepared || inputs.kind(input) == "directory") {
          "Tree '${asset.destination}' requires a directory artifact"
        }
        use(input)
      }
      asset.kind == "directory" || asset.symlinkTarget != null -> Unit
      asset.recipe != null -> deriveJarInputs(asset.recipe, inputs, prepared, asset.destination, ::use)
      else -> {
        require(asset.inputs.size == 1) { "Completed asset '${asset.destination}' requires one declared file" }
        val input = asset.inputs.single()
        val operation = prepared.get(input)
        if (operation == null) {
          inputs.requireReference(DevPluginReference(input))
        }
        else {
          require(
            operation.kind in setOf("native-extract", "ordinary-resource") ||
            operation.kind == "layout-assets" && operation.layoutAssets?.format == "file"
          ) {
            "Completed asset '${asset.destination}' requires a prepared entry source"
          }
        }
        usedInputs.add(input)
      }
    }
  }
  require(usedInputs.none { it in independent }) { "Independent artifacts must not be remainder inputs" }
  val callbackInputs = LinkedHashSet<String>()
  for (preparation in plan.preparations) {
    if (preparation.id in goExecuted) continue
    for (input in preparation.inputs) {
      if (input !in prepared) {
        val library = inputs.library(input)
        if (library == null) callbackInputs.add(input) else library.files.mapTo(callbackInputs, DevPluginReference::artifact)
      }
    }
  }
  return DevPluginInputDerivation(
    inputs = usedInputs.toList(),
    preparationInputs = callbackInputs.toList(),
    remainderInputs = usedInputs.filterNot { it in prepared },
  )
}

/**
 * Checks every operation against the selected preparations and the catalogue kinds. The generator runs it, so a
 * mismatch fails `plugin-model-tool` and not a Bazel action. The preparer runs [compileDevPluginPreparationActions] instead.
 */
@ApiStatus.Internal
fun validateDevPluginOperations(
  plan: PluginPackingPlan,
  catalogue: DevPluginPlanCatalogue,
  operations: List<DevPluginPreparationOperation>,
) {
  val inputs = InputCatalogue(catalogue)
  val definitions = plan.preparations.associateBy { it.id }
  val seen = HashSet<String>()
  for (operation in operations) {
    require(
      operation.kind in setOf(
        "module-filter", "native-extract", "native-archive", "native-presigned", "library-resource", "ordinary-resource",
        "library-layout-filter", "library-layout-patches", "layout-assets"
      )
    ) {
      "Unknown preparation operation kind '${operation.kind}'"
    }
    val signature = devPluginPreparationOperationSignature(operation, DEV_PLUGIN_PREPARATION_FORMAT)
    require(seen.add(operation.id)) { "Duplicate preparation operation '${operation.id}'" }
    val definition = requireNotNull(definitions.get(operation.id)) { "Unexpected preparation operation '${operation.id}'" }
    val references = operation.sourceReferences()
    val requiredInputs = references.mapTo(LinkedHashSet(), DevPluginReference::artifact)
    require(
      if (operation.kind == "ordinary-resource" || operation.kind == "layout-assets" || operation.isCallbackPreparation()) {
        definition.inputs == requiredInputs.toList()
      }
      else if (operation.kind == "native-archive") {
        definition.inputs.size == requiredInputs.size && definition.inputs.toSet() == requiredInputs
      }
      else {
        definition.inputs == listOf(operation.input.artifact)
      }
    ) { "Preparation '${operation.id}' must declare exactly inputs $requiredInputs" }
    require(definition.outputs == operation.declaredOutputs()) { "Preparation '${operation.id}' must declare exactly outputs ${operation.declaredOutputs()}" }
    require(definition.modelSignature == signature) {
      "Preparation '${operation.id}' has a stale operation signature: stated=${definition.modelSignature} computed=$signature"
    }
    if (operation.isCallbackPreparation()) {
      validateDevPluginCallbackReferences(operation, plan) { reference ->
        inputs.requireReference(reference)
        inputs.kind(reference.artifact)
      }
    }
    else references.forEach(inputs::requireReference)
    when (operation.kind) {
      "ordinary-resource" -> {
        val resource = requireNotNull(operation.resource)
        require(inputs.kind(operation.input.artifact) == resource.inputKind) { "The resource artifact kind changed" }
        resource.timestampMetadata?.let { require(inputs.kind(it.artifact) == "file") { "Timestamp metadata must have the file kind" } }
      }
      "module-filter" -> operation.excludes.forEach { FileSystems.getDefault().getPathMatcher("glob:$it") }
      "native-extract" -> {
        val consumers = plan.assets.filter { operation.output in it.asset.inputs }
        require(consumers.isNotEmpty() && consumers.all {
          it.artifact == null && it.asset.recipe == null && it.asset.symlinkTarget == null &&
          it.asset.inputs == listOf(operation.output) && it.asset.mode == operation.mode
        }) { "Native extraction '${operation.id}' requires copy assets with mode ${operation.mode}" }
      }
      "library-resource" -> {
        val library = requireNotNull(operation.library)
        require(inputs.kind(operation.input.artifact) == library.inputKind) { "The library artifact kind changed" }
        val consumers = plan.assets.filter { operation.output in it.asset.inputs }
        require(consumers.size == 1 && consumers.all {
          it.artifact == null && it.asset.kind == "tree" && it.asset.destination == library.targetPath &&
          it.asset.inputs == listOf(operation.output) && !it.asset.classPath
        }) { "Library resource '${operation.id}' requires one tree asset at '${library.targetPath}'" }
      }
      "layout-assets" -> {
        val layoutAssets = requireNotNull(operation.layoutAssets)
        validateDevPluginLayoutAssetPreparation(layoutAssets, operation.inputs)
        if (layoutAssets.format == "tree") {
          val consumers = plan.assets.filter { operation.output in it.asset.inputs }
          require(
            consumers.size == 1 && consumers.single().artifact == null && consumers.single().asset.kind == "tree" &&
            consumers.single().asset.destination == layoutAssets.root && consumers.single().asset.inputs == listOf(operation.output) &&
            !consumers.single().asset.classPath
          ) {
            "Layout asset preparation '${operation.id}' requires one tree asset at '${layoutAssets.root}'"
          }
        }
        if (layoutAssets.format == "file") {
          val consumers = plan.assets.filter { operation.output in it.asset.inputs }
          require(
            consumers.size == 1 && consumers.single().artifact == null && consumers.single().asset.kind == "file" &&
            consumers.single().asset.destination == layoutAssets.root && consumers.single().asset.inputs == listOf(operation.output) &&
            consumers.single().asset.recipe == null && !consumers.single().asset.classPath
          ) {
            "Layout asset preparation '${operation.id}' requires one file asset at '${layoutAssets.root}'"
          }
        }
      }
    }
  }
  val missing = definitions.keys - seen
  require(missing.isEmpty()) { "Missing preparation operations: $missing" }
  validateDevPluginResourceOperations(operations, plan)
  validateDevPluginCallbackOperations(operations, plan)
}

private class InputCatalogue(catalogue: DevPluginPlanCatalogue) {
  private val artifacts = HashMap<String, String>()
  private val libraries = HashMap<String, DevPluginLibrary>()

  init {
    for (artifact in catalogue.artifacts) {
      require(artifact.id.isNotBlank() && artifact.id.trim() == artifact.id && artifact.id.none { it == '\u0000' || it == '\r' || it == '\n' }) {
        "Invalid artifact ID '${artifact.id}'"
      }
      require(artifact.kind in setOf("archive", "file", "directory")) { "Unknown artifact root kind '${artifact.kind}'" }
      require(artifacts.putIfAbsent(artifact.id, if (artifact.kind == "archive") "file" else artifact.kind) == null) {
        "Duplicate artifact ID '${artifact.id}'"
      }
    }
    for (library in catalogue.libraries) {
      require(
        library.id.isNotBlank() && library.files.isNotEmpty() && library.files.size == library.files.toSet().size &&
        libraries.putIfAbsent(library.id, library) == null && !artifacts.containsKey(library.id)
      ) {
        "Invalid or duplicate library '${library.id}'"
      }
      library.files.forEach(::requireReference)
    }
  }

  fun contains(id: String): Boolean = artifacts.containsKey(id) || libraries.containsKey(id)

  fun kind(id: String): String = requireNotNull(artifacts.get(id)) { "Unresolved input '$id'" }

  fun library(id: String): DevPluginLibrary? = libraries.get(id)

  fun requireReference(reference: DevPluginReference) {
    if (kind(reference.artifact) == "directory") {
      if (reference.path.isNotEmpty()) validatePreparationPath(reference.path)
    }
    else {
      require(reference.path.isEmpty()) { "File input '${reference.artifact}' cannot have a relative path" }
    }
  }
}

private fun deriveJarInputs(
  recipe: CanonicalJarRecipe,
  catalogue: InputCatalogue,
  prepared: Map<String, DevPluginPreparationOperation>,
  destination: String,
  use: (String) -> Unit,
) {
  require(recipe.writer.outputName.isEmpty() || recipe.writer.outputName == destination.substringAfterLast('/')) {
    "The writer output name does not match '$destination'"
  }
  require(recipe.writer.manifest in setOf("single-meaningful-source", "keep", "drop")) {
    "Unknown manifest policy '${recipe.writer.manifest}'"
  }
  require(!recipe.writer.rewriteBootClassPath || destination.substringAfterLast('/').contains("intellij.platform.coverage.agent")) {
    "Coverage manifest rewriting requires the coverage agent destination"
  }
  for (source in recipe.sources) {
    require(source.options.size == source.options.toSet().size && source.options.all {
      it in setOf("patch", "lib-module", "manifest=keep", "manifest=drop", "manifest=coverage-agent", "manifest=rewrite-boot-class-path")
    }) { "Source '${source.input}' requires an unsupported preparation option: ${source.options}" }
    require(source.options.count { it.startsWith("manifest=") } <= 1) { "Source '${source.input}' has conflicting manifest policies" }
    if (source.kind == "prepared") {
      require(source.options.isEmpty() && source.expansion.isEmpty() && source.entry.isEmpty() && source.filter == "prepared") {
        "Prepared source '${source.input}' must materialize its options"
      }
      val operation = requireNotNull(prepared.get(source.input)) { "Unresolved prepared source '${source.input}'" }
      require(recipe.writer.manifest != "single-meaningful-source" || source.preparedManifest != null) {
        "Prepared sources require an explicit manifest policy"
      }
      source.preparedManifest?.let { metadata ->
        val libraryLayout = operation.libraryLayout
        if (libraryLayout != null) {
          require(metadata == libraryLayout.callbackPreparedManifest(source.input)) {
            "Prepared source '${source.input}' has stale library manifest facts"
          }
          return@let
        }
        val policiesMatch = if (metadata.sourceManifestPolicies.isEmpty()) {
          operation.kind == "module-filter" && operation.manifest == "keep"
        }
        else {
          metadata.sourceManifestPolicies == listOf(operation.manifest)
        }
        require(policiesMatch) { "Prepared source '${source.input}' has stale manifest policies" }
        require(metadata.originalMeaningfulSourceCount != null || operation.kind == "module-filter") {
          "Preparation '${operation.id}' does not produce the module patches required by its manifest recipe"
        }
      }
      use(source.input)
      if (operation.kind in setOf("native-archive", "native-presigned") && operation.archiveSha256 == null) {
        use(operation.input.artifact)
      }
      continue
    }
    val filter = when (source.filter) {
      "module", "library", "all" -> source.filter
      "module-v1" -> "module"
      "library-v1" -> "library"
      "none" -> "all"
      else -> throw IllegalArgumentException("Custom filter '${source.filter}' requires declared preparation inputs")
    }
    val references = when (source.kind) {
      "zip", "archive", "module" -> {
        require(source.expansion.isEmpty() && source.entry.isEmpty() && "patch" !in source.options) {
          "Archive source '${source.input}' contains entry or expansion options"
        }
        listOf(DevPluginReference(source.input))
      }
      "library" -> {
        require(source.entry.isEmpty() && "patch" !in source.options) { "Library '${source.input}' contains entry options" }
        val library = requireNotNull(catalogue.library(source.input)) { "Unknown library '${source.input}'" }
        val expansion = library.files.map { if (it.path.isEmpty()) it.artifact else "${it.artifact}/${it.path}" }
        require(source.expansion == expansion) {
          "Library '${source.input}' has stale file order: expected=${source.expansion}, actual=$expansion"
        }
        library.files
      }
      "file" -> {
        require(source.expansion.isEmpty() && filter == "all") { "File source '${source.input}' contains filter or expansion options" }
        validatePreparationPath(source.entry)
        listOf(DevPluginReference(source.input))
      }
      else -> throw IllegalArgumentException("Source kind '${source.kind}' requires declared preparation inputs")
    }
    for (reference in references) {
      catalogue.requireReference(reference)
      use(reference.artifact)
    }
  }
}
