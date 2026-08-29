// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DevDistPluginDescriptorMain")

package org.jetbrains.intellij.build.dev

import kotlinx.coroutines.runBlocking
import org.jdom.Element
import org.jetbrains.intellij.build.CompatibleBuildRange
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.classPath.DescriptorSearchScope
import org.jetbrains.intellij.build.classPath.DescriptorResolveContext
import org.jetbrains.intellij.build.classPath.XIncludeElementResolverImpl
import org.jetbrains.intellij.build.classPath.resolveAndEmbedContentModuleDescriptor
import org.jetbrains.intellij.build.impl.DescriptorCacheWriter
import org.jetbrains.intellij.build.impl.DescriptorMarker
import org.jetbrains.intellij.build.impl.PluginDescriptorPatchRequest
import org.jetbrains.intellij.build.impl.ScopedCachedDescriptorContainer
import org.jetbrains.intellij.build.impl.applyPluginDescriptorPatch
import org.jetbrains.intellij.build.impl.computePluginBuildNumber
import org.jetbrains.intellij.build.impl.getCompatiblePlatformVersionRange
import org.jetbrains.intellij.build.impl.osArchDescriptorMarker
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Writes one plugin's patched `META-INF/plugin.xml`, from declared files and nothing else.
 *
 * The `dev_dist_plugin_descriptor` rule runs one of these per plugin. It is a main of its own and not a mode of
 * `DevDistMain`, because the assembler's every option exists to serve the assembly. This entry point must not do any
 * of the following, and a reviewer can read the list against the code:
 *
 * * construct a `BuildContext`, a `PluginLayout` or a `PlatformLayout`;
 * * call `buildProductInProcess`, `createDevBuildContext` or a product-properties factory;
 * * set `intellij.build.ultimate.home.path`, or read `BuildPaths.COMMUNITY_ROOT`. The build number arrives as a file;
 * * read `intellij.build.bazel.inputs.manifest`. Its inputs are its arguments;
 * * open a socket. It downloads nothing;
 * * construct a real [ModuleOutputProvider]. [RefusingModuleOutputProvider] throws from every method, so an unseeded
 *   descriptor cache fails loudly instead of loading a project model.
 *
 * The seam that makes this work is the descriptor cache. `resolveElement` and `resolveContentModuleDescriptor` both read
 * the cache before they touch the output provider, so a run that seeds both caches from its declared files never asks
 * the provider anything.
 *
 * The patch itself is [applyPluginDescriptorPatch], the same body the assembly runs. One body means the two producers
 * cannot disagree, so `./build/dev-dist.cmd descriptors` compares the request and not the code.
 */
fun main(args: Array<String>) {
  val request = parseDevDistPluginDescriptorRequest(readArgumentLines(args))
  val content = runBlocking { patchPluginDescriptorFromPlan(request) }
  Files.createDirectories(request.output.parent)
  Files.write(request.output, content.encodeToByteArray())
}

/**
 * One plugin's request, as the rule states it.
 *
 * Every field is a string, a boolean, a path or a list of those. Nothing here can reach a layout or a build context.
 */
