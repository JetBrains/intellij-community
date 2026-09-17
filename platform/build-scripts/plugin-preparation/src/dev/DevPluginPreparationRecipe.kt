package org.jetbrains.intellij.build.dev

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.devDist.PluginPackingPlan
import org.jetbrains.intellij.build.devDist.PluginPackingPreparation
import org.jetbrains.intellij.build.devDist.devDistSignature

private val preparationRecipeJson = Json { encodeDefaults = true }

/** The recipe format of every generated operation. The generator and the preparer hash an operation with this version. */
@ApiStatus.Internal
const val DEV_PLUGIN_PREPARATION_FORMAT: Int = 2

@ApiStatus.Internal
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DevPluginPreparationRecipe(
  @EncodeDefault(EncodeDefault.Mode.ALWAYS) @JvmField val version: Int = 1,
  @JvmField val operations: List<DevPluginPreparationOperation>,
)

/**
 * A native-extract operation copies one archive entry to its output. Its mode must match every consuming copy asset.
 * A native-archive operation attaches ordered replacements and reservations to one archive source.
 * Native operations use explicit entries and replacement artifacts. They do not select a platform or sign files.
 */
@ApiStatus.Internal
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DevPluginPreparationOperation(
  @JvmField val id: String,
  @JvmField val kind: String = "module-filter",
  @SerialName("input") @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val serializedInput: DevPluginReference? = null,
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val inputs: List<DevPluginReference> = emptyList(),
  @JvmField val output: String,
  @JvmField val manifest: String,
  @JvmField val excludes: List<String> = emptyList(),
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val entry: String = "",
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val mode: Int = 0,
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val filter: String = "",
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val overrides: List<DevPluginNativeOverride> = emptyList(),
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val library: DevPluginLibraryResourceConfiguration? = null,
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val resource: DevPluginResourceConfiguration? = null,
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val libraryLayout: DevPluginLibraryLayoutRecipeConfiguration? = null,
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val layoutAssets: DevPluginLayoutAssetPreparation? = null,
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val archiveSha256: String? = null,
) {
  val input: DevPluginReference
    get() = requireNotNull(serializedInput) { "Preparation operation '$id' has no primary input" }

  constructor(
    id: String,
    kind: String = "module-filter",
    input: DevPluginReference,
    output: String,
    manifest: String,
    excludes: List<String> = emptyList(),
    entry: String = "",
    mode: Int = 0,
    filter: String = "",
    overrides: List<DevPluginNativeOverride> = emptyList(),
    library: DevPluginLibraryResourceConfiguration? = null,
    resource: DevPluginResourceConfiguration? = null,
    libraryLayout: DevPluginLibraryLayoutRecipeConfiguration? = null,
    archiveSha256: String? = null,
  ) : this(
    id = id,
    kind = kind,
    serializedInput = input,
    output = output,
    manifest = manifest,
    excludes = excludes,
    entry = entry,
    mode = mode,
    filter = filter,
    overrides = overrides,
    library = library,
    resource = resource,
    libraryLayout = libraryLayout,
    archiveSha256 = archiveSha256,
  )
}

/**
 * Returns the SHA-256 signature for [PluginPackingPreparation.modelSignature].
 * The canonical input is compact UTF-8 JSON for a recipe with this operation alone, in declaration order.
 * Default native fields are omitted to preserve the signatures of existing module-filter operations. Other defaults are included.
 * The recipe version and the exclusion order are part of the signature.
 * Library resources preserve the original immutable generator's source signature instead of using the operation JSON.
 * Ordinary resources preserve the original inventory signature. Their runtime verifies the ordered layout and declared outputs.
 */
