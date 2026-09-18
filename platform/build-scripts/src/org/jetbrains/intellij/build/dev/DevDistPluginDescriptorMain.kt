// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DevDistPluginDescriptorMain")

package org.jetbrains.intellij.build.dev

import com.intellij.openapi.util.JDOMUtil
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.classPath.DescriptorSearchScope
import org.jetbrains.intellij.build.classPath.DescriptorResolveContext
import org.jetbrains.intellij.build.classPath.XIncludeElementResolverImpl
import org.jetbrains.intellij.build.classPath.resolveAndEmbedContentModuleDescriptor
import org.jetbrains.intellij.build.classPath.resolveIncludes
import org.jetbrains.intellij.build.impl.DescriptorCacheWriter
import org.jetbrains.intellij.build.impl.ScopedCachedDescriptorContainer
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves one plugin's embedded product descriptor, or produces the application info of its embedded frontend, from
 * declared files and nothing else.
 *
 * The `dev_dist_embedded_product_descriptor` rule runs the `--embedded-product` mode for each plugin that declares an
 * embedded product descriptor. The `dev_dist_frontend_application_info` rule runs the `--application-info` mode for the
 * plugin that packs the JetBrains Client, see [prepareCwmClientApplicationInfo]. It is a main of its own and not a mode
 * of `DevDistMain`, because the assembler's every option exists to serve the assembly. This entry point must not do any
 * of the following, and a reviewer can read the list against the code:
 *
 * * construct a `BuildContext`, a `PluginLayout` or a `PlatformLayout`;
 * * call `buildProductInProcess`, `createDevBuildContext` or a product-properties factory;
 * * set `intellij.build.ultimate.home.path`, or read `BuildPaths.COMMUNITY_ROOT`;
 * * read `intellij.build.bazel.inputs.manifest`. Its inputs are its arguments;
 * * open a socket. It downloads nothing;
 * * construct a real [ModuleOutputProvider]. [RefusingModuleOutputProvider] throws from every method, so an unseeded
 *   descriptor cache fails loudly instead of loading a project model.
 *
 * The seam that makes this work is the descriptor cache. `resolveElement` and `resolveContentModuleDescriptor` both
 * read the cache before they touch the output provider. So a run that seeds the cache from its declared files never
 * asks the provider anything.
 */
fun main(args: Array<String>) {
  val lines = readArgumentLines(args)
  when (devDistPluginDescriptorMode(lines)) {
    EMBEDDED_PRODUCT_MODE -> {
      val request = parseDevDistEmbeddedProductDescriptorRequest(lines)
      writeOutput(request.output, resolveEmbeddedProductDescriptorFromPlan(request))
    }
    APPLICATION_INFO_MODE -> {
      val request = parseDevDistFrontendApplicationInfoRequest(lines)
      writeOutput(request.output, resolveFrontendApplicationInfo(request).encodeToByteArray())
    }
  }
}

private fun writeOutput(output: Path, content: ByteArray) {
  Files.createDirectories(output.parent)
  Files.write(output, content)
}

internal const val EMBEDDED_PRODUCT_MODE: String = "--embedded-product"
internal const val APPLICATION_INFO_MODE: String = "--application-info"

/** The one mode flag of the request. A request states exactly one of [EMBEDDED_PRODUCT_MODE] and [APPLICATION_INFO_MODE]. */
internal fun devDistPluginDescriptorMode(lines: List<String>): String {
  val modes = lines.filter { it == EMBEDDED_PRODUCT_MODE || it == APPLICATION_INFO_MODE }
  require(modes.size == 1) { "Exactly one of $EMBEDDED_PRODUCT_MODE and $APPLICATION_INFO_MODE is required, got $modes" }
  return modes.single()
}

