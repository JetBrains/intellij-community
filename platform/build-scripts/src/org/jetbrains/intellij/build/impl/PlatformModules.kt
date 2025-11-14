// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "RedundantSuppression", "ReplaceGetOrSet", "ReplacePutWithAssignment")
package org.jetbrains.intellij.build.impl

import io.opentelemetry.api.trace.Span
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.PLATFORM_LOADER_JAR
import org.jetbrains.intellij.build.UTIL_8_JAR
import org.jetbrains.intellij.build.UTIL_JAR
import org.jetbrains.intellij.build.UTIL_RT_JAR
import org.jetbrains.intellij.build.classPath.getEmbeddedContentModulesOfPluginsWithUseIdeaClassloader
import org.jetbrains.intellij.build.impl.PlatformJarNames.TEST_FRAMEWORK_JAR
import org.jetbrains.intellij.build.isModuleNameLikeFilename
import org.jetbrains.intellij.build.productLayout.ProductModulesLayout
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModuleReference
import java.util.SortedSet

/**
 * List of modules which are included in lib/app.jar in all IntelliJ-based IDEs and loaded by the core classloader.
 * 
 * **Please don't add new modules here!**
 *
 * If you need to add a module to all IDEs, register it as a content module in intellij.moduleSets.essential.xml, see [this article](https://youtrack.jetbrains.com/articles/IJPL-A-956) for
 * details. You can use 'loading="embedded"' to make it still loaded by the core classloader if needed.
 */
@Suppress("RemoveRedundantQualifierName")
internal val PLATFORM_CORE_MODULES = java.util.List.of(
  "intellij.platform.builtInServer",
  "intellij.platform.diff",
  "intellij.platform.editor.ui",
  "intellij.platform.externalSystem",
  "intellij.platform.externalSystem.dependencyUpdater",
  "intellij.platform.codeStyle",
  "intellij.platform.lang.core",
  "intellij.platform.debugger",
  "intellij.platform.ml",
  "intellij.platform.remote.core",
  "intellij.platform.remoteServers.agent.rt",
  "intellij.platform.usageView",
  "intellij.platform.execution",

  "intellij.platform.analysis.impl",
  "intellij.platform.editor.ex",
  "intellij.platform.externalProcessAuthHelper",
  "intellij.platform.lvcs",
  "intellij.platform.macro",
  "intellij.platform.remoteServers.impl",
  "intellij.platform.debugger.impl",
  "intellij.platform.smRunner",
  "intellij.platform.structureView.impl",
  "intellij.platform.testRunner",
  "intellij.platform.rd.community",
  "intellij.remoteDev.util",
  "intellij.platform.feedback",
  "intellij.platform.usageView.impl",
  "intellij.platform.buildScripts.downloader",

  "intellij.platform.runtime.product",
  "intellij.platform.bootstrap",

  "intellij.platform.markdown.utils",
  "intellij.platform.util.commonsLangV2Shim",

  "intellij.platform.externalSystem.impl",
  "intellij.platform.credentialStore.ui",

  // do we need it?
  "intellij.platform.sqlite",
  // todo not used by platform - move to plugin
  "intellij.platform.ide.designer",
  "intellij.platform.ide.remote",
  "intellij.platform.ide.ui.inspector",
  "intellij.platform.threadDumpParser",

  "intellij.platform.ide.favoritesTreeView",
  "intellij.platform.bookmarks",
  "intellij.platform.todo",
)

