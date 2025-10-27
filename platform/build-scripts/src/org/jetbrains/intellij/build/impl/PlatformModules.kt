// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "RedundantSuppression", "ReplaceGetOrSet", "ReplacePutWithAssignment")
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRule
import io.opentelemetry.api.trace.Span
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jdom.CDATA
import org.jdom.Element
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.ContentModuleFilter
import org.jetbrains.intellij.build.FrontendModuleFilter
import org.jetbrains.intellij.build.PLATFORM_LOADER_JAR
import org.jetbrains.intellij.build.UTIL_8_JAR
import org.jetbrains.intellij.build.UTIL_JAR
import org.jetbrains.intellij.build.UTIL_RT_JAR
import org.jetbrains.intellij.build.impl.PlatformJarNames.PRODUCT_BACKEND_JAR
import org.jetbrains.intellij.build.impl.PlatformJarNames.PRODUCT_JAR
import org.jetbrains.intellij.build.impl.PlatformJarNames.TEST_FRAMEWORK_JAR
import org.jetbrains.intellij.build.productLayout.ProductModulesLayout
import org.jetbrains.intellij.build.productLayout.buildProductContentXml
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModuleReference
import java.nio.file.Files
import java.nio.file.Path
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
private val PLATFORM_CORE_MODULES = java.util.List.of(
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
  "intellij.platform.kernel",

  "intellij.platform.analysis.impl",
  "intellij.platform.diff.impl",
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

internal fun collectPlatformModules(to: MutableCollection<String>) {
  to.addAll(PLATFORM_CORE_MODULES)
}

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
  val layout = PlatformLayout()
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
        relativeOutputFile = getProductModuleJarName(moduleName, context, frontendModuleFilter),
        reason = "productImplementationModules",
      )
    )
  }
  explicit.addAll(toModuleItemSequence(list = PLATFORM_CORE_MODULES, productLayout = productLayout, reason = "PLATFORM_CORE_MODULES", context = context))
  explicit.addAll(toModuleItemSequence(list = productLayout.productApiModules, productLayout = productLayout, reason = "productApiModules", context = context))

  val explicitModuleNames = explicit.map { it.moduleName }.toList()

  val productPluginContentModules = processAndGetProductPluginContentModules(
    context = context,
    layout = layout,
    includedPlatformModulesPartialList = layout.includedModules.asSequence().map { it.moduleName } +
                                         computeImplicitRequiredModules(
                                           explicit = explicitModuleNames,
                                           layout = layout,
                                           productPluginContentModules = emptySet(),
                                           productLayout = productLayout,
                                           context = context,
                                           validateImplicitPlatformModule = false,
                                         ).asSequence().map { it.first } +
                                         explicitModuleNames

  )

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
  layout.collectProjectLibrariesFromIncludedModules(context = context) { lib, module ->
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

  layout.includedModules
    .asSequence()
    .filter { it.moduleName.startsWith(LIB_MODULE_PREFIX) }
    .forEach { moduleItem ->
      val module = context.findRequiredModule(moduleItem.moduleName)
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
          result.put(libName, moduleItem.moduleName)
        }
    }

  return result
}

private fun getProductModuleJarName(moduleName: String, context: BuildContext, frontendModuleFilter: FrontendModuleFilter): String {
  return when {
    isModuleCloseSource(moduleName = moduleName, context = context) -> if (frontendModuleFilter.isBackendModule(moduleName)) PRODUCT_BACKEND_JAR else PRODUCT_JAR
    else -> PlatformJarNames.getPlatformModuleJarName(moduleName, frontendModuleFilter)
  }
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
        result.addOrGet(ProjectLibraryData(libraryName = libName, packMode = packMode, reason = "<- $moduleName"))
          .dependentModules
          .computeIfAbsent(plugin.directoryName) { mutableListOf() }
          .add(moduleName)
      }
    }
  }
  return result
}