/** The declared inputs of the application info of one embedded frontend. */
internal class DevDistFrontendApplicationInfoRequest(
  @JvmField val output: Path,
  @JvmField val clientApplicationInfo: Path,
  @JvmField val productApplicationInfo: Path,
  @JvmField val buildNumber: Path,
  @JvmField val eapOverride: String? = null,
  @JvmField val versionSuffixOverride: String? = null,
  @JvmField val nightly: Boolean = false,
  @JvmField val branchName: String? = null,
)

/** The application info XML of one embedded frontend from declared files and no product properties. */
internal fun resolveFrontendApplicationInfo(request: DevDistFrontendApplicationInfoRequest): String {
  return prepareCwmClientApplicationInfo(
    clientFile = request.clientApplicationInfo,
    productFile = request.productApplicationInfo,
    buildNumberFile = request.buildNumber,
    isEapOverride = request.eapOverride,
    versionSuffixOverride = request.versionSuffixOverride,
    nightlyBuild = request.nightly,
    branchName = request.branchName,
  )
}

/** The declared inputs of one embedded product descriptor. */
internal class DevDistEmbeddedProductDescriptorRequest(
  @JvmField val output: Path,
  @JvmField val source: Path,
  /** Descriptor files keyed by the load path that an XInclude or content-module lookup uses. */
  @JvmField val descriptors: Map<String, Path>,
  /** Ordered jar candidates keyed by resolver load path. */
  @JvmField val descriptorsInJar: Map<String, List<Path>> = emptyMap(),
  @JvmField val modules: List<String>,
  @JvmField val separateJarModules: Set<String>,
)