internal class DevDistPluginDescriptorRequest(
  @JvmField val output: Path,
  @JvmField val mainModule: String,
  @JvmField val directoryName: String,
  @JvmField val mainJarName: String,
  @JvmField val source: Path,
  @JvmField val buildNumberFile: Path,
  @JvmField val buildDateInSeconds: Long,
  @JvmField val releaseDate: String,
  @JvmField val releaseVersion: String,
  @JvmField val isEap: Boolean,
  @JvmField val exactVersion: Boolean,
  @JvmField val retainProductDescriptor: Boolean,
  @JvmField val embedsContentModules: Boolean,
  /**
   * The content modules the product's filter refuses. Normally empty.
   *
   * The survivors are the descriptor's own `<content>`, which this action already declares as an input. So the plan
   * states the refusals, and a refusal that reaches no `<module/>` fails the action.
   */
  @JvmField val refusedContentModules: List<String>,
  /** Which content module's embedded descriptor takes `separate-jar="true"`. A deviation, so normally empty. */
  @JvmField val separateJarModules: Set<String>,
  /** The descriptors this plugin's patch can reach, keyed by the load path a resolver asks for. */
  @JvmField val pluginDescriptors: Map<String, Path>,
  /**
   * A descriptor no production source root holds, keyed by load path and valued by the jars that may hold it.
   *
   * The load path is also the zip entry: `toLoadPath` strips the leading `/`, and that is the path the assembly's
   * `findFileInModuleLibraryDependencies` asks a library jar for. The rule declares a library container rather than one
   * jar, so the value is every jar of that container and the first one with the entry answers.
   */
  @JvmField val pluginDescriptorsInJar: Map<String, List<Path>> = emptyMap(),
  /** The same, for the platform's search scope. */
  @JvmField val platformDescriptors: Map<String, Path>,
  /** The plugin's own search-scope modules. */
  @JvmField val pluginModules: List<String>,
  /** The platform's search-scope modules. */
  @JvmField val platformModules: List<String>,
  /** The layout's raw text patch as marker-table rows, in the order it applies them - see [applyDescriptorMarkers]. */
  @JvmField val markers: List<String> = emptyList(),
  /** What the layout appends to the IDE build version. Empty for a layout that stamps it unchanged. */
  @JvmField val versionSuffix: String = "",
)

/** Runs the shared patch body over [request] and returns the text the plugin's main jar receives. */
internal suspend fun patchPluginDescriptorFromPlan(request: DevDistPluginDescriptorRequest): String {
  val buildNumber = Files.readString(request.buildNumberFile).trim()
  val pluginVersion = computePluginBuildNumber(buildNumber = buildNumber, buildDateInSeconds = request.buildDateInSeconds) +
                      request.versionSuffix
  val compatibleBuildRange = when {
    request.exactVersion -> CompatibleBuildRange.EXACT
    request.isEap -> CompatibleBuildRange.RESTRICTED_TO_SAME_RELEASE
    else -> CompatibleBuildRange.NEWER_WITH_SAME_BASELINE
  }

  val sourceContent = Files.readAllBytes(request.source).decodeToString()
  val pluginCache = SeededDescriptorContainer(
    readSeed(request.pluginDescriptors) + readSeedFromJars(request.pluginDescriptorsInJar),
    isModuleSetOwner = false,
  )
  val platformCache = SeededDescriptorContainer(readSeed(request.platformDescriptors), isModuleSetOwner = true)
  val xIncludeResolver = XIncludeElementResolverImpl(
    searchPath = listOf(
      DescriptorSearchScope(LinkedHashSet(request.pluginModules), pluginCache),
      DescriptorSearchScope(LinkedHashSet(request.platformModules), platformCache),
    ),
    context = RefusingDescriptorResolveContext,
  )

  return applyPluginDescriptorPatch(
    request = PluginDescriptorPatchRequest(
      mainModule = request.mainModule,
      directoryName = request.directoryName,
      mainJarName = request.mainJarName,
      sourceContent = sourceContent,
      // The raw text patch, from the plan's marker table. A layout that states the patch as code is held out of the
      // population, so a plugin that gets here either states no patch or states one this table can express.
      rawPatchedContent = applyDescriptorMarkers(text = sourceContent, markers = request.markers),
      pluginVersion = pluginVersion,
      compatibleSinceUntil = getCompatiblePlatformVersionRange(compatibleBuildRange, buildNumber),
      releaseDate = request.releaseDate,
      releaseVersion = request.releaseVersion,
      // A dev distribution publishes no plugin: `PluginBuilder` passes an empty set on this path.
      toPublish = false,
      retainProductDescriptorForBundledPlugin = request.retainProductDescriptor,
      isEap = request.isEap,
      embedsContentModules = request.embedsContentModules,
    ),
    xIncludeResolver = xIncludeResolver,
    // The stage record belongs to the assembly, which is the arm the byte gate compares against. Recording here would
    // make the two arms one.
    stages = null,
    embedContentModules = { rootElement ->
      embedContentModulesFromPlan(
        rootElement = rootElement,
        request = request,
        pluginCache = pluginCache,
        xIncludeResolver = xIncludeResolver,
      )
    },
    patchText = { it },
  )
}

