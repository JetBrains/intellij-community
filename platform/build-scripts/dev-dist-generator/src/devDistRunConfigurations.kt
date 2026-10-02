// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.devDistGenerator

import com.intellij.openapi.util.JDOMUtil
import org.jdom.Element
import org.jdom.JDOMException
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.PLUGIN_XML_RELATIVE_PATH
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.text.Normalizer
import java.util.Collections
import java.util.TreeMap
import java.util.TreeSet
import kotlin.io.path.name

private const val DEV_MAIN_CLASS = "org.jetbrains.intellij.build.devServer.DevMainKt"
private const val PLATFORM_PREFIX_PROPERTY = "idea.platform.prefix"
private const val FRONTEND_BASE_PREFIX_PROPERTY = "dev.build.base.ide.platform.prefix.for.frontend"
private const val ADDITIONAL_MODULES_PROPERTY = "additional.modules"
private const val RUNTIME_MODULE_REPOSITORY_PROPERTY = "intellij.build.generate.runtime.module.repository"
private const val RUNTIME_MODULE_REPOSITORY_PATH_PROPERTY = "intellij.platform.runtime.repository.path"

/**
 * The properties that leave the `jvm_flags`: the fields of a row, and the two product selectors `DevMainImpl` reads.
 * `PreBuiltDevMain` takes the product from the distribution, so no launcher needs a selector. The boolean fields of a
 * half leave them too, see [DevDistHalf.rowFieldProperties].
 */
private val ROW_FIELD_PROPERTIES = setOf(
  PLATFORM_PREFIX_PROPERTY,
  FRONTEND_BASE_PREFIX_PROPERTY,
  ADDITIONAL_MODULES_PROPERTY,
  RUNTIME_MODULE_REPOSITORY_PROPERTY,
)

/**
 * The data directories of the IDE, which the launcher owns: `_runtime_jvm_flags` in `community/build/intellij_dev.bzl`
 * gives every row its own directories under `out/dev-data/<row>` and refuses a row that states one. `idea.plugins.path`
 * stays in the flags, because a row may load its plugins from a directory of its own.
 */
private val LAUNCHER_DATA_PROPERTIES = setOf("idea.config.path", "idea.system.path", "idea.log.path")

private const val PROJECT_DIR_MACRO = "\$PROJECT_DIR\$"

/**
 * The environment variables that only the legacy engine reads: `IdeBuilder.kt` takes the JPS output directory from
 * `CLASSES_DIR`. A launcher starts a composed distribution and reads no JPS output, so a row neither passes such a
 * variable as `env` nor turns it into a flag.
 */
private val LEGACY_ENGINE_ENV = setOf("CLASSES_DIR")

/**
 * A value that names a JPS output directory below `$PROJECT_DIR$/out/`: `out/classes` of the ultimate half, and
 * `out/production` and `out/test` of the community half. `_declare_run_launcher` in
 * `community/build/intellij_dev_dist_declarations.bzl` refuses the same directories in a launcher flag.
 */
private val JPS_OUTPUT = Regex(Regex.escape("$PROJECT_DIR_MACRO/out/") + "(classes|production|test)(?=[/:;\" ]|$)")

private const val USER_HOME_MACRO = "\$USER_HOME\$"

/** `bazel run` sets the variable, and the launcher expands `${NAME}` at launch; `$$` is a literal `$` for Bazel. */
private const val BAZEL_WORKSPACE_DIRECTORY = "\$\${BUILD_WORKSPACE_DIRECTORY}"
private const val BAZEL_HOME = "\$\${HOME}"

/** The reason of a run-configuration module the project does not have, see [devDistRunConfigurationModules]. */
internal const val UNPLANNABLE_MODULE_DOES_NOT_EXIST = "module does not exist"

/** The reason of a run-configuration module without `META-INF/plugin.xml` in its sources. */
internal const val UNPLANNABLE_NO_PLUGIN_DESCRIPTOR = "no plugin descriptor"