/** Resolves one embedded product descriptor from declared files and no project model. */
internal fun resolveEmbeddedProductDescriptorFromPlan(request: DevDistEmbeddedProductDescriptorRequest): ByteArray {
  val descriptorCache = SeededDescriptorContainer(
    readSeed(request.descriptors) + readSeedFromJars(request.descriptorsInJar),
    isModuleSetOwner = false,
  )
  val resolver = XIncludeElementResolverImpl(
    searchPath = listOf(DescriptorSearchScope(LinkedHashSet(request.modules), descriptorCache)),
    context = RefusingDescriptorResolveContext,
  )
  val xml = JDOMUtil.load(Files.readAllBytes(request.source))
  resolveIncludes(xml, resolver)

  for (contentElement in xml.getChildren("content")) {
    for (moduleElement in contentElement.getChildren("module")) {
      val moduleName = requireNotNull(moduleElement.getAttributeValue("name")) {
        "An embedded product content module states no name"
      }
      resolveAndEmbedContentModuleDescriptor(
        moduleElement = moduleElement,
        descriptorCache = descriptorCache,
        xIncludeResolver = resolver,
        outputProvider = RefusingModuleOutputProvider,
        descriptorModifier = { descriptor ->
          if (descriptor.getAttributeValue("package") != null &&
              moduleName.substringBeforeLast('/') == moduleName &&
              request.separateJarModules.contains(moduleName)) {
            descriptor.setAttribute("separate-jar", "true")
          }
        },
      )
    }
  }
  return JDOMUtil.write(xml).encodeToByteArray()
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
      // this tool is a plain `java_binary` with no JVM argument of its own.
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

  override fun getModuleOutputRoots(module: JpsModule, forTests: Boolean): List<Path> = refuse("getModuleOutputRoots")

  override fun readFileContentFromModuleOutput(module: JpsModule, relativePath: String, forTests: Boolean): ByteArray? =
    refuse("readFileContentFromModuleOutput($relativePath)")

  private fun refuse(what: String): Nothing {
    throw UnsupportedOperationException(
      "$what needs a JPS project model, and this action declares its descriptors as files instead. " +
      "The plan of this plugin is incomplete: add the descriptor the resolver asked for."
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

internal fun parseDevDistEmbeddedProductDescriptorRequest(lines: List<String>): DevDistEmbeddedProductDescriptorRequest {
  var output: Path? = null
  var source: Path? = null
  val descriptors = LinkedHashMap<String, Path>()
  val descriptorsInJar = LinkedHashMap<String, MutableList<Path>>()
  val modules = ArrayList<String>()
  val separateJarModules = LinkedHashSet<String>()
  var modeSeen = false

  for (line in lines) {
    if (line.isEmpty()) {
      continue
    }

    val separator = line.indexOf('=')
    val option = if (separator == -1) line else line.substring(0, separator)
    val value = if (separator == -1) "" else line.substring(separator + 1)
    when (option) {
      EMBEDDED_PRODUCT_MODE -> {
        require(value.isEmpty() && !modeSeen) { "$EMBEDDED_PRODUCT_MODE is a flag and is declared once" }
        modeSeen = true
      }
      "--out" -> output = Path.of(value)
      "--source" -> source = Path.of(value)
      "--descriptor" -> putDescriptor(descriptors, value)
      "--descriptor-in-jar" -> appendDescriptorJar(descriptorsInJar, value)
      "--module" -> modules.add(value)
      "--separate-jar" -> separateJarModules.add(value)
      else -> throw IllegalArgumentException("Unknown embedded product descriptor option '$option'")
    }
  }

  require(modeSeen) { "$EMBEDDED_PRODUCT_MODE is required" }
  return DevDistEmbeddedProductDescriptorRequest(
    output = requireNotNull(output) { "--out is required" },
    source = requireNotNull(source) { "--source is required" },
    descriptors = descriptors,
    descriptorsInJar = descriptorsInJar,
    modules = modules,
    separateJarModules = separateJarModules,
  )
}

/**
 * The argument grammar of the `--application-info` mode. The three file options and `--out` are required. The four
 * option overrides default to the values of a dev build: no EAP override, no suffix override, not nightly, no branch.
 */
internal fun parseDevDistFrontendApplicationInfoRequest(lines: List<String>): DevDistFrontendApplicationInfoRequest {
  var output: Path? = null
  var clientApplicationInfo: Path? = null
  var productApplicationInfo: Path? = null
  var buildNumber: Path? = null
  var eapOverride: String? = null
  var versionSuffixOverride: String? = null
  var nightly = false
  var branchName: String? = null
  var modeSeen = false

  for (line in lines) {
    if (line.isEmpty()) {
      continue
    }

    val separator = line.indexOf('=')
    val option = if (separator == -1) line else line.substring(0, separator)
    val value = if (separator == -1) "" else line.substring(separator + 1)
    when (option) {
      APPLICATION_INFO_MODE -> {
        require(value.isEmpty() && !modeSeen) { "$APPLICATION_INFO_MODE is a flag and is declared once" }
        modeSeen = true
      }
      "--out" -> output = Path.of(value)
      "--client-application-info" -> clientApplicationInfo = Path.of(value)
      "--product-application-info" -> productApplicationInfo = Path.of(value)
      "--build-number" -> buildNumber = Path.of(value)
      "--eap-override" -> eapOverride = value.ifEmpty { null }
      "--version-suffix-override" -> versionSuffixOverride = value.ifEmpty { null }
      "--nightly" -> {
        require(value.isEmpty()) { "--nightly is a flag" }
        nightly = true
      }
      "--branch-name" -> branchName = value.ifEmpty { null }
      else -> throw IllegalArgumentException("Unknown frontend application info option '$option'")
    }
  }

  require(modeSeen) { "$APPLICATION_INFO_MODE is required" }
  return DevDistFrontendApplicationInfoRequest(
    output = requireNotNull(output) { "--out is required" },
    clientApplicationInfo = requireNotNull(clientApplicationInfo) { "--client-application-info is required" },
    productApplicationInfo = requireNotNull(productApplicationInfo) { "--product-application-info is required" },
    buildNumber = requireNotNull(buildNumber) { "--build-number is required" },
    eapOverride = eapOverride,
    versionSuffixOverride = versionSuffixOverride,
    nightly = nightly,
    branchName = branchName,
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
