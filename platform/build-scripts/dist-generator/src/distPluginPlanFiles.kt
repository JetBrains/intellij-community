@file:Suppress("DestructuringDeclaration", "ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.distGenerator

import kotlinx.serialization.json.Json
import org.jetbrains.intellij.build.dev.devBuildPathIdentity
import org.jetbrains.intellij.build.dist.PluginPackingProjection
import org.jetbrains.intellij.build.dist.pluginPackingExecutionVersion
import org.jetbrains.intellij.build.mapConcurrent
import org.jetbrains.intellij.build.productLayout.model.error.FileDiff
import org.jetbrains.intellij.build.productLayout.stats.DistPlanFileResult
import org.jetbrains.intellij.build.productLayout.util.DeferredFileUpdater
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.TreeMap
import java.util.TreeSet
import kotlin.io.path.invariantSeparatorsPathString

/** The suffix of a plan file name. It marks the file in a package that holds other files, so the sweep can list it. */
internal const val PLAN_FILE_SUFFIX: String = ".dev-plan.json"

/** Every plan file of a package. Nothing else of this form sits in a package, so an unlisted match is obsolete. */
private val PROJECTION_FILE_NAME = Regex("[A-Za-z0-9._-]+\\.dev-plan\\.json")

/**
 * The encoding of a plan file. A default value is omitted, and every reader restores it. `version` is encoded always,
 * because every reader checks it. The operation tree has nullable fields without a default, so a null stays explicit.
 * The plan owner compares the operations as this text.
 */
internal val PLAN_JSON = Json {
  prettyPrint = true
  encodeDefaults = false
  explicitNulls = true
}

/**
 * The plan files of every complex plugin, keyed by project-relative path, and the home of every plugin.
 *
 * [homes] is keyed by main module and sorted. [exportedFiles] holds the plan file names of every plugin whose
 * community `dev` section exports them, see [DistPluginPlanHome.exportsPlanFiles], keyed by main module and
 * sorted. A plugin with its plan home in a product package or in an ultimate package has no entry.
 */
internal class DistPluginPlanFiles private constructor(
  files: Map<String, String>,
  @JvmField val updates: DistPluginPlanUpdates,
  bindings: Map<DistPluginPlanKey, EmittedRecord>,
  homes: Map<String, DistPluginPlanHome>,
  exportedFiles: Map<String, List<String>>,
  reusedHomes: Set<String>,
) {
  @JvmField
  val files: Map<String, String> = Collections.unmodifiableMap(TreeMap(files))
  private val bindings = Collections.unmodifiableMap(HashMap(bindings))

  @JvmField
  val homes: Map<String, DistPluginPlanHome> = Collections.unmodifiableMap(TreeMap(homes))

  @JvmField
  val exportedFiles: Map<String, List<String>> = Collections.unmodifiableMap(TreeMap(exportedFiles))

  /**
   * The plugins whose home is the own package of the plugin, where the community half writes the same plan files, sorted.
   * Only the ultimate half has such a plugin. [updates] neither writes nor sweeps these files.
   */
  @JvmField
  val reusedHomes: Set<String> = Collections.unmodifiableSet(TreeSet(reusedHomes))

  /** Direct file maps are inert. Collection binds the emitted files to their owner records. */
  constructor(files: Map<String, String>, updates: DistPluginPlanUpdates) : this(files, updates, emptyMap(), emptyMap(), emptyMap(), emptySet())

  /** The plan home of [mainModule], which has at least one emitted plan file. */
  fun home(mainModule: String): DistPluginPlanHome {
    return requireNotNull(homes.get(mainModule)) { "Plugin '$mainModule' has no plan file, so it has no plan home" }
  }

  /** The package of the plan file [key] reads: the plan home of its plugin, or the product package of a non-baseline text. */
  fun planHome(key: DistPluginPlanKey): DistPluginPlanHome {
    return requireNotNull(bindings.get(key)) { "The plan file of $key is not bound" }.home
  }

  fun executionGraphLabels(key: DistPluginPlanKey, record: DistPluginPlanRecord): DistPluginExecutionGraphLabels {
    val binding = requireNotNull(bindings.get(key)) { "The graph files are not bound to this owner record and key" }
    require(binding.record === record) { "The graph files belong to another owner record" }
    require(record.preparationRecipe != null) { "The execution record is incomplete" }
    return binding.labels
  }

  /**
   * The destination of each asset of [record], as the plan file of [key] states it. A folded file states a
   * `{platform:<name>}` slot where the destination differs per platform, and the macro resolves the slot per chain.
   */
  fun assetDestinations(key: DistPluginPlanKey, record: DistPluginPlanRecord): List<String> {
    val binding = requireNotNull(bindings.get(key)) { "The plan file of $key is not bound" }
    require(binding.record === record) { "The plan file of $key belongs to another owner record" }
    val assets = record.plan.projection.assets
    if (!binding.folded) {
      return assets.map { it.destination }
    }
    val text = requireNotNull(files.get(binding.path)) { "The owner did not emit graph file '${binding.path}'" }
    val folded = PLAN_JSON.decodeFromString(PluginPackingProjection.serializer(), text).assets
    require(folded.size == assets.size) { "The folded plan file '${binding.path}' has ${folded.size} assets, and $key has ${assets.size}" }
    return folded.map { it.destination }
  }

  /**
   * Checks that the emitted plan file gives back the record text. A folded file is resolved for the key's platform with
   * the slot values of [labels] first, as `dev_plugin_file_graph` resolves it for the chain.
   */
  fun checkExecutionGraph(key: DistPluginPlanKey, record: DistPluginPlanRecord, labels: DistPluginExecutionGraphLabels) {
    require(labels == executionGraphLabels(key, record)) { "The graph labels differ from the emitted owner files" }
    val binding = bindings.getValue(key)
    val home = homes.getValue(key.plugin)
    require(binding.home === home || binding.home.directory == crossHalfPackageDirectory(key.plugin, product = null)) {
      "The plan file of $key is outside the plan home of its plugin"
    }
    val text = requireNotNull(files.get(binding.path)) { "The owner did not emit graph file '${binding.path}'" }
    val resolved = if (binding.folded) resolvePluginPlanText(text, key.variant, labels.platformValues) else text
    require(resolved == record.graphContent()) { "The graph file differs from the owner record" }
  }

  /**
   * [folded]: [path] is the tokenized plan of every platform record of the plugin, and [labels] carry this record's
   * slot values. [home] is the package [path] sits in.
   */
  private class EmittedRecord(
    val record: DistPluginPlanRecord,
    val path: String,
    val labels: DistPluginExecutionGraphLabels,
    val folded: Boolean,
    val home: DistPluginPlanHome,
  )

  companion object {
    /**
     * Emits the plan files of [records]. A plan file has one of three forms. A neutral record has
     * `<plugin>.dev-plan.json` without a token. The platform records of one plugin fold into one tokenized
     * `<plugin>.dev-plan.json` when they share one JSON shape. Every platform key then binds to that file and to its
     * slot values. A platform record whose fold was refused keeps `<plugin>.<platform>.dev-plan.json`. The census
     * prints one line per plugin with platform records.
     *
     * Two products may plan one plugin. Products whose texts are equal share one text class and its files. The first
     * class in [productOrder] is the baseline, and it names the files above. Every other class writes
     * `<plugin>.<class>[.<platform>].dev-plan.json`, named after its first product, and the census prints
     * `kept <plugin> for <product>`.
     *
     * The baseline files sit in the plugin's plan home, see [distPluginPlanHome]. The home is resolved over the texts
     * of every product, so one plugin has one home. The file of another class names a product, so for a community
     * plugin it sits in the product package instead. A home in a module package must hold a `BUILD.bazel`,
     * because the plan file needs the package. The same run generates a product package, so the check skips it.
     *
     * [projectRoot] is the root of [half], and the files are relative to it. [half] gives the case-safe name of a product
     * and the default [productOrder].
     *
     * [ownHome] gives the home of a plugin whose own package already holds its baseline files, or `null`. It gets the
     * baseline files with their text, keyed by file name. The run writes no file into such a home and sweeps
     * none there, because another pass owns the files, see [DistOwnPackagePlans].
     *
     * [planTexts] holds the encoded and folded texts of [records]. A caller that collects twice passes one instance.
     */
    fun collect(
      projectRoot: Path,
      records: Map<DistPluginPlanKey, DistPluginPlanRecord>,
      index: DistBazelIndex,
      half: DistHalf,
      productOrder: Collection<String> = half.splitProducts,
      ownHome: (plugin: String, baselineFiles: Map<String, String>) -> DistPluginPlanHome? = { _, _ -> null },
      planTexts: DistPluginPlanTexts = DistPluginPlanTexts.prepare(records, productOrder),
    ): DistPluginPlanFiles {
      require(planTexts.records === records && planTexts.productOrder == productOrder.toList()) { "The plan texts belong to other records or another product order" }
      val files = TreeMap<String, String>()
      val bindings = HashMap<DistPluginPlanKey, EmittedRecord>()
      val homes = TreeMap<String, DistPluginPlanHome>()
      val exportedFiles = TreeMap<String, List<String>>()
      val reusedHomes = TreeMap<String, DistPluginPlanHome>()
      val identities = HashMap<List<String>, DistPluginPlanKey>()
      val paths = HashMap<String, DistPluginPlanKey>()
      for ((position, entry) in planTexts.sorted.withIndex()) {
        val key = entry.key
        val components = listOf(half.caseSafeProductName(key.product), key.plugin, key.variant)
        validatePlanIdentity(key.product)
        validatePlanIdentity(components.first())
        validatePlanIdentity(key.plugin)
        if (key.variant.isNotEmpty()) validatePlanIdentity(key.variant)
        val previous = identities.putIfAbsent(components.map(::devBuildPathIdentity), key)
        if (previous != null) {
          require(planTexts.text(previous) == planTexts.text(key)) { "Plugin plan identities collide: $previous and $key" }
        }
        planTexts.rethrowValidationFailure(position)
      }
      for ((plugin, byProduct) in planTexts.groups) {
        // The texts of every product come first, because the home reads every label of every text.
        val textsByProduct = LinkedHashMap<String, ProductPlanTexts>()
        for (product in byProduct.keys) {
          textsByProduct.put(product, planTexts.reportProductTexts(plugin, product))
        }
        val baselineFiles = textsByProduct.values.first().files.entries.associateTo(TreeMap()) { (suffix, text) ->
          "$plugin$suffix$PLAN_FILE_SUFFIX" to text
        }
        val reusedHome = ownHome(plugin, baselineFiles)
        val home = reusedHome ?: distPluginPlanHome(plugin, index)
        if (reusedHome != null) {
          reusedHomes.put(plugin, reusedHome)
        }
        requirePlanHomePackage(projectRoot, plugin, home)
        homes.put(plugin, home)
        val classHome = if (home.exportsPlanFiles) crossHalfPlanHome(plugin) else home
        val pluginFileNames = ArrayList<String>()
        // The plan class of every text already emitted: empty for the baseline text, else its first product.
        val classes = LinkedHashMap<Map<String, String>, String>()
        for ((product, group) in byProduct) {
          val texts = textsByProduct.getValue(product)
          val existingClass = classes.get(texts.files)
          val shared = existingClass != null
          val planClass = when {
            existingClass != null -> existingClass
            classes.isEmpty() -> ""
            else -> {
              println("kept $plugin for $product: its plan text differs from the text of ${textsByProduct.keys.first()}")
              half.caseSafeProductName(product)
            }
          }
          if (existingClass == null) classes.put(texts.files, planClass)
          val planHome = if (planClass.isEmpty()) home else classHome
          val classSuffix = if (planClass.isEmpty()) "" else ".$planClass"
          val foldedFileName = "$plugin$classSuffix$PLAN_FILE_SUFFIX"
          val fold = texts.fold
          if (fold != null && !shared) {
            // The shared file registers once, with the first platform key of the group.
            val path = planHome.path(foldedFileName)
            val owner = group.keys.first { it.variant.isNotEmpty() }
            require(paths.putIfAbsent(devBuildPathIdentity(path), owner) == null) { "Plugin projection paths collide at '$path'" }
            files.put(path, fold.body)
            if (planHome === home) pluginFileNames.add(foldedFileName)
          }
          for ((key, record) in group) {
            if (fold != null && key.variant.isNotEmpty()) {
              val labels = DistPluginExecutionGraphLabels(
                projection = planHome.label(foldedFileName),
                platformValues = fold.valuesByPlatform.getValue(key.variant),
                planClass = planClass,
                folded = true,
              )
              bindings.put(key, EmittedRecord(record, planHome.path(foldedFileName), labels, folded = true, home = planHome))
            }
            else {
              val stem = planFileStem(key, planClass)
              val fileName = "$stem$PLAN_FILE_SUFFIX"
              val path = planHome.path(fileName)
              val text = planTexts.text(key)
              checkPlanTextHoldsNoToken(text, stem)
              if (!shared) {
                require(paths.putIfAbsent(devBuildPathIdentity(path), key) == null) { "Plugin projection paths collide at '$path'" }
                files.put(path, text)
                if (planHome === home) pluginFileNames.add(fileName)
              }
              val labels = DistPluginExecutionGraphLabels(planHome.label(fileName), planClass = planClass)
              bindings.put(key, EmittedRecord(record, path, labels, folded = false, home = planHome))
            }
          }
        }
        if (home.exportsPlanFiles) {
          exportedFiles.put(plugin, pluginFileNames.sorted())
        }
      }
      // A reused home is a package of another pass, so the run neither writes nor sweeps it.
      val reusedDirectories = reusedHomes.values.mapTo(HashSet()) { it.directory }
      val planHomes = (bindings.values.mapTo(LinkedHashSet()) { it.home } + homes.values).filterNot { it.directory in reusedDirectories }
      val updates = DistPluginPlanUpdates(
        projectRoot = projectRoot,
        files = files.filterKeys { it.substringBeforeLast('/', "") !in reusedDirectories },
        sweepDirectories = planSweepDirectories(projectRoot, index, planHomes),
      )
      return DistPluginPlanFiles(files, updates, bindings, homes, exportedFiles, reusedHomes.keys)
    }
  }
}

/** Fails when the module package of [home] holds no `BUILD.bazel`: the converter creates the file, and a plan file needs the package. */
private fun requirePlanHomePackage(projectRoot: Path, plugin: String, home: DistPluginPlanHome) {
  if (!home.isModulePackage) return
  val buildFile = projectRoot.resolve(home.path(BUILD_FILE_NAME))
  require(Files.isRegularFile(buildFile)) { "Plugin '$plugin' has no ${home.path(BUILD_FILE_NAME)}, so its plan files have no package" }
}

/**
 * The project-relative directory of every package a plan file may sit in: the package of every module [index] places,
 * every plugin package under [CROSS_HALF_PACKAGE_ROOT] on disk, and the directory of every home of [homes]. Sorted
 * and without a duplicate. A product package of this run is listed through [homes],
 * because its package may not exist on disk yet. The ultimate half writes no community package, so it sweeps none.
 */
private fun planSweepDirectories(projectRoot: Path, index: DistBazelIndex, homes: Collection<DistPluginPlanHome>): List<String> {
  val result = TreeSet<String>()
  homes.mapTo(result) { it.directory }
  // Only the community half writes a plan file into the package of a community module, so only it sweeps one there.
  for (module in index.targets.modules.keys) {
    if (!index.planPackageIsCommunity && index.isCommunity(module) == true) {
      continue
    }
    val directory = index.packageDir(module) ?: continue
    result.add(projectRoot.relativize(directory).invariantSeparatorsPathString)
  }
  val crossHalfRoot = projectRoot.resolve(CROSS_HALF_PACKAGE_ROOT)
  if (Files.isDirectory(crossHalfRoot)) {
    Files.list(crossHalfRoot).use { entries ->
      entries.filter { Files.isDirectory(it) }.forEach { result.add("$CROSS_HALF_PACKAGE_ROOT/${it.fileName}") }
    }
  }
  return result.toList()
}

/**
 * The plan texts of one product's records of one plugin, keyed by what follows the plugin in the file name. The key is
 * the empty string for a neutral or folded file, and `.<platform>` for a kept platform record. Two products whose maps
 * are equal share the baseline's files. [fold] is the fold of the platform records, or `null` when they keep their files.
 */
internal class ProductPlanTexts(
  @JvmField val files: Map<String, String>,
  @JvmField val fold: DistPluginPlanFold.Folded?,
)

/** [texts] holds the plan text of every key of [group]. The census lines of the fold go to [census]. */
private fun productPlanTexts(
  product: String,
  plugin: String,
  group: Map<DistPluginPlanKey, DistPluginPlanRecord>,
  texts: Map<DistPluginPlanKey, PreparedPlanText>,
  census: MutableList<String>,
): ProductPlanTexts {
  val platformTexts = LinkedHashMap<String, String>()
  for (key in group.keys) {
    if (key.variant.isNotEmpty()) platformTexts.put(key.variant, texts.getValue(key).get())
  }
  val fold = foldPlatformRecords(product, plugin, platformTexts, census)
  val files = LinkedHashMap<String, String>()
  if (fold != null) {
    files.put("", fold.body)
  }
  for (key in group.keys) {
    if (fold == null || key.variant.isEmpty()) {
      files.put(if (key.variant.isEmpty()) "" else ".${key.variant}", texts.getValue(key).get())
    }
  }
  return ProductPlanTexts(files = files, fold = fold)
}

/**
 * Folds the platform records of one plugin and adds its census line to [census]. `folded` names the slots when one
 * tokenized body resolves to every record; the fold proves the resolution and fails the run on a mismatch. `kept`
 * names the first differing path when the records differ in shape, or the one platform record. A folded plan can have
 * no slot. Returns null when the records keep their per-platform files.
 */
private fun foldPlatformRecords(
  product: String,
  plugin: String,
  textsByPlatform: LinkedHashMap<String, String>,
  census: MutableList<String>,
): DistPluginPlanFold.Folded? {
  if (textsByPlatform.isEmpty()) return null
  val platforms = textsByPlatform.keys.joinToString(", ")
  if (textsByPlatform.size < 2) {
    census.add("kept $plugin: one platform record ($platforms)")
    return null
  }
  val fold = try {
    foldDistPluginPlanTexts(textsByPlatform)
  }
  catch (e: IllegalStateException) {
    throw IllegalStateException("The plan fold of $product/$plugin failed: ${e.message}", e)
  }
  return when (fold) {
    is DistPluginPlanFold.Folded -> {
      census.add("folded $plugin platforms=[$platforms] slots=[${fold.slotNames.joinToString(", ")}] proof=resolved ${textsByPlatform.size} of ${textsByPlatform.size} records")
      fold
    }
    is DistPluginPlanFold.Refused -> {
      census.add("kept $plugin at ${fold.path}: ${fold.reason} (${fold.platformA} vs ${fold.platformB})")
      null
    }
  }
}

/**
 * The stem of an unfolded plan file: `<plugin>` for a neutral record, and `<plugin>.<variant>` for a platform record
 * whose fold was refused. A folded group names its one file `<plugin>` in [DistPluginPlanFiles.collect]. A
 * non-empty [planClass] follows the plugin, so a text that differs from the baseline text has a file of its own.
 */
private fun planFileStem(key: DistPluginPlanKey, planClass: String): String {
  val classSuffix = if (planClass.isEmpty()) "" else ".$planClass"
  return if (key.variant.isEmpty()) "${key.plugin}$classSuffix" else "${key.plugin}$classSuffix.${key.variant}"
}

/** The product package as the home of the plan files of a community plugin's non-baseline texts. */
private fun crossHalfPlanHome(plugin: String): DistPluginPlanHome {
  val directory = crossHalfPackageDirectory(plugin, product = null)
  return DistPluginPlanHome(directory = directory, packageLabel = "//$directory", callIsCrossHalf = true, exportsPlanFiles = false)
}

/** The text of the one plan file of this record. */
private fun DistPluginPlanRecord.graphContent(): String {
  return PLAN_JSON.encodeToString(PluginPackingProjection.serializer(), fileProjection) + "\n"
}

internal fun collectDistPluginPlanFiles(
  projectRoot: Path,
  records: Map<DistPluginPlanKey, DistPluginPlanRecord>,
  index: DistBazelIndex,
  half: DistHalf,
  productOrder: Collection<String> = half.splitProducts,
  ownHome: (plugin: String, baselineFiles: Map<String, String>) -> DistPluginPlanHome? = { _, _ -> null },
  planTexts: DistPluginPlanTexts = DistPluginPlanTexts.prepare(records, productOrder),
): DistPluginPlanFiles {
  return DistPluginPlanFiles.collect(projectRoot, records, index, half, productOrder, ownHome, planTexts)
}

/**
 * The plan texts of [records] in [productOrder], encoded and folded once on concurrent threads.
 *
 * [DistPluginPlanFiles.collect] reads them, so two collections of the same records encode and fold only once.
 * A collection reports a kept failure and the census lines at the place a sequential run reaches them.
 * So the output and the first failure do not depend on the thread schedule.
 */
internal class DistPluginPlanTexts private constructor(
  @JvmField val records: Map<DistPluginPlanKey, DistPluginPlanRecord>,
  @JvmField val productOrder: List<String>,
  /** The records sorted by plugin, then by product rank, product and variant. */
  @JvmField val sorted: List<Map.Entry<DistPluginPlanKey, DistPluginPlanRecord>>,
  /** The records of [sorted], grouped by plugin and then by product, in the order of [sorted]. */
  @JvmField val groups: Map<String, Map<String, Map<DistPluginPlanKey, DistPluginPlanRecord>>>,
  private val texts: Map<DistPluginPlanKey, PreparedPlanText>,
  private val validationFailures: List<RuntimeException?>,
  private val productTexts: Map<Pair<String, String>, PreparedProductTexts>,
) {
  /** The text of the one plan file of the record of [key]. Throws the encoding failure of the record, if it has one. */
  fun text(key: DistPluginPlanKey): String = texts.getValue(key).get()

  /** Throws the validation failure of the record at [position] in [sorted], if the record has one. */
  fun rethrowValidationFailure(position: Int) {
    validationFailures.get(position)?.let { throw it }
  }

  /** Prints the census lines of the fold of [plugin] for [product], then throws its failure or returns its texts. */
  fun reportProductTexts(plugin: String, product: String): ProductPlanTexts {
    val prepared = productTexts.getValue(plugin to product)
    for (line in prepared.census) {
      println(line)
    }
    prepared.failure?.let { throw it }
    return checkNotNull(prepared.texts)
  }

  private class PreparedProductTexts(
    @JvmField val texts: ProductPlanTexts?,
    @JvmField val census: List<String>,
    @JvmField val failure: RuntimeException?,
  )

  companion object {
    fun prepare(records: Map<DistPluginPlanKey, DistPluginPlanRecord>, productOrder: Collection<String>): DistPluginPlanTexts {
      val order = productOrder.toList()
      val rank = order.withIndex().associate { (index, product) -> product to index }
      // The sort puts the products of one plugin together, the baseline product first. The platform records of one
      // product follow each other in the alphabetical order, which is `HOST_PLATFORMS` order.
      val sorted = records.entries.sortedWith(
        compareBy({ it.key.plugin }, { rank.get(it.key.product) ?: Int.MAX_VALUE }, { it.key.product }, { it.key.variant })
      )
      val groups = LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<DistPluginPlanKey, DistPluginPlanRecord>>>()
      for ((key, record) in sorted) {
        groups.computeIfAbsent(key.plugin) { LinkedHashMap() }.computeIfAbsent(key.product) { LinkedHashMap() }.put(key, record)
      }
      val prepared = sorted.mapConcurrent { (key, record) ->
        var text: String? = null
        val textFailure = captureFailure { text = record.graphContent() }
        PreparedPlanText(text = text, failure = textFailure) to captureFailure { validateRecord(key, record) }
      }
      val texts = HashMap<DistPluginPlanKey, PreparedPlanText>(sorted.size)
      for ((position, entry) in sorted.withIndex()) {
        texts.put(entry.key, prepared.get(position).first)
      }
      val productGroups = groups.flatMap { (plugin, byProduct) -> byProduct.map { (product, group) -> Triple(plugin, product, group) } }
      val productTexts = productGroups.mapConcurrent { (plugin, product, group) ->
        val census = ArrayList<String>()
        var result: ProductPlanTexts? = null
        val failure = captureFailure { result = productPlanTexts(product, plugin, group, texts, census) }
        PreparedProductTexts(texts = result, census = census, failure = failure)
      }
      val productTextsByKey = HashMap<Pair<String, String>, PreparedProductTexts>(productGroups.size)
      for ((position, group) in productGroups.withIndex()) {
        productTextsByKey.put(group.first to group.second, productTexts.get(position))
      }
      return DistPluginPlanTexts(
        records = records,
        productOrder = order,
        sorted = sorted,
        groups = groups,
        texts = texts,
        validationFailures = prepared.map { it.second },
        productTexts = productTextsByKey,
      )
    }
  }
}

/** The encoded plan text of one record, or the failure of its encoding. */
private class PreparedPlanText(
  private val text: String?,
  private val failure: RuntimeException?,
) {
  fun get(): String {
    failure?.let { throw it }
    return checkNotNull(text)
  }
}

/** Runs [action] and returns its runtime failure, or `null`. An error or a checked exception propagates. */
private inline fun captureFailure(action: () -> Unit): RuntimeException? {
  try {
    action()
    return null
  }
  catch (e: RuntimeException) {
    return e
  }
}

private fun validatePlanIdentity(value: String) {
  require(
    value.isNotBlank() && value != "." && value != ".." && !value.endsWith('.') &&
    value.all { it.isLetterOrDigit() || it in "._-" }) { "Unsafe plugin plan identity '$value'" }
  require(!Regex("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\\..*)?").matches(value)) { "Unsafe plugin plan identity '$value'" }
}

private fun validateRecord(key: DistPluginPlanKey, record: DistPluginPlanRecord) {
  val plan = record.plan
  val projection = plan.projection
  require(key.plugin == projection.plugin && key.variant == projection.variant && key.variant == record.variant.id) {
    "Plugin plan key does not match its record: $key"
  }
  val executionVersion = pluginPackingExecutionVersion(projection.assets)
  require(projection.version == executionVersion) {
    "Unsupported plugin projection version: ${projection.version}; expected $executionVersion for its assets"
  }
  val reused = plan.reusedModules
  require(reused.distinct().size == reused.size && reused.none(String::isBlank)) {
    "Plugin plan '$key' has a duplicate or empty reused module"
  }
  validateRawInputBindings(record, plan.selectedPlan().requiredInputs)
}

private fun validateRawInputBindings(record: DistPluginPlanRecord, requiredInputs: List<String>) {
  val plan = record.plan
  val projection = plan.projection
  val artifacts = plan.catalogue.artifacts.associateBy { it.id }
  require(artifacts.size == plan.catalogue.artifacts.size) { "Duplicate plugin catalogue artifacts" }
  val raw = plan.requiredRawInputs.associateBy { it.id }
  require(raw.size == plan.requiredRawInputs.size) { "Duplicate plugin raw input bindings" }
  for (binding in plan.requiredRawInputs) {
    validateBindingLabel(binding.label)
    val artifact = artifacts.get(binding.id)
    require(artifact != null && artifact.kind == binding.kind && artifact.fileName == binding.fileName && binding.id.isNotBlank()) {
      "Raw input '${binding.id}' does not match its catalogue artifact"
    }
    require(
      binding.kind in setOf("archive", "file", "directory") && binding.fileName.isNotEmpty() &&
      binding.fileName !in setOf(".", "..") && binding.fileName.none { it in "/\\:\u0000" }) {
      "Invalid raw input metadata for '${binding.id}'"
    }
    binding.sourceTreePrefix?.let { prefix ->
      require(binding.kind == "directory" && prefix.none { it in "\\:\u0000" } &&
              (prefix.isEmpty() || prefix.split('/').none { it.isEmpty() || it in setOf(".", "..") })) {
        "Invalid source tree prefix for '${binding.id}'"
      }
    }
  }
  val libraries = plan.catalogue.libraries.filter { it.id != null }.associateBy { it.id!! }
  require(libraries.size == plan.catalogue.libraries.count { it.id != null }) { "Duplicate plugin library bindings" }
  val (requiredLibraries, requiredRaw) = requiredInputs.partition { it in libraries }
  require(requiredRaw == plan.requiredRawInputs.map { it.id }) {
    "Plugin '${projection.plugin}' has stale raw input ownership: expected=$requiredRaw, actual=${raw.keys}"
  }
  require(requiredLibraries == plan.requiredLibraries) {
    "Plugin '${projection.plugin}' has stale library ownership: expected=$requiredLibraries, actual=${plan.requiredLibraries}"
  }
  for (library in plan.requiredLibraries) {
    val member = libraries.getValue(library).files.firstOrNull { it in raw }
    require(member == null) { "Library '$library' is required as a whole, and its member '$member' is a raw input too" }
  }
  for (asset in projection.assets) {
    for (source in asset.recipe?.sources.orEmpty()) {
      if (source.kind != "library") continue
      require(source.input in libraries) { "Library source '${source.input}' names no catalogue library" }
    }
  }
}

/**
 * Writes the plan files of one run and sweeps the stale ones.
 *
 * [files] is keyed by project-relative path, and every key names a plan file in one of [sweepDirectories]. The sweep
 * visits every directory of [sweepDirectories] and deletes every plan file the run did not emit. So a plugin that
 * moved its home, left the complex set, or left the project loses its old file in the same run.
 */
internal class DistPluginPlanUpdates(
  private val projectRoot: Path,
  files: Map<String, String>,
  sweepDirectories: Collection<String>,
) {
  private val updater = DeferredFileUpdater(projectRoot)
  @JvmField
  val results: List<DistPlanFileResult>

  init {
    val directories = sweepDirectories.toHashSet()
    require(files.keys.all { it.substringBeforeLast('/', "") in directories && PROJECTION_FILE_NAME.matches(it.substringAfterLast('/')) }) {
      "The graph file map names a file outside the swept directories"
    }
    // The sweep and the reads touch thousands of files, so they run concurrently. The diffs are then made in path
    // order, as a sequential run made them.
    val obsolete = sweepDirectories.toList().mapConcurrent { directory ->
      collectObsoleteProjections(projectRoot, directory, files.keys)
    }.flatten()
    val sortedFiles = files.entries.sortedBy { it.key }
    val oldContents = sortedFiles.mapConcurrent { (relativePath, _) ->
      val path = projectRoot.resolve(relativePath)
      if (Files.exists(path)) Files.readString(path) else ""
    }
    results = sortedFiles.mapIndexed { index, (relativePath, content) ->
      DistPlanFileResult(relativePath, updater.writeIfChanged(projectRoot.resolve(relativePath), oldContents[index], content))
    }
    for (relativePath in obsolete.sorted()) updater.delete(projectRoot.resolve(relativePath))
  }

  fun getDiffs(): List<FileDiff> = updater.getDiffs()

  fun commit() {
    updater.commit()
  }
}

private fun validateBindingLabel(label: String) {
  val match = Regex("(?:@[A-Za-z0-9._+-]+)?//([^:]*):([^:]+)").matchEntire(label)
  require(
    match != null && label.none { it.isWhitespace() || it == '\\' || it.isISOControl() } &&
          (match.groupValues.get(1).isEmpty() || match.groupValues.get(1).split('/').none { it in setOf("", ".", "..") }) &&
          match.groupValues.get(2).split('/').none { it in setOf("", ".", "..") }) { "Unsafe plugin binding label '$label'" }
}

/** The project-relative path of every plan file of [relativeDirectory] that is not in [current], sorted. */
private fun collectObsoleteProjections(projectRoot: Path, relativeDirectory: String, current: Set<String>): List<String> {
  val directory = if (relativeDirectory.isEmpty()) projectRoot else projectRoot.resolve(relativeDirectory)
  if (!Files.exists(directory)) return emptyList()
  require(Files.isDirectory(directory)) { "Plugin projection path is not a directory: $directory" }
  val obsolete = ArrayList<String>()
  Files.list(directory).use { entries ->
    entries.forEach { entry ->
      val name = entry.fileName.toString()
      if (PROJECTION_FILE_NAME.matches(name) && Files.isRegularFile(entry)) {
        val relativePath = listOf(relativeDirectory, name).filter { it.isNotEmpty() }.joinToString("/")
        if (relativePath !in current) obsolete.add(relativePath)
      }
    }
  }
  return obsolete.sorted()
}
