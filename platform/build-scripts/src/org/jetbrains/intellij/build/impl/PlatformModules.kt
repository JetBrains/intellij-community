// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.JDOMUtil
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jdom.CDATA
import org.jdom.Element
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.impl.PlatformJarNames.APP_JAR
import org.jetbrains.intellij.build.impl.PlatformJarNames.PRODUCT_CLIENT_JAR
import org.jetbrains.intellij.build.impl.PlatformJarNames.PRODUCT_JAR
import org.jetbrains.intellij.build.impl.PlatformJarNames.TEST_FRAMEWORK_JAR
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModuleDependency
import org.jetbrains.jps.model.module.JpsModuleReference
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

private val PLATFORM_API_MODULES = java.util.List.of(
  "intellij.platform.analysis",
  "intellij.platform.builtInServer",
  "intellij.platform.diff",
  "intellij.platform.editor",
  "intellij.platform.externalSystem",
  "intellij.platform.externalSystem.dependencyUpdater",
  "intellij.platform.codeStyle",
  "intellij.platform.lang.core",
  "intellij.platform.ml",
  "intellij.platform.remote.core",
  "intellij.platform.remoteServers.agent.rt",
  "intellij.platform.remoteServers",
  "intellij.platform.usageView",
  "intellij.platform.execution",
  "intellij.xml",
  "intellij.xml.psi",
  "intellij.xml.structureView",
)

/**
 * List of modules which are included in lib/app.jar in all IntelliJ based IDEs.
 */
private val PLATFORM_IMPLEMENTATION_MODULES = java.util.List.of(
  "intellij.platform.analysis.impl",
  "intellij.platform.diff.impl",
  "intellij.platform.editor.ex",
  "intellij.platform.externalProcessAuthHelper",
  "intellij.platform.inspect",
  "intellij.platform.lvcs",
  "intellij.platform.macro",
  "intellij.platform.scriptDebugger.protocolReaderRuntime",
  "intellij.regexp",
  "intellij.platform.remoteServers.impl",
  "intellij.platform.scriptDebugger.backend",
  "intellij.platform.scriptDebugger.ui",
  "intellij.platform.smRunner",
  "intellij.platform.structureView.impl",
  "intellij.platform.tasks.impl",
  "intellij.platform.testRunner",
  "intellij.platform.dependenciesToolwindow",
  "intellij.platform.rd.community",
  "intellij.remoteDev.util",
  "intellij.platform.feedback",
  "intellij.platform.warmup",
  "intellij.idea.community.build.dependencies",
  "intellij.platform.usageView.impl",
  "intellij.platform.ml.impl",

  "intellij.platform.runtime.product",
  "intellij.platform.bootstrap",

  "intellij.relaxng",
  "intellij.json",
  "intellij.spellchecker",
  "intellij.platform.webSymbols",
  "intellij.xml.dom.impl",

  "intellij.platform.vcs.log",

  "intellij.platform.markdown.utils",
  "intellij.platform.util.commonsLangV2Shim",

  // do we need it?
  "intellij.platform.sqlite",
)

internal val PLATFORM_CUSTOM_PACK_MODE: Map<String, LibraryPackMode> = java.util.Map.of(
  "jetbrains-annotations", LibraryPackMode.STANDALONE_SEPARATE_WITHOUT_VERSION_NAME,
  "intellij-coverage", LibraryPackMode.STANDALONE_SEPARATE,
)

internal fun collectPlatformModules(to: MutableCollection<String>) {
  to.addAll(PLATFORM_API_MODULES)
  to.addAll(PLATFORM_IMPLEMENTATION_MODULES)
}

