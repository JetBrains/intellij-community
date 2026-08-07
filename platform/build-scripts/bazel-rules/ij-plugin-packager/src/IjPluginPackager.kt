package com.intellij.tools.build.bazel.ijPluginPackager

import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.pluginSystem.parser.impl.elements.ContentModuleElement
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.pluginSystem.parser.impl.parseContentAndXIncludes
import com.intellij.util.io.toByteArray
import org.jetbrains.intellij.build.io.readEntryFromZip
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path

/**
 * Builds a plugin distribution from JARs of its modules.
 *
 * Command-line format: `output_directory [--plugin_content_yaml path] --descriptor_module module_name:path_to_jar
 * [--content_module module_name:path_to_jar]...`.
 */
//todo: support persistent worker mode
object IjPluginPackager {
  @JvmStatic
  fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "Expected an output directory" }

    var descriptorModule: ModuleArgument? = null
    var pluginContentYamlPath: Path? = null
    val contentModuleArguments = HashMap<String, ModuleArgument>()
    var index = 1
    while (index < args.size) {
      require(index + 1 < args.size) { "Expected a value after ${args[index]}" }
      when (args[index]) {
        "--descriptor_module" -> {
          require(descriptorModule == null) { "--descriptor_module must be specified only once" }
          descriptorModule = parseModuleArgument(args[index + 1])
        }
        "--content_module" -> {
          val module = parseModuleArgument(args[index + 1])
          val oldValue = contentModuleArguments.put(module.name, module)
          require(oldValue == null) { "Two --content_module arguments for the same module: ${module.name}" }
        }
        "--plugin_content_yaml" -> {
          require(pluginContentYamlPath == null) { "--plugin_content_yaml must be specified only once" }
          pluginContentYamlPath = Path.of(args[index + 1])
        }
        else -> error("Unknown option: ${args[index]}")
      }
      index += 2
    }

    val outputDirectory = Path.of(args[0])
    val libDirectory = outputDirectory.resolve("lib")
    Files.createDirectories(libDirectory)
    val descriptorModuleArgument = requireNotNull(descriptorModule) { "--descriptor_module must be specified" }
    val descriptorJar = descriptorModuleArgument.jar
    val originalPluginXmlContent = readEntryFromZip(descriptorJar, PLUGIN_DESCRIPTOR_ENTRY_NAME)
    requireNotNull(originalPluginXmlContent) { "$PLUGIN_DESCRIPTOR_ENTRY_NAME is not found in $descriptorJar" }
    val contentModules = parseContentAndXIncludes(originalPluginXmlContent, descriptorJar.toString()).contentModules
    val pluginContentYamlWriter = pluginContentYamlPath?.let { PluginContentYamlWriter(it, outputDirectory) }

    val contentModuleDescriptors = packContentModulesAndReturnTheirDescriptors(
      contentModules = contentModules,
      contentModuleArguments = contentModuleArguments,
      libDirectory = libDirectory,
      pluginContentYamlWriter = pluginContentYamlWriter,
    )

    val descriptorOutputJar = libDirectory.resolve(generateNameForPluginDescriptorJar(descriptorModuleArgument.name))
    PluginJarPackager(descriptorOutputJar).use {
      it.addEntriesFromJar(descriptorJar) { filePath, dataFetcher ->
        if (!isIncludedFromModuleOutput(filePath)) {
          return@addEntriesFromJar null
        }
        val data = dataFetcher()
        if (filePath == PLUGIN_DESCRIPTOR_ENTRY_NAME) {
          val pluginDescriptorRoot = JDOMUtil.load(data.toByteArray())
          embedContentModules(pluginDescriptorRoot, contentModuleDescriptors)
          val patchedData = JDOMUtil.write(pluginDescriptorRoot)
          return@addEntriesFromJar ByteBuffer.wrap(patchedData.toByteArray())
        }
        data
      }
    }
    pluginContentYamlWriter?.addModule(descriptorOutputJar, descriptorModuleArgument.name)
    pluginContentYamlWriter?.write()
  }

  private fun packContentModulesAndReturnTheirDescriptors(
    contentModules: List<ContentModuleElement>,
    contentModuleArguments: HashMap<String, ModuleArgument>,
    libDirectory: Path,
    pluginContentYamlWriter: PluginContentYamlWriter?,
  ): Map<String, ByteArray> {
    val contentModuleDescriptors = HashMap<String, ByteArray>()
    for (contentModule in contentModules) {
      val contentModuleArgument = requireNotNull(contentModuleArguments[contentModule.name]) { "No --content_module argument for '${contentModule.name}' registered in plugin.xml" }
      val destinationDirectory = if (contentModule.loadingRule == ModuleLoadingRuleValue.EMBEDDED) libDirectory else libDirectory.resolve("modules")
      Files.createDirectories(destinationDirectory)
      val contentDescriptorName = "${contentModule.name}.xml"
      val outputJar = destinationDirectory.resolve("${contentModule.name}.jar")
      PluginJarPackager(outputJar).use {
        it.addEntriesFromJar(contentModuleArgument.jar) { filePath, dataFetcher ->
          if (!isIncludedFromModuleOutput(filePath)) {
            return@addEntriesFromJar null
          }
          val data = dataFetcher()
          if (filePath == contentDescriptorName) {
            val dataBytes = data.toByteArray()
            contentModuleDescriptors[contentModule.name] = dataBytes
            ByteBuffer.wrap(dataBytes)
          }
          else {
            data
          }
        }
      }
      pluginContentYamlWriter?.addContentModule(outputJar, contentModule.name)
    }
    return contentModuleDescriptors
  }

  private fun isIncludedFromModuleOutput(filePath: String): Boolean {
    return filePath != "icon-robots.txt" && !filePath.endsWith("/icon-robots.txt")
  }

  private fun parseModuleArgument(argument: String): ModuleArgument {
    val separatorIndex = argument.indexOf(':')
    require(separatorIndex > 0 && separatorIndex < argument.lastIndex) {
      "Expected module argument in the form module_name:path_to_jar, got: $argument"
    }
    return ModuleArgument(
      name = argument.substring(0, separatorIndex),
      jar = Path.of(argument.substring(separatorIndex + 1)),
    )
  }

  private data class ModuleArgument(
    @JvmField val name: String,
    @JvmField val jar: Path,
  )
}


private fun generateNameForPluginDescriptorJar(moduleName: String): String = moduleName.removePrefix("intellij.").replace('.', '-') + ".jar"

private const val PLUGIN_DESCRIPTOR_ENTRY_NAME = "META-INF/plugin.xml"
