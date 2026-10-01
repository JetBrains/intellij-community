@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.devDistGenerator

import org.jetbrains.intellij.build.dev.devBuildPathIdentity
import org.jetbrains.intellij.build.devDist.pluginPackingExecutionVersion
import org.jetbrains.intellij.build.impl.SUPPORTED_DISTRIBUTIONS

/**
 * The plan file label of one record, and the value of each `{platform:<name>}` slot of a folded plan file for the
 * record's platform. [folded] is true when the record reads a folded plan file. A folded plan file can have no slot.
 * [platformValues] is empty for a neutral record and for a platform record whose fold was refused.
 * [planClass] names the plan text in the plan file name, or is empty for the baseline text.
 */
internal data class DevDistPluginExecutionGraphLabels(
  @JvmField val projection: String,
  @JvmField val platformValues: Map<String, String> = emptyMap(),
  @JvmField val planClass: String = "",
  @JvmField val folded: Boolean = false,
)

/**
 * Label configuration for one plan record. The owner record supplies the original layout binding.
 * A null [targetPlatform] is a neutral record that serves every platform. [packageLabel] is the package of the call,
 * which is always a package of the main repository. [planPackage] is the package of the plan file when it differs
 * from [packageLabel], and empty otherwise. See [DevDistPluginPlanHome] for the rule. [chainClass] names a call that
 * differs from the baseline call of the plugin, or is empty for the baseline call. [name] is the chain stem the macro
 * derives from the main module, [chainClass] and the platform.
 */
internal class DevDistPluginExecutionConfiguration(
  @JvmField val key: DevDistPluginPlanKey,
  @JvmField val packageLabel: String,
  @JvmField val planPackage: String,
  @JvmField val chainClass: String,
  @JvmField val name: String,
  @JvmField val componentName: String,
  @JvmField val targetPlatform: String?,
  @JvmField val graph: DevDistPluginExecutionGraphLabels,
  @JvmField val descriptorLabel: String,
  @JvmField val descriptorVariant: String,
)

/**
 * The `dev_dist_complex_plugin` arguments of one chain, rendered as Starlark, the label of its component, and the slot
 * values of its folded plan file. [platform] is null for a chain that serves every platform. [folded] is true for a
 * chain with a folded plan file. [platformValues] is empty for a chain without a folded plan file, and for a folded
 * plan file without a slot. The fold over the platforms happens in [renderDevDistPluginExecutionCalls].
 */
internal class DevDistPluginExecutionVariant(
  @JvmField val platform: String?,
  @JvmField val arguments: List<Pair<String, String>>,
  @JvmField val componentLabel: String,
  @JvmField val platformValues: Map<String, String>,
  @JvmField val folded: Boolean = false,
)