internal fun hasPlatformCoverage(productLayout: ProductModulesLayout, enabledPluginModules: Set<String>, context: BuildContext): Boolean {
  val modules = HashSet<String>()
  collectIncludedPluginModules(enabledPluginModules = enabledPluginModules, product = productLayout, result = modules)
  modules.addAll(PLATFORM_API_MODULES)
  modules.addAll(PLATFORM_IMPLEMENTATION_MODULES)
  modules.addAll(productLayout.productApiModules)
  modules.addAll(productLayout.productImplementationModules)

  val coverageModuleName = "intellij.platform.coverage"
  if (modules.contains(coverageModuleName)) {
    return true
  }

  val javaExtensionService = JpsJavaExtensionService.getInstance()
  for (moduleName in modules) {
    for (element in context.findRequiredModule(moduleName).dependenciesList.dependencies) {
      if (element !is JpsModuleDependency ||
          javaExtensionService.getDependencyExtension(element)?.scope?.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME) != true) {
        continue
      }

      if (element.moduleReference.moduleName == coverageModuleName) {
        return true
      }
    }
  }

  return false
}

private fun addModule(relativeJarPath: String, moduleNames: Collection<String>, productLayout: ProductModulesLayout, layout: PlatformLayout) {
  layout.withModules(moduleNames.asSequence()
                       .filter { !productLayout.excludedModuleNames.contains(it) }
                       .map { ModuleItem(moduleName = it, relativeOutputFile = relativeJarPath, reason = "addModule") }
                       .toList())
}

@Suppress("RedundantSuspendModifier")
suspend fun createPlatformLayout(pluginsToPublish: Set<PluginLayout>, context: BuildContext): PlatformLayout {
  val enabledPluginModules = getEnabledPluginModules(pluginsToPublish = pluginsToPublish, context = context)
  val productLayout = context.productProperties.productLayout
  return createPlatformLayout(
    addPlatformCoverage = !productLayout.excludedModuleNames.contains("intellij.platform.coverage") &&
                          hasPlatformCoverage(productLayout = productLayout, enabledPluginModules = enabledPluginModules, context = context),
    projectLibrariesUsedByPlugins = computeProjectLibsUsedByPlugins(enabledPluginModules = enabledPluginModules, context = context),
    context = context,
  )
}

