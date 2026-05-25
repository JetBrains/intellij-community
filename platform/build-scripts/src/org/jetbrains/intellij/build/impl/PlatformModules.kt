// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "RedundantSuppression", "ReplaceGetOrSet", "ReplacePutWithAssignment")
package org.jetbrains.intellij.build.impl

import com.intellij.util.graph.DFSTBuilder
import com.intellij.util.graph.OutboundSemiGraph
import io.opentelemetry.api.trace.Span
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.PLATFORM_LOADER_JAR
import org.jetbrains.intellij.build.UTIL_8_JAR
import org.jetbrains.intellij.build.UTIL_JAR
import org.jetbrains.intellij.build.UTIL_RT_JAR
import org.jetbrains.intellij.build.classPath.getEmbeddedContentModulesOfPluginsWithUseIdeaClassloader
import org.jetbrains.intellij.build.forEachConcurrent
import org.jetbrains.intellij.build.impl.PlatformJarNames.TEST_FRAMEWORK_JAR
import org.jetbrains.intellij.build.isModuleNameLikeFilename
import org.jetbrains.intellij.build.productLayout.LIB_MODULE_PREFIX
import org.jetbrains.intellij.build.productLayout.ProductModulesLayout
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModuleDependency
import org.jetbrains.jps.model.module.JpsModuleReference
import java.util.SortedSet

@Suppress("RemoveRedundantQualifierName")
private val PLATFORM_CUSTOM_PACK_MODE: Map<String, LibraryPackMode> = java.util.Map.of(
  "jetbrains-annotations", LibraryPackMode.STANDALONE_SEPARATE_WITHOUT_VERSION_NAME,
)

private fun addModule(relativeJarPath: String, moduleNames: Sequence<String>, productLayout: ProductModulesLayout, layout: PlatformLayout) {
  layout.withModules(
    moduleNames
      .filter { !productLayout.excludedModuleNames.contains(it) }
      .map { ModuleItem(moduleName = it, relativeOutputFile = relativeJarPath, reason = "addModule") }
  )
}

suspend fun createPlatformLayout(context: BuildContext): PlatformLayout {
  val enabledPluginModules = context.getBundledPluginModules().toHashSet()
  return createPlatformLayout(
    projectLibrariesUsedByPlugins = computeProjectLibsUsedByPlugins(enabledPluginModules = enabledPluginModules, context = context),
    context = context,
  )
}