/**
 * One `DevMainKt` run configuration of `.idea/runConfigurations`, as the row of `dev_server_run_configurations.bzl` it
 * becomes.
 *
 * [name] is the sanitized configuration name and the launcher `//build:<name>`. [product] is the `build/dev-build.json`
 * key: the base prefix plus the platform prefix when `dev.build.base.ide.platform.prefix.for.frontend` is set, else the
 * platform prefix. [additionalModules] is sorted and distinct. [jvmFlags] is sorted and holds every flag except the
 * properties the row states as fields, the two product selectors, which [product] replaces, and the data directories
 * the launcher owns ([LAUNCHER_DATA_PROPERTIES]). An
 * environment variable that names `$PROJECT_DIR$` becomes a `-D` property there, because the Bazel `env` of a launcher
 * cannot name the workspace. `$PROJECT_DIR$` is the workspace root, as it is for the IDE. [env] holds every other
 * variable in XML order. A variable of [LEGACY_ENGINE_ENV] is in neither. [runtimeModuleRepository] is `true` for
 * `-Dintellij.build.generate.runtime.module.repository=true`, and for `-Dintellij.platform.runtime.repository.path` with a
 * value below `$PROJECT_DIR$/out/`, which then leaves the flags. [programArgs] are the `PROGRAM_PARAMETERS` in the form
 * Bazel `args` take, see [bazelProgramArgument]. [booleanFields] are the boolean fields of the half that the row sets to
 * `true`, in the order of [DevDistHalf.rowFieldProperties].
 */
@ApiStatus.Internal
class DevRunConfigurationRow(
  @JvmField val name: String,
  @JvmField val xmlFileName: String,
  @JvmField val product: String,
  @JvmField val additionalModules: List<String>,
  @JvmField val jvmFlags: List<String>,
  @JvmField val env: Map<String, String>,
  @JvmField val runtimeModuleRepository: Boolean,
  @JvmField val booleanFields: List<String> = emptyList(),
  @JvmField val programArgs: List<String> = emptyList(),
)

/**
 * Every `DevMainKt` run configuration of [runConfigurationsDir], in file name order.
 *
 * A file whose name contains `.Generated.` is skipped. A file the XML parser rejects is reported on the error stream
 * and skipped. A file with several `<configuration>` elements counts the last one that has a name. A configuration
 * without `-Didea.platform.prefix` fails and names its file, and so do two configurations with one sanitized name. A
 * flag or a converted environment variable that names a [JPS_OUTPUT] directory fails too.
 * [fieldProperties] are the boolean fields of the half, keyed by property, see [DevDistHalf.rowFieldProperties].
 */
@ApiStatus.Internal
fun readDevRunConfigurationRows(
  runConfigurationsDir: Path,
  fieldProperties: Map<String, String> = emptyMap(),
): List<DevRunConfigurationRow> {
  if (!Files.isDirectory(runConfigurationsDir)) {
    return emptyList()
  }
  val xmlFiles = Files.list(runConfigurationsDir).use { stream ->
    stream
      .filter { Files.isRegularFile(it) && it.name.endsWith(".xml") && !it.name.contains(".Generated.") }
      .sorted(compareBy { it.name })
      .toList()
  }
  val rows = ArrayList<DevRunConfigurationRow>()
  val configurationNames = HashMap<DevRunConfigurationRow, String>()
  for (xmlFile in xmlFiles) {
    val component = loadRunConfigurationFile(xmlFile) ?: continue
    if (component.name != "component") {
      continue
    }
    val configuration = component.getChildren("configuration").lastOrNull { it.getAttributeValue("name") != null } ?: continue
    val options = configurationOptions(configuration)
    if (options.get("MAIN_CLASS_NAME") != DEV_MAIN_CLASS) {
      continue
    }
    val configurationName = configuration.getAttributeValue("name")
    val row = devRunConfigurationRow(
      name = sanitizeRunConfigurationName(configurationName),
      xmlFileName = xmlFile.name,
      vmOptions = vmOptions(options.get("VM_PARAMETERS") ?: ""),
      env = configurationEnv(configuration),
      programParameters = options.get("PROGRAM_PARAMETERS") ?: "",
      fieldProperties = fieldProperties,
    )
    rows.add(row)
    configurationNames.put(row, configurationName)
  }
  checkUniqueRowNames(rows, configurationNames)
  return rows
}