internal fun renderDevDistPluginExecutionTargets(
  owner: DevDistBuildSections,
  entry: DevDistPluginPlanEntry,
  record: DevDistPluginPlanRecord,
  files: DevDistPluginPlanFiles,
  configuration: DevDistPluginExecutionConfiguration,
): DevDistPluginExecutionVariant {
  val key = configuration.key
  require(key.product == entry.product && key.plugin == entry.mainModule && entry.records.get(key.variant) === record) {
    "The execution configuration has a stale entry or record"
  }
  require(owner.pluginPlanRecords.get(key) === record) { "The execution record does not belong to this owner and entry" }
  val originalDirectoryName = record.requireOriginalLayout(entry.layout(key.variant))
  require(
    record.variant.id == key.variant && record.plan.projection.plugin == entry.mainModule && record.plan.projection.variant == key.variant
  ) { "The execution record has a stale plugin or variant" }
  val platform = configuration.targetPlatform
  if (platform == null) {
    require(key.variant.isEmpty()) { "A neutral execution requires the neutral record" }
  }
  else {
    val distribution = requireNotNull(record.variant.distribution) { "Execution requires an exact platform variant" }
    require(distribution in SUPPORTED_DISTRIBUTIONS) { "Unsupported execution distribution: $distribution" }
    val recordPlatform = devDistHostPlatform(distribution)
    require(platform == recordPlatform && key.variant == platform) { "The execution target platform does not match the record: $recordPlatform" }
  }
  checkExecutionName(entry.product)
  checkExecutionPathComponent(originalDirectoryName)
  checkExecutionName(configuration.name)
  checkExecutionName(configuration.componentName)
  val inCommunity = configuration.packageLabel.startsWith(COMMUNITY_REPOSITORY_PREFIX)
  require(configuration.packageLabel.startsWith("//") || inCommunity) { "Execution declarations must sit in a package of either half" }
  checkExecutionLabel("${configuration.packageLabel}:probe")
  val planPackage = configuration.planPackage
  require(planPackage != configuration.packageLabel) { "The plan package of ${configuration.name} is stated although it is the package of the call" }
  if (planPackage.isNotEmpty()) {
    checkExecutionLabel("$planPackage:probe")
  }
  val plan = record.plan
  requireNotNull(record.preparationRecipe) { "The execution record is incomplete: no preparation recipe" }
  val selected = plan.selectedPlan()
  val version = pluginPackingExecutionVersion(plan.projection.assets)
  require(plan.projection.version == version) { "The execution record has a stale execution version" }
  files.checkExecutionGraph(key, record, configuration.graph)

  val raw = LinkedHashMap<String, DevDistPluginRawInput>()
  val labels = HashSet<String>()
  val directLabels = HashSet<String>()
  val identities = HashSet<String>()
  val artifacts = plan.catalogue.artifacts.associateBy { it.id }
  require(artifacts.size == plan.catalogue.artifacts.size) { "Duplicate catalogue artifact IDs" }
  for (input in plan.requiredRawInputs) {
    checkExecutionId(input.id)
    checkExecutionLabel(input.label)
    checkExecutionPathComponent(input.fileName)
    require(raw.put(input.id, input) == null && identities.add(devBuildPathIdentity(input.id))) { "Duplicate or aliased input ID: ${input.id}" }
    val labelIdentity = devBuildPathIdentity(input.label)
    labels.add(labelIdentity)
    require(input.sourceTreePrefix != null || directLabels.add(labelIdentity)) {
      "Duplicate or aliased input label: ${input.label}"
    }
    val artifact = artifacts.get(input.id)
    require(input.kind in setOf("archive", "file", "directory") && artifact?.kind == input.kind && artifact.fileName == input.fileName) {
      "The input metadata differs from its owner: ${input.id}"
    }
  }
  val libraries = plan.catalogue.libraries.filter { it.id != null }.associateBy { requireNotNull(it.id) }
  require(libraries.size == plan.catalogue.libraries.count { it.id != null }) { "Duplicate catalogue library IDs" }
  val (requiredLibraries, requiredRaw) = selected.requiredInputs.partition { it in libraries }
  require(requiredRaw == raw.keys.toList()) { "The execution record has stale ordered input ownership" }
  require(requiredLibraries == plan.requiredLibraries) { "The execution record has stale ordered library ownership" }
  // The remainder action reads every catalogue artifact, so the derivation must name each raw input and library once.
  val remainderIds = record.remainderInputs
  require(remainderIds.distinct().size == remainderIds.size && remainderIds.toSet() == raw.keys + requiredLibraries) {
    "The execution record has unowned raw inputs"
  }

  val compiled = plan.catalogue.moduleRoots.values.flatten().toHashSet()
  val resources = raw.keys.filterNotTo(LinkedHashSet()) { it in compiled }
  val descriptorLabel = configuration.descriptorLabel
  require(
    configuration.descriptorVariant.isEmpty() || platform != null && (
      configuration.descriptorVariant == platform ||
      configuration.descriptorVariant == platform.substringBeforeLast('_') ||
      configuration.descriptorVariant == platform.substringAfterLast('_'))
  ) {
    "The descriptor variant does not serve ${platform ?: "every platform"}"
  }
  checkExecutionLabel(descriptorLabel)
  val descriptorInput = raw.values.singleOrNull { it.label == descriptorLabel }
  require(descriptorInput?.kind == "file" && descriptorInput.id !in compiled) {
    "The descriptor target must be one owned generated file"
  }
  resources.add(descriptorInput.id)
  require(resources.all { it in raw && it !in compiled }) { "Resource inputs must name owned source inputs, not compiled modules or libraries" }
  // A library reaches the catalogue as its container label, which is also its ID. The catalogue rule expands it to
  // the member jars, so no member is a raw input of the same chain.
  for (library in requiredLibraries) {
    checkExecutionId(library)
    checkExecutionLabel(library)
    require(identities.add(devBuildPathIdentity(library))) { "Library and artifact IDs collide: $library" }
    require(directLabels.add(devBuildPathIdentity(library))) { "Duplicate or aliased library label: $library" }
    labels.add(devBuildPathIdentity(library))
    val member = libraries.getValue(library).files.firstOrNull { it in raw }
    require(member == null) { "Library '$library' is required as a whole, and its member '$member' is a raw input too" }
  }

  // A reused jar is keyed by its module. The chain names the `content_module_jar` target; both rules read the module
  // name from its provider. The recipe of the matched asset must equal the recipe the target packs: the verdict of the
  // section owner is the one place that states it, and this check is the generation-time guard of that equality.
  val canonical = owner.verdicts.contentModuleJarLabels
  val reused = plan.reusedModules
  require(reused.distinct().size == reused.size) { "The execution record has a duplicate reused module" }
  val independent = LinkedHashMap<String, String>()
  for (artifact in plan.reusableArtifacts) {
    val binding = requireNotNull(canonical.get(artifact.module)) { "The reused module has no content_module_jar target: ${artifact.module}" }
    require(binding.canonicalArtifact == artifact) {
      "The reused jar of '${artifact.module}' does not match the recipe its content_module_jar target packs"
    }
    checkExecutionLabel(binding.label)
    require(independent.put(binding.label, artifact.module) == null) { "Duplicate independent provider: ${binding.label}" }
    val excluded = listOf(binding.label, "${binding.label}.production.jar", "${binding.label}.production.metadata.json").map(::devBuildPathIdentity)
    require(excluded.none { it in labels } && devBuildPathIdentity(descriptorLabel) !in excluded) {
      "An independent artifact overlaps a raw input or descriptor: ${binding.label}"
    }
  }
  val names = listOf("graph", "catalogue", "remainder", "component").associateWith { "${configuration.name}_$it" }
  val sourceTrees = raw.values.filter { it.sourceTreePrefix != null }
  val declarations = names.values.map { "${configuration.packageLabel}:$it" }
  val graphLabels = listOf(configuration.graph.projection)
  graphLabels.forEach(::checkExecutionLabel)
  val inputs = buildList {
    raw.values.mapTo(this) { it.label }
    addAll(requiredLibraries)
    addAll(independent.keys)
    independent.keys.mapTo(this) { "$it.production.jar" }
    add(descriptorLabel)
    independent.keys.mapTo(this) { "$it.production.metadata.json" }
  }
  val inputIdentities = inputs.mapTo(HashSet(), ::devBuildPathIdentity)
  val graphIdentities = graphLabels.map(::devBuildPathIdentity)
  require(graphIdentities.distinct().size == graphIdentities.size && graphIdentities.none { it in inputIdentities }) {
    "A graph file collides with an input label"
  }
  val dependencies = inputs + graphLabels
  require(declarations.map(::devBuildPathIdentity).none { it in dependencies.map(::devBuildPathIdentity) }) { "An execution target collides with an input label" }
  // The macro derives the chain stem, the component name, the plan file label and the descriptor's catalogue ID. Its
  // inputs are the main module, the chain class, the plan class, the plan package, the platform and `platform_plans`.
  // The checked-in values must be what it derives, or a chain would read another plugin's plan.
  val chainClass = configuration.chainClass
  if (chainClass.isNotEmpty()) checkExecutionName(chainClass)
  require(configuration.name == devDistChainStem(entry.mainModule, chainClass, platform)) {
    "The chain stem of ${configuration.name} does not follow `<main module>[.<chain class>][_<platform>]`"
  }
  require(configuration.componentName == entry.mainModule) { "The component name of ${configuration.name} is not its main module" }
  val platformValues = configuration.graph.platformValues
  val folded = configuration.graph.folded
  require(platform != null || !folded) { "The chain ${configuration.name} serves every platform but reads a folded plan file" }
  require(folded || platformValues.isEmpty()) { "The chain ${configuration.name} states platform values without a folded plan file" }
  val planClass = configuration.graph.planClass
  if (planClass.isNotEmpty()) checkExecutionName(planClass)
  val planClassSuffix = if (planClass.isEmpty()) "" else ".$planClass"
  val planPackageLabel = planPackage.ifEmpty { configuration.packageLabel }
  val stem = "$planPackageLabel:${entry.mainModule}$planClassSuffix${if (platform != null && !folded) ".$platform" else ""}"
  require(configuration.graph.projection == "$stem$PLAN_FILE_SUFFIX") {
    "The plan file of ${configuration.name} does not follow `<plan package>:<main module>[.<plan class>]$PLAN_FILE_SUFFIX` for a folded or neutral plan " +
    "and `<plan package>:<main module>[.<plan class>].<platform>$PLAN_FILE_SUFFIX` for a refused fold"
  }
  require(descriptorInput.id == devDistDescriptorInputId(entry.mainModule)) {
    "The descriptor of ${configuration.name} has the catalogue ID ${descriptorInput.id}"
  }
  val directoryName = if (originalDirectoryName == derivedPluginDirectoryName(entry.mainModule)) "" else originalDirectoryName
  // The macro derives the `.production.jar` file of each reused artifact from its owner label, so the owner labels are
  // stated once. A source tree is keyed by its artifact ID, so one target can serve two IDs with different prefixes.
  // A call in a community package names a label as a community package spells it. An ID keeps its spelling, because
  // the plan file keeps it. A chain that reads a plan file of a community package spells a label-shaped ID as that
  // package does, because the ultimate half reuses such a plan file as the community half wrote it.
  val communityPass = owner.index.planPackageIsCommunity
  if (inCommunity) {
    require(isCommunityCallLabel(descriptorLabel, communityPass) && planPackage.isEmpty()) {
      "A community call names an ultimate descriptor or plan package: ${configuration.name}"
    }
  }
  val label: (String) -> String = { if (inCommunity) communityCallLabel(it, communityPass) else it }
  val planInCommunity = planPackage.ifEmpty { configuration.packageLabel }.startsWith(COMMUNITY_REPOSITORY_PREFIX)
  val id: (String) -> String = { if (planInCommunity && "//" in it) communityCallLabel(it, communityPass) else it }
  val arguments = listOf(
    "main_module" to executionQuote(entry.mainModule),
    "descriptor" to executionQuote(label(descriptorLabel)),
    "execution_version" to version.toString(),
    "directory_name" to executionQuote(directoryName),
    "plan_class" to executionQuote(planClass),
    "chain_class" to executionQuote(chainClass),
    "plan_package" to executionQuote(planPackage),
    "source_tree_targets" to executionDictionary(sourceTrees.map { input -> input.id to executionQuote(label(input.label)) }),
    "source_tree_prefixes" to executionDictionary(sourceTrees.map { input -> input.id to executionQuote(requireNotNull(input.sourceTreePrefix)) }),
    "optional_source_trees" to executionStrings(sourceTrees.filter { it.optionalSourceTree }.map(DevDistPluginRawInput::id)),
    "artifact_inputs" to executionDictionary(raw.values.filter { it.id !in resources }.map { label(it.label) to executionQuote(it.id) }),
    "resource_inputs" to executionDictionary(
      raw.values.filter { it.id in resources && it.sourceTreePrefix == null && it.id != descriptorInput.id }.map { label(it.label) to executionQuote(it.id) }
    ),
    "libraries" to executionDictionary(requiredLibraries.map { label(it) to executionQuote(id(it)) }),
    "independent_artifacts" to executionStrings(independent.keys.map(label)),
  )
  return DevDistPluginExecutionVariant(platform, arguments, "${configuration.packageLabel}:${names.getValue("component")}", platformValues, folded)
}