@ApiStatus.Internal
fun devPluginPreparationOperationSignature(
  operation: DevPluginPreparationOperation,
  version: Int = 1,
): String {
  require(version in 1..2) { "Unsupported preparation recipe version $version" }
  validatePreparationOperation(operation, version)
  if (operation.archiveSha256 != null) return devPluginNativeArchiveOperationSignature(operation)
  if (operation.kind == "library-resource") {
    return devPluginLibraryResourceSignature(requireNotNull(operation.library), operation.input)
  }
  if (operation.kind == "ordinary-resource") {
    return devPluginResourceSignature(operation)
  }
  if (operation.isCallbackPreparation()) return operation.callbackSignature()
  val configuration = preparationRecipeJson.encodeToString(DevPluginPreparationRecipe(version, listOf(operation)))
  return devDistSignature { putString(configuration) }
}

/**
 * Validates the recipe against the required preparations and the catalogue without reading files.
 * A Go-executed operation ([isGoExecutedOperation]) is validated and gets no action: the recipe states it.
 */
@ApiStatus.Internal
fun compileDevPluginPreparationActions(
  recipe: DevPluginPreparationRecipe,
  plan: PluginPackingPlan,
  catalogue: DevPluginArtifactCatalogue,
): Map<String, DevPluginPreparationAction> {
  require(recipe.version in 1..2) { "Unsupported preparation recipe version ${recipe.version}" }
  validateDevPluginPreparationOutputs(plan, catalogue)
  val definitions = plan.preparations.associateBy(PluginPackingPreparation::id)
  val operations = LinkedHashMap<String, DevPluginPreparationOperation>()
  for (operation in snapshotDevPluginPreparationRecipe(recipe).operations) {
    validatePreparationOperation(operation, recipe.version)
    require(operations.putIfAbsent(operation.id, operation) == null) { "Duplicate preparation operation '${operation.id}'" }
    val definition = requireNotNull(definitions.get(operation.id)) { "Unexpected preparation operation '${operation.id}'" }
    if (operation.isCallbackPreparation()) {
      val requiredInputs = operation.sourceReferences().map { it.artifact }.distinct()
      require(definition.inputs == requiredInputs) { "Preparation '${operation.id}' must declare exactly inputs $requiredInputs" }
    }
    else if (operation.kind == "layout-assets") {
      val requiredInputs = operation.sourceReferences().map(DevPluginReference::artifact).distinct()
      require(definition.inputs == requiredInputs) { "Preparation '${operation.id}' must declare exactly inputs $requiredInputs" }
    }
    else if (operation.kind == "ordinary-resource") {
      val requiredInputs = operation.sourceReferences().map { it.artifact }
      require(definition.inputs == requiredInputs) { "Preparation '${operation.id}' must declare exactly inputs $requiredInputs" }
    }
    else if (operation.kind == "native-archive") {
      val requiredInputs = (listOf(operation.input) + operation.overrides.mapNotNull(DevPluginNativeOverride::input))
        .mapTo(LinkedHashSet(), DevPluginReference::artifact)
      require(definition.inputs.size == requiredInputs.size && definition.inputs.toSet() == requiredInputs) {
        "Preparation '${operation.id}' must declare exactly inputs $requiredInputs"
      }
    }
    else {
      require(definition.inputs == listOf(operation.input.artifact)) {
        "Preparation '${operation.id}' must declare exactly input '${operation.input.artifact}'"
      }
    }
    require(definition.outputs == operation.declaredOutputs()) {
      if (operation.kind == "ordinary-resource") "Preparation '${operation.id}' must declare exactly outputs ${operation.declaredOutputs()}"
      else "Preparation '${operation.id}' must declare exactly output '${operation.output}'"
    }
    val computedSignature = devPluginPreparationOperationSignature(operation, recipe.version)
    require(definition.modelSignature == computedSignature) {
      "Preparation '${operation.id}' has a stale operation signature: stated=${definition.modelSignature} computed=$computedSignature. " +
      "Regenerate the dev distribution declarations."
    }
    if (operation.kind == "native-extract") {
      val consumers = plan.assets.filter { operation.output in it.asset.inputs }
      require(consumers.isNotEmpty() && consumers.all {
        it.artifact == null && it.asset.recipe == null && it.asset.symlinkTarget == null &&
        it.asset.inputs == listOf(operation.output) && it.asset.mode == operation.mode
      }) { "Native extraction '${operation.id}' requires copy assets with mode ${operation.mode}" }
    }
    if (operation.kind == "library-resource") {
      val library = requireNotNull(operation.library)
      val consumers = plan.assets.filter { operation.output in it.asset.inputs }
      require(consumers.size == 1 && consumers.all {
        it.artifact == null && it.asset.kind == "tree" && it.asset.destination == library.targetPath &&
        it.asset.inputs == listOf(operation.output) && !it.asset.classPath
      }) { "Library resource '${operation.id}' requires one tree asset at '${library.targetPath}'" }
    }
    if (operation.kind == "layout-assets" && requireNotNull(operation.layoutAssets).format == "tree") {
      val layoutAssets = requireNotNull(operation.layoutAssets)
      val consumers = plan.assets.filter { operation.output in it.asset.inputs }
      require(consumers.size == 1 && consumers.single().artifact == null && consumers.single().asset.kind == "tree" &&
              consumers.single().asset.destination == layoutAssets.root && consumers.single().asset.inputs == listOf(operation.output) &&
              !consumers.single().asset.classPath) {
        "Layout asset preparation '${operation.id}' requires one tree asset at '${layoutAssets.root}'"
      }
    }
    if (operation.kind == "layout-assets" && requireNotNull(operation.layoutAssets).format == "file") {
      val layoutAssets = requireNotNull(operation.layoutAssets)
      val consumers = plan.assets.filter { operation.output in it.asset.inputs }
      require(consumers.size == 1 && consumers.single().artifact == null && consumers.single().asset.kind == "file" &&
              consumers.single().asset.destination == layoutAssets.root && consumers.single().asset.inputs == listOf(operation.output) &&
              consumers.single().asset.recipe == null && !consumers.single().asset.classPath) {
        "Layout asset preparation '${operation.id}' requires one file asset at '${layoutAssets.root}'"
      }
    }
  }
  val missing = definitions.keys - operations.keys
  require(missing.isEmpty()) { "Missing preparation operations: $missing" }
  validateDevPluginResourceOperations(operations.values.toList(), plan)
  validateDevPluginCallbackOperations(operations.values.toList(), plan)
  val callbackActions = compileDevPluginCallbackActions(operations.values.toList(), plan, catalogue)
  val resourceActions by lazy { compileDevPluginResources(operations.values.filter { it.kind == "ordinary-resource" }, plan, catalogue) }
  val inputs = PreparationCatalogue(catalogue)
  val actions = LinkedHashMap<String, DevPluginPreparationAction>()
  for (operation in operations.values) {
    if (operation.isCallbackPreparation()) {
      validateDevPluginCallbackReferences(operation, plan) { inputs.requireReference(it).kind }
      @Suppress("ReplacePutWithAssignment")
      actions.put(operation.id, callbackActions.getValue(operation.id))
      continue
    }
    if (isGoExecutedOperation(operation)) {
      operation.sourceReferences().forEach(inputs::requireReference)
      continue
    }
    val artifact = if (operation.kind == "layout-assets") null else inputs.requireReference(operation.input)
    if (operation.library != null) {
      require(requireNotNull(artifact).kind == operation.library.inputKind) { "The library artifact kind changed" }
    }
    operation.sourceReferences().forEach(inputs::requireReference)
    operation.resource?.let { resource ->
      require(requireNotNull(artifact).kind == resource.inputKind) { "The resource artifact kind changed" }
      resource.timestampMetadata?.let { require(inputs.requireReference(it).kind == "file") { "Timestamp metadata must have the file kind" } }
    }
    val action = when (operation.kind) {
      "ordinary-resource" -> DevPluginPreparationAction { context -> resourceActions.getValue(operation.id).prepare(context) }
      "module-filter" -> error("Preparation '${operation.id}' is executed by the Go packer")
      "native-extract" -> DevPluginPreparationAction { context ->
        listOf(context.prepareNativeExtraction(operation.output, operation.input, operation.entry))
      }
      "native-archive" -> DevPluginPreparationAction { context ->
        if (operation.archiveSha256 != null) {
          require(context.definition == definitions.getValue(operation.id)) { "Native preparation '${operation.id}' has a stale replay definition" }
        }
        listOf(context.prepareNativeArchive(operation.output, operation.input, operation.manifest, operation.filter, operation.overrides,
                                            operation.archiveSha256))
      }
      "native-presigned" -> compileDevPluginPresignedNativeAction(operation, plan)
      "library-resource" -> DevPluginPreparationAction { context ->
        val archive = context.inputPath(operation.input)
        listOf(context.prepareTree(operation.output) { tree -> extractDevPluginLibraryResources(archive, tree) })
      }
      "layout-assets" -> compileDevPluginLayoutAssetAction(operation)
      else -> error("Unknown preparation operation kind '${operation.kind}'")
    }
    actions.put(operation.id, action)
  }
  return actions
}