private fun devRunConfigurationRow(
  name: String,
  xmlFileName: String,
  vmOptions: VmOptions,
  env: Map<String, String>,
  programParameters: String,
  fieldProperties: Map<String, String>,
): DevRunConfigurationRow {
  val properties = vmOptions.properties
  val platformPrefix = properties.get(PLATFORM_PREFIX_PROPERTY) ?: error("$PLATFORM_PREFIX_PROPERTY not found in VM options ($xmlFileName)")
  val frontendBasePrefix = properties.get(FRONTEND_BASE_PREFIX_PROPERTY)
  val product = if (frontendBasePrefix == null) platformPrefix else frontendBasePrefix + platformPrefix
  // The row composes the file that the IDE run expected under out/.
  val repositoryPath = properties.get(RUNTIME_MODULE_REPOSITORY_PATH_PROPERTY)?.takeIf { it.startsWith("$PROJECT_DIR_MACRO/out/") }
  val flagProperties = properties.filterKeys { key ->
    key !in ROW_FIELD_PROPERTIES && key !in fieldProperties && key !in LAUNCHER_DATA_PROPERTIES &&
    (repositoryPath == null || key != RUNTIME_MODULE_REPOSITORY_PATH_PROPERTY)
  }
  val envProperties = LinkedHashMap<String, String>()
  val bazelEnv = LinkedHashMap<String, String>()
  for ((key, value) in env) {
    if (key in LEGACY_ENGINE_ENV) {
      continue
    }
    if (value.contains(PROJECT_DIR_MACRO)) {
      val property = key.lowercase().replace('_', '.')
      if (property !in LAUNCHER_DATA_PROPERTIES) {
        envProperties.put(property, value)
      }
    }
    else {
      bazelEnv.put(key, value.replace('\\', '/'))
    }
  }
  val conflicts = flagProperties.keys intersect envProperties.keys
  check(conflicts.isEmpty()) { "The VM options and the environment variables of $xmlFileName both set $conflicts" }
  for (value in flagProperties.values + envProperties.values + vmOptions.jvmFlags) {
    check(!JPS_OUTPUT.containsMatchIn(value)) {
      "$xmlFileName: '$value' names a JPS output below \$PROJECT_DIR\$/out/, which a dev launch does not build." +
      " State a file of the distribution, such as -D$RUNTIME_MODULE_REPOSITORY_PROPERTY=true for the runtime module repository," +
      " or a path outside out/classes, out/production and out/test"
    }
  }
  val jvmFlags = (flagProperties + envProperties)
    .map { (key, value) -> "-D$key=${value.replace(PROJECT_DIR_MACRO, BAZEL_WORKSPACE_DIRECTORY)}" }
    .plus(vmOptions.jvmFlags)
    .sorted()
  return DevRunConfigurationRow(
    name = name,
    xmlFileName = xmlFileName,
    product = product,
    additionalModules = (properties.get(ADDITIONAL_MODULES_PROPERTY) ?: "").split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted(),
    jvmFlags = jvmFlags,
    env = bazelEnv,
    runtimeModuleRepository = properties.get(RUNTIME_MODULE_REPOSITORY_PROPERTY) == "true" || repositoryPath != null,
    booleanFields = fieldProperties.mapNotNull { (property, field) -> field.takeIf { properties.get(property) == "true" } },
    programArgs = programArguments(programParameters).map(::bazelProgramArgument),
  )
}

/**
 * The arguments of a `PROGRAM_PARAMETERS` string, split the way `ParametersList` of the IDE splits it: at whitespace
 * outside double quotes, with the quotes dropped.
 */
internal fun programArguments(parameters: String): List<String> {
  val result = ArrayList<String>()
  val current = StringBuilder()
  var quoted = false
  var started = false
  for (char in parameters) {
    when {
      char == '"' -> {
        quoted = !quoted
        started = true
      }
      char.isWhitespace() && !quoted -> {
        if (started) {
          result.add(current.toString())
          current.setLength(0)
          started = false
        }
      }
      else -> {
        current.append(char)
        started = true
      }
    }
  }
  if (started) {
    result.add(current.toString())
  }
  return result
}