/**
 * The content-module stage, driven by the plan instead of by a `ContentModuleFilter`.
 *
 * The assembly's `collectContentModules` removes an optional `<module/>` its filter refuses, and that filter reads the
 * JPS project model. The plan states the refusals instead, so this removes the `<module/>` of each refused name and
 * keeps the rest where they are.
 *
 * Every refusal must be found. A refusal that reaches no `<module/>` is a plan the descriptor has moved away from, so
 * this fails and names what it could not find.
 */
private suspend fun embedContentModulesFromPlan(
  rootElement: Element,
  request: DevDistPluginDescriptorRequest,
  pluginCache: ScopedCachedDescriptorContainer,
  xIncludeResolver: XIncludeElementResolverImpl,
) {
  val refused = LinkedHashSet(request.refusedContentModules)
  val found = HashSet<String>()
  val kept = ArrayList<Pair<Element, String>>()
  for (content in rootElement.getChildren("content")) {
    val iterator = content.getChildren("module").iterator()
    while (iterator.hasNext()) {
      val moduleElement = iterator.next()
      val moduleName = requireNotNull(moduleElement.getAttributeValue("name")) {
        "A <module/> of ${request.mainModule} states no name"
      }
      if (refused.contains(moduleName)) {
        found.add(moduleName)
        iterator.remove()
      }
      else {
        kept.add(moduleElement to moduleName)
      }
    }
  }

  val missing = refused.filterNot { found.contains(it) }
  check(missing.isEmpty()) {
    "The plan of ${request.mainModule} refuses the content modules $missing. " +
    "Its descriptor states no <module/> of those names"
  }

  if (!request.embedsContentModules) {
    return
  }

  for ((moduleElement, moduleName) in kept) {
    resolveAndEmbedContentModuleDescriptor(
      moduleElement = moduleElement,
      descriptorCache = pluginCache,
      xIncludeResolver = xIncludeResolver,
      outputProvider = RefusingModuleOutputProvider,
      descriptorModifier = { descriptor ->
        // The three gates `embedContentModule` applies, in its order. The middle one is the reason a name that holds a
        // `/` never takes the attribute: such a name points at a descriptor of another module, and the assembly asks
        // the verdict of the module before the `/` only when the two are the same string.
        if (descriptor.getAttributeValue("package") != null &&
            moduleName.substringBeforeLast('/') == moduleName &&
            request.separateJarModules.contains(moduleName)) {
          descriptor.setAttribute("separate-jar", "true")
        }
      },
    )
  }
}

private fun readSeed(files: Map<String, Path>): Map<String, ByteArray> {
  val result = HashMap<String, ByteArray>(files.size)
  for ((loadPath, file) in files) {
    result[loadPath] = Files.readAllBytes(file)
  }
  return result
}

/**
 * The same, for a descriptor that lives inside a declared library container.
 *
 * The assembly reaches such a file through `findFileInModuleLibraryDependencies`, which asks each declared library jar
 * for the load path. This asks the container's jars in the container's own order, and the first jar with the entry
 * answers. A container whose jars all miss fails the run, and the failure names every jar it asked.
 */
private fun readSeedFromJars(candidates: Map<String, List<Path>>): Map<String, ByteArray> {
  val result = HashMap<String, ByteArray>(candidates.size)
  for ((loadPath, jars) in candidates) {
    var data: ByteArray? = null
    for (jar in jars) {
      // A zip file system and not `ImmutableZipFile`: that reader needs `sun.nio.ch` opened to the unnamed module, and
      // this tool is a plain `java_binary` with no JVM argument of its own. Six entries of one plugin are read this way.
      data = FileSystems.newFileSystem(jar).use { zip ->
        val entry = zip.getPath(loadPath)
        if (Files.exists(entry)) Files.readAllBytes(entry) else null
      }
      if (data != null) {
        break
      }
    }
    result[loadPath] = requireNotNull(data) { "No declared jar has the entry '$loadPath': ${jars.joinToString()}" }
  }
  return result
}