internal suspend fun createPlatformLayout(addPlatformCoverage: Boolean, projectLibrariesUsedByPlugins: SortedSet<ProjectLibraryData>, context: BuildContext): PlatformLayout {
  val jetBrainsClientModuleFilter = context.jetBrainsClientModuleFilter
  val productLayout = context.productProperties.productLayout
  val layout = PlatformLayout()
  // used only in modules that packed into Java
  layout.withoutProjectLibrary("jps-javac-extension")
  layout.withoutProjectLibrary("Eclipse")

  // this library is used in some modules compatible with Java 7, it's replaced by its superset 'jetbrains-annotations' in the distribution
  layout.withoutProjectLibrary("jetbrains-annotations-java5")

  for (customizer in productLayout.platformLayoutSpec) {
    customizer(layout, context)
  }
  for ((module, patterns) in productLayout.moduleExcludes) {
    layout.excludeFromModule(module, patterns)
  }

  addModule(UTIL_RT_JAR, listOf(
    "intellij.platform.util.rt",
  ), productLayout = productLayout, layout = layout)
  // trove is not used by JB Client - fix RuntimeModuleRepositoryChecker assert
  addModule("trove.jar", listOf(
    "intellij.platform.util.trove",
  ), productLayout = productLayout, layout = layout)
  layout.withProjectLibrary(libraryName = "ion", jarName = UTIL_8_JAR)

  // maven uses JDOM in an external process
  addModule(UTIL_8_JAR, listOf(
    "intellij.platform.util.jdom",
    "intellij.platform.util.xmlDom",
    "intellij.platform.tracing.rt",
    "intellij.platform.util.base",
    "intellij.platform.diagnostic",
    "intellij.platform.util",
    "intellij.platform.core",
    // it has package `kotlin.coroutines.jvm.internal` - should be packed into the same JAR as coroutine lib,
    // to ensure that package index will not report one more JAR in a search path
    "intellij.platform.bootstrap.coroutine",
  ), productLayout = productLayout, layout = layout)
  // used by jdom - pack to the same JAR
  layout.withProjectLibrary(libraryName = "aalto-xml", jarName = UTIL_8_JAR)
  // Space plugin uses it and bundles into IntelliJ IDEA, but not bundles into DataGrip, so, or Space plugin should bundle this lib,
  // or IJ Platform. As it is a small library and consistency is important across other coroutine libs, bundle to IJ Platform.
  layout.withProjectLibrary(libraryName = "kotlinx-coroutines-slf4j", jarName = APP_JAR)

  // https://jetbrains.team/p/ij/reviews/67104/timeline
  // https://youtrack.jetbrains.com/issue/IDEA-179784
  // https://youtrack.jetbrains.com/issue/IDEA-205600
  layout.withProjectLibraries(listOf(
    "javax.annotation-api",
    "javax.activation",
    "jaxb-runtime",
    "jaxb-api",
  ))

  // used by intellij.database.jdbcConsole - put to a small util module
  layout.withProjectLibrary(libraryName = "jbr-api", jarName = UTIL_JAR)
  // platform-loader.jar is loaded by JVM classloader as part of loading our custom PathClassLoader class - reduce file size
  addModule(PLATFORM_LOADER_JAR, listOf(
    "intellij.platform.util.rt.java8",
    "intellij.platform.util.classLoader",
    "intellij.platform.util.zip",
    "intellij.platform.boot",
    "intellij.platform.runtime.repository",
    "intellij.platform.runtime.loader",
  ), productLayout = productLayout, layout = layout)
  addModule(UTIL_JAR, listOf(
    // Scala uses GeneralCommandLine in JPS plugin
    "intellij.platform.ide.util.io",
    "intellij.platform.extensions",
    "intellij.platform.util.nanoxml",
  ), productLayout = productLayout, layout = layout)
  addModule("externalProcess-rt.jar", listOf(
    "intellij.platform.externalProcessAuthHelper.rt"
  ), productLayout = productLayout, layout = layout)
  addModule("stats.jar", listOf(
    "intellij.platform.statistics",
    "intellij.platform.statistics.uploader",
    "intellij.platform.statistics.config",
  ), productLayout = productLayout, layout = layout)
  if (!productLayout.excludedModuleNames.contains("intellij.java.guiForms.rt")) {
    layout.withModule("intellij.java.guiForms.rt", "forms_rt.jar")
  }
  addModule("jps-model.jar", listOf(
    "intellij.platform.jps.model",
    "intellij.platform.jps.model.serialization",
    "intellij.platform.jps.model.impl"
  ), productLayout = productLayout, layout = layout)
  addModule("external-system-rt.jar", listOf(
    "intellij.platform.externalSystem.rt",
    "intellij.platform.objectSerializer.annotations"
  ), productLayout = productLayout, layout = layout)
  addModule("cds/classesLogAgent.jar", listOf("intellij.platform.cdsAgent"), productLayout = productLayout, layout = layout)
  val explicit = mutableListOf<ModuleItem>()
  for (moduleName in productLayout.productImplementationModules) {
    if (productLayout.excludedModuleNames.contains(moduleName)) {
      continue
    }

    explicit.add(
      ModuleItem(
        moduleName = moduleName,
        relativeOutputFile = when {
          isModuleCloseSource(moduleName, context = context) -> if (jetBrainsClientModuleFilter.isModuleIncluded(moduleName)) PRODUCT_CLIENT_JAR else PRODUCT_JAR
          else -> PlatformJarNames.getPlatformModuleJarName(moduleName, context)
        },
        reason = "productImplementationModules",
      )
    )
  }
  explicit.addAll(toModuleItemSequence(list = PLATFORM_API_MODULES, productLayout = productLayout, reason = "PLATFORM_API_MODULES", context = context))
  explicit.addAll(toModuleItemSequence(list = PLATFORM_IMPLEMENTATION_MODULES, productLayout = productLayout, reason = "PLATFORM_IMPLEMENTATION_MODULES", context = context))
  explicit.addAll(toModuleItemSequence(list = productLayout.productApiModules, productLayout = productLayout, reason = "productApiModules", context = context))
  if (addPlatformCoverage) {
    explicit.add(ModuleItem(moduleName = "intellij.platform.coverage", relativeOutputFile = APP_JAR, reason = "coverage"))
  }

  val explicitModuleNames = explicit.map { it.moduleName }.toList()

  val productPluginContentModules = processAndGetProductPluginContentModules(
    context = context,
    layout = layout,
    includedPlatformModulesPartialList = (layout.includedModules.asSequence().map { it.moduleName } + computeImplicitRequiredModules(
      explicit = explicitModuleNames,
      layout = layout,
      productPluginContentModules = emptySet(),
      productLayout = productLayout,
      context = context,
      validateImplicitPlatformModule = false,
    ).asSequence().map { it.first } + explicitModuleNames).toList(),
    productPluginSourceModuleName = context.productProperties.productPluginSourceModuleName ?: context.productProperties.applicationInfoModule,
  )

  val implicit = computeImplicitRequiredModules(
    explicit = explicitModuleNames,
    layout = layout,
    productPluginContentModules = productPluginContentModules.mapTo(HashSet()) { it.moduleName },
    productLayout = productLayout,
    context = context,
    validateImplicitPlatformModule = context.options.validateImplicitPlatformModule,
  )
  layout.withModules(
    (explicit.asSequence() +
     productPluginContentModules +
     implicit.asSequence().map {
       ModuleItem(
         moduleName = it.first,
         relativeOutputFile = PlatformJarNames.getPlatformModuleJarName(it.first, context),
         reason = "<- " + it.second.asReversed().joinToString(separator = " <- ")
       )
     })
      .sortedBy { it.moduleName }
      .toList(),
  )
  for (item in projectLibrariesUsedByPlugins) {
    if (!layout.excludedProjectLibraries.contains(item.libraryName)) {
      layout.includedProjectLibraries.add(item)
    }
  }
  // as a separate step, not a part of computing implicitModules, as we should collect libraries from a such implicitly included modules
  layout.collectProjectLibrariesFromIncludedModules(context = context) { lib, module ->
    val name = lib.name
    // this module is used only when running IDE from sources, no need to include its dependencies, see IJPL-125
    if (module.name == "intellij.platform.buildScripts.downloader" && (name == "zstd-jni")) {
      return@collectProjectLibrariesFromIncludedModules
    }

    layout.includedProjectLibraries
      .addOrGet(ProjectLibraryData(libraryName = name,
                                   packMode = PLATFORM_CUSTOM_PACK_MODE.getOrDefault(name, LibraryPackMode.MERGED),
                                   reason = "<- ${module.name}"))
      .dependentModules.computeIfAbsent("core") { mutableListOf() }.add(module.name)
  }

  val platformMainModule = "intellij.platform.main"
  if (context.isEmbeddedJetBrainsClientEnabled && layout.includedModules.none { it.moduleName == platformMainModule }) {
    /* this module is used by JetBrains Client, but it isn't packed in commercial IDEs, so let's put it in a separate JAR which won't be
       loaded when the IDE is started in the regular mode */
    layout.withModule(platformMainModule, "ext/platform-main.jar")
  }

  return layout
}