private fun validatePreparationOperation(operation: DevPluginPreparationOperation, version: Int) {
  require(operation.kind in setOf("module-filter", "native-extract", "native-archive", "native-presigned", "library-resource", "ordinary-resource",
                                "library-layout-filter", "library-layout-patches", "layout-assets")) {
    "Unknown preparation operation kind '${operation.kind}'"
  }
  require(operation.kind == "library-resource" || operation.library == null) { "Only a library-resource operation may declare library fields" }
  require(operation.kind == "ordinary-resource" || operation.resource == null) { "Only an ordinary-resource operation may declare resource fields" }
  require(operation.kind == "layout-assets" || operation.layoutAssets == null) { "Only a layout-assets operation may declare layout assets" }
  require(operation.kind == "native-archive" || operation.archiveSha256 == null) { "Only a native-archive operation may declare an archive digest" }
  operation.archiveSha256?.let { digest ->
    require(digest.matches(Regex("[0-9a-f]{64}"))) { "A native archive digest must be a lowercase SHA-256 value" }
  }
  require(operation.kind in setOf("library-layout-filter", "library-layout-patches") || operation.libraryLayout == null) {
    "Only a library layout operation may declare library layout fields"
  }
  require(operation.manifest in setOf("keep", "drop", "coverage-agent", "rewrite-boot-class-path")) {
    "Unknown preparation manifest policy '${operation.manifest}'"
  }
  val references = operation.sourceReferences()
  for (id in listOf(operation.id) + operation.declaredOutputs() + references.map(DevPluginReference::artifact)) {
    require(id.isNotBlank() && id.trim() == id && id.none { it == '\u0000' || it == '\r' || it == '\n' }) {
      "Invalid preparation ID '$id'"
    }
  }
  for (reference in references) {
    if (reference.path.isNotEmpty()) {
      validatePreparationPath(reference.path)
    }
  }
  when (operation.kind) {
    "library-layout-filter", "library-layout-patches" -> {
      require(version == 2) { "A callback operation requires preparation recipe version 2" }
      operation.callbackSignature()
    }
    "ordinary-resource" -> {
      require(version == 2) { "An ordinary-resource operation requires preparation recipe version 2" }
      require(operation.manifest == "keep" && operation.excludes.isEmpty() && operation.entry.isEmpty() && operation.mode == 0 &&
              operation.filter.isEmpty() && operation.overrides.isEmpty()) { "An ordinary-resource operation requires the original resource policy" }
      devPluginResourceSignature(operation)
    }
    "layout-assets" -> {
      require(version == 2) { "A layout-assets operation requires preparation recipe version 2" }
      require(operation.serializedInput == null && operation.manifest == "keep" && operation.excludes.isEmpty() && operation.entry.isEmpty() &&
              operation.mode == 0 && operation.filter.isEmpty() && operation.overrides.isEmpty() && operation.library == null &&
              operation.resource == null && operation.libraryLayout == null && operation.archiveSha256 == null) {
        "A layout-assets operation requires its original layout policy"
      }
      validateDevPluginLayoutAssetPreparation(requireNotNull(operation.layoutAssets) { "A layout-assets operation requires layout assets" }, operation.inputs)
    }
    "library-resource" -> {
      require(version == 2) { "A library-resource operation requires preparation recipe version 2" }
      require(operation.manifest == "keep" && operation.excludes.isEmpty() && operation.entry.isEmpty() && operation.mode == 0 &&
              operation.filter.isEmpty() && operation.overrides.isEmpty()) { "A library-resource operation requires the original extraction policy" }
      devPluginLibraryResourceSignature(requireNotNull(operation.library) { "A library-resource operation requires library fields" }, operation.input)
    }
    "module-filter" -> require(operation.entry.isEmpty() && operation.mode == 0 && operation.filter.isEmpty() && operation.overrides.isEmpty()) {
      "A module-filter operation must not declare native fields"
    }
    "native-extract" -> {
      validatePreparationPath(operation.entry)
      require(operation.mode in 1..511) { "Unsupported native extraction mode ${operation.mode}" }
      require(operation.manifest == "keep" && operation.excludes.isEmpty() && operation.filter.isEmpty() && operation.overrides.isEmpty()) {
        "A native-extract operation requires keep policy and no filters or overrides"
      }
    }
    "native-archive" -> {
      require(operation.entry.isEmpty() && operation.mode == 0 && operation.excludes.isEmpty()) {
        "A native-archive operation must not declare an extraction entry, mode, or custom exclusions"
      }
      validateDevPluginNativeOverrides(operation.filter, operation.overrides)
    }
    "native-presigned" -> {
      require(version == 2) { "A presigned native operation requires preparation recipe version 2" }
      require(operation.entry.isEmpty() && operation.mode == 0 && operation.excludes.isEmpty() && operation.overrides.isEmpty() &&
              operation.manifest == "keep" && operation.filter == "library" && operation.archiveSha256 == null) {
        "A presigned native operation requires the original library policy"
      }
    }
  }
}