/**
 * One program argument as a Bazel `args` entry. Bazel expands make variables there, so every `$` is written `$$`, and
 * `$PROJECT_DIR$` and `$USER_HOME$` become `${BUILD_WORKSPACE_DIRECTORY}` and `${HOME}`, which the launcher expands.
 * A relative path stays: the launcher starts the IDE in the workspace, as the IDE runs the configuration.
 */
internal fun bazelProgramArgument(argument: String): String {
  val projectDir = "\u0000P"
  val userHome = "\u0000H"
  return argument.replace(PROJECT_DIR_MACRO, projectDir).replace(USER_HOME_MACRO, userHome)
    .replace("\$", "\$\$")
    .replace(projectDir, BAZEL_WORKSPACE_DIRECTORY).replace(userHome, BAZEL_HOME)
}

private fun checkUniqueRowNames(rows: List<DevRunConfigurationRow>, configurationNames: Map<DevRunConfigurationRow, String>) {
  val duplicates = rows.groupBy { it.name }.filterValues { it.size > 1 }
  check(duplicates.isEmpty()) {
    "Two or more run configurations of .idea/runConfigurations have one launcher name; rename or remove one of them:\n" +
    duplicates.entries.joinToString("\n") { (name, conflicting) ->
      "  $name: " + conflicting.joinToString { "'${configurationNames.get(it)}' (${it.xmlFileName})" }
    }
  }
}

/**
 * The launcher name of a run configuration: the name without diacritics, `C++`, `C#` and `F#` spelled out, and every
 * run of other characters than letters and digits as one `_`, trimmed and in lower case.
 */
@ApiStatus.Internal
fun sanitizeRunConfigurationName(configurationName: String): String {
  var normalized = Normalizer.normalize(configurationName.trim(), Normalizer.Form.NFKD).replace(DIACRITIC, "")
  for ((pattern, replacement) in LANGUAGE_NAMES) {
    normalized = normalized.replace(pattern, replacement)
  }
  return normalized.replace(NOT_A_LETTER_OR_DIGIT, "_").trim('_').lowercase()
}

private val DIACRITIC = Regex("\\p{M}")
private val NOT_A_LETTER_OR_DIGIT = Regex("[^A-Za-z0-9]+")
private val LANGUAGE_NAMES = listOf(
  Regex("C\\+\\+", RegexOption.IGNORE_CASE) to "cpp",
  Regex("C#", RegexOption.IGNORE_CASE) to "csharp",
  Regex("F#", RegexOption.IGNORE_CASE) to "fsharp",
)

/**
 * The dev-server run configurations of one run, read once from `.idea/runConfigurations`.
 *
 * [rows] are the rows [readDevRunConfigurationRows] reads. [modulesByProduct] holds the additional plugin modules the
 * rows of each split product name. A module the project does not have, or a module without a plugin descriptor, stops
 * the run. This class is the one place that decides these facts, and the population, the dev sections and the plan read
 * the same answer.
 */
internal class DevDistRunConfigurations private constructor(
  @JvmField val rows: List<DevRunConfigurationRow>,
  @JvmField val modulesByProduct: Map<String, List<String>>,
) {
  companion object {
    /** Reads the rows of `.idea/runConfigurations` under [root], the root of [half], see [DevDistHalf.root]. */
    fun read(half: DevDistHalf, root: Path, outputProvider: ModuleOutputProvider): DevDistRunConfigurations {
      val rows = readDevRunConfigurationRows(root.resolve(RUN_CONFIGURATIONS_DIRECTORY), half.rowFieldProperties)
      val modules = devDistRunConfigurationModules(
        named = devDistRunConfigurationModules(rows, half.splitProducts),
        moduleReason = { module ->
          val jpsModule = outputProvider.findModule(module)
          when {
            jpsModule == null -> UNPLANNABLE_MODULE_DOES_NOT_EXIST
            outputProvider.findFileInModuleSources(jpsModule, PLUGIN_XML_RELATIVE_PATH, onlyProductionSources = false) == null -> UNPLANNABLE_NO_PLUGIN_DESCRIPTOR
            else -> null
          }
        },
      )
      return DevDistRunConfigurations(rows = rows, modulesByProduct = modules)
    }
  }
}