private const val PLATFORM_TOKEN = "{platform}"

/**
 * Whether a community package can name [label] in its community spelling: a label of `@community` or of `@lib`. An
 * ultimate label is spelled `//` here, so a community call cannot state it. In the [communityPass], a `//` label is a
 * label of the community checkout, so a community call can state it too.
 */
internal fun isCommunityCallLabel(label: String, communityPass: Boolean = false): Boolean {
  return label.startsWith(COMMUNITY_REPOSITORY_PREFIX) || label.startsWith("@lib//") || communityPass && label.startsWith("//")
}

/**
 * [label] as a community package spells it: `@community//x:y` is `//x:y`, and every other label keeps its spelling. In
 * the [communityPass], every label comes from the community model, so a call can name it. Another pass can name only a
 * community call label, see [isCommunityCallLabel].
 */
private fun communityCallLabel(label: String, communityPass: Boolean): String {
  require(communityPass || isCommunityCallLabel(label)) { "A community call cannot name '$label'" }
  return if (label.startsWith(COMMUNITY_REPOSITORY_PREFIX)) "//" + label.removePrefix(COMMUNITY_REPOSITORY_PREFIX) else label
}

/**
 * The chain stem `dev_dist_complex_plugin` derives: `<main module>[.<chain class>][_<platform>]`. The component, the
 * graph, the catalogue and the remainder of one chain add their suffix to it.
 */
