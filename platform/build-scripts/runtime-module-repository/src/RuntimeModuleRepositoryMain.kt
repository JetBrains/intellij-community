// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("RuntimeModuleRepositoryMain")

package com.intellij.platform.buildScripts.runtimeModuleRepository

import com.intellij.openapi.util.JDOMUtil
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.serialization.JpsSerializationManager
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

private const val MODULE_FACTS_OPTION = "--module-facts"
private const val LAYOUT_OPTION = "--layout"
private const val DESCRIPTOR_OPTION = "--descriptor"
private const val CONTENT_MODULE_DESCRIPTOR_OPTION = "--content-module-descriptor"
private const val IDE_PROPERTIES_OPTION = "--ide-properties"
private const val OUTPUT_DIR_OPTION = "--output-dir"

/**
 * Writes `modules/module-descriptors.dat` and `modules/module-descriptors.jar` of a distribution. It needs no build context.
 *
 * The options:
 * - `--module-facts=<file>`: the [RuntimeModuleFactsTable] of the modules of the layout, see `RuntimeModuleFactsMain`.
 * - `--layout=<file>`: the [RuntimeModuleRepositoryLayout] of the distribution.
 * - `--descriptor=<module>=<file>`: the plugin descriptor of the plugin that [RuntimeModuleRepositoryPluginLayout.descriptorModule] names.
 *   Give one for each plugin of the layout. The descriptor has its `xi:include` references inlined. Each `<content><module>` element holds the descriptor of its
 *   content module as text, as the plugin classpath file needs it.
 * - `--content-module-descriptor=<module>=<file>`: the descriptor of a content module that the plugin descriptor does not hold. It is optional and repeatable.
 * - `--ide-properties=<file>`: an additional `idea.properties` file of the product, which can suppress plugins. It is optional and repeatable.
 * - `--output-dir=<dir>`: the distribution root. The files go to its `modules` directory.
 *
 * An argument `@<file>` reads more arguments from the file, one argument on each line.
 */
fun main(args: Array<String>) {
  try {
    generateRuntimeModuleRepositoryFromLayout(args.asList())
  }
  catch (e: RuntimeModuleRepositoryException) {
    System.err.println("ERROR: ${e.message}")
    e.cause?.printStackTrace()
    exitProcess(1)
  }
}

internal fun generateRuntimeModuleRepositoryFromLayout(args: List<String>) {
  val options = parseOptions(expandArgumentFiles(args))
  val model = readRuntimeModuleFacts(Path.of(options.single(MODULE_FACTS_OPTION)))
  val layout = readRuntimeModuleRepositoryLayout(Path.of(options.single(LAYOUT_OPTION)))
  val descriptorFiles = options.keyedPaths(DESCRIPTOR_OPTION)
  val contentModuleDescriptorFiles = options.keyedPaths(CONTENT_MODULE_DESCRIPTOR_OPTION)
  val idePropertiesFiles = options.all(IDE_PROPERTIES_OPTION).map { Path.of(it) }
  val outputDir = Path.of(options.single(OUTPUT_DIR_OPTION))

  val pluginConfigurationModuleToDistributionEntries = LinkedHashMap<String, List<PluginDistributionEntry>>()
  for (plugin in layout.plugins) {
    require(pluginConfigurationModuleToDistributionEntries.put(plugin.descriptorModule, plugin.entries) == null) {
      "The layout has more than one plugin with the descriptor module '${plugin.descriptorModule}'"
    }
  }
  val unusedDescriptors = descriptorFiles.keys - pluginConfigurationModuleToDistributionEntries.keys
  require(unusedDescriptors.isEmpty()) { "$DESCRIPTOR_OPTION names modules that the layout has no plugin for: ${unusedDescriptors.sorted()}" }

  val originalPluginDescriptorsData = layout.plugins.map { plugin ->
    val descriptorFile = requireNotNull(descriptorFiles[plugin.descriptorModule]) { "$DESCRIPTOR_OPTION is not set for '${plugin.descriptorModule}'" }
    val descriptorContent = Files.readAllBytes(descriptorFile)
    val embeddedDescriptors = readEmbeddedContentModuleDescriptors(descriptorContent)
    readPluginDescriptorDataForHeader(
      pluginDescriptorContent = descriptorContent,
      pluginDescriptorJpsModuleName = plugin.descriptorModule,
      additionalFrontendOnlyPlugin = plugin.additionalFrontendOnlyPlugin,
      contentModuleDescriptorProvider = { _, contentModule ->
        embeddedDescriptors[contentModule.name] ?: contentModuleDescriptorFiles[contentModule.name]?.let(Files::readAllBytes)
      },
    )
  }
  val pluginDescriptorsData = removeDataForSuppressedPlugins(originalPluginDescriptorsData, idePropertiesFiles)
  val additionalFrontendOnlyPluginModules = layout.plugins.filter { it.additionalFrontendOnlyPlugin }.mapTo(HashSet()) { it.descriptorModule }
  val repository = generateRuntimeModuleRepository(
    pluginDescriptorsData = pluginDescriptorsData,
    pluginConfigurationModuleToDistributionEntries = pluginConfigurationModuleToDistributionEntries,
    additionalFrontendOnlyPluginModules = additionalFrontendOnlyPluginModules,
    model = model,
  )
  saveRuntimeModuleRepository(repository, outputDir.resolve(RUNTIME_REPOSITORY_MODULES_DIR_NAME))
}