// sqlite - used by DB and "import settings" (temporarily)
fun isLibraryAlwaysPackedIntoPlugin(name: String): Boolean = name == "flexmark" || name == "okhttp" || name == "sqlite"

internal fun computeProjectLibsUsedByPlugins(enabledPluginModules: Set<String>, context: BuildContext): SortedSet<ProjectLibraryData> {
  val result = ObjectLinkedOpenHashSet<ProjectLibraryData>()
  val pluginLayoutsByJpsModuleNames = getPluginLayoutsByJpsModuleNames(
    modules = enabledPluginModules,
    productLayout = context.productProperties.productLayout,
  )

  val helper = (context as BuildContextImpl).jarPackagerDependencyHelper
  for (plugin in pluginLayoutsByJpsModuleNames) {
    if (plugin.auto) {
      continue
    }

    for (moduleName in plugin.includedModules.asSequence().map { it.moduleName }.distinct()) {
      val module = context.findRequiredModule(moduleName)
      for (element in helper.getLibraryDependencies(module)) {
        val libRef = element.libraryReference
        if (libRef.parentReference is JpsModuleReference) {
          continue
        }

        val libName = libRef.libraryName
        if (plugin.hasLibrary(libName) || isLibraryAlwaysPackedIntoPlugin(libName)) {
          continue
        }

        val packMode = PLATFORM_CUSTOM_PACK_MODE.getOrDefault(libName, LibraryPackMode.MERGED)
        result.addOrGet(ProjectLibraryData(libName, packMode, reason = "<- $moduleName"))
          .dependentModules
          .computeIfAbsent(plugin.directoryName) { mutableListOf() }
          .add(moduleName)
      }
    }
  }
  return result
}

