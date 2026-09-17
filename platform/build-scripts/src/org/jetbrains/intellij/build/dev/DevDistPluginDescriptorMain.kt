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
 * Resolves one plugin's embedded product descriptor, from declared files and nothing else.
 *
 * The `dev_dist_embedded_product_descriptor` rule runs one of these for each plugin that declares an embedded product
 * descriptor. It is a main of its own and not a mode of `DevDistMain`, because the assembler's every option exists to
 * serve the assembly. This entry point must not do any of the following, and a reviewer can read the list against the
 * code:
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
  val request = parseDevDistEmbeddedProductDescriptorRequest(readArgumentLines(args))
  val content = resolveEmbeddedProductDescriptorFromPlan(request)
  Files.createDirectories(request.output.parent)
  Files.write(request.output, content)
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
      "--embedded-product" -> {
        require(value.isEmpty() && !modeSeen) { "--embedded-product is a flag and is declared once" }
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

  require(modeSeen) { "--embedded-product is required" }
  return DevDistEmbeddedProductDescriptorRequest(
    output = requireNotNull(output) { "--out is required" },
    source = requireNotNull(source) { "--source is required" },
    descriptors = descriptors,
    descriptorsInJar = descriptorsInJar,
    modules = modules,
    separateJarModules = separateJarModules,
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