suspend fun getEnabledPluginModules(pluginsToPublish: Set<PluginLayout>, context: BuildContext): Set<String> {
  val result = LinkedHashSet<String>()
  result.addAll(context.getBundledPluginModules())
  pluginsToPublish.mapTo(result) { it.mainModule }
  return result
}

private fun isModuleCloseSource(moduleName: String, context: BuildContext): Boolean {
  if (moduleName.endsWith(".resources") || moduleName.endsWith(".icons") || moduleName.startsWith(LIB_MODULE_PREFIX)) {
    return false
  }

  val sourceRoots = context.findRequiredModule(moduleName).sourceRoots.filter { it.rootType == JavaSourceRootType.SOURCE }
  if (sourceRoots.isEmpty()) {
    return false
  }

  return sourceRoots.any {
    !it.path.startsWith(context.paths.communityHomeDir)
  }
}

private suspend fun toModuleItemSequence(list: Collection<String>, productLayout: ProductModulesLayout, reason: String, context: BuildContext): Sequence<ModuleItem> {
  val frontendModuleFilter = context.getFrontendModuleFilter()
  return list.asSequence()
    .filter { !productLayout.excludedModuleNames.contains(it) }
    .map { ModuleItem(moduleName = it, relativeOutputFile = PlatformJarNames.getPlatformModuleJarName(it, frontendModuleFilter), reason = reason) }
}

private suspend fun computeImplicitRequiredModules(
  explicit: List<String>,
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

  val result = mutableListOf<Pair<String, PersistentList<String>>>()
  computeTransitive(list = rootList, context = context, unique = unique, result = result)

  if (validateImplicitPlatformModule) {
    withContext(Dispatchers.IO) {
      for ((name, chain) in result) {
        launch(CoroutineName("validating the implicit platform module $name")) {
          val file = context.findFileInModuleSources(name, "META-INF/plugin.xml")
          check(file == null) {
            "Module $name contains $file, so it is a plugin, but plugin must be not included in a platform (chain: $chain)"
          }
        }
      }
    }
  }

  return result
}

private fun computeTransitive(
  list: List<Pair<String, PersistentList<String>>>,
  context: BuildContext,
  unique: HashSet<String>,
  result: MutableList<Pair<String, PersistentList<String>>>,
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
    computeTransitive(list = result.subList(oldSize, result.size).sortedBy { it.first }, context = context, unique = unique, result = result)
  }
}

private val regenerateProductSpec = System.getProperty("intellij.build.regenerate.product.spec", "true").toBoolean()

// result _must be_ consistent, do not use Set.of or HashSet here
private suspend fun processAndGetProductPluginContentModules(
  context: BuildContext,
  layout: PlatformLayout,
  includedPlatformModulesPartialList: Sequence<String>,
): Set<ModuleItem> {
  val xIncludePathResolver = createXIncludePathResolver(includedPlatformModulesPartialList.distinct().toList(), context)
  return withContext(Dispatchers.IO) {
    val productPluginSourceModuleName = context.productProperties.applicationInfoModule
    val file = requireNotNull(
      context.findFileInModuleSources(productPluginSourceModuleName, "META-INF/plugin.xml")
      ?: context.findFileInModuleSources(moduleName = productPluginSourceModuleName, relativePath = "META-INF/${context.productProperties.platformPrefix}Plugin.xml")
    ) { "Cannot find product plugin descriptor in '$productPluginSourceModuleName' module" }

    val originalContent: String
    val xml: Element
    val moduleToSetChainMapping: Map<String, List<String>>?
    // process programmatic content modules if defined
    val programmaticModulesSpec = context.productProperties.getProductContentDescriptor()
    if (programmaticModulesSpec == null || !regenerateProductSpec) {
      originalContent = Files.readString(file)
      xml = JDOMUtil.load(originalContent)
      resolveNonXIncludeElement(original = xml, base = file, pathResolver = xIncludePathResolver, trackSourceFile = true)
      moduleToSetChainMapping = null
    }
    else {
      val sb = StringBuilder()
      val result = buildProductContentXml(
        spec = programmaticModulesSpec,
        moduleOutputProvider = context,
        sb = sb,
        inlineXmlIncludes = true,
      )
      Span.current().addEvent("Generated ${result.contentBlocks.size} content blocks with ${result.contentBlocks.sumOf { it.modules.size }} total modules")

      originalContent = sb.toString()
      xml = JDOMUtil.load(sb)
      resolveNonXIncludeElement(original = xml, base = file, pathResolver = xIncludePathResolver, trackSourceFile = false)
      moduleToSetChainMapping = result.moduleToSetChainMapping
    }

    val moduleItems = collectAndEmbedProductModules(
      root = xml,
      xIncludePathResolver = xIncludePathResolver,
      context = context,
      moduleToSetChainOverride = moduleToSetChainMapping
    )
    val data = JDOMUtil.write(xml)
    if (data != originalContent) {
      layout.withPatch { moduleOutputPatcher, _, _ ->
        moduleOutputPatcher.patchModuleOutput(moduleName = productPluginSourceModuleName, path = "META-INF/${file.fileName}", content = data)
      }
    }
    layout.cachedDescriptorContainer.productDescriptor = xml

    moduleItems
  }
}