internal fun devDistChainStem(mainModule: String, chainClass: String, platform: String?): String {
  val classSuffix = if (chainClass.isEmpty()) "" else ".$chainClass"
  return "$mainModule$classSuffix${if (platform == null) "" else "_$platform"}"
}

/** The arguments the macro substitutes the platform token in. Every other argument must not vary with the platform. */
private val PLATFORM_ARGUMENTS = java.util.Set.of(
  "descriptor", "artifact_inputs", "resource_inputs", "libraries", "source_tree_targets", "source_tree_prefixes", "optional_source_trees", "independent_artifacts",
)

/**
 * Renders the `dev_dist_complex_plugin` calls of one plugin. One call declares every platform chain when the chains
 * differ only in the platform token. Otherwise each chain keeps a call of its own, so the difference stays visible in
 * the generated file instead of hidden behind a fallback. A call whose chains keep a plan file per platform, because
 * the fold was refused, states `platform_plans = True`. A call whose chains have a folded plan file with slots states
 * the slot values of each of its platforms in `platform_values`. A folded plan file without a slot states neither.
 *
 * The calls are top-level statements of a `BUILD.bazel`: each ends with one newline, one blank line separates two
 * calls, and the text has no leading blank line.
 */
internal fun renderDevDistPluginExecutionCalls(mainModule: String, variants: List<DevDistPluginExecutionVariant>): String {
  require(variants.isNotEmpty()) { "Plugin '$mainModule' has no execution chain" }
  for (variant in variants) {
    for ((name, value) in variant.arguments) {
      require(PLATFORM_TOKEN !in value) { "Plugin '$mainModule' states the platform token in $name: $value" }
    }
  }
  // Every chain of a plugin with a folded plan file states the same slots. Every chain of another plugin states none.
  require(variants.map { it.folded }.distinct().size == 1) { "Plugin '$mainModule' mixes folded and unfolded plan files across its chains" }
  require(variants.map { it.platformValues.keys }.distinct().size == 1) { "Plugin '$mainModule' states different plan slots across its chains" }
  val folded = variants.first().folded
  val calls: List<Pair<List<String>, List<Pair<String, String>>>> = if (variants.size == 1 && variants.single().platform == null) {
    require(variants.single().platformValues.isEmpty()) { "Plugin '$mainModule' states plan slot values on a chain that serves every platform" }
    listOf(emptyList<String>() to variants.single().arguments)
  }
  else {
    val platforms = variants.map { requireNotNull(it.platform) { "Plugin '$mainModule' mixes a neutral chain with platform chains" } }
    val masked = variants.map { variant ->
      variant.arguments.map { (name, value) ->
        name to (if (name in PLATFORM_ARGUMENTS) value.replace(requireNotNull(variant.platform), PLATFORM_TOKEN) else value)
      }
    }
    if (masked.distinct().size == 1) listOf(platforms to masked.first()) else variants.map { listOf(requireNotNull(it.platform)) to it.arguments }
  }
  val valuesByPlatform = variants.filter { it.platform != null }.associate { requireNotNull(it.platform) to it.platformValues }
  return buildString {
    for ((platforms, arguments) in calls) {
      val platformValues = executionPlatformValues(platforms.associateWith { valuesByPlatform.getValue(it) })
      val platformPlans = if (platforms.isNotEmpty() && !folded) listOf("platform_plans" to "True") else emptyList()
      executionCall(
        "dev_dist_complex_plugin",
        arguments + ("platforms" to executionStrings(platforms)) + ("platform_values" to platformValues) + platformPlans,
      )
    }
  }.removePrefix("\n")
}

