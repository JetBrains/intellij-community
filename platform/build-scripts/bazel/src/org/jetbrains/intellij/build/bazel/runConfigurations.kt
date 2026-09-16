// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import org.jdom.Element
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly
import java.nio.file.Files
import java.nio.file.Path
import java.text.Normalizer
import java.util.TreeSet
import java.util.stream.Collectors
import kotlin.io.path.createParentDirectories
import kotlin.io.path.name
import kotlin.io.path.writeText

// Lightweight model of an IntelliJ Run Configuration as stored under .idea/runConfigurations/*.xml
// The XML format is intentionally not fully modeled. We capture common fields and keep the raw element for advanced usage.
data class RunConfigurationSpec(
  val name: String,
  val type: String,
  val factoryName: String?,
  val moduleName: String?,
  val options: Map<String, String>,
  val env: Map<String, String>,
  val logs: List<RunLogSpec>,
  val raw: Element,
) {
  data class VmOptions(val properties: Map<String, String>, val jvmFlags: List<String>)

  fun validate(xmlFile: Path) {
    if (name.isBlank()) {
      throw IllegalStateException("Name cannot be blank (${xmlFile.name}")
    }

    if (type.isBlank()) {
      throw IllegalStateException("Type cannot be blank in configuration '$name' (${xmlFile.name})")
    }
  }

  val vmOptions: VmOptions by lazy {
    val rawParams = options["VM_PARAMETERS"]?.trim() ?: return@lazy VmOptions(emptyMap(), emptyList())

    // 1. Clean the master string from surrounding XML/JSON quotes
    val master = rawParams.removeSurrounding("\"").removeSurrounding("&quot;")

    // 2. Use a placeholder strategy to protect spaces inside any type of quotes
    val spacePlaceholder = "\u0000"
    val quotePatterns = listOf(Regex("&quot;(.*?)&quot;"), Regex("\"(.*?)\""))

    var shielded = master
    quotePatterns.forEach { regex ->
      shielded = regex.replace(shielded) { match ->
        // Replace spaces inside the match with the placeholder
        match.value.replace(" ", spacePlaceholder)
      }
    }

    val parts = shielded.split(Regex("""\s+"""))
      .asSequence()
      .map { it.replace(spacePlaceholder, " ") }
      .map { it.trim().removeSurrounding("\"").removeSurrounding("&quot;") }
      .toList()

    val properties = mutableMapOf<String, String>()
    val jvmFlags = mutableListOf<String>()

    // 3. Split by actual whitespace, then restore spaces and clean up
    parts.forEach { part ->
      when {
        part.startsWith("-D") -> {
          val content = part.removePrefix("-D")
          val split = content.split("=", limit = 2)
          val key = split[0]
          val value = split.getOrElse(1) { "" }.removeSurrounding("\"").removeSurrounding("&quot;")
          properties[key] = value
        }
        else -> {
          jvmFlags.add(part)
        }
      }
    }

    VmOptions(properties, jvmFlags)
  }
}

data class RunLogSpec(val alias: String?, val path: String?)

// Loads and deserializes all run configurations from the given project root directory.
// It looks for XML files under .idea/runConfigurations and parses configuration elements.
// Malformed files are skipped with a warning written to stderr. The function never throws due to a single bad file.
internal fun loadRunConfigurations(projectRoot: Path, subDir: Path = projectRoot.resolve(".idea").resolve("runConfigurations")): Map<Path, RunConfigurationSpec> {
  if (!Files.isDirectory(subDir)) return emptyMap()

  val xmlFiles = Files.list(subDir)
      .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".xml") }
      .collect(Collectors.toList())

  val result = mutableMapOf<Path, RunConfigurationSpec>()
  for (file in xmlFiles) {
    try {
      val component = JDOMUtil.load(file)
      if (component.name != "component") continue
      @Suppress("UNCHECKED_CAST")
      val configs = component.getChildren("configuration") as List<Element>
      for (config in configs) parseConfiguration(config)?.let { result.put(file, it) }
    }
    catch (t: Throwable) {
      System.err.println("[runConfigurations] Failed to parse $file: ${t.message}")
    }
  }
  return result
}