internal suspend fun createPlatformLayout(projectLibrariesUsedByPlugins: SortedSet<ProjectLibraryData>, context: BuildContext): PlatformLayout {
  val productLayout = context.productProperties.productLayout
  val descriptorCacheContainer = DescriptorCacheContainer()
  val layout = PlatformLayout(descriptorCacheContainer)
  // used only in modules that packed into Java
  layout.withoutProjectLibrary("jps-javac-extension")
  layout.withoutProjectLibrary("Eclipse")

  for (customizer in productLayout.platformLayoutSpec) {
    customizer(layout, context)
  }
  for ((module, patterns) in productLayout.moduleExcludes) {
    layout.excludeFromModule(module, patterns)
  }

  addModule(UTIL_RT_JAR, sequenceOf(
    "intellij.platform.util.rt",
  ), productLayout = productLayout, layout = layout)
  // trove is not used by JB Client - fix RuntimeModuleRepositoryChecker assert
  addModule("trove.jar", sequenceOf(
    "intellij.platform.util.trove",
    "intellij.platform.util.troveCompileOnly",
  ), productLayout = productLayout, layout = layout)

  // maven uses JDOM in an external process
  addModule(UTIL_8_JAR, sequenceOf(
    "intellij.platform.util.jdom",
    "intellij.platform.util.xmlDom",
    "intellij.platform.tracing.rt",
    "intellij.platform.util.base",
    "intellij.platform.util.base.multiplatform",
    "intellij.platform.diagnostic",
    // it contains common telemetry-related code (utils, TelemetryContext) for OpenTelemetry
    "intellij.platform.diagnostic.telemetry.rt",
    "intellij.platform.util",
    "intellij.platform.util.multiplatform",
    // it has package `kotlin.coroutines.jvm.internal` - should be packed into the same JAR as coroutine lib,
    // to ensure that package index will not report one more JAR in a search path
    "intellij.platform.bootstrap.coroutine",
    "intellij.platform.eel",  // EelFiles, which is a replacement for java.nio.file.Files, may be used everywhere
    "intellij.platform.eel.nioFs",  // NIO bridge for EEL (EelPath <-> Path conversions, EelPathBoundDescriptor)
  ), productLayout = productLayout, layout = layout)

  // todo as content module
  layout.withProjectLibraries(sequenceOf(
    "slf4j-api",
    "slf4j-jdk14",
  ), UTIL_8_JAR)

  // https://jetbrains.team/p/ij/reviews/67104/timeline
  // https://youtrack.jetbrains.com/issue/IDEA-179784
  // https://youtrack.jetbrains.com/issue/IDEA-205600
  layout.withProjectLibraries(sequenceOf(
    "javax.annotation-api",
    "javax.activation",
    "jaxb-runtime",
    "jaxb-api",
  ))

  // TODO(Shumaf.Lovpache): IJPL-1014 convert lsp4j to product modules after merge into master
  if (context.project.libraryCollection.findLibrary("eclipse.lsp4j") != null) {
    layout.withProjectLibraries(
      sequenceOf(
        "eclipse.lsp4j",
        "eclipse.lsp4j.jsonrpc",
      ) + sequenceOf(
        "eclipse.lsp4j.debug",
        "eclipse.lsp4j.jsonrpc.debug",
      ).filter { context.project.libraryCollection.findLibrary(it) != null }
    )
  }

  // platform-loader.jar is loaded by JVM classloader as part of loading our custom PathClassLoader class - reduce file size
  addModule(PLATFORM_LOADER_JAR, sequenceOf(
    "intellij.platform.util.rt.java8",
    "intellij.platform.util.classLoader",
    "intellij.platform.util.zip",
    "intellij.platform.boot",
    "intellij.platform.runtime.repository",
    "intellij.platform.runtime.loader",
  ), productLayout = productLayout, layout = layout)
  addModule(UTIL_JAR, sequenceOf(
    // Scala uses GeneralCommandLine in JPS plugin
    "intellij.platform.ide.util.io",
    "intellij.platform.extensions",
    "intellij.platform.util.nanoxml",
  ), productLayout = productLayout, layout = layout)
  addModule("externalProcess-rt.jar", sequenceOf(
    "intellij.platform.externalProcessAuthHelper.rt"
  ), productLayout = productLayout, layout = layout)
  if (!productLayout.excludedModuleNames.contains("intellij.java.guiForms.rt")) {
    layout.withModule("intellij.java.guiForms.rt", "forms_rt.jar")
  }
  addModule("jps-model.jar", sequenceOf(
    "intellij.platform.jps.model",
    "intellij.platform.jps.model.serialization",
    "intellij.platform.jps.model.impl"
  ), productLayout = productLayout, layout = layout)
  addModule("external-system-rt.jar", sequenceOf(
    "intellij.platform.externalSystem.rt",
    "intellij.platform.objectSerializer.annotations"
  ), productLayout = productLayout, layout = layout)

  val explicit = ArrayList<ModuleItem>()
  for (moduleName in productLayout.productImplementationModules) {
    if (productLayout.excludedModuleNames.contains(moduleName)) {
      continue
    }

    explicit.add(ModuleItem(moduleName = moduleName, relativeOutputFile = "$moduleName.jar", reason = "productImplementationModules"))
    markContentModuleToScrambleIfNeeded(moduleName = moduleName, context = context, isEmbedded = true)
  }
  val explicitModuleNames = explicit.map { it.moduleName }
  val outputProvider = context.outputProvider
  val runtimeDependencyIndex = RuntimeDependencyIndex((context as BuildContextImpl).jarPackagerDependencyHelper)

  // we should filter out modules which are included in plugins with `use-idea-classloader`
  val pluginsContents = computeContentModulesPluginsWhichUseIdeaClassloader(context)

  val productPluginContentModules = processAndGetProductPluginContentModules(
    layout = layout,
    descriptorCache = descriptorCacheContainer.forPlatform(layout),
    includedPlatformModulesPartialList = computePartialListToResolveIncludesAndCollectProductModules(
      layout = layout,
      explicitModuleNames = explicitModuleNames,
      productLayout = productLayout,
      pluginsContents = pluginsContents,
      runtimeDependencyResolver = runtimeDependencyIndex,
    ),
    context = context,
  ).toCollection(LinkedHashSet())

  // compute and add dependencies for embedded modules with includeDependencies=true
  val embeddedModulesWithDeps = productPluginContentModules.filter {
    it.reason == ModuleIncludeReasons.PRODUCT_EMBEDDED_MODULES && it.includeDependencies
  }
  if (embeddedModulesWithDeps.isNotEmpty()) {
    // Collect modules already in layout
    val alreadyIncluded = layout.includedModules.mapTo(HashSet()) { it.moduleName }
    productPluginContentModules.mapTo(alreadyIncluded) { it.moduleName }
    alreadyIncluded.addAll(explicitModuleNames)

    val embeddedDependencies = computeEmbeddedModuleDependencies(
      alreadyIncluded = alreadyIncluded,
      embeddedModules = embeddedModulesWithDeps,
      productLayout = productLayout,
      outputProvider = outputProvider,
      runtimeDependencyResolver = runtimeDependencyIndex,
    )
    productPluginContentModules.addAll(embeddedDependencies)
  }

  val implicit = computeImplicitRequiredModules(
    explicit = explicitModuleNames,
    layout = layout,
    productPluginContentModules = productPluginContentModules.mapTo(HashSet()) { it.moduleName },
    productLayout = productLayout,
    pluginsContents = pluginsContents,
    runtimeDependencyResolver = runtimeDependencyIndex,
  )

  if (context.options.validateImplicitPlatformModule) {
    val implicitContentModuleAllowlist = context.productProperties.getProductContentDescriptor()?.allowedMissingDependencies?.mapTo(HashSet()) { it.value } ?: emptySet()
    implicit.forEachConcurrent { (name, chain) ->
      validateImplicitPlatformModule(
        name = name,
        chain = chain,
        outputProvider = outputProvider,
        allowedMissingDependencies = implicitContentModuleAllowlist,
        isClientBuild = context.useModularLoader,
      )
    }
  }

  val filteredExplicit = LinkedHashSet(explicit)
  for (item in productPluginContentModules) {
    val iterator = filteredExplicit.iterator()
    while (iterator.hasNext()) {
      if (item.moduleName == iterator.next().moduleName) {
        // todo - error instead of warn
        Span.current().addEvent("product module MUST NOT BE explicitly specified: ${item.moduleName}")
        iterator.remove()
      }
    }
  }

  layout.withModules(
    (filteredExplicit.asSequence() +
     productPluginContentModules +
     implicit.asSequence().map {
       ModuleItem(
         moduleName = it.first,
         relativeOutputFile = "${it.first}.jar",
         reason = "<- " + it.second.asReversed().joinToString(separator = " <- ")
       )
     })
      .sortedBy { it.moduleName },
  )

  val libAsProductModule = collectExportedLibrariesFromLibraryModules(layout, context).keys
  layout.libAsProductModule = libAsProductModule

  // sqlite - used by DB and "import settings" (temporarily)
  layout.alwaysPackToPlugin(listOf("flexmark", "sqlite"))
  for (item in projectLibrariesUsedByPlugins) {
    val libName = item.libraryName
    if (!libAsProductModule.contains(libName) && !layout.isProjectLibraryExcluded(libName) && !layout.isLibraryAlwaysPackedIntoPlugin(libName)) {
      layout.includedProjectLibraries.add(item)
    }
  }

  // as a separate step, not a part of computing implicitModules, as we should collect libraries from such implicitly included modules
  layout.collectProjectLibrariesFromIncludedModules(outputProvider) { libName, module ->
    // this module is used only when running IDE from sources, no need to include its dependencies, see IJPL-125
    if (module.name == "intellij.platform.buildScripts.downloader" && libName == "zstd-jni") {
      return@collectProjectLibrariesFromIncludedModules
    }

    if (libAsProductModule.contains(libName)) {
      return@collectProjectLibrariesFromIncludedModules
    }

    layout.includedProjectLibraries
      .addOrGet(
        ProjectLibraryData(
          libraryName = libName,
          packMode = PLATFORM_CUSTOM_PACK_MODE.getOrDefault(libName, LibraryPackMode.MERGED),
          reason = "<- ${module.name}",
          owner = null,
        )
      )
      .dependentModules.computeIfAbsent("core") { mutableListOf() }.add(module.name)
  }

  val platformMainModule = "intellij.platform.starter"
  if (context.isEmbeddedFrontendEnabled && layout.includedModules.none { it.moduleName == platformMainModule }) {
    /* this module is used by JetBrains Client, but it isn't packed in commercial IDEs, so let's put it in a separate JAR which won't be
       loaded when the IDE is started in the regular mode */
    layout.withModule(platformMainModule, "ext/platform-main.jar")
  }

  return layout
}