/**
 * `{platform: {slot: value}}` in `HOST_PLATFORMS` order, which is the alphabetical order of the ids, with the slots
 * sorted by name. `{}` when the platforms of the call state no slot: a folded plan file without a slot, or a plan file
 * per platform.
 */
private fun executionPlatformValues(valuesByPlatform: Map<String, Map<String, String>>): String {
  if (valuesByPlatform.values.all { it.isEmpty() }) return "{}"
  return executionDictionary(valuesByPlatform.keys.sorted().map { platform ->
    platform to executionDictionary(valuesByPlatform.getValue(platform).toSortedMap().map { (slot, value) -> slot to executionQuote(value) }, indent = 8)
  })
}

private fun checkExecutionName(value: String) {
  require(
    value.isNotBlank() && value !in setOf(".", "..") && !value.endsWith('.') &&
          value.all { it.isLetterOrDigit() || it in "._-" } &&
          !Regex("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\\..*)?").matches(value)) { "Unsafe execution name: '$value'" }
}

private fun checkExecutionPathComponent(value: String) {
  require(
    value.isNotBlank() && value == value.trim() && value !in setOf(".", "..") && !value.endsWith('.') &&
          value.none { it.isISOControl() || it.isWhitespace() && it != ' ' || it in "/\\:\"'<>|?*" } &&
          !Regex("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\\..*)?").matches(value)) { "Unsafe execution path component: '$value'" }
}