/**
 * The raw text patch of one plan entry: the first occurrence of each literal replaced, in the table's order.
 *
 * ### Why a plain replacement and not `checkedReplace`
 *
 * `checkedReplace` compiles the literal as a regular expression and reads `$` and `\` in the replacement. The Go
 * executor's `regexp` is RE2 and Java's `Pattern` is not, so a row that reached either engine would be a row the two
 * producers could read differently. The generator therefore refuses a row whose literal states a regular-expression
 * metacharacter and one whose replacement states `$` or `\`, and both producers replace a plain string here.
 *
 * A literal the descriptor does not state fails the action. `checkedReplace` tolerates that case outside TeamCity, for
 * an `Update IDE from Sources` run that re-patches a text it already patched; this action reads a declared source file
 * and can never be in that state.
 */
internal fun applyDescriptorMarkers(text: String, markers: List<String>): String {
  var result = text
  for (row in markers) {
    val marker = parseDescriptorMarkerRow(row)
    val at = result.indexOf(marker.literal)
    require(at >= 0) { "The descriptor does not state '${marker.literal}', which the marker table replaces" }
    result = result.substring(0, at) + marker.replacement + result.substring(at + marker.literal.length)
  }
  return result
}

/**
 * One marker-table row as a literal and its replacement.
 *
 * `os-arch:<osId>:<marketplaceName>` names the operating system and the architecture, and `osArchDescriptorMarker`
 * builds the replacement - it is the one owner of that text, and the text holds a newline the request's parameter file
 * could not carry. `marker:<literal>:<replacement>` states a plain replacement, and the literal ends at the first `:`.
 * An unknown shape fails the action, so no run can emit an unpatched text.
 */
internal fun parseDescriptorMarkerRow(row: String): DescriptorMarker {
  val separator = row.indexOf(':')
  require(separator > 0) { "A marker row is '<shape>:...', and '$row' is not" }
  val rest = row.substring(separator + 1)
  when (row.substring(0, separator)) {
    "os-arch" -> {
      val osId = rest.substringBefore(':')
      val architecture = rest.substringAfter(':', missingDelimiterValue = "")
      val os = requireNotNull(OsFamily.entries.firstOrNull { it.osId == osId }) { "'$osId' is no OsFamily.osId" }
      val arch = requireNotNull(JvmArchitecture.entries.firstOrNull { it.marketplaceName == architecture }) {
        "'$architecture' is no JvmArchitecture.marketplaceName"
      }
      return osArchDescriptorMarker(os = os, arch = arch)
    }
    "marker" -> {
      val literal = rest.substringBefore(':')
      require(literal.isNotEmpty() && rest.length > literal.length) { "A marker row is 'marker:<literal>:<replacement>', and '$row' is not" }
      return DescriptorMarker(literal = literal, replacement = rest.substring(literal.length + 1))
    }
  }
  error("'$row' states a marker shape this tool does not know, so the descriptor would be emitted unpatched")
}

/** A descriptor cache seeded from declared files, with no layout to key it by. */
private class SeededDescriptorContainer(
  seed: Map<String, ByteArray>,
  override val isModuleSetOwner: Boolean,
) : ScopedCachedDescriptorContainer {
  private val content = ConcurrentHashMap<String, ByteArray>(seed)

  override fun getCachedFileData(name: String): ByteArray? = content[name]

  override fun put(name: String, data: ByteArray) {
    content[name] = data
  }

  override fun putIfAbsent(name: String, data: ByteArray) {
    content.putIfAbsent(name, data)
  }

  override fun write(): DescriptorCacheWriter {
    return object : DescriptorCacheWriter {
      private val staged = HashMap<String, ByteArray>()

      @Synchronized
      override fun put(name: String, data: ByteArray) {
        staged[name] = data
      }

      @Synchronized
      override fun apply() {
        content.putAll(staged)
        staged.clear()
      }
    }
  }
}