private fun computePartialListToResolveIncludesAndCollectProductModules(
  layout: PlatformLayout,
  explicitModuleNames: Collection<String>,
  productLayout: ProductModulesLayout,
  pluginsContents: Set<String>,
  runtimeDependencyResolver: RuntimeDependencyResolver,
): Collection<String> {
  val result = LinkedHashSet<String>()
  layout.includedModules.mapTo(result) { it.moduleName }
  computeImplicitRequiredModules(
    explicit = explicitModuleNames,
    layout = layout,
    productPluginContentModules = emptySet(),
    productLayout = productLayout,
    pluginsContents = pluginsContents,
    runtimeDependencyResolver = runtimeDependencyResolver,
  ).mapTo(result) { it.first }
  result.addAll(explicitModuleNames)
  return result
}

/**
 * Collects names of libraries that are exported by library modules (modules with prefix [LIB_MODULE_PREFIX]).
 * 
 * Library modules like `intellij.libraries.grpc` export one or more project libraries 
 * (e.g., `grpc-core`, `grpc-stub`, `grpc-kotlin-stub`, `grpc-protobuf`).
 * These exported libraries should be treated as product modules and not included separately.
 * 
 * Note: We cannot replace all direct library references with library modules due to:
 * - Dual project structures (Fleet, Toolbox) that require direct library references
 * - Modules used in both production and build scripts (e.g., `intellij.platform.buildScripts.downloader`)
 * 
 * @param layout the platform layout containing included modules
 * @param context the build context
 * @return map from library name to the library module that exports it
 */