private fun checkExecutionId(value: String) {
  require(value.isNotBlank() && value == value.trim() && value.none { it.isISOControl() }) { "Unsafe input ID: '$value'" }
}

private fun checkExecutionLabel(value: String) {
  val match = Regex("(?:@[A-Za-z0-9._+-]+)?//([^:]*):([^:]+)").matchEntire(value)
  require(
    match != null && value.none { it.isWhitespace() || it.isISOControl() || it in "\\\"'" } &&
          (match.groupValues.get(1).isEmpty() || match.groupValues.get(1).split('/').none { it in setOf("", ".", "..") }) &&
          match.groupValues.get(2).split('/').none { it in setOf("", ".", "..") }) { "Unsafe execution label: '$value'" }
}

private fun executionQuote(value: String): String = quoteStarlarkString(value)

private fun executionStrings(values: List<String>, indent: Int = 4): String {
  if (values.size <= 1) return values.joinToString(", ", "[", "]", transform = ::executionQuote)
  val prefix = " ".repeat(indent)
  return values.joinToString("\n", "[\n", "\n$prefix]") { "$prefix    ${executionQuote(it)}," }
}

/** The entries stand [indent] plus four spaces deep, and the closing brace stands [indent] spaces deep. */
private fun executionDictionary(values: List<Pair<String, String>>, indent: Int = 4): String {
  if (values.isEmpty()) return "{}"
  val prefix = " ".repeat(indent)
  return values.joinToString("\n", "{\n", "\n$prefix}") { "$prefix    ${executionQuote(it.first)}: ${it.second}," }
}

/** An argument at its macro default is not stated. An empty input-id list is one such argument. */
private val DEFAULT_ARGUMENT_VALUES = java.util.Set.of("[]", "{}", "\"\"")

/**
 * The argument order of a call in a `BUILD.bazel` is the buildifier order: `name` first, and every other argument in
 * name order. So `main_module` stands among the others, and `platform_plans` and `platform_values` precede `platforms`.
 */
private const val LEADING_ARGUMENT = "name"

private fun StringBuilder.executionCall(rule: String, attributes: List<Pair<String, String>>) {
  if (!endsWith("\n\n")) append('\n')
  append("$rule(\n")
  val ordered = attributes.filterNot { it.second in DEFAULT_ARGUMENT_VALUES }
    .sortedWith(compareBy<Pair<String, String>> { it.first != LEADING_ARGUMENT }.thenBy { it.first })
  for ((name, value) in ordered) append("    $name = $value,\n")
  append(")\n")
}