/** The name of the product-properties class the resolver compares against. This run has no product properties. */
private const val NO_PRODUCT_PROPERTIES = "DevDistPluginDescriptorMain"

private object RefusingDescriptorResolveContext : DescriptorResolveContext {
  override val outputProvider: ModuleOutputProvider
    get() = RefusingModuleOutputProvider

  override val productPropertiesName: String
    get() = NO_PRODUCT_PROPERTIES
}

/**
 * Every method throws.
 *
 * A descriptor this run did not declare must fail loudly. The alternative is a provider that loads a project model,
 * which is the one thing this entry point exists not to do.
 */
// The two nullable return types are the interface's, and every body here throws. Kotlin reads that as a return type
// that is never null, which it is - the signature still has to match what it overrides.
@Suppress("RedundantNullableReturnType")
private object RefusingModuleOutputProvider : ModuleOutputProvider {
  override val useTestCompilationOutput: Boolean
    get() = refuse("useTestCompilationOutput")

  override fun findModule(name: String): JpsModule? = refuse("findModule($name)")

  override fun getModuleImlFile(module: JpsModule): Path = refuse("getModuleImlFile")

  override fun findRequiredModule(name: String): JpsModule = refuse("findRequiredModule($name)")

  override fun findLibraryRoots(libraryName: String, moduleLibraryModuleName: String?): List<Path> =
    refuse("findLibraryRoots($libraryName)")

  override fun getProjectLibraryToModuleMap(): Map<String, String> = refuse("getProjectLibraryToModuleMap")

  override fun getModuleOutputRoots(module: JpsModule, forTests: Boolean): List<Path> = refuse("getModuleOutputRoots")

  override suspend fun readFileContentFromModuleOutput(module: JpsModule, relativePath: String, forTests: Boolean): ByteArray? =
    refuse("readFileContentFromModuleOutput($relativePath)")

  private fun refuse(what: String): Nothing {
    throw UnsupportedOperationException(
      "$what needs a JPS project model, and this action declares its descriptors as files instead. " +
      "The plan of this plugin is incomplete: add the descriptor the patch asked for."
    )
  }
}

/**
 * The argument lines of this request.
 *
 * The rule passes one `--flagfile=<path>` of a multiline parameter file, the way `content_module_jar` and `ij_plugin`
 * do. Plain arguments are accepted too, so the binary is runnable by hand.
 */
internal fun readArgumentLines(args: Array<String>): List<String> {
  if (args.size == 1 && args[0].startsWith("--flagfile=")) {
    return Files.readAllLines(Path.of(args[0].removePrefix("--flagfile=")))
  }
  return args.toList()
}

