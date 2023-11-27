// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import com.intellij.util.xml.dom.readXmlAsModel
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.impl.PlatformJarNames.APP_JAR
import org.jetbrains.intellij.build.impl.PlatformJarNames.PRODUCT_CLIENT_JAR
import org.jetbrains.intellij.build.impl.PlatformJarNames.PRODUCT_JAR
import org.jetbrains.intellij.build.impl.PlatformJarNames.TEST_FRAMEWORK_JAR
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModuleDependency
import org.jetbrains.jps.model.module.JpsModuleReference
import java.util.*

private val PLATFORM_API_MODULES = persistentListOf(
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
  "intellij.xml.analysis",
  "intellij.xml",
  "intellij.xml.psi",
  "intellij.xml.structureView",
)

/**
 * List of modules which are included in lib/app.jar in all IntelliJ based IDEs.
 */
private val PLATFORM_IMPLEMENTATION_MODULES = persistentListOf(
  "intellij.platform.analysis.impl",
  "intellij.platform.diff.impl",
  "intellij.platform.editor.ex",
  "intellij.execution.process.elevation",
  "intellij.platform.externalProcessAuthHelper",
  "intellij.platform.inspect",
  // lvcs.xml - convert into product module
  "intellij.platform.lvcs.impl",
  "intellij.platform.macro",
  "intellij.platform.scriptDebugger.protocolReaderRuntime",
  "intellij.regexp",
  "intellij.platform.remoteServers.impl",
  "intellij.platform.scriptDebugger.backend",
  "intellij.platform.scriptDebugger.ui",
  "intellij.platform.smRunner",
  "intellij.platform.smRunner.vcs",
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

  "intellij.platform.bootstrap",

  "intellij.relaxng",
  "intellij.json",
  "intellij.spellchecker",
  "intellij.platform.webSymbols",
  "intellij.xml.dom.impl",

  "intellij.platform.vcs.dvcs.impl",
  "intellij.platform.vcs.log.graph.impl",
  "intellij.platform.vcs.log.impl",
  "intellij.smart.update",

  "intellij.platform.collaborationTools",
  "intellij.platform.collaborationTools.auth",

  "intellij.platform.compose",
  "intellij.platform.compose.skikoRuntime",

  "intellij.platform.markdown.utils",
  "intellij.platform.util.commonsLangV2Shim"
)

