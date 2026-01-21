// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import com.intellij.openapi.util.JDOMUtil
import org.jdom.Element
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly
import java.nio.file.Files
import java.nio.file.Path
import java.text.Normalizer
import java.util.stream.Collectors
import kotlin.io.path.name
import kotlin.text.split

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
private fun loadRunConfigurations(projectRoot: Path, subDir: Path = projectRoot.resolve(".idea").resolve("runConfigurations")): Map<Path, RunConfigurationSpec> {
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

private fun parseConfiguration(configuration: Element): RunConfigurationSpec? {
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

internal fun saveDevServerRunConfigurations(ultimateRoot: Path, targetFilePath: Path) {
  val runConfigurations: Map<Path, RunConfigurationSpec> = loadRunConfigurations(ultimateRoot)
    .onEach { (xmlFile, spec) -> spec.validate(xmlFile) }
    .filter { (_, v) -> v.options["MAIN_CLASS_NAME"] != null && v.options["MAIN_CLASS_NAME"] == "org.jetbrains.intellij.build.devServer.DevMainKt" }

  val fileUpdater = BazelFileUpdater(targetFilePath)
  fileUpdater.removeSections("devServer-runs")

  val buildTargetsBazel = RunConfigurationsFile()
  val generatedConfigurations = runConfigurations
    .toSortedMap( compareBy { it.fileName.toString() } )
    .map { (xmlFile, spec) ->
      buildTargetsBazel.generateDevServerRunConfiguration(xmlFile, spec).also {
        fileUpdater.insertAutoGeneratedSection(sectionName = "devServer-runs", buildTargetsBazel.render())
      }
    }
  val generatedConfigurationsCounts = generatedConfigurations.groupingBy { it }.eachCount()
  val nonUnique = generatedConfigurationsCounts.filter { it.value > 1 }.keys
  require(nonUnique.isEmpty()) { "Generated configurations must be unique: $nonUnique" }

  fileUpdater.save()
}

internal class RunConfigurationsFile : BuildFile() {
  override fun render(existingLoads: Map<String, Set<String>>): String {
    return "def dev_server_run_configurations():\n" +
        super.render(existingLoads).lines().joinToString("\n") { line ->
          if (line.isNotEmpty()) "  $line" else line
        }.let { it + if (!it.endsWith("\n")) "\n" else "" }   // preserve trailing newline
  }

  fun generateDevServerRunConfiguration(xmlFile: Path, runConfiguration: RunConfigurationSpec) : String {
    val generatedName = sanitizeName(runConfiguration.name)

    target("intellij_dev_binary_ultimate") {
      option("#xmlFile", xmlFile.fileName.toString())
      option("name", generatedName)
      option("platform_prefix", runConfiguration.vmOptions.properties["idea.platform.prefix"] ?: error("idea.platform.prefix not found in VM options"))


      val runConfigurationProperties = runConfiguration.vmOptions.properties
        .filterNot { (k, _) -> k == "idea.platform.prefix" }
      val envsWithProjectDir = runConfiguration.env.filter { (_, v) -> v.contains(projectDirVar) }
        .mapKeys { it.key.envVariableNameToProperty() }

      if ((runConfigurationProperties.keys intersect envsWithProjectDir.keys).isNotEmpty()) {
        error("Unable to generate run configuration: conflicting VM options and environment variables")
      }

      option("jvm_flags",
             (runConfigurationProperties + envsWithProjectDir)
               .map { (k, v) -> "-D$k=${v.projectDirToBazelWorkspace(generatedName)}" }
               .plus(runConfiguration.vmOptions.jvmFlags)
      )
      runConfiguration.env.filterNot { (_, v) -> v.contains(projectDirVar)}.also { env ->
        if (env.isNotEmpty()) {
          option("env", env)
        }
      }
    }
    return generatedName
  }
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