/** The directory of the `DevMainKt` run configurations of a half, relative to the root of the half. */
@ApiStatus.Internal
const val RUN_CONFIGURATIONS_DIRECTORY: String = ".idea/runConfigurations"

/**
 * [DevDistRunConfigurations.modulesByProduct] over the modules [named] per product. [moduleReason] returns the reason a module
 * cannot become a plugin of a dev distribution, or null. A module with a reason stops the run, and the message names
 * every such module of every product, like a bundled plugin the plan cannot state.
 */
@ApiStatus.Internal
fun devDistRunConfigurationModules(
  named: Map<String, List<String>>,
  moduleReason: (String) -> String?,
): Map<String, List<String>> {
  val rejected = ArrayList<String>()
  val result = TreeMap<String, List<String>>()
  for ((product, modules) in named) {
    for (module in modules) {
      val reason = moduleReason(module) ?: continue
      rejected.add("$product/$module ($reason)")
    }
    result.put(product, java.util.List.copyOf(modules))
  }
  check(rejected.isEmpty()) {
    "The dev-server run configurations name modules that cannot become a plugin of a dev distribution: $rejected." +
    " Fix the module, or remove it from -Dadditional.modules."
  }
  return Collections.unmodifiableMap(result)
}

/** The additional modules [rows] name, by product, for the products [splitProducts] names. Sorted and distinct. */
internal fun devDistRunConfigurationModules(rows: List<DevRunConfigurationRow>, splitProducts: Set<String>): Map<String, List<String>> {
  val modulesByProduct = TreeMap<String, TreeSet<String>>()
  for (row in rows) {
    if (row.product in splitProducts) {
      modulesByProduct.computeIfAbsent(row.product) { TreeSet() }.addAll(row.additionalModules)
    }
  }
  val result = TreeMap<String, List<String>>()
  for ((product, modules) in modulesByProduct) {
    result.put(product, java.util.List.copyOf(modules))
  }
  return Collections.unmodifiableMap(result)
}

/**
 * The products of [splitProducts] with one of [rows] that sets `-Dintellij.build.generate.runtime.module.repository=true`.
 * The plan emits the `platform_runtime_module_repository` fragment for such a product, and only a row that sets the
 * property composes it.
 */
internal fun devDistRuntimeModuleRepositoryProducts(rows: List<DevRunConfigurationRow>, splitProducts: Set<String>): Set<String> {
  return rows.filter { it.runtimeModuleRepository && it.product in splitProducts }.mapTo(TreeSet()) { it.product }
}

/**
 * The content of `build/dev_server_run_configurations.bzl`: `DEV_RUN_CONFIGURATIONS`, one entry per row by launcher name,
 * and `dev_server_run_configurations()`, which passes it to `intellij_dev_run_configurations`.
 *
 * A row states only what differs from the reader's defaults. A flag list or a program argument list of two or more
 * items that two or more rows state alike is one private `_JVM_FLAGS_<first row>` or `_PROGRAM_ARGS_<first row>` list,
 * as a shared residual jar struct is in
 * `dev_dist_fragment_inputs.bzl`. An entry whose every field fits on one line is one line, with its XML file as a
 * trailing comment; buildifier keeps both forms.
 *
 * Fails for a row of a product outside [splitProducts], which has no plan to launch from, and for a row that sets a
 * property of [refusedProperties], see [DevDistHalf.refusedRowProperties].
 *
 * [macrosBzl] is the `.bzl` file of the half that exports `intellij_dev_run_configurations`. [header] is the first line of
 * the file, see [generatedByHeader].
 */