internal fun parseDevDistPluginDescriptorRequest(lines: List<String>): DevDistPluginDescriptorRequest {
  var output: Path? = null
  var mainModule: String? = null
  var directoryName: String? = null
  var mainJarName: String? = null
  var source: Path? = null
  var buildNumberFile: Path? = null
  var buildDateInSeconds = 0L
  var releaseDate: String? = null
  var releaseVersion: String? = null
  var isEap = false
  var exactVersion = false
  var retainProductDescriptor = false
  var embedsContentModules = true
  val refusedContentModules = ArrayList<String>()
  val separateJarModules = LinkedHashSet<String>()
  val pluginDescriptors = LinkedHashMap<String, Path>()
  val pluginDescriptorsInJar = LinkedHashMap<String, MutableList<Path>>()
  val platformDescriptors = LinkedHashMap<String, Path>()
  val pluginModules = ArrayList<String>()
  val platformModules = ArrayList<String>()
  val markers = ArrayList<String>()
  var versionSuffix = ""

  for (line in lines) {
    if (line.isEmpty()) {
      continue
    }

    val separator = line.indexOf('=')
    val option = if (separator == -1) line else line.substring(0, separator)
    val value = if (separator == -1) "" else line.substring(separator + 1)
    when (option) {
      "--out" -> output = Path.of(value)
      "--main-module" -> mainModule = value
      "--directory-name" -> directoryName = value
      "--main-jar-name" -> mainJarName = value
      "--source" -> source = Path.of(value)
      "--build-number-file" -> buildNumberFile = Path.of(value)
      "--build-date-seconds" -> buildDateInSeconds = value.toLong()
      "--release-date" -> releaseDate = value
      "--release-version" -> releaseVersion = value
      "--eap" -> isEap = value.toBooleanStrict()
      "--exact-version" -> exactVersion = value.toBooleanStrict()
      "--retain-product-descriptor" -> retainProductDescriptor = value.toBooleanStrict()
      "--embed-content-modules" -> embedsContentModules = value.toBooleanStrict()
      "--refused-content-module" -> refusedContentModules.add(value)
      "--separate-jar" -> separateJarModules.add(value)
      "--plugin-descriptor" -> putDescriptor(pluginDescriptors, value)
      "--plugin-descriptor-in-jar" -> appendDescriptorJar(pluginDescriptorsInJar, value)
      "--marker" -> markers.add(value)
      "--version-suffix" -> versionSuffix = value
      "--platform-descriptor" -> putDescriptor(platformDescriptors, value)
      "--plugin-module" -> pluginModules.add(value)
      "--platform-module" -> platformModules.add(value)
      else -> throw IllegalArgumentException("Unknown option '$option'")
    }
  }

  val module = requireNotNull(mainModule) { "--main-module is required" }
  return DevDistPluginDescriptorRequest(
    output = requireNotNull(output) { "--out is required" },
    mainModule = module,
    directoryName = directoryName ?: devDistPluginDirectoryName(module),
    mainJarName = mainJarName ?: "${devDistPluginDirectoryName(module)}.jar",
    source = requireNotNull(source) { "--source is required" },
    buildNumberFile = requireNotNull(buildNumberFile) { "--build-number-file is required" },
    buildDateInSeconds = buildDateInSeconds,
    releaseDate = requireNotNull(releaseDate) { "--release-date is required" },
    releaseVersion = requireNotNull(releaseVersion) { "--release-version is required" },
    isEap = isEap,
    exactVersion = exactVersion,
    retainProductDescriptor = retainProductDescriptor,
    embedsContentModules = embedsContentModules,
    refusedContentModules = refusedContentModules,
    separateJarModules = separateJarModules,
    pluginDescriptors = pluginDescriptors,
    pluginDescriptorsInJar = pluginDescriptorsInJar,
    platformDescriptors = platformDescriptors,
    pluginModules = pluginModules,
    platformModules = platformModules,
    markers = markers,
    versionSuffix = versionSuffix,
  )
}

private fun putDescriptor(into: MutableMap<String, Path>, value: String) {
  val separator = value.indexOf('=')
  require(separator > 0) { "A descriptor is '<load path>=<file>', and '$value' is not" }
  into[value.substring(0, separator)] = Path.of(value.substring(separator + 1))
}

/**
 * The same, for a jar of a library container.
 *
 * One option per (load path, jar), because the rule declares a container and states every jar of it. The order is the
 * container's own, and the first jar with the entry answers - see [readSeedFromJars].
 */
private fun appendDescriptorJar(into: MutableMap<String, MutableList<Path>>, value: String) {
  val separator = value.indexOf('=')
  require(separator > 0) { "A descriptor is '<load path>=<file>', and '$value' is not" }
  into.computeIfAbsent(value.substring(0, separator)) { ArrayList() }.add(Path.of(value.substring(separator + 1)))
}

/** The plugin's directory under `plugins/`, as `PluginLayout` derives it. */
internal fun devDistPluginDirectoryName(mainModule: String): String =
  mainModule.removePrefix("intellij.").replace('.', '-')
