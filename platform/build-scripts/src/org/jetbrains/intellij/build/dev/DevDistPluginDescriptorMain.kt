// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DevDistPluginDescriptorMain")

package org.jetbrains.intellij.build.dev

import kotlinx.coroutines.runBlocking
import org.jdom.Element
import org.jetbrains.intellij.build.CompatibleBuildRange
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.classPath.DescriptorSearchScope
import org.jetbrains.intellij.build.classPath.DescriptorResolveContext
import org.jetbrains.intellij.build.classPath.XIncludeElementResolverImpl
import org.jetbrains.intellij.build.classPath.resolveAndEmbedContentModuleDescriptor
import org.jetbrains.intellij.build.impl.DescriptorCacheWriter
import org.jetbrains.intellij.build.impl.PluginDescriptorPatchRequest
import org.jetbrains.intellij.build.impl.ScopedCachedDescriptorContainer
import org.jetbrains.intellij.build.impl.applyPluginDescriptorPatch
import org.jetbrains.intellij.build.impl.computePluginBuildNumber
import org.jetbrains.intellij.build.impl.getCompatiblePlatformVersionRange
import org.jetbrains.jps.model.module.JpsModule
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
  /** The filtered content-module names, in the order the descriptor must keep them. */
  @JvmField val contentModules: List<String>,
  /** Which content module's embedded descriptor takes `separate-jar="true"`. A deviation, so normally empty. */
  @JvmField val separateJarModules: Set<String>,
  /** The descriptors this plugin's patch can reach, keyed by the load path a resolver asks for. */
  @JvmField val pluginDescriptors: Map<String, Path>,
  /** The same, for the platform's search scope. */
  @JvmField val platformDescriptors: Map<String, Path>,
  /** The plugin's own search-scope modules. */
  @JvmField val pluginModules: List<String>,
  /** The platform's search-scope modules. */
  @JvmField val platformModules: List<String>,
)

/** Runs the shared patch body over [request] and returns the text the plugin's main jar receives. */
internal suspend fun patchPluginDescriptorFromPlan(request: DevDistPluginDescriptorRequest): String {
  val buildNumber = Files.readString(request.buildNumberFile).trim()
  val pluginVersion = computePluginBuildNumber(buildNumber = buildNumber, buildDateInSeconds = request.buildDateInSeconds)
  val compatibleBuildRange = when {
    request.exactVersion -> CompatibleBuildRange.EXACT
    request.isEap -> CompatibleBuildRange.RESTRICTED_TO_SAME_RELEASE
    else -> CompatibleBuildRange.NEWER_WITH_SAME_BASELINE
  }

  val sourceContent = Files.readAllBytes(request.source).decodeToString()
  val pluginCache = SeededDescriptorContainer(readSeed(request.pluginDescriptors), isModuleSetOwner = false)
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
      // The raw text patch is a per-layout lambda. No plugin this rule serves declares one, and the rule refuses a
      // plugin that does, so the stage is the identity here.
      rawPatchedContent = sourceContent,
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
 * JPS project model. The plan states the survivors in order instead, so this removes every `<module/>` the plan does not
 * name and keeps the rest where they are.
 */
private suspend fun embedContentModulesFromPlan(
  rootElement: Element,
  request: DevDistPluginDescriptorRequest,
  pluginCache: ScopedCachedDescriptorContainer,
  xIncludeResolver: XIncludeElementResolverImpl,
) {
  val survivors = LinkedHashSet(request.contentModules)
  val kept = ArrayList<Pair<Element, String>>()
  for (content in rootElement.getChildren("content")) {
    val iterator = content.getChildren("module").iterator()
    while (iterator.hasNext()) {
      val moduleElement = iterator.next()
      val moduleName = requireNotNull(moduleElement.getAttributeValue("name")) {
        "A <module/> of ${request.mainModule} states no name"
      }
      if (survivors.contains(moduleName)) {
        kept.add(moduleElement to moduleName)
      }
      else {
        iterator.remove()
      }
    }
  }

  check(kept.map { it.second } == request.contentModules) {
    "The plan of ${request.mainModule} states the content modules ${request.contentModules}, " +
    "and its descriptor states ${kept.map { it.second }}"
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
  val contentModules = ArrayList<String>()
  val separateJarModules = LinkedHashSet<String>()
  val pluginDescriptors = LinkedHashMap<String, Path>()
  val platformDescriptors = LinkedHashMap<String, Path>()
  val pluginModules = ArrayList<String>()
  val platformModules = ArrayList<String>()

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
      "--content-module" -> contentModules.add(value)
      "--separate-jar" -> separateJarModules.add(value)
      "--plugin-descriptor" -> putDescriptor(pluginDescriptors, value)
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
    contentModules = contentModules,
    separateJarModules = separateJarModules,
    pluginDescriptors = pluginDescriptors,
    platformDescriptors = platformDescriptors,
    pluginModules = pluginModules,
    platformModules = platformModules,
  )
}

private fun putDescriptor(into: MutableMap<String, Path>, value: String) {
  val separator = value.indexOf('=')
  require(separator > 0) { "A descriptor is '<load path>=<file>', and '$value' is not" }
  into[value.substring(0, separator)] = Path.of(value.substring(separator + 1))
}

/** The plugin's directory under `plugins/`, as `PluginLayout` derives it. */
internal fun devDistPluginDirectoryName(mainModule: String): String =
  mainModule.removePrefix("intellij.").replace('.', '-')