@ApiStatus.Internal
fun renderDevServerRunConfigurations(
  rows: List<DevRunConfigurationRow>,
  splitProducts: Set<String>,
  macrosBzl: String,
  refusedProperties: Map<String, String> = emptyMap(),
  header: String,
): String {
  val sortedRows = rows.sortedBy { it.name }
  for (row in sortedRows) {
    check(row.product in splitProducts) {
      "${row.xmlFileName}: product '${row.product}' has no dev-distribution plan; add it to the split distributions of its half"
    }
    for ((property, instead) in refusedProperties) {
      check(row.jvmFlags.none { it.startsWith("-D$property=") }) {
        "${row.xmlFileName}: $instead, not as -D$property"
      }
    }
  }

  val sharedFlagLists = shareLists(sortedRows, DevRunConfigurationRow::jvmFlags, prefix = "_JVM_FLAGS_")
  val sharedArgumentLists = shareLists(sortedRows, DevRunConfigurationRow::programArgs, prefix = "_PROGRAM_ARGS_")

  return buildString {
    append(header)
    append("#\n")
    append("# The `DevMainKt` run configurations of `.idea/runConfigurations`, one launcher `//build:<name>` each. Only the\n")
    append("# product, `additional_modules` and `runtime_module_repository` choose a distribution, so rows that state the same\n")
    append("# three share one; see `intellij_dev_run_configurations` in `").append(macrosBzl.substringAfterLast(':')).append("`.\n")
    append("load(\"").append(macrosBzl).append("\", \"intellij_dev_run_configurations\")\n\n")
    for ((shared, what) in listOf(sharedFlagLists to "flag list", sharedArgumentLists to "program argument list")) {
      if (shared.isEmpty()) continue
      append("# A ").append(what).append(" of two or more items that two or more rows state alike, named after the first of those rows.\n")
      for ((items, name) in shared.entries.sortedBy { it.value }) {
        append(name).append(" = [\n")
        for (item in items) {
          append("    ").append(starlarkString(item)).append(",\n")
        }
        append("]\n\n")
      }
    }
    append("DEV_RUN_CONFIGURATIONS = {\n")
    for (row in sortedRows) {
      appendRow(row, sharedFlagLists, sharedArgumentLists)
    }
    append("}\n\n")
    append("def dev_server_run_configurations():\n")
    append("    intellij_dev_run_configurations(DEV_RUN_CONFIGURATIONS)\n")
  }
}

/** One field of a row: its value on one line, or the items of a list that takes a line each. */
private class RowField(@JvmField val name: String, @JvmField val inline: String?, @JvmField val items: List<String> = emptyList())

/**
 * The lists of two or more items that two or more rows state alike in the field [items], each with the private name
 * [prefix] and the upper-cased name of the first row that states it.
 */
private fun shareLists(sortedRows: List<DevRunConfigurationRow>, items: (DevRunConfigurationRow) -> List<String>, prefix: String): Map<List<String>, String> {
  val uses = sortedRows.groupingBy(items).eachCount()
  val result = LinkedHashMap<List<String>, String>()
  for (row in sortedRows) {
    val list = items(row)
    if (list.size >= 2 && uses.getValue(list) >= 2 && list !in result) {
      result.put(list, prefix + row.name.uppercase())
    }
  }
  return result
}

private fun StringBuilder.appendRow(
  row: DevRunConfigurationRow,
  sharedFlagLists: Map<List<String>, String>,
  sharedArgumentLists: Map<List<String>, String>,
) {
  val fields = ArrayList<RowField>()
  fields.add(RowField("product", starlarkString(row.product)))
  if (row.additionalModules.isNotEmpty()) {
    fields.add(listField("additional_modules", row.additionalModules))
  }
  if (row.jvmFlags.isNotEmpty()) {
    val shared = sharedFlagLists.get(row.jvmFlags)
    fields.add(if (shared == null) listField("jvm_flags", row.jvmFlags) else RowField("jvm_flags", shared))
  }
  if (row.programArgs.isNotEmpty()) {
    val shared = sharedArgumentLists.get(row.programArgs)
    fields.add(if (shared == null) listField("program_args", row.programArgs) else RowField("program_args", shared))
  }
  if (row.env.isNotEmpty()) {
    fields.add(RowField("env", row.env.entries.joinToString(prefix = "{", postfix = "}") { (key, value) -> starlarkString(key) + ": " + starlarkString(value) }))
  }
  if (row.runtimeModuleRepository) {
    fields.add(RowField("runtime_module_repository", "True"))
  }
  for (field in row.booleanFields) {
    fields.add(RowField(field, "True"))
  }

  val comment = "  # " + row.xmlFileName
  append("    ").append(starlarkString(row.name)).append(": struct(")
  if (fields.all { it.inline != null }) {
    fields.joinTo(this, separator = ", ") { it.name + " = " + it.inline }
    append("),").append(comment).append('\n')
    return
  }
  append('\n')
  for ((index, field) in fields.withIndex()) {
    val fieldComment = if (index == 0) comment else ""
    if (field.inline != null) {
      append("        ").append(field.name).append(" = ").append(field.inline).append(',').append(fieldComment).append('\n')
    }
    else {
      append("        ").append(field.name).append(" = [").append(fieldComment).append('\n')
      for (item in field.items) {
        append("            ").append(starlarkString(item)).append(",\n")
      }
      append("        ],\n")
    }
  }
  append("    ),\n")
}

