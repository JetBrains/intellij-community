package com.intellij.tools.build.bazel.ijPluginPackager

import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.pluginSystem.parser.impl.elements.ContentModuleElement
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.pluginSystem.parser.impl.parseContentAndXIncludes
import io.opentelemetry.api.trace.Tracer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.jdom.Element
import org.jetbrains.bazel.jvm.WorkRequest
import org.jetbrains.bazel.jvm.WorkRequestExecutor
import org.jetbrains.bazel.jvm.WorkRequestReaderWithoutDigest
import org.jetbrains.bazel.jvm.processRequests
import org.jetbrains.intellij.build.io.readEntryFromZip
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.readText

/**
 * Builds a plugin distribution from JARs of its modules.
 *
 * Runs as a Bazel persistent worker started by the `ij_plugin` rule (`ij_plugin.bzl`); the arguments of a work request are passed in
 * a params file specified as the only `--flagfile=path` argument. There is no standalone mode: without `--persistent_worker` the process
 * reports an error and exits, so it cannot be run via `bazel run`.
 *
 * The first argument is the path to the output directory where the distribution should be generated.
 * Other arguments are:
 * * `--descriptor_module module_name:path_to_jar[,path_to_jar...]` (mandatory): specifies the name of a JPS module containing the plugin descriptor and path to its JAR file;
 *   the first path contains the plugin descriptor, each additional path is a module-level library JAR, which is packed as a separate JAR in
 *   the `lib` directory;
 * * `--content_module module_name:path_to_jar[,path_to_jar...]`: includes a plugin content module in the distribution; the first path contains the module XML descriptor, if
 *   additional paths are specified, they are packed together with the content module;
 *   Specify each module only once.
 * * `--non_classpath_data relative_output_path:input_path`: copies a file or directory to the relative path in the plugin distribution;
 * * `--packed_modules path`: enables generation of the `packed-modules.yaml` file, which names each jar in the distribution and the modules packed into it;
 * * `--plugin_version version`: updates `<version>` tag in `plugin.xml` with the provided version;
 * * `--since_build build`: updates `since-build` attribute in `<idea-version>` tag in `plugin.xml` with the provided value;
 * * `--until_build build`: updates `until-build` attribute in `<idea-version>` tag in `plugin.xml` with the provided value;
 * * `--build_number_file path`: provides a path to a file with a build number that will be used as a value for `--plugin_version`, `--since_build` and `--until_build` if the
 *   corresponding arguments use `$build_number_from_file` placeholder. If it's used in `--plugin_version version`, `SNAPSHOT` text is replaced by a big number to follow SemVer
 *   format.
 *
 * All paths are relative to the base directory of the request.
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
    var packedModulesPath: Path? = null
    val contentModuleArguments = HashMap<String, ModuleArgument>()
    val nonClasspathData = ArrayList<NonClasspathDataArgument>()
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
          if (oldValue != null) {
            throw IjPluginPackagingException("Two 'content_modules' arguments for the same module '${module.name}' in 'ij_plugin' rule")
          }
        }
        "--packed_modules" -> {
          require(packedModulesPath == null) { "--packed_modules must be specified only once" }
          packedModulesPath = baseDir.resolve(args[index + 1])
        }
        "--non_classpath_data" -> nonClasspathData.add(parseNonClasspathDataArgument(args[index + 1], baseDir))
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
          buildNumberFile = baseDir.resolve(args[index + 1])
        }
        else -> error("Unknown option: ${args[index]}")
      }
      index += 2
    }

    val outputDirectory = baseDir.resolve(args[0])
    val libDirectory = outputDirectory.resolve("lib")
    Files.createDirectories(libDirectory)
    val descriptorModuleArgument = requireNotNull(descriptorModule) { "--descriptor_module must be specified" }
    val descriptorJar = descriptorModuleArgument.jars.first()
    val originalPluginXmlContent = readEntryFromZip(descriptorJar, PLUGIN_DESCRIPTOR_ENTRY_NAME)
                                   ?: throw IjPluginPackagingException("$PLUGIN_DESCRIPTOR_ENTRY_NAME is not found in $descriptorJar")
    val contentModuleElements = parseContentAndXIncludes(originalPluginXmlContent, descriptorJar.toString()).contentModules
    val packedModulesWriter = packedModulesPath?.let { PackedModulesWriter(it, outputDirectory) }

    val contentModules = readContentModuleDescriptors(contentModuleElements, contentModuleArguments)
    val (contentModulesToMergeWithMainJar, contentModulesToPackSeparately) = contentModules.partition { it.shouldBeMergedToMainJar }
    packContentModuleJars(
      contentModules = contentModulesToPackSeparately,
      libDirectory = libDirectory,
      packedModulesWriter = packedModulesWriter,
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

    val pluginDescriptorJarName = generateNameForPluginDescriptorJar(descriptorModuleArgument.name)
    val descriptorOutputJar = libDirectory.resolve(pluginDescriptorJarName)
    PluginJarPackager(descriptorOutputJar).use { packager ->
      val patchedPluginXmlContent = patchPluginDescriptor(
        originalContent = originalPluginXmlContent,
        pluginVersion = computePluginVersion(pluginVersion, buildNumberFromFile),
        sinceBuild = substituteBuildNumber(sinceBuild, buildNumberFromFile),
        untilBuild = substituteBuildNumber(untilBuild, buildNumberFromFile),
        contentModules = contentModules.associateBy { it.moduleElement.name },
        presentablePluginDescriptorLocation = descriptorJar.pathString,
      )
      packager.addFile(PLUGIN_DESCRIPTOR_ENTRY_NAME, patchedPluginXmlContent, presentableOrigin = descriptorJar.pathString)
      packager.addEntriesFromJar(descriptorJar) { filePath, dataFetcher ->
        if (!isIncludedFromModuleOutput(filePath) || filePath == PLUGIN_DESCRIPTOR_ENTRY_NAME) {
          return@addEntriesFromJar null
        }
        dataFetcher()
      }
      contentModulesToMergeWithMainJar.forEach { contentModule ->
        val jar = contentModule.jars.singleOrNull()
                  ?: error("Content module packed with the main JAR must have exactly one jar, but '${contentModule.moduleElement.name}' has ${contentModule.jars}")
        packager.addEntriesFromJar(jar) { filePath, dataFetcher ->
          if (!isIncludedFromModuleOutput(filePath)) {
            return@addEntriesFromJar null
          }
          dataFetcher()
        }
      }
    }
    packedModulesWriter?.addModule(descriptorOutputJar, descriptorModuleArgument.name)
    contentModulesToMergeWithMainJar.forEach {
      packedModulesWriter?.addModule(descriptorOutputJar, it.moduleElement.name)
    }
    copyNonClasspathData(nonClasspathData, outputDirectory)
    packAdditionalJarsForDescriptorModule(descriptorModuleArgument.jars.asSequence().drop(1), libDirectory, pluginDescriptorJarName)
    packedModulesWriter?.write()
  }

  private fun packAdditionalJarsForDescriptorModule(jars: Sequence<Path>, libDirectory: Path, pluginDescriptorJarName: String) {
    val existingJarNames = HashMap<String, String>()
    existingJarNames[pluginDescriptorJarName] = "plugin descriptor module JAR"
    jars.forEach {  jar ->
      val targetJarName = removeVersionFromJar(jar.name)
      val old = existingJarNames.put(targetJarName, jar.pathString)
      val targetJar = libDirectory.resolve(targetJarName)
      if (old != null) {
        throw IjPluginPackagingException("Duplicate JAR name: both $old and ${jar.pathString} are put to ${targetJar.pathString}")
      }
      PluginJarPackager(targetJar).use {
        it.addEntriesFromJar(jar) { filePath, dataFetcher ->
          if (isSkippedFromLibraries(filePath)) {
            return@addEntriesFromJar null
          }
          dataFetcher()
        }
      }
    }
  }

  private fun readContentModuleDescriptors(
    contentModuleElements: List<ContentModuleElement>,
    contentModuleArguments: Map<String, ModuleArgument>,
  ): List<ContentModuleData> {
    return contentModuleElements.map { contentModuleElement ->
      val contentModuleArgument = contentModuleArguments.get(contentModuleElement.name)
                                  ?: throw IjPluginPackagingException("No 'content_module' argument is specified in 'ij_plugin' rule for '${contentModuleElement.name}' registered in plugin.xml")
      val contentModuleDescriptorJar = contentModuleArgument.jars.first()
      val contentDescriptorName = "${contentModuleElement.name}.xml"
      val descriptorContent = readEntryFromZip(contentModuleDescriptorJar, contentDescriptorName)
      if (descriptorContent == null) {
        throw IjPluginPackagingException("Module descriptor '${contentDescriptorName}' is not found in '${contentModuleDescriptorJar.pathString}'")
      }
      val contentDescriptorRoot = try {
        JDOMUtil.load(descriptorContent)
      } catch (e: Exception) {
        throw IjPluginPackagingException("Failed to parse module descriptor '${contentDescriptorName}' in '${contentModuleDescriptorJar.pathString}': ${e.message}")
      }
      ContentModuleData(contentModuleElement, contentDescriptorRoot, contentModuleArgument.jars)
    }
  }

  private fun packContentModuleJars(
    contentModules: List<ContentModuleData>,
    libDirectory: Path,
    packedModulesWriter: PackedModulesWriter?,
  ) {
    for (contentModule in contentModules) {
      val contentModuleElement = contentModule.moduleElement
      val destinationDirectory = if (contentModuleElement.loadingRule == ModuleLoadingRuleValue.EMBEDDED) libDirectory else libDirectory.resolve("modules")
      Files.createDirectories(destinationDirectory)
      val outputJar = destinationDirectory.resolve("${contentModuleElement.name}.jar")
      PluginJarPackager(outputJar).use {
        val containMultipleLibraries = contentModule.jars.size > 2
        for ((index, jar) in contentModule.jars.withIndex()) {
          val first = index == 0
          it.addEntriesFromJar(jar) { filePath, dataFetcher ->
            if (!isIncludedFromModuleOutput(filePath)) {
              return@addEntriesFromJar null
            }
            if (!first && (containMultipleLibraries && isSkippedWhileMergingLibraries(filePath) || isSkippedFromLibraries(filePath))) {
              return@addEntriesFromJar null
            }
            dataFetcher()
          }
        }
      }
      packedModulesWriter?.addContentModule(outputJar, contentModuleElement.name)
    }
  }

  private fun isIncludedFromModuleOutput(filePath: String): Boolean {
    return filePath != "icon-robots.txt" && !filePath.endsWith("/icon-robots.txt")
  }

  /**
   * Returns `true` if the given file path should be skipped while merging multiple libraries in a single JAR.
   */
  private fun isSkippedWhileMergingLibraries(filePath: String): Boolean {
    // this function intentionally doesn't include all patterns from librarySourcesFilter.kt because exclusion of files may break library's logic
    return filePath == "META-INF/MANIFEST.MF"
           || filePath == "module-info.class" // IJ platform doesn't use JPMS, so it's ok to skip these entries
  }

  /**
   * Returns `true` if files with the given relative path inside JAR coming from a library should be skipped.
   */
  private fun isSkippedFromLibraries(filePath: String): Boolean {
    return filePath == "META-INF/INDEX.LIST" // the included list will become incorrect, and JAR Index isn't supported since JVM 21 (JDK-8302819)
  }

  private fun parseModuleArgument(argument: String, baseDir: Path): ModuleArgument {
    val separatorIndex = argument.indexOf(':')
    require(separatorIndex > 0 && separatorIndex < argument.lastIndex) {
      "Expected a module argument in the form module_name:path_to_jar[,path_to_jar...]. Got: $argument"
    }
    val paths = argument.substring(separatorIndex + 1).split(',')
    require(paths.all { it.isNotEmpty() }) {
      "Expected a module argument in the form module_name:path_to_jar[,path_to_jar...]. Got: $argument"
    }
    return ModuleArgument(
      name = argument.substring(0, separatorIndex),
      jars = paths.map { baseDir.resolve(it) },
    )
  }

  private fun parseNonClasspathDataArgument(argument: String, baseDir: Path): NonClasspathDataArgument {
    val separatorIndex = argument.indexOf(':')
    require(separatorIndex > 0 && separatorIndex < argument.lastIndex) {
      "Expected a non-classpath data argument in the form relative_output_path:input_path. Got: $argument"
    }
    val outputPath = argument.substring(0, separatorIndex)
    val relativeOutputPath = Path.of(outputPath)
    if (relativeOutputPath.isAbsolute) {
      throw IjPluginPackagingException("Non-classpath data output path must be relative: $outputPath")
    }
    return NonClasspathDataArgument(
      relativeOutputPath = relativeOutputPath,
      source = baseDir.resolve(argument.substring(separatorIndex + 1)),
    )
  }

  private data class ModuleArgument(
    @JvmField val name: String,
    @JvmField val jars: List<Path>,
  )
}