@Suppress("RemoveRedundantQualifierName")
internal val PLATFORM_CUSTOM_PACK_MODE: Map<String, LibraryPackMode> = java.util.Map.of(
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

const val LIB_MODULE_PREFIX: String = "intellij.libraries."

internal suspend fun createPlatformLayout(projectLibrariesUsedByPlugins: SortedSet<ProjectLibraryData>, context: BuildContext): PlatformLayout {
  val frontendModuleFilter = context.getFrontendModuleFilter()
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
    "intellij.platform.core",
    // it has package `kotlin.coroutines.jvm.internal` - should be packed into the same JAR as coroutine lib,
    // to ensure that package index will not report one more JAR in a search path
    "intellij.platform.bootstrap.coroutine",
    "intellij.platform.eel",  // EelFiles, which is a replacement for java.nio.file.Files, may be used everywhere
  ), productLayout = productLayout, layout = layout)

  // https://jetbrains.team/p/ij/reviews/67104/timeline
  // https://youtrack.jetbrains.com/issue/IDEA-179784
  // https://youtrack.jetbrains.com/issue/IDEA-205600
  layout.withProjectLibraries(sequenceOf(
    "javax.annotation-api",
    "javax.activation",
    "jaxb-runtime",
    "jaxb-api",
    // todo - convert to product module
    "netty-codec-compression"
  ))

  layout.withProjectLibraries(
    sequenceOf(
      "org.codehaus.groovy:groovy",
      "org.codehaus.groovy:groovy-jsr223",
      "org.codehaus.groovy:groovy-json",
      "org.codehaus.groovy:groovy-templates",
      "org.codehaus.groovy:groovy-xml",
    ),
    "groovy.jar"
  )
  // ultimate only
  if (context.project.libraryCollection.findLibrary("org.apache.ivy") != null) {
    layout.withProjectLibrary("org.apache.ivy", "groovy.jar", reason = "ivy workaround")
  }
  // TODO(Shumaf.Lovpache): IJPL-1014 convert lsp4j to product modules after merge into master
  if (context.project.libraryCollection.findLibrary("eclipse.lsp4j") != null) {
    layout.withProjectLibraries(
      sequenceOf(
        "eclipse.lsp4j",
        "eclipse.lsp4j.jsonrpc",
        "eclipse.lsp4j.debug",
        "eclipse.lsp4j.jsonrpc.debug",
      )
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
  addModule("stats.jar", sequenceOf(
    "intellij.platform.experiment",
    "intellij.platform.statistics",
    "intellij.platform.statistics.uploader",
    "intellij.platform.statistics.config",
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
  val explicit = mutableListOf<ModuleItem>()
  for (moduleName in productLayout.productImplementationModules) {
    if (productLayout.excludedModuleNames.contains(moduleName)) {
      continue
    }

    explicit.add(
      ModuleItem(
        moduleName = moduleName,
        relativeOutputFile = getProductModuleJarName(moduleName = moduleName, context = context, frontendModuleFilter = frontendModuleFilter),
        reason = "productImplementationModules",
      )
    )
  }
  explicit.addAll(toModuleItemSequence(list = PLATFORM_CORE_MODULES, productLayout = productLayout, reason = "PLATFORM_CORE_MODULES", context = context))
  explicit.addAll(toModuleItemSequence(list = productLayout.productApiModules, productLayout = productLayout, reason = "productApiModules", context = context))

  val explicitModuleNames = explicit.map { it.moduleName }.toList()

  val productPluginContentModules = processAndGetProductPluginContentModules(
    layout = layout,
    descriptorCache = descriptorCacheContainer.forPlatform(layout),
    includedPlatformModulesPartialList = computePartialListToResolveIncludesAndCollectProductModules(
      layout = layout,
      explicitModuleNames = explicitModuleNames,
      productLayout = productLayout,
      context = context
    ),
    context = context,
  ).toMutableSet()

  // Compute and add dependencies for embedded modules with includeDependencies=true
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
      context = context,
    )
    productPluginContentModules.addAll(embeddedDependencies)
  }

  val implicit = computeImplicitRequiredModules(
    explicit = explicitModuleNames,
    layout = layout,
    productPluginContentModules = productPluginContentModules.mapTo(HashSet()) { it.moduleName },
    productLayout = productLayout,
    context = context,
    validateImplicitPlatformModule = context.options.validateImplicitPlatformModule,
  )

  val filteredExplicit = LinkedHashSet(explicit)
  for (item in productPluginContentModules) {
    val iterator = filteredExplicit.iterator()
    while (iterator.hasNext()) {
      if (item.moduleName == iterator.next().moduleName && !PRODUCT_MODULE_IMPL_COMPOSITION.values.any { it.contains(item.moduleName) }) {
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
         relativeOutputFile = PlatformJarNames.getPlatformModuleJarName(it.first, frontendModuleFilter),
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
  layout.collectProjectLibrariesFromIncludedModules(context) { lib, module ->
    val libName = lib.name
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

private suspend fun computePartialListToResolveIncludesAndCollectProductModules(
  layout: PlatformLayout,
  explicitModuleNames: Collection<String>,
  productLayout: ProductModulesLayout,
  context: BuildContext,
): Collection<String> {
  val result = LinkedHashSet<String>()
  layout.includedModules.mapTo(result) { it.moduleName }
  computeImplicitRequiredModules(
    explicit = explicitModuleNames,
    layout = layout,
    productPluginContentModules = emptySet(),
    productLayout = productLayout,
    context = context,
    validateImplicitPlatformModule = false,
  )
    .mapTo(result) { it.first }
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
fun collectExportedLibrariesFromLibraryModules(
  layout: PlatformLayout,
  context: BuildContext,
): Map<String, String> {
  val javaExtensionService = JpsJavaExtensionService.getInstance()
  val result = mutableMapOf<String, String>()
  val includedModuleNames = layout.includedModules.map { it.moduleName }
  val corePluginsContentModuleNames = computeContentModulesPluginsWhichUseIdeaClassloader(context)

  (includedModuleNames + corePluginsContentModuleNames)
    .asSequence()
    .filter { it.startsWith(LIB_MODULE_PREFIX) }
    .forEach { moduleName ->
      val module = context.findRequiredModule(moduleName)
      // get all library dependencies from the module
      module.dependenciesList.dependencies
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
      val module = context.findRequiredModule(moduleName)
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

private fun toModuleItemSequence(list: Collection<String>, productLayout: ProductModulesLayout, reason: String, context: BuildContext): Sequence<ModuleItem> {
  val frontendModuleFilter = context.getFrontendModuleFilter()
  return list.asSequence()
    .filter { !productLayout.excludedModuleNames.contains(it) }
    .map { ModuleItem(moduleName = it, relativeOutputFile = PlatformJarNames.getPlatformModuleJarName(it, frontendModuleFilter), reason = reason) }
}

/**
 * Computes transitive dependencies for embedded modules that have `includeDependencies=true`.
 * Dependencies are packaged into the same JAR as their parent embedded module.
 *
 * @param embeddedModules embedded modules with includeDependencies=true (already filtered)
 * @param productLayout product modules layout
 * @param context build context
 * @return set of module items representing dependencies to add
 */
private fun computeEmbeddedModuleDependencies(
  embeddedModules: Collection<ModuleItem>,
  productLayout: ProductModulesLayout,
  alreadyIncluded: HashSet<String>,
  context: BuildContext,
): Set<ModuleItem> {
  val result = LinkedHashSet<ModuleItem>()
  val rootChain = persistentListOf<String>()

  // For each embedded module, compute its transitive dependencies
  for (embeddedModule in embeddedModules) {
    val moduleName = embeddedModule.moduleName
    val relativeOutputFile = embeddedModule.relativeOutputFile
    val moduleSet = embeddedModule.moduleSet

    // Prepare list for dependency computation - same pattern as computeImplicitRequiredModules
    val rootList = listOf(moduleName to rootChain)
    val deps = mutableListOf<Pair<String, PersistentList<String>>>()
    computeTransitive(list = rootList, unique = alreadyIncluded, result = deps, context = context)

    // Add dependencies to result, filtering out excluded modules
    for ((depName, chain) in deps) {
      if (productLayout.excludedModuleNames.contains(depName)) {
        continue
      }

      result.add(
        ModuleItem(
          moduleName = depName,
          relativeOutputFile = relativeOutputFile, // Same JAR as parent embedded module
          reason = ModuleIncludeReasons.PRODUCT_EMBEDDED_MODULES + " <- " + chain.asReversed().joinToString(separator = " <- "),
          moduleSet = moduleSet,
        )
      )
    }
  }

  return result
}

private suspend fun computeImplicitRequiredModules(
  explicit: Collection<String>,
  layout: PlatformLayout,
  productPluginContentModules: Set<String>,
  productLayout: ProductModulesLayout,
  context: BuildContext,
  validateImplicitPlatformModule: Boolean,
): List<Pair<String, PersistentList<String>>> {
  val rootChain = persistentListOf<String>()
  val rootList = layout.filteredIncludedModuleNames(excludedRelativeJarPath = TEST_FRAMEWORK_JAR, includeFromSubdirectories = false)
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

  val unique = HashSet<String>()
  layout.includedModules.mapTo(unique) { it.moduleName }
  unique.addAll(explicit)
  unique.addAll(productPluginContentModules)
  unique.addAll(productLayout.excludedModuleNames)
  unique.add("fleet.backend")
  // Module intellij.featuresTrainer contains, so it is a plugin, but plugin must be not included in a platform
  // (chain: [intellij.pycharm.community, intellij.python.featuresTrainer])
  unique.add("intellij.pycharm.community")
  unique.add("intellij.python.featuresTrainer")
  unique.add("intellij.pycharm.ds")
  unique.add("intellij.notebooks.visualization")

  // we should filter out modules which are included in plugins with `use-idea-classloader`
  val pluginsContents = computeContentModulesPluginsWhichUseIdeaClassloader(context)

  val requiredDependencies = mutableListOf<Pair<String, PersistentList<String>>>()
  computeTransitive(list = rootList, unique = unique, result = requiredDependencies, context = context)
  val requiredModules = requiredDependencies.filter { it.first !in pluginsContents }

  if (validateImplicitPlatformModule) {
    withContext(Dispatchers.IO) {
      for ((name, chain) in requiredModules) {
        launch(CoroutineName("validating the implicit platform module $name")) {
          val file = context.findFileInModuleSources(name, "META-INF/plugin.xml")
          check(file == null) {
            "Module $name contains $file, so it is a plugin, but plugin must be not included in a platform (chain: $chain)"
          }
        }
      }
    }
  }

  return requiredModules
}

private fun computeContentModulesPluginsWhichUseIdeaClassloader(context: BuildContext): Set<String> {
  val bundledPlugins = getPluginLayoutsByJpsModuleNames(modules = context.getBundledPluginModules(), productLayout = context.productProperties.productLayout)
  val pluginContents = bundledPlugins.flatMap { getEmbeddedContentModulesOfPluginsWithUseIdeaClassloader(context, it.mainModule, cacheContainer = null) }.toSet()
  return pluginContents
}

private fun computeTransitive(
  list: List<Pair<String, PersistentList<String>>>,
  unique: HashSet<String>,
  result: MutableList<Pair<String, PersistentList<String>>>,
  context: BuildContext,
) {
  val oldSize = result.size
  for ((dependentName, dependentChain) in list) {
    val dependentModule = context.findRequiredModule(dependentName)
    val chain = dependentChain.add(dependentName)
    JpsJavaExtensionService.dependencies(dependentModule).includedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME).processModules { module ->
      val name = module.name
      if (unique.add(name)) {
        result.add(name to chain)
      }
    }
  }

  if (oldSize != result.size) {
    computeTransitive(list = result.subList(oldSize, result.size).sortedBy { it.first }, unique = unique, result = result, context = context)
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

  fun isProductModule(reason: String?): Boolean =
    reason == PRODUCT_MODULES ||
    reason == PRODUCT_EMBEDDED_MODULES ||
    reason?.startsWith("$PRODUCT_EMBEDDED_MODULES <- ") == true
}
