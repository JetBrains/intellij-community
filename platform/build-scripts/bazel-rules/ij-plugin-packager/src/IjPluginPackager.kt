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
 * Command-line format: `output_directory --descriptor_module module_name:path_to_jar [--content_module module_name:path_to_jar]...`.
 */
//todo: support persistent worker mode
object IjPluginPackager {
  @JvmStatic
  fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "Expected an output directory" }

    var descriptorModule: ModuleArgument? = null
    val contentModuleArguments = HashMap<String, ModuleArgument>()
    var index = 1
    while (index < args.size) {
      require(index + 1 < args.size) { "Expected a value after ${args[index]}" }
      val module = parseModuleArgument(args[index + 1])
      when (args[index]) {
        "--descriptor_module" -> {
          require(descriptorModule == null) { "--descriptor_module must be specified only once" }
          descriptorModule = module
        }
        "--content_module" -> {
          val oldValue = contentModuleArguments.put(module.name, module)
          require(oldValue == null) { "Two --content_module arguments for the same module: ${module.name}" }
        }
        else -> error("Unknown option: ${args[index]}")
      }
      index += 2
    }

    val libDirectory = Path.of(args[0]).resolve("lib")
    Files.createDirectories(libDirectory)
    val descriptorJar = requireNotNull(descriptorModule) { "--descriptor_module must be specified" }.jar
    val originalPluginXmlContent = readEntryFromZip(descriptorJar, PLUGIN_DESCRIPTOR_ENTRY_NAME)
    requireNotNull(originalPluginXmlContent) { "$PLUGIN_DESCRIPTOR_ENTRY_NAME is not found in $descriptorJar" }
    val contentModules = parseContentAndXIncludes(originalPluginXmlContent, descriptorJar.toString()).contentModules

    val contentModuleDescriptors = packContentModulesAndReturnTheirDescriptors(contentModules, contentModuleArguments, libDirectory)

    PluginJarPackager(libDirectory.resolve(descriptorJar.fileName)).use {
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

  }

  private fun packContentModulesAndReturnTheirDescriptors(
    contentModules: List<ContentModuleElement>,
    contentModuleArguments: HashMap<String, ModuleArgument>,
    libDirectory: Path,
  ): Map<String, ByteArray> {
    val contentModuleDescriptors = HashMap<String, ByteArray>()
    for (contentModule in contentModules) {
      val contentModuleArgument = requireNotNull(contentModuleArguments[contentModule.name]) { "No --content_module argument for '${contentModule.name}' registered in plugin.xml" }
      val destinationDirectory = if (contentModule.loadingRule == ModuleLoadingRuleValue.EMBEDDED) libDirectory else libDirectory.resolve("modules")
      Files.createDirectories(destinationDirectory)
      val contentDescriptorName = "${contentModule.name}.xml"
      PluginJarPackager(destinationDirectory.resolve("${contentModule.name}.jar")).use {
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

private const val PLUGIN_DESCRIPTOR_ENTRY_NAME = "META-INF/plugin.xml"