suspend fun collectExportedLibrariesFromLibraryModules(
  layout: PlatformLayout,
  context: BuildContext,
): Map<String, String> {
  val javaExtensionService = JpsJavaExtensionService.getInstance()
  val result = LinkedHashMap<String, String>()
  val includedModuleNames = layout.includedModules.map { it.moduleName }
  val corePluginsContentModuleNames = computeContentModulesPluginsWhichUseIdeaClassloader(context)

  (includedModuleNames + corePluginsContentModuleNames)
    .asSequence()
    .filter { it.startsWith(LIB_MODULE_PREFIX) }
    .forEach { moduleName ->
      // get all library dependencies from the module
      context.outputProvider.findRequiredModule(moduleName).dependenciesList.dependencies
        .asSequence()
        .filterIsInstance<JpsLibraryDependency>()
        .filter { libDep ->
          // Check if this library is exported
          javaExtensionService.getDependencyExtension(libDep)?.isExported == true
        }
        .mapNotNull { it.library?.name }
        .forEach { libName ->
          result.put(libName, moduleName)
        }
    }

  return result
}

internal fun computeProjectLibsUsedByPlugins(enabledPluginModules: Set<String>, context: BuildContext): SortedSet<ProjectLibraryData> {
  val result = ObjectLinkedOpenHashSet<ProjectLibraryData>()
  val pluginLayoutsByJpsModuleNames = getPluginLayoutsByJpsModuleNames(modules = enabledPluginModules, productLayout = context.productProperties.productLayout)

  val helper = (context as BuildContextImpl).jarPackagerDependencyHelper
  for (plugin in pluginLayoutsByJpsModuleNames) {
    if (plugin.auto) {
      continue
    }

    for (moduleName in plugin.includedModules.asSequence().map { it.moduleName }.distinct()) {
      val module = context.outputProvider.findRequiredModule(moduleName)
      for (element in helper.getLibraryDependencies(module, withTests = false)) {
        val libRef = element.libraryReference
        if (libRef.parentReference is JpsModuleReference) {
          continue
        }

        val libName = libRef.libraryName
        if (plugin.hasLibrary(libName)) {
          continue
        }

        val packMode = PLATFORM_CUSTOM_PACK_MODE.getOrDefault(libName, LibraryPackMode.MERGED)
        // TODO: owner is null in this case? Since it is loaded by platform
        result.addOrGet(ProjectLibraryData(libraryName = libName, packMode = packMode, reason = "<- $moduleName", owner = null))
          .dependentModules
          .computeIfAbsent(plugin.directoryName) { mutableListOf() }
          .add(moduleName)
      }
    }
  }
  return result
}

fun getEnabledPluginModules(pluginsToPublish: Set<PluginLayout>, context: BuildContext): Set<String> {
  val result = LinkedHashSet<String>()
  result.addAll(context.getBundledPluginModules())
  pluginsToPublish.mapTo(result) { it.mainModule }
  return result
}

/**
 * Sorts embedded modules topologically so dependencies are processed before dependents.
 * This ensures that when computing transitive dependencies, modules don't incorrectly
 * include dependencies that should belong to their own dependencies.
 */
private fun sortEmbeddedModulesTopologically(embeddedModules: Collection<ModuleItem>, outputProvider: ModuleOutputProvider): List<ModuleItem> {
  val graph = EmbeddedModuleGraph(embeddedModules, outputProvider)
  val builder = DFSTBuilder(graph)
  builder.circularDependency?.let { (from, to) ->
    throw IllegalStateException("Circular dependency detected: ${from.moduleName} -> ${to.moduleName}")
  }
  return builder.sortedNodes
}

private class EmbeddedModuleGraph(
  private val modules: Collection<ModuleItem>,
  private val outputProvider: ModuleOutputProvider,
) : OutboundSemiGraph<ModuleItem> {
  private val moduleByName = modules.associateBy { it.moduleName }

  override fun getNodes(): Collection<ModuleItem> = modules

  override fun getOut(node: ModuleItem): Iterator<ModuleItem> {
    val jpsModule = outputProvider.findRequiredModule(node.moduleName)
    return jpsModule.dependenciesList.dependencies
      .asSequence()
      .filterIsInstance<JpsModuleDependency>()
      .mapNotNull { moduleByName.get(it.moduleReference.moduleName) }
      .iterator()
  }
}

/**
 * Computes transitive dependencies for embedded modules that have `includeDependencies=true`.
 * Dependencies are packaged into the same JAR as their parent embedded module.
 *
 * @param embeddedModules embedded modules with includeDependencies=true (already filtered)
 * @param productLayout product modules layout
 * @return set of module items representing dependencies to add
 */
private fun computeEmbeddedModuleDependencies(
  embeddedModules: Collection<ModuleItem>,
  productLayout: ProductModulesLayout,
  alreadyIncluded: HashSet<String>,
  outputProvider: ModuleOutputProvider,
  runtimeDependencyResolver: RuntimeDependencyResolver,
): Set<ModuleItem> {
  return computeEmbeddedModuleDependenciesInOrder(
    embeddedModulesInProcessingOrder = sortEmbeddedModulesTopologically(embeddedModules, outputProvider).asReversed(),
    excludedModuleNames = productLayout.excludedModuleNames,
    alreadyIncluded = alreadyIncluded,
    dependencyResolver = runtimeDependencyResolver,
  )
}

private fun computeImplicitRequiredModules(
  explicit: Collection<String>,
  layout: PlatformLayout,
  productPluginContentModules: Set<String>,
  productLayout: ProductModulesLayout,
  pluginsContents: Set<String>,
  runtimeDependencyResolver: RuntimeDependencyResolver,
): List<Pair<String, PersistentList<String>>> {
  return collectTransitiveRuntimeDependencies(
    roots = buildImplicitTraversalRoots(
      explicit = explicit,
      layout = layout,
      productLayout = productLayout,
      productPluginContentModules = productPluginContentModules,
    ),
    blockedOrSeen = buildImplicitTraversalBlockedSet(
      explicit = explicit,
      layout = layout,
      productLayout = productLayout,
      productPluginContentModules = productPluginContentModules,
    ),
    omitFromResult = pluginsContents,
    dependencyResolver = runtimeDependencyResolver,
  )
}

private fun buildImplicitTraversalRoots(
  explicit: Collection<String>,
  layout: PlatformLayout,
  productLayout: ProductModulesLayout,
  productPluginContentModules: Set<String>,
): List<Pair<String, PersistentList<String>>> {
  val rootChain = persistentListOf<String>()
  return layout.filteredIncludedModuleNames(excludedRelativeJarPath = TEST_FRAMEWORK_JAR, includeFromSubdirectories = false)
    .plus(explicit)
    .filter {
      !productLayout.excludedModuleNames.contains(it) &&
      !productPluginContentModules.contains(it) &&
      !it.startsWith("intellij.pycharm.") &&
      !it.startsWith("intellij.python.") &&
      !it.startsWith("intellij.codeServer.") &&
      !it.startsWith("intellij.clion.") &&
      !it.startsWith("intellij.cidr.") &&
      !it.startsWith("intellij.appcode.") &&
      it != "fleet.backend" &&
      it != "intellij.codeServer" &&
      it != "intellij.goland"
    }
    .distinct()
    .sorted()
    .map { it to rootChain }
    .toList()
}