/**
 * Returns the content module descriptors that the `<content><module>` elements of [pluginDescriptor] hold as text, by module name.
 */
private fun readEmbeddedContentModuleDescriptors(pluginDescriptor: ByteArray): Map<String, ByteArray> {
  val result = HashMap<String, ByteArray>()
  for (content in JDOMUtil.load(pluginDescriptor).getChildren("content")) {
    for (module in content.getChildren("module")) {
      val name = module.getAttributeValue("name") ?: continue
      val text = module.text
      if (text.isNotBlank()) {
        result[name] = text.encodeToByteArray()
      }
    }
  }
  return result
}

internal fun loadProject(projectDir: Path): JpsProject {
  // The repository needs only the names, the dependencies and the source roots of modules and libraries, so the library roots can stay unresolved.
  val pathVariables = mapOf("MAVEN_REPOSITORY" to projectDir.resolve(".m2").toString())
  return JpsSerializationManager.getInstance().loadProject(projectDir.toString(), pathVariables, false)
}

private fun expandArgumentFiles(args: List<String>): List<String> {
  return args.flatMap { arg ->
    if (arg.startsWith("@")) Files.readAllLines(Path.of(arg.substring(1))).filter { it.isNotEmpty() } else listOf(arg)
  }
}

private class Options(private val values: Map<String, List<String>>) {
  fun all(name: String): List<String> = values[name] ?: emptyList()

  fun single(name: String): String {
    val list = all(name)
    require(list.size == 1) { if (list.isEmpty()) "$name is required" else "$name is set more than once" }
    return list.single()
  }

  /** Reads the values in the form `<key>=<path>`. */
  fun keyedPaths(name: String): Map<String, Path> {
    val result = LinkedHashMap<String, Path>()
    for (value in all(name)) {
      val separator = value.indexOf('=')
      require(separator > 0) { "$name expects <key>=<path>, but got '$value'" }
      val key = value.substring(0, separator)
      require(result.put(key, Path.of(value.substring(separator + 1))) == null) { "$name sets '$key' more than once" }
    }
    return result
  }
}

private val knownOptions = setOf(
  MODULE_FACTS_OPTION, LAYOUT_OPTION, DESCRIPTOR_OPTION, CONTENT_MODULE_DESCRIPTOR_OPTION, IDE_PROPERTIES_OPTION, OUTPUT_DIR_OPTION,
)

private fun parseOptions(args: List<String>): Options {
  val values = LinkedHashMap<String, MutableList<String>>()
  for (arg in args) {
    val separator = arg.indexOf('=')
    require(separator > 0) { "Expected an option in the form --<name>=<value>, but got '$arg'" }
    val name = arg.substring(0, separator)
    require(name in knownOptions) { "Unknown option '$name'" }
    values.computeIfAbsent(name) { ArrayList() }.add(arg.substring(separator + 1))
  }
  return Options(values)
}