/** A list of one item on one line, a longer list one item per line. */
private fun listField(name: String, items: List<String>): RowField {
  return if (items.size == 1) RowField(name, "[" + starlarkString(items.single()) + "]") else RowField(name, inline = null, items = items)
}

private fun starlarkString(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

/** The root element of [xmlFile], or `null` for a file the parser rejects. */
private fun loadRunConfigurationFile(xmlFile: Path): Element? {
  try {
    return JDOMUtil.load(xmlFile)
  }
  catch (e: JDOMException) {
    System.err.println("[devDistRunConfigurations] Failed to parse $xmlFile: ${e.message}")
  }
  catch (e: IOException) {
    System.err.println("[devDistRunConfigurations] Failed to parse $xmlFile: ${e.message}")
  }
  return null
}

private fun configurationOptions(configuration: Element): Map<String, String> {
  val options = HashMap<String, String>()
  for (option in configuration.getChildren("option")) {
    val name = option.getAttributeValue("name") ?: continue
    val value = option.getAttributeValue("value") ?: continue
    options.put(name, value)
  }
  return options
}

private fun configurationEnv(configuration: Element): Map<String, String> {
  val env = LinkedHashMap<String, String>()
  for (variable in configuration.getChild("envs")?.getChildren("env").orEmpty()) {
    val name = variable.getAttributeValue("name") ?: continue
    env.put(name, variable.getAttributeValue("value") ?: "")
  }
  return env
}

/** The `-D` properties of a `VM_PARAMETERS` string, the last value of a key winning, and its other flags in order. */
private class VmOptions(@JvmField val properties: Map<String, String>, @JvmField val jvmFlags: List<String>)

private const val SPACE_PLACEHOLDER = '\u0000'
private val QUOTED_SPANS = listOf(Regex("&quot;(.*?)&quot;"), Regex("\"(.*?)\""))
private val WHITESPACE = Regex("\\s+")

/**
 * The flags of [vmParameters].
 *
 * The quotes around the whole string go first. A space inside a quoted span survives the split. The quotes around a
 * token and around a value go last. A quote that does not close a span stays.
 */
private fun vmOptions(vmParameters: String): VmOptions {
  var shielded = vmParameters.trim().removeSurrounding("\"").removeSurrounding("&quot;")
  for (regex in QUOTED_SPANS) {
    shielded = regex.replace(shielded) { match -> match.value.replace(' ', SPACE_PLACEHOLDER) }
  }
  val properties = LinkedHashMap<String, String>()
  val jvmFlags = ArrayList<String>()
  for (part in shielded.split(WHITESPACE)) {
    val token = part.replace(SPACE_PLACEHOLDER, ' ').trim().removeSurrounding("\"").removeSurrounding("&quot;")
    if (token.isEmpty()) {
      continue
    }
    if (!token.startsWith("-D")) {
      jvmFlags.add(token)
      continue
    }
    val content = token.substring(2)
    val separator = content.indexOf('=')
    if (separator < 0) {
      properties.put(content, "")
    }
    else {
      val value = content.substring(separator + 1).removeSurrounding("\"").removeSurrounding("&quot;")
      properties.put(content.substring(0, separator), value)
    }
  }
  return VmOptions(properties = properties, jvmFlags = jvmFlags)
}