// todo implement correct processing
@Suppress("RemoveRedundantQualifierName")
private val excludedPaths = java.util.Set.of(
  "/META-INF/cwmBackendConnection.xml",
  "/META-INF/cwmConnectionFrontend.xml",
  "/META-INF/clientUltimate.xml",
  "/META-INF/backendUltimate.xml",
  "/META-INF/controllerBackendUltimate.xml"
)

private val COMMUNITY_IMPL_EXTENSIONS = setOf(
  "/META-INF/community-extensions.xml"
)

fun createXIncludePathResolver(
  includedPlatformModulesPartialList: List<String>,
  context: BuildContext,
): XIncludePathResolver {
  return object : XIncludePathResolver {
    override fun resolvePath(relativePath: String, base: Path?, isOptional: Boolean, isDynamic: Boolean): Path? {
      if ((isOptional || isDynamic || excludedPaths.contains(relativePath)) && !COMMUNITY_IMPL_EXTENSIONS.contains(relativePath)) {
        // It isn't safe to resolve includes at build time if they're optional.
        // This could lead to issues when running another product using this distribution.
        // E.g., if the corresponding module is somehow being excluded on runtime.
        return null
      }

      val loadPath = toLoadPath(relativePath)

      // Resolve module set files directly from generated directories
      if (loadPath.startsWith("META-INF/intellij.moduleSets.")) {
        for (provider in context.productProperties.moduleSetsProviders) {
          val file = provider.getOutputDirectory(context.paths).resolve(loadPath)
          if (Files.exists(file)) {
            return file
          }
        }
      }

      if (base != null) {
        val parent = base.parent
        val file = if (parent.endsWith("META-INF") && loadPath.startsWith("META-INF/")) {
          parent.parent.resolve(loadPath)
        }
        else {
          parent.resolve(loadPath)
        }
        file.takeIf { Files.exists(it) }?.let {
          return it
        }
      }

      for (module in includedPlatformModulesPartialList) {
        findFileInModuleSources(context.findRequiredModule(module), loadPath)?.let {
          return it
        }
      }
      return null
    }
  }
}