internal val PLATFORM_CUSTOM_PACK_MODE: Map<String, LibraryPackMode> = persistentMapOf(
  "jetbrains-annotations" to LibraryPackMode.STANDALONE_SEPARATE_WITHOUT_VERSION_NAME,
  "intellij-coverage" to LibraryPackMode.STANDALONE_SEPARATE,
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

private fun addModule(relativeJarPath: String,
                      moduleNames: Collection<String>,
                      productLayout: ProductModulesLayout,
                      layout: PlatformLayout) {
  layout.withModules(moduleNames.asSequence()
                       .filter { !productLayout.excludedModuleNames.contains(it) }
                       .map { ModuleItem(moduleName = it, relativeOutputFile = relativeJarPath, reason = "addModule") }.toList())
}

suspend fun createPlatformLayout(pluginsToPublish: Set<PluginLayout>, context: BuildContext): PlatformLayout {
  val enabledPluginModules = getEnabledPluginModules(pluginsToPublish = pluginsToPublish, productProperties = context.productProperties)
  val productLayout = context.productProperties.productLayout
  return createPlatformLayout(
    addPlatformCoverage = !productLayout.excludedModuleNames.contains("intellij.platform.coverage") &&
                          hasPlatformCoverage(productLayout = productLayout,
                                              enabledPluginModules = enabledPluginModules,
                                              context = context),
    projectLibrariesUsedByPlugins = computeProjectLibsUsedByPlugins(enabledPluginModules = enabledPluginModules, context = context),
    context = context,
  )
}

internal suspend fun createPlatformLayout(addPlatformCoverage: Boolean,
                                          projectLibrariesUsedByPlugins: SortedSet<ProjectLibraryData>,
                                          context: BuildContext): PlatformLayout {
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
    "intellij.platform.util.trove",
  ), productLayout = productLayout, layout = layout)
  layout.withProjectLibrary(libraryName = "ion", jarName = UTIL_RT_JAR)

  // skiko-runtime needed for Compose
  layout.withModuleLibrary(
    libraryName = "jetbrains.skiko.awt.runtime.all",
    moduleName = "intellij.platform.compose.skikoRuntime",
    relativeOutputPath = "skiko-runtime.jar"
  )

  // maven uses JDOM in an external process
  addModule(UTIL_8_JAR, listOf(
    "intellij.platform.util.jdom",
    "intellij.platform.util.xmlDom",
    "intellij.platform.tracing.rt",
    "intellij.platform.util.base",
    "intellij.platform.diagnostic",
    "intellij.platform.util",
    "intellij.platform.core",
  ), productLayout = productLayout, layout = layout)
  // used by jdom - pack to the same JAR
  layout.withProjectLibrary(libraryName = "aalto-xml", jarName = UTIL_8_JAR)
  // Space plugin uses it and bundles into IntelliJ IDEA, but not bundles into DataGrip, so, or Space plugin should bundle this lib,
  // or IJ Platform. As it is a small library and consistency is important across other coroutine libs, bundle to IJ Platform.
  layout.withProjectLibrary(libraryName = "kotlinx-coroutines-slf4j", jarName = APP_JAR)
  // make sure that all ktor libraries bundled into the platform
  layout.withProjectLibrary(libraryName = "ktor-client-content-negotiation")
  layout.withProjectLibrary(libraryName = "ktor-client-logging")
  layout.withProjectLibrary(libraryName = "ktor-serialization-kotlinx-json")

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
  val productPluginSourceModuleName = context.productProperties.productPluginSourceModuleName
                                      ?: context.productProperties.applicationInfoModule
  val productPluginContentModules = getProductPluginContentModules(context = context,
                                                                   productPluginSourceModuleName = productPluginSourceModuleName)
  val explicit = mutableListOf<ModuleItem>()
  for (moduleName in productLayout.productImplementationModules) {
    if (productLayout.excludedModuleNames.contains(moduleName)) {
      continue
    }

    explicit.add(ModuleItem(moduleName = moduleName,
                            relativeOutputFile = when {
                              isModuleCloseSource(moduleName, context = context) -> {
                                if (jetBrainsClientModuleFilter.isModuleIncluded(moduleName)) PRODUCT_CLIENT_JAR else PRODUCT_JAR
                              }
                              else -> PlatformJarNames.getPlatformModuleJarName(moduleName, context) 
                            },
                            reason = "productImplementationModules"))
  }
  explicit.addAll(toModuleItemSequence(PLATFORM_API_MODULES, productLayout = productLayout, reason = "PLATFORM_API_MODULES", context))
  explicit.addAll(toModuleItemSequence(PLATFORM_IMPLEMENTATION_MODULES, productLayout = productLayout, reason = "PLATFORM_IMPLEMENTATION_MODULES",
                                       context))
  explicit.addAll(toModuleItemSequence(productLayout.productApiModules, productLayout = productLayout, reason = "productApiModules",
                                       context))
  if (addPlatformCoverage) {
    explicit.add(ModuleItem(moduleName = "intellij.platform.coverage", relativeOutputFile = APP_JAR, reason = "coverage"))
  }
  val implicit = computeImplicitRequiredModules(
    explicit = explicit.map { it.moduleName }.toList(),
    layout = layout,
    productPluginContentModules = productPluginContentModules.mapTo(HashSet()) { it.moduleName },
    productLayout = productLayout,
    context = context,
  )
  layout.withModules((explicit +
                      productPluginContentModules +
                      implicit.asSequence().map {
                        ModuleItem(moduleName = it.first,
                                   relativeOutputFile = PlatformJarNames.getPlatformModuleJarName(it.first, context),
                                   reason = "<- " + it.second.asReversed().joinToString(separator = " <- "))
                      }).sortedBy { it.moduleName }.toList())
  for (item in projectLibrariesUsedByPlugins) {
    if (!layout.excludedProjectLibraries.contains(item.libraryName)) {
      layout.includedProjectLibraries.add(item)
    }
  }
  // as a separate step, not a part of computing implicitModules, as we should collect libraries from a such implicitly included modules
  layout.collectProjectLibrariesFromIncludedModules(context = context) { lib, module ->
    val name = lib.name
    // this module is used only when running IDE from sources, no need to include its dependencies, see IJPL-125
    if (module.name == "intellij.platform.buildScripts.downloader" && (name == "zstd-jni" || name == "zstd-jni-windows-aarch64")) {
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

internal fun computeProjectLibsUsedByPlugins(enabledPluginModules: Set<String>, context: BuildContext): SortedSet<ProjectLibraryData> {
  val result = ObjectLinkedOpenHashSet<ProjectLibraryData>()
  val jpsJavaExtensionService = JpsJavaExtensionService.getInstance()
  val pluginLayoutsByJpsModuleNames = getPluginLayoutsByJpsModuleNames(modules = enabledPluginModules,
                                                                       productLayout = context.productProperties.productLayout)
  for (plugin in pluginLayoutsByJpsModuleNames) {
    if (plugin.auto) {
      continue
    }

    for (moduleName in plugin.includedModules.asSequence().map { it.moduleName }.distinct()) {
      for (element in context.findRequiredModule(moduleName).dependenciesList.dependencies) {
        val libraryReference = (element as? JpsLibraryDependency)?.libraryReference ?: continue
        if (libraryReference.parentReference is JpsModuleReference) {
          continue
        }

        if (jpsJavaExtensionService.getDependencyExtension(element)?.scope?.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME) != true) {
          continue
        }

        val libraryName = element.libraryReference.libraryName
        if (plugin.hasLibrary(libraryName)) {
          continue
        }

        val packMode = PLATFORM_CUSTOM_PACK_MODE.getOrDefault(libraryName, LibraryPackMode.MERGED)
        result.addOrGet(ProjectLibraryData(libraryName, packMode, reason = "<- $moduleName"))
          .dependentModules
          .computeIfAbsent(plugin.directoryName) { mutableListOf() }
          .add(moduleName)
      }
    }
  }
  return result
}

internal fun getEnabledPluginModules(pluginsToPublish: Set<PluginLayout>, productProperties: ProductProperties): Set<String> {
  val result = LinkedHashSet<String>()
  result.addAll(productProperties.productLayout.bundledPluginModules)
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

private fun toModuleItemSequence(list: Collection<String>,
                                 productLayout: ProductModulesLayout,
                                 reason: String,
                                 context: BuildContext): Sequence<ModuleItem> {
  return list.asSequence()
    .filter { !productLayout.excludedModuleNames.contains(it) }
    .map { ModuleItem(moduleName = it, relativeOutputFile = PlatformJarNames.getPlatformModuleJarName(it, context), reason = reason) }
}

private suspend fun computeImplicitRequiredModules(explicit: List<String>,
                                                   layout: PlatformLayout,
                                                   productPluginContentModules: Set<String>,
                                                   productLayout: ProductModulesLayout,
                                                   context: BuildContext): List<Pair<String, PersistentList<String>>> {
  val rootChain = persistentListOf<String>()
  val rootList = layout.filteredIncludedModuleNames(TEST_FRAMEWORK_JAR)
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
  compute(list = rootList, context = context, unique = unique, result = result)

  if (context.options.validateImplicitPlatformModule) {
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

private fun compute(list: List<Pair<String, PersistentList<String>>>,
                    context: BuildContext,
                    unique: HashSet<String>,
                    result: MutableList<Pair<String, PersistentList<String>>>) {
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
    compute(list = result.subList(oldSize, result.size).sortedBy { it.first },
            context = context,
            unique = unique,
            result = result)
  }
}

// result _must be_ consistent, do not use Set.of or HashSet here
private suspend fun getProductPluginContentModules(context: BuildContext, productPluginSourceModuleName: String): Set<ModuleItem> {
  val content = withContext(Dispatchers.IO) {
    var file = context.findFileInModuleSources(productPluginSourceModuleName, "META-INF/plugin.xml")
    if (file == null) {
      file = context.findFileInModuleSources(productPluginSourceModuleName,
                                             "META-INF/${context.productProperties.platformPrefix}Plugin.xml")
      if (file == null) {
        context.messages.warning("Cannot find product plugin descriptor in '$productPluginSourceModuleName' module")
        return@withContext null
      }
    }

    readXmlAsModel(file).getChild("content")
  } ?: return emptySet()

  val modules = content.children("module")
  val result = LinkedHashSet<ModuleItem>()
  for (module in modules) {
    result.add(ModuleItem(moduleName = module.attributes.get("name") ?: continue, relativeOutputFile = "modules.jar", reason = "productModule"))
  }
  return result
}