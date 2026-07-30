package com.intellij.tools.build.bazel.ijPluginPackager

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
    val contentModules = ArrayList<ModuleArgument>()
    var index = 1
    while (index < args.size) {
      require(index + 1 < args.size) { "Expected a value after ${args[index]}" }
      val module = parseModuleArgument(args[index + 1])
      when (args[index]) {
        "--descriptor_module" -> {
          require(descriptorModule == null) { "--descriptor_module must be specified only once" }
          descriptorModule = module
        }
        "--content_module" -> contentModules.add(module)
        else -> error("Unknown option: ${args[index]}")
      }
      index += 2
    }

    val libDirectory = Path.of(args[0]).resolve("lib")
    Files.createDirectories(libDirectory)
    val descriptorJar = requireNotNull(descriptorModule) { "--descriptor_module must be specified" }.jar
    Files.copy(descriptorJar, libDirectory.resolve(descriptorJar.fileName))

    if (contentModules.isNotEmpty()) {
      val modulesDirectory = libDirectory.resolve("modules")
      Files.createDirectories(modulesDirectory)
      for (module in contentModules) {
        Files.copy(module.jar, modulesDirectory.resolve("${module.name}.jar"))
      }
    }
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