suspend fun embedContentModules(file: Path, xIncludePathResolver: XIncludePathResolver, xml: Element, layout: PluginLayout?, context: BuildContext) {
  val frontendModuleFilter = context.getFrontendModuleFilter()
  val contentModuleFilter = context.getContentModuleFilter()
  resolveNonXIncludeElement(original = xml, base = file, pathResolver = xIncludePathResolver, trackSourceFile = false)

  val moduleElements = xml.getChildren("content").flatMap { it.getChildren("module") }
  for (moduleElement in moduleElements) {
    val moduleName = moduleElement.getAttributeValue("name") ?: continue
    check(moduleElement.content.isEmpty())

    val jpsModuleName = moduleName.substringBeforeLast('/')
    val loadingRule = moduleElement.getAttributeValue("loading")
    val dependencyHelper = (context as BuildContextImpl).jarPackagerDependencyHelper
    if (dependencyHelper.isOptionalLoadingRule(loadingRule) && !contentModuleFilter.isOptionalModuleIncluded(jpsModuleName, pluginMainModuleName = layout?.mainModule)) {
      Span.current().addEvent("Tag for module '$moduleName' is removed from plugin.xml file for '${layout?.mainModule}' by $contentModuleFilter")
      moduleElement.parent.removeContent(moduleElement)
      continue
    }

    val descriptor = getModuleDescriptor(moduleName = moduleName, jpsModuleName = jpsModuleName, xIncludePathResolver = xIncludePathResolver, context = context)
    if (jpsModuleName == moduleName &&
        dependencyHelper.isPluginModulePackedIntoSeparateJar(context.findRequiredModule(jpsModuleName.removeSuffix("._test")), layout, frontendModuleFilter)) {
      descriptor.setAttribute("separate-jar", "true")
    }
    moduleElement.setContent(CDATA(JDOMUtil.write(descriptor)))
  }
}

// see isV2ModulePath
internal fun toLoadPath(relativePath: String): String {
  @Suppress("SpellCheckingInspection")
  return when {
    relativePath[0] == '/' -> relativePath.substring(1)
    relativePath.startsWith("intellij.") || relativePath.startsWith("fleet.") -> relativePath
    else -> "META-INF/$relativePath"
  }
}

private fun getModuleDescriptor(moduleName: String, jpsModuleName: String, xIncludePathResolver: XIncludePathResolver, context: BuildContext): Element {
  val descriptorFile = "${moduleName.replace('/', '.')}.xml"
  val file = requireNotNull(findFileInModuleSources(module = context.findRequiredModule(jpsModuleName), relativePath = descriptorFile)) {
    "Cannot find file $descriptorFile in module $jpsModuleName"
  }
  val xml = JDOMUtil.load(file)
  resolveNonXIncludeElement(original = xml, base = file, pathResolver = xIncludePathResolver)
  return xml
}

private suspend fun collectAndEmbedProductModules(
  root: Element,
  xIncludePathResolver: XIncludePathResolver,
  context: BuildContext,
  moduleToSetChainOverride: Map<String, List<String>>? = null
): Set<ModuleItem> {
  val frontendModuleFilter = context.getFrontendModuleFilter()
  val contentModuleFilter = context.getContentModuleFilter()
  val moduleItems = LinkedHashSet<ModuleItem>()
  for (content in root.getChildren("content")) {
    val iterator = content.getChildren("module").iterator()
    while (iterator.hasNext()) {
      processProductModule(
        iterator = iterator,
        context = context,
        contentModuleFilter = contentModuleFilter,
        frontendModuleFilter = frontendModuleFilter,
        result = moduleItems,
        xIncludePathResolver = xIncludePathResolver,
        moduleToSetChainOverride = moduleToSetChainOverride,
      )
    }
  }
  return moduleItems
}

private fun processProductModule(
  iterator: MutableIterator<Element>,
  context: BuildContext,
  contentModuleFilter: ContentModuleFilter,
  frontendModuleFilter: FrontendModuleFilter,
  result: LinkedHashSet<ModuleItem>,
  xIncludePathResolver: XIncludePathResolver,
  moduleToSetChainOverride: Map<String, List<String>>? = null,
) {
  val moduleElement = iterator.next()
  val moduleName = moduleElement.getAttributeValue("name") ?: return
  val loadingRule = moduleElement.getAttributeValue("loading")
  val dependencyHelper = (context as BuildContextImpl).jarPackagerDependencyHelper
  if (dependencyHelper.isOptionalLoadingRule(loadingRule) && !contentModuleFilter.isOptionalModuleIncluded(moduleName = moduleName, pluginMainModuleName = null)) {
    Span.current().addEvent("Tag for module '$moduleName' is removed from the core plugin by $contentModuleFilter")
    iterator.remove()
    return
  }

  val isEmbedded = loadingRule == ModuleLoadingRule.EMBEDDED.name.lowercase()
  val isInScrambledFile = isEmbedded && isModuleCloseSource(moduleName = moduleName, context = context)
  val relativeOutFile = if (isInScrambledFile) {
    if (frontendModuleFilter.isBackendModule(moduleName)) PRODUCT_BACKEND_JAR else PRODUCT_JAR
  }
  else {
    "$moduleName.jar"
  }

  // extract module set from override mapping (for programmatic spec)
  val moduleSet = moduleToSetChainOverride?.get(moduleName)
  result.add(
    ModuleItem(
      moduleName = moduleName,
      relativeOutputFile = relativeOutFile,
      reason = if (isEmbedded) ModuleIncludeReasons.PRODUCT_EMBEDDED_MODULES else ModuleIncludeReasons.PRODUCT_MODULES,
      moduleSet = moduleSet,
    )
  )
  PRODUCT_MODULE_IMPL_COMPOSITION.get(moduleName)?.let { list ->
    list
      .filter { !context.productProperties.productLayout.productImplementationModules.contains(it) }
      .mapTo(result) { subModuleName ->
        ModuleItem(moduleName = subModuleName, relativeOutputFile = relativeOutFile, reason = ModuleIncludeReasons.PRODUCT_MODULES, moduleSet = moduleSet)
      }
  }

  // We do not embed the module descriptor because scrambling can rename classes.
  //
  // However, we cannot rely solely on the `PLUGIN_CLASSPATH` descriptor: for non-embedded modules,
  // xi:included files (e.g., META-INF/VcsExtensionPoints.xml) are not resolvable from the core classpath,
  // since a non-embedded module uses a separate classloader.
  //
  // Because scrambling applies only (by policy) to embedded modules, we embed the module descriptor
  // for non-embedded modules to address this.
  //
  // Note: We could implement runtime loading via the module’s classloader, but that would
  // significantly complicate the runtime code.
  if (!isInScrambledFile) {
    check(moduleElement.content.isEmpty())
    val moduleDescriptor = getModuleDescriptor(moduleName = moduleName, jpsModuleName = moduleName, xIncludePathResolver = xIncludePathResolver, context = context)
    moduleElement.setContent(CDATA(JDOMUtil.write(moduleDescriptor)))
  }
  // For Gateway or a module-based loader, where PLUGIN_CLASSPATH isn’t used, performance will be slightly affected
  // (most product modules shouldn’t be embedded anyway).
  // That’s acceptable because remote development will be migrated to the path-based class loader anyway.
  // We prefer not to increase code complexity without a strong reason.
}

// Contrary to what it looks like, this is not a step back.
// Previously, it was specified in PLATFORM_IMPLEMENTATION_MODULES/PLATFORM_API_MODULES.
// Once the shape of the extracted module becomes fully discernible,
// we can consider ways to improve `pluginAuto` and eliminate the need for an explicit declaration here.
@Suppress("RemoveRedundantQualifierName")
private val PRODUCT_MODULE_IMPL_COMPOSITION = java.util.Map.of(
  "intellij.rider", listOf(
  "intellij.platform.debugger.modulesView"
),
  "intellij.platform.rpc.backend", listOf(
  "fleet.rpc.server",
)
)

internal object ModuleIncludeReasons {
  const val PRODUCT_MODULES: String = "productModule"
  const val PRODUCT_EMBEDDED_MODULES: String = "productEmbeddedModule"

  fun isProductModule(reason: String?): Boolean = reason == PRODUCT_MODULES || reason == PRODUCT_EMBEDDED_MODULES
}