/** Copies mutable recipe values before they cross the generation and execution boundary. */
@ApiStatus.Internal
fun snapshotDevPluginPreparationRecipe(recipe: DevPluginPreparationRecipe): DevPluginPreparationRecipe {
  return recipe.copy(operations = java.util.List.copyOf(recipe.operations.map { operation ->
    operation.copy(
      inputs = java.util.List.copyOf(operation.inputs),
      excludes = java.util.List.copyOf(operation.excludes),
      overrides = java.util.List.copyOf(operation.overrides),
      resource = operation.resource?.let { it.copy(entries = java.util.List.copyOf(it.entries), outputs = java.util.List.copyOf(it.outputs)) },
      layoutAssets = operation.layoutAssets?.let { preparation ->
        preparation.copy(assets = java.util.List.copyOf(preparation.assets.map { asset ->
          asset.copy(
            sources = java.util.List.copyOf(asset.sources),
            transform = asset.transform?.let { transform ->
              transform.copy(mappings = java.util.List.copyOf(transform.mappings))
            },
          )
        }))
      },
    )
  }))
}

internal fun DevPluginPreparationOperation.sourceReferences(): List<DevPluginReference> {
  return if (kind == "layout-assets") inputs
  else if (isCallbackPreparation()) callbackSourceReferences()
  else if (kind == "ordinary-resource") listOfNotNull(input, requireNotNull(resource).timestampMetadata)
  else listOf(input) + overrides.mapNotNull(DevPluginNativeOverride::input)
}