internal fun parseConfiguration(configuration: Element): RunConfigurationSpec? {
  val name = configuration.getAttributeValue("name") ?: return null
  val type = configuration.getAttributeValue("type")
  val factoryName = configuration.getAttributeValue("factoryName")

  val options = LinkedHashMap<String, String>()
  configuration.getChildren("option").forEach { opt ->
    val optName = opt.getAttributeValue("name") ?: return@forEach
    val optValue = opt.getAttributeValue("value") ?: return@forEach
    options[optName] = optValue
  }

  val moduleName = configuration.getChild("module")?.getAttributeValue("name")

  val env = LinkedHashMap<String, String>()
  configuration.getChild("envs")?.getChildren("env")?.forEach { envEl ->
    val key = envEl.getAttributeValue("name") ?: return@forEach
    val value = envEl.getAttributeValue("value") ?: ""
    env[key] = value
  }

  val logs = configuration.getChildren("log_file").map { lf ->
    RunLogSpec(alias = lf.getAttributeValue("alias"), path = lf.getAttributeValue("path"))
  }

  return RunConfigurationSpec(
    name = name,
    type = type,
    factoryName = factoryName,
    moduleName = moduleName,
    options = options,
    env = env,
    logs = logs,
    raw = configuration.clone(),
  )
}

private const val DEV_MAIN_CLASS = "org.jetbrains.intellij.build.devServer.DevMainKt"
private const val PLATFORM_PREFIX_PROPERTY = "idea.platform.prefix"
private const val FRONTEND_BASE_PREFIX_PROPERTY = "dev.build.base.ide.platform.prefix.for.frontend"
private const val ADDITIONAL_MODULES_PROPERTY = "additional.modules"
private const val CUSTOM_COMMAND_PROPERTY = "idea.dev.mode.custom.command"
private const val RUNTIME_MODULE_REPOSITORY_PROPERTY = "intellij.build.generate.runtime.module.repository"
private const val COMPILE_CLION_BACKEND_BEFORE_RUN_PROPERTY = "intellij.build.dev.server.compile.clion.backend.before.run"

/**
 * One dev-server run configuration, as the converter reads it from `.idea/runConfigurations`.
 *
 * [product] is the `build/dev-build.json` key: the base prefix plus [platformPrefix] for a frontend, else [platformPrefix].
 * [additionalModules] keeps the order of the XML, because the order reaches the `ide_config` of the distribution.
 * [jvmFlags] holds every flag except the platform prefix and the additional modules.
 * [keepsDevMain] names the flag that keeps the launcher on `DevMainKt`, or is null. The converter reads no module fact.
 * The plan generator decides whether it can plan each additional module.
 */
internal data class DevServerRunConfiguration(
  @JvmField val name: String,
  @JvmField val xmlFile: Path,
  @JvmField val product: String,
  @JvmField val platformPrefix: String,
  @JvmField val additionalModules: List<String>,
  @JvmField val jvmFlags: List<String>,
  @JvmField val env: Map<String, String>,
  @JvmField val keepsDevMain: String?,
  @JvmField val spec: RunConfigurationSpec,
)

/**
 * Every `DevMainKt` run configuration of the project, sorted by XML file name.
 */
internal fun devServerRunConfigurations(ultimateRoot: Path): List<DevServerRunConfiguration> {
  val rows = loadRunConfigurations(ultimateRoot)
    .onEach { (xmlFile, spec) -> spec.validate(xmlFile) }
    .filterNot { (xmlFile, _) -> xmlFile.fileName.toString().contains(".Generated.") } // IJI-3518, generated by .NET
    .filter { (_, spec) -> spec.options.get("MAIN_CLASS_NAME") == DEV_MAIN_CLASS }
    .toSortedMap(compareBy { it.fileName.toString() })
    .map { (xmlFile, spec) -> devServerRunConfiguration(xmlFile = xmlFile, spec = spec) }
  checkForDuplicateConfigurations(rows.map {
    GeneratedConfigurationInfo(xmlFile = it.xmlFile, originalName = it.spec.name, generatedName = it.name)
  })
  return rows
}

internal fun devServerRunConfiguration(xmlFile: Path, spec: RunConfigurationSpec): DevServerRunConfiguration {
  val name = sanitizeName(spec.name)
  val properties = spec.vmOptions.properties
  val platformPrefix = properties.get(PLATFORM_PREFIX_PROPERTY)
                       ?: error("$PLATFORM_PREFIX_PROPERTY not found in VM options (${xmlFile.name})")
  val frontendBasePrefix = properties.get(FRONTEND_BASE_PREFIX_PROPERTY)
  val additionalModules = (properties.get(ADDITIONAL_MODULES_PROPERTY) ?: "")
    .split(',')
    .map { it.trim() }
    .filter { it.isNotEmpty() }
  return DevServerRunConfiguration(
    name = name,
    xmlFile = xmlFile,
    product = if (frontendBasePrefix == null) platformPrefix else frontendBasePrefix + platformPrefix,
    platformPrefix = platformPrefix,
    additionalModules = additionalModules,
    jvmFlags = spec.bazelJvmFlags(
      configurationName = name,
      excludedProperties = setOf(PLATFORM_PREFIX_PROPERTY, ADDITIONAL_MODULES_PROPERTY),
    ),
    env = spec.bazelEnv(),
    keepsDevMain = keepsDevMain(properties),
    spec = spec,
  )
}

/**
 * The flag that keeps a configuration on `DevMainKt`, or null.
 *
 * `PreBuiltDevMain` has no custom command, no runtime module repository and no before-run step.
 */
private fun keepsDevMain(properties: Map<String, String>): String? {
  if (properties.containsKey(CUSTOM_COMMAND_PROPERTY)) {
    return CUSTOM_COMMAND_PROPERTY
  }
  if (properties.containsKey(RUNTIME_MODULE_REPOSITORY_PROPERTY)) {
    return RUNTIME_MODULE_REPOSITORY_PROPERTY
  }
  if (properties.get(COMPILE_CLION_BACKEND_BEFORE_RUN_PROPERTY) == "true") {
    return "$COMPILE_CLION_BACKEND_BEFORE_RUN_PROPERTY=true"
  }
  return null
}

/**
 * Writes `dev_server_run_configurations.bzl`, one macro call per [DevServerRunConfiguration].
 *
 * The whole file is one generated section, in the form buildifier keeps. A project without a row leaves the file as it is.
 */
internal fun saveDevServerRunConfigurations(ultimateRoot: Path, targetFilePath: Path) {
  val buildTargetsBazel = RunConfigurationsFile()
  for (row in devServerRunConfigurations(ultimateRoot)) {
    buildTargetsBazel.generateDevServerRunConfiguration(row)
  }
  val rendered = buildTargetsBazel.render()
  if (rendered.isEmpty()) {
    return
  }
  val content = "### auto-generated section `devServer-runs` start\n$rendered\n### auto-generated section `devServer-runs` end\n"
  if (runCatching { Files.readString(targetFilePath) }.getOrNull() != content) {
    targetFilePath.createParentDirectories().writeText(content)
  }
}

private const val DEV_ULTIMATE_BZL = "//build:intellij_dev_ultimate.bzl"
private const val RUN_CONFIGURATION_MACRO = "intellij_dev_run_configuration"
private const val DEV_MAIN_LAUNCHER_MACRO = "intellij_dev_binary_ultimate"

/**
 * The content of `dev_server_run_configurations.bzl`: the `load` line of the macros the calls name, then
 * `def dev_server_run_configurations():` over one call per [DevServerRunConfiguration].
 */
internal class RunConfigurationsFile : BuildFile() {
  private val usedMacros = TreeSet<String>()

  override fun render(existingLoads: Map<String, Set<String>>): String {
    if (usedMacros.isEmpty()) {
      return ""
    }
    val body = super.render(existingLoads).lines().joinToString("\n") { line ->
      if (line.isEmpty()) line else "$INDENT$line"
    }
    return LoadStatement(bzlFile = DEV_ULTIMATE_BZL, symbols = usedMacros.toList()).render() + "\n\n" +
           "def dev_server_run_configurations():\n" +
           body + if (body.endsWith("\n")) "" else "\n"
  }

  /**
   * A row without [DevServerRunConfiguration.keepsDevMain] becomes an `intellij_dev_run_configuration` call.
   * A row with a reason keeps the `intellij_dev_binary_ultimate` call and states the reason in a `#keepsDevMain` comment.
   */
  fun generateDevServerRunConfiguration(row: DevServerRunConfiguration) {
    val keepsDevMain = row.keepsDevMain
    val macro = if (keepsDevMain == null) RUN_CONFIGURATION_MACRO else DEV_MAIN_LAUNCHER_MACRO
    usedMacros.add(macro)
    target(macro) {
      option("#xmlFile", row.xmlFile.fileName.toString())
      if (keepsDevMain != null) {
        option("#keepsDevMain", keepsDevMain)
      }
      option("name", row.name)
      if (keepsDevMain == null) {
        option("product", row.product)
      }
      option("platform_prefix", row.platformPrefix)
      if (keepsDevMain == null && row.additionalModules.isNotEmpty()) {
        option("additional_modules", row.additionalModules)
      }
      option("jvm_flags", if (keepsDevMain == null) row.jvmFlags else row.devMainJvmFlags())
      if (row.env.isNotEmpty()) {
        option("env", LinkedHashMap(row.env))
      }
    }
  }
}