private fun buildImplicitTraversalBlockedSet(
  explicit: Collection<String>,
  layout: PlatformLayout,
  productLayout: ProductModulesLayout,
  productPluginContentModules: Set<String>,
): HashSet<String> {
  val blockedOrSeen = HashSet<String>()
  layout.includedModules.mapTo(blockedOrSeen) { it.moduleName }
  blockedOrSeen.addAll(explicit)
  blockedOrSeen.addAll(productPluginContentModules)
  blockedOrSeen.addAll(productLayout.excludedModuleNames)
  blockedOrSeen.add("fleet.backend")
  // Module intellij.featuresTrainer contains, so it is a plugin, but plugin must be not included in a platform
  // (chain: [intellij.pycharm.community, intellij.python.featuresTrainer])
  blockedOrSeen.add("intellij.pycharm.community")
  blockedOrSeen.add("intellij.python.featuresTrainer")
  blockedOrSeen.add("intellij.pycharm.ds")
  blockedOrSeen.add("intellij.notebooks.visualization")
  return blockedOrSeen
}

private suspend fun validateImplicitPlatformModule(
  name: String,
  chain: PersistentList<String>,
  outputProvider: ModuleOutputProvider,
  allowedMissingDependencies: Set<String>,
  isClientBuild: Boolean,
) {
  val jpsModule = outputProvider.findRequiredModule(name)
  val pluginXml = outputProvider.readFileContentFromModuleOutput(jpsModule, "META-INF/plugin.xml")
  check(pluginXml == null) {
    "Module $name contains ${pluginXml.contentToString()}, so it is a plugin, but plugin must be not included in a platform (chain: $chain)"
  }

  if (outputProvider.readFileContentFromModuleOutput(jpsModule, contentModuleNameToDescriptorFileName(name)) == null) {
    return
  }
  else if (allowedMissingDependencies.contains(name) || chain.firstOrNull() == "intellij.tools.testsBootstrap") {
    Span.current().addEvent("Suppressing implicit content module validation for $name via allowMissingDependencies (chain: $chain)")
  }
  else if (isClientBuild) {
    // RustIdeBuildTest failed, disable assertion as it is not a production classloader / packaging for now
    Span.current().addEvent("Suppressing implicit content module validation for $name via allowMissingDependencies " +
                            "(chain: $chain) because it is a client build (non-production classloader)")
  }
  else {
    error("Module $name is a content module. Implicit platform auto-inclusion is prohibited; plugin model must be the only truth for packaging (chain: $chain)")
  }
}

private suspend fun computeContentModulesPluginsWhichUseIdeaClassloader(context: BuildContext): Set<String> {
  val bundledPlugins = getPluginLayoutsByJpsModuleNames(modules = context.getBundledPluginModules(), productLayout = context.productProperties.productLayout)
  return bundledPlugins.flatMapTo(LinkedHashSet()) {
    getEmbeddedContentModulesOfPluginsWithUseIdeaClassloader(pluginMainModule = it.mainModule, cacheContainer = null, context = context)
  }
}

// see isV2ModulePath
internal fun toLoadPath(relativePath: String): String {
  @Suppress("SpellCheckingInspection")
  return when {
    relativePath[0] == '/' -> relativePath.substring(1)
    isModuleNameLikeFilename(relativePath) -> relativePath
    else -> "META-INF/$relativePath"
  }
}

internal object ModuleIncludeReasons {
  const val PRODUCT_MODULES: String = "productModule"
  const val PRODUCT_EMBEDDED_MODULES: String = "productEmbeddedModule"

  fun isProductModule(reason: String?): Boolean {
    return reason == PRODUCT_MODULES ||
           reason == PRODUCT_EMBEDDED_MODULES ||
           reason?.startsWith("$PRODUCT_EMBEDDED_MODULES <- ") == true
  }
}