internal class ContentModuleData(
  val moduleElement: ContentModuleElement,
  val moduleDescriptorRoot: Element,
  val jars: List<Path>,
) {
  val shouldBeMergedToMainJar: Boolean
    // `package` attribute is deprecated but still used in many plugins in the monorepo (IJPL-216355)
    get() = moduleElement.loadingRule != ModuleLoadingRuleValue.EMBEDDED && moduleDescriptorRoot.getAttributeValue("package") != null && jars.size == 1
}

private const val BUILD_NUMBER_FROM_FILE_MARKER = $$"$build_number_from_file"

private fun computePluginVersion(string: String?, buildNumberFromFile: Lazy<String>): String? {
  return if (string == BUILD_NUMBER_FROM_FILE_MARKER) {
    //transform `263.SNAPSHOT` text to `263.99999999.0` to follow SemVer format
    var snapshotVersion = buildNumberFromFile.value.replace("SNAPSHOT", "99999999")
    if (snapshotVersion.count { it == '.' } == 1) {
      snapshotVersion += ".0"
    }
    snapshotVersion
  }
  else string
}

private fun substituteBuildNumber(string: String?, buildNumberFromFile: Lazy<String>): String? {
  return if (string == BUILD_NUMBER_FROM_FILE_MARKER) buildNumberFromFile.value else string
}

private val JAR_NAME_WITH_VERSION_PATTERN = Regex("(.*)-\\d+(?:\\.\\d+)*\\.jar")

private fun removeVersionFromJar(fileName: String): String {
  val matcher = JAR_NAME_WITH_VERSION_PATTERN.matchEntire(fileName)
  return if (matcher != null) "${matcher.groupValues[1]}.jar" else fileName
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

    try {
      runInterruptible(Dispatchers.IO) {
        val args = Files.readAllLines(baseDir.resolve(paramsFile.removePrefix(FLAG_FILE_PREFIX)))
        IjPluginPackager.packPlugin(args = args, baseDir = baseDir)
      }
    }
    catch (e: IjPluginPackagingException) {
      writer.appendLine("ERROR: ${e.message}")
      return 1
    }
    return 0
  }
}

private fun generateNameForPluginDescriptorJar(moduleName: String): String = moduleName.removePrefix("intellij.").replace('.', '-') + ".jar"

private const val PLUGIN_DESCRIPTOR_ENTRY_NAME = "META-INF/plugin.xml"

private const val FLAG_FILE_PREFIX = "--flagfile="