/** The `jvm_flags` of a `DevMainKt` launcher: [DevServerRunConfiguration.jvmFlags] plus the additional-modules flag. */
private fun DevServerRunConfiguration.devMainJvmFlags(): List<String> {
  if (additionalModules.isEmpty()) {
    return jvmFlags
  }
  return (jvmFlags + "-D$ADDITIONAL_MODULES_PROPERTY=${additionalModules.joinToString(",")}").sorted()
}

/**
 * The sorted `jvm_flags` of the Bazel launcher: every `-D` property except [excludedProperties],
 * every environment variable that names `$PROJECT_DIR$` as a `-D` property, and the other JVM flags.
 */
private fun RunConfigurationSpec.bazelJvmFlags(configurationName: String, excludedProperties: Set<String>): List<String> {
  val runConfigurationProperties = vmOptions.properties.filterNot { (k, _) -> k in excludedProperties }
  val envsWithProjectDir = env.filter { (_, v) -> v.contains(projectDirVar) }
    .mapKeys { it.key.envVariableNameToProperty() }

  if ((runConfigurationProperties.keys intersect envsWithProjectDir.keys).isNotEmpty()) {
    error("Unable to generate run configuration: conflicting VM options and environment variables")
  }

  return (runConfigurationProperties + envsWithProjectDir)
    .map { (k, v) -> "-D$k=${v.projectDirToBazelWorkspace(configurationName)}" }
    .plus(vmOptions.jvmFlags)
    .sorted()
}

/**
 * The `env` of the Bazel launcher: every environment variable that does not name `$PROJECT_DIR$`.
 */
private fun RunConfigurationSpec.bazelEnv(): Map<String, String> {
  return env.filterNot { (_, v) -> v.contains(projectDirVar) }.mapValues { (_, v) -> FileUtil.toSystemIndependentName(v) }
}

private val projectDirVar = "\\\$PROJECT_DIR\\\$".toRegex()
private fun String.projectDirToBazelWorkspace(configurationName: String): String = replace("\$PROJECT_DIR\$", "\$\${BUILD_WORKSPACE_DIRECTORY}/out/dev-data/$configurationName")

private fun String.envVariableNameToProperty(): String = toLowerCaseAsciiOnly().replace("_", ".")

private fun sanitizeName(input: String): String {
  val trimmed = input.trim()

  // normalize and remove diacritical marks (accents)
  var normalized = Normalizer.normalize(trimmed, Normalizer.Form.NFKD)
    .replace(Regex("\\p{M}"), "")

  val languageMap = mapOf(
    Regex("C\\+\\+", RegexOption.IGNORE_CASE) to "cpp",
    Regex("C#", RegexOption.IGNORE_CASE) to "csharp",
    Regex("F#", RegexOption.IGNORE_CASE) to "fsharp",
  )

  for ((pattern, replacement) in languageMap) {
    normalized = normalized.replace(pattern, replacement)
  }

  // replace any sequence of non-alphanumeric characters with underscore
  val replaced = normalized.replace(Regex("[^A-Za-z0-9]+"), "_")

  // collapse multiple underscores and trim leading/trailing underscores
  val collapsed = replaced.replace(Regex("_+"), "_").trim('_')

  // all lowercase
  return collapsed.lowercase()
}

private data class GeneratedConfigurationInfo(
  val xmlFile: Path,
  val originalName: String,
  val generatedName: String
)

private fun checkForDuplicateConfigurations(configurations: List<GeneratedConfigurationInfo>) {
  val configsByGeneratedName = configurations.groupBy { it.generatedName }
  val duplicates = configsByGeneratedName.filter { it.value.size > 1 }

  if (duplicates.isNotEmpty()) {
    val duplicateDetails = duplicates.entries.joinToString("\n\n") { (generatedName, configs) ->
      """
  Sanitized name: '$generatedName'
  Conflicts from ${configs.size} configurations:
${configs.joinToString("\n") { config ->
        "    - Original name: '${config.originalName}'\n      XML file: ${config.xmlFile}"
      }}
      """.trimIndent()
    }

    error("""
      |Generated run configuration names must be unique, but found duplicates:
      |
      |$duplicateDetails
      |
      |To resolve this issue:
      |  1. Check .idea/runConfigurations/ for duplicate or similar configuration names
      |  2. Rename or remove conflicting run configurations
    """.trimMargin())
  }
}
