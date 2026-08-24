package com.intellij.tools.build.bazel.ijPluginPackager

import com.intellij.platform.pluginSystem.parser.impl.elements.ContentModuleElement
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.pluginSystem.parser.impl.parseContentAndXIncludes
import com.intellij.util.io.toByteArray
import io.opentelemetry.api.trace.Tracer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.jetbrains.bazel.jvm.WorkRequest
import org.jetbrains.bazel.jvm.WorkRequestExecutor
import org.jetbrains.bazel.jvm.WorkRequestReaderWithoutDigest
import org.jetbrains.bazel.jvm.processRequests
import org.jetbrains.intellij.build.io.readEntryFromZip
import java.io.Writer
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Builds a plugin distribution from JARs of its modules.
 *
 * Runs as a Bazel persistent worker started by the `ij_plugin` rule (`ij_plugin.bzl`); the arguments of a work request are passed in
 * a params file specified as the only `--flagfile=path` argument. There is no standalone mode: without `--persistent_worker` the process
 * reports an error and exits, so it cannot be run via `bazel run`.
 *
 * Arguments format: `output_directory [--plugin_content_yaml path] --descriptor_module module_name:path_to_jar
 * [--content_module module_name:path_to_jar]...`. All paths are relative to the base directory of the request.
 */
object IjPluginPackager {
  @JvmStatic
  fun main(args: Array<String>) {
    processRequests(
      startupArgs = args,
      serviceName = "ij-plugin-packager",
      reader = WorkRequestReaderWithoutDigest(System.`in`),
      executorFactory = { _, _ -> IjPluginPackagerExecutor },
    )
  }

  internal fun packPlugin(args: List<String>, baseDir: Path) {
    require(args.isNotEmpty()) { "Expected an output directory" }

    var descriptorModule: ModuleArgument? = null
    var pluginContentYamlPath: Path? = null
    val contentModuleArguments = HashMap<String, ModuleArgument>()
    var pluginVersion: String? = null
    var sinceBuild: String? = null
    var untilBuild: String? = null
    var buildNumberFile: Path? = null
    var index = 1
    while (index < args.size) {
      require(index + 1 < args.size) { "Expected a value after ${args[index]}" }
      when (args[index]) {
        "--descriptor_module" -> {
          require(descriptorModule == null) { "--descriptor_module must be specified only once" }
          descriptorModule = parseModuleArgument(args[index + 1], baseDir)
        }
        "--content_module" -> {
          val module = parseModuleArgument(args[index + 1], baseDir)
          val oldValue = contentModuleArguments.put(module.name, module)
          require(oldValue == null) { "Two --content_module arguments for the same module: ${module.name}" }
        }
        "--plugin_content_yaml" -> {
          require(pluginContentYamlPath == null) { "--plugin_content_yaml must be specified only once" }
          pluginContentYamlPath = baseDir.resolve(args[index + 1])
        }
        "--plugin_version" -> {
          require(pluginVersion == null) { "--plugin_version must be specified only once" }
          pluginVersion = args[index + 1]
        }
        "--since_build" -> {
          require(sinceBuild == null) { "--since_build must be specified only once" }
          sinceBuild = args[index + 1]
        }
        "--until_build" -> {
          require(untilBuild == null) { "--until_build must be specified only once" }
          untilBuild = args[index + 1]
        }
        "--build_number_file" -> {
          require(buildNumberFile == null) { "--build_number_file must be specified only once" }
          buildNumberFile = Path.of(args[index + 1])
        }
        else -> error("Unknown option: ${args[index]}")
      }
      index += 2
    }

    val outputDirectory = baseDir.resolve(args[0])
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

    val buildNumberFromFile = lazy {
      requireNotNull(buildNumberFile) { "--build_number_file is not specified but it's used in other arguments" }
      try {
        buildNumberFile.readText()
      }
      catch (e: Exception) {
        error("Failed to read build number from file $buildNumberFile: $e")
      }
    }

    val descriptorOutputJar = libDirectory.resolve(generateNameForPluginDescriptorJar(descriptorModuleArgument.name))
    PluginJarPackager(descriptorOutputJar).use {
      val patchedPluginXmlContent = patchPluginDescriptor(
        originalContent = originalPluginXmlContent,
        pluginVersion = substituteBuildNumber(pluginVersion, buildNumberFromFile),
        sinceBuild = substituteBuildNumber(sinceBuild, buildNumberFromFile),
        untilBuild = substituteBuildNumber(untilBuild, buildNumberFromFile),
        contentModuleDescriptors = contentModuleDescriptors
      )
      it.addFile(PLUGIN_DESCRIPTOR_ENTRY_NAME, patchedPluginXmlContent)
      it.addEntriesFromJar(descriptorJar) { filePath, dataFetcher ->
        if (!isIncludedFromModuleOutput(filePath) || filePath == PLUGIN_DESCRIPTOR_ENTRY_NAME) {
          return@addEntriesFromJar null
        }
        dataFetcher()
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

  private fun parseModuleArgument(argument: String, baseDir: Path): ModuleArgument {
    val separatorIndex = argument.indexOf(':')
    require(separatorIndex > 0 && separatorIndex < argument.lastIndex) {
      "Expected module argument in the form module_name:path_to_jar, got: $argument"
    }
    return ModuleArgument(
      name = argument.substring(0, separatorIndex),
      jar = baseDir.resolve(argument.substring(separatorIndex + 1)),
    )
  }

  private data class ModuleArgument(
    @JvmField val name: String,
    @JvmField val jar: Path,
  )
}

private fun substituteBuildNumber(string: String?, buildNumberFromFile: Lazy<String>): String? {
  return if (string == $$"$build_number_from_file") buildNumberFromFile.value else string
}

internal object IjPluginPackagerExecutor : WorkRequestExecutor {
  override suspend fun execute(request: WorkRequest, writer: Writer, baseDir: Path, tracer: Tracer): Int {
    val paramsFile = request.arguments.singleOrNull()?.takeIf { it.startsWith(FLAG_FILE_PREFIX) }
    if (paramsFile == null) {
      writer.appendLine(
        "ERROR: the arguments must be passed in a params file specified as the only `$FLAG_FILE_PREFIX` argument, " +
        "got '${request.arguments.joinToString(" ")}'"
      )
      return 3
    }

    // failures are reported by the worker framework, which also tears the worker down on `Error`
    runInterruptible(Dispatchers.IO) {
      val args = Files.readAllLines(baseDir.resolve(paramsFile.removePrefix(FLAG_FILE_PREFIX)))
      IjPluginPackager.packPlugin(args = args, baseDir = baseDir)
    }
    return 0
  }
}

private fun generateNameForPluginDescriptorJar(moduleName: String): String = moduleName.removePrefix("intellij.").replace('.', '-') + ".jar"

private const val PLUGIN_DESCRIPTOR_ENTRY_NAME = "META-INF/plugin.xml"

private const val FLAG_FILE_PREFIX = "--flagfile="