internal fun getEnabledPluginModules(pluginsToPublish: Set<PluginLayout>, context: BuildContext): Set<String> {
  val result = LinkedHashSet<String>()
  result.addAll(context.bundledPluginModules)
  pluginsToPublish.mapTo(result) { it.mainModule }
  return result
}

private fun isModuleCloseSource(moduleName: String, context: BuildContext): Boolean {
  if (moduleName.endsWith(".resources") || moduleName.endsWith(".icons")) {
    return false
  }

  val sourceRoots = context.findRequiredModule(moduleName).sourceRoots.filter { it.rootType == JavaSourceRootType.SOURCE }
  if (sourceRoots.isEmpty()) {
    return false
  }

  return sourceRoots.any { moduleSourceRoot ->
    !moduleSourceRoot.path.startsWith(context.paths.communityHomeDir)
  }
}

private fun toModuleItemSequence(list: Collection<String>, productLayout: ProductModulesLayout, reason: String, context: BuildContext): Sequence<ModuleItem> {
  return list.asSequence()
    .filter { !productLayout.excludedModuleNames.contains(it) }
    .map { ModuleItem(moduleName = it, relativeOutputFile = PlatformJarNames.getPlatformModuleJarName(it, context), reason = reason) }
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
  val rootList = layout.filteredIncludedModuleNames(TEST_FRAMEWORK_JAR, includeFromSubdirectories = false)
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

  val result = mutableListOf<Pair<String, PersistentList<String>>>()
  computeTransitive(list = rootList, context = context, unique = unique, result = result)

  if (validateImplicitPlatformModule) {
    withContext(Dispatchers.IO) {
      for ((name, chain) in result) {
        launch {
          val file = context.findFileInModuleSources(name, "META-INF/plugin.xml")
          require(file == null) {
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

// result _must be_ consistent, do not use Set.of or HashSet here
private suspend fun processAndGetProductPluginContentModules(
  context: BuildContext,
  productPluginSourceModuleName: String,
  layout: PlatformLayout,
  includedPlatformModulesPartialList: List<String>,
): Set<ModuleItem> {
  val xIncludePathResolver = createXIncludePathResolver(includedPlatformModulesPartialList, context)
  return withContext(Dispatchers.IO) {
    val file = requireNotNull(
      context.findFileInModuleSources(productPluginSourceModuleName, "META-INF/plugin.xml")
      ?: context.findFileInModuleSources(moduleName = productPluginSourceModuleName, relativePath = "META-INF/${context.productProperties.platformPrefix}Plugin.xml")
    ) { "Cannot find product plugin descriptor in '$productPluginSourceModuleName' module" }

    processProductXmlDescriptor(file = file, moduleName = productPluginSourceModuleName, withPatch = layout::withPatch, xIncludePathResolver = xIncludePathResolver, context = context)
  }
}

fun createXIncludePathResolver(includedPlatformModulesPartialList: List<String>, context: BuildContext): XIncludePathResolver {
  return object : XIncludePathResolver {
    override fun resolvePath(relativePath: String, base: Path?, isOptional: Boolean): Path? {
      if (isOptional) {
        // It isn't safe to resolve includes at build time if they're optional.
        // This could lead to issues when running another product using this distribution.
        // E.g., if the corresponding module is somehow being excluded on runtime.
        return null
      }

      val loadPath = toLoadPath(relativePath)
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
        context.findFileInModuleSources(module, loadPath)?.let {
          return it
        }
      }
      return null
    }
  }
}

suspend fun processProductXmlDescriptor(
  file: Path,
  moduleName: String,
  withPatch: suspend(patcher: suspend (ModuleOutputPatcher, PlatformLayout, BuildContext) -> Unit) -> Unit,
  xIncludePathResolver: XIncludePathResolver,
  context: BuildContext,
): Set<ModuleItem> {
  val xml = JDOMUtil.load(file)
  resolveNonXIncludeElement(original = xml, base = file, pathResolver = xIncludePathResolver)
  val result = collectAndEmbedProductModules(root = xml, xIncludePathResolver = xIncludePathResolver, context = context)
  val data = JDOMUtil.write(xml)
  val fileName = file.fileName.toString()
  withPatch { moduleOutputPatcher, _, _ ->
    moduleOutputPatcher.patchModuleOutput(moduleName, "META-INF/$fileName", data)
  }
  return result
}

// see PluginXmlPathResolver.toLoadPath
private fun toLoadPath(relativePath: String): String {
  return when {
    relativePath[0] == '/' -> relativePath.substring(1)
    relativePath.startsWith("intellij.") -> relativePath
    else -> "META-INF/$relativePath"
  }
}

private fun getModuleDescriptor(moduleName: String, xIncludePathResolver: XIncludePathResolver, context: BuildContext): CDATA {
  val descriptorFile = "$moduleName.xml"
  val file = requireNotNull(context.findFileInModuleSources(moduleName, descriptorFile)) {
    "Cannot find file $descriptorFile in module $moduleName"
  }
  val xml = JDOMUtil.load(file)
  resolveNonXIncludeElement(original = xml, base = file, pathResolver = xIncludePathResolver)
  return CDATA(JDOMUtil.write(xml))
}

private fun collectAndEmbedProductModules(root: Element, xIncludePathResolver: XIncludePathResolver, context: BuildContext): Set<ModuleItem> {
  val result = LinkedHashSet<ModuleItem>()
  for (moduleElement in (root.getChildren("content").asSequence().flatMap { it.getChildren("module") })) {
    val moduleName = moduleElement.getAttributeValue("name") ?: continue
    val relativeOutFile = "modules/$moduleName.jar"
    result.add(ModuleItem(moduleName = moduleName, relativeOutputFile = relativeOutFile, reason = ModuleIncludeReasons.PRODUCT_MODULES))
    PRODUCT_MODULE_IMPL_COMPOSITION.get(moduleName)?.let {
      it.mapTo(result) { subModuleName ->
        ModuleItem(moduleName = subModuleName, relativeOutputFile = relativeOutFile, reason = ModuleIncludeReasons.PRODUCT_MODULES)
      }
    }

    check(moduleElement.content.isEmpty())
    moduleElement.setContent(getModuleDescriptor(moduleName = moduleName, xIncludePathResolver = xIncludePathResolver, context = context))
  }
  return result
}

// Contrary to what it looks like, this is not a step back.
// Previously, it was specified in PLATFORM_IMPLEMENTATION_MODULES/PLATFORM_API_MODULES.
// Once the shape of the extracted module becomes fully discernible,
// we can consider ways to improve `pluginAuto` and eliminate the need for an explicit declaration here.
private val PRODUCT_MODULE_IMPL_COMPOSITION = java.util.Map.of(
  "intellij.platform.vcs.log.impl", listOf(
    "intellij.platform.vcs.log.graph.impl",
  ),
  "intellij.platform.collaborationTools", listOf(
    "intellij.platform.collaborationTools.auth.base",
    "intellij.platform.collaborationTools.auth",
  ),
  "intellij.platform.vcs.dvcs.impl", listOf(
    "intellij.platform.vcs.dvcs"
  ),
  "intellij.rider", listOf(
    "intellij.platform.debugger.modulesView"
  ),
)

internal object ModuleIncludeReasons {
  const val PRODUCT_MODULES: String = "productModule"
}