// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import com.intellij.util.xml.dom.readXmlAsModel
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.ProductModulesLayout
import org.jetbrains.jps.model.java.*
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.Path
import java.util.*
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashSet

private const val UTIL_JAR = "util.jar"
private const val UTIL_RT_JAR = "util_rt.jar"

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
  "intellij.platform.elevation",
  "intellij.platform.elevation.client",
  "intellij.platform.elevation.common",
  "intellij.platform.elevation.daemon",
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

  "intellij.platform.markdown.utils",
)

private const val UTIL_8 = "util-8.jar"

internal val PLATFORM_CUSTOM_PACK_MODE: Map<String, LibraryPackMode> = persistentMapOf(
  "jetbrains-annotations-java5" to LibraryPackMode.STANDALONE_SEPARATE_WITHOUT_VERSION_NAME,
  "intellij-coverage" to LibraryPackMode.STANDALONE_SEPARATE,
)

internal fun collectPlatformModules(to: MutableCollection<String>) {
  to.addAll(PLATFORM_API_MODULES)
  to.addAll(PLATFORM_IMPLEMENTATION_MODULES)
}

internal fun hasPlatformCoverage(productLayout: ProductModulesLayout, enabledPluginModules: Set<String>, context: BuildContext): Boolean {
  val modules = LinkedHashSet<String>()
  modules.addAll(productLayout.getIncludedPluginModules(enabledPluginModules))
  modules.addAll(PLATFORM_API_MODULES)
  modules.addAll(PLATFORM_IMPLEMENTATION_MODULES)
  modules.addAll(productLayout.productApiModules)
  modules.addAll(productLayout.productImplementationModules)

  val coverageModuleName = "intellij.platform.coverage"
  if (modules.contains(coverageModuleName)) {
    return true
  }

  for (moduleName in modules) {
    var contains = false
    JpsJavaExtensionService.dependencies(context.findRequiredModule(moduleName))
      .productionOnly()
      .processModules { module ->
        if (!contains && module.name == coverageModuleName) {
          contains = true
        }
      }

    if (contains) {
      return true
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

internal suspend fun createPlatformLayout(productLayout: ProductModulesLayout,
                                          hasPlatformCoverage: Boolean,
                                          additionalProjectLevelLibraries: SortedSet<ProjectLibraryData>,
                                          context: BuildContext): PlatformLayout {
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

  addModule(UTIL_RT_JAR, listOf(
    "intellij.platform.util.rt",
    "intellij.platform.util.trove",
  ), productLayout, layout)

  addModule(UTIL_8, listOf(
    "intellij.platform.util.rt.java8",
    "intellij.platform.util.classLoader",
    "intellij.platform.util.jdom",
    "intellij.platform.util.xmlDom",
  ), productLayout, layout)
  // fastutil-min cannot be in libsThatUsedInJps - guava is used by JPS and also in this JAR,
  // but it leads to conflict in some old 3rd-party JDBC driver, so, pack fastutil-min into another JAR
  layout.withProjectLibrary(libraryName = "fastutil-min", jarName = UTIL_8)

  // util.jar is loaded by JVM classloader as part of loading our custom PathClassLoader class - reduce file size
  addModule(UTIL_JAR, listOf(
    "intellij.platform.util.zip",
    "intellij.platform.util",
    "intellij.platform.util.base",
    "intellij.platform.extensions",
    "intellij.platform.tracing.rt",
    "intellij.platform.core",
    // Scala uses GeneralCommandLine in JPS plugin
    "intellij.platform.ide.util.io",
    "intellij.platform.boot",
  ), productLayout, layout)

  addModule("externalProcess-rt.jar", listOf(
    "intellij.platform.externalProcessAuthHelper.rt"
  ), productLayout, layout)

  addModule("stats.jar", listOf(
    "intellij.platform.statistics",
    "intellij.platform.statistics.uploader",
    "intellij.platform.statistics.config",
  ), productLayout, layout)

  if (!productLayout.excludedModuleNames.contains("intellij.java.guiForms.rt")) {
    layout.withModule("intellij.java.guiForms.rt", "forms_rt.jar")
  }

  addModule("jps-model.jar", listOf(
    "intellij.platform.jps.model",
    "intellij.platform.jps.model.serialization",
    "intellij.platform.jps.model.impl"
  ), productLayout, layout)

  addModule("external-system-rt.jar", listOf(
    "intellij.platform.externalSystem.rt",
    "intellij.platform.objectSerializer.annotations"
  ), productLayout, layout)

  addModule("cds/classesLogAgent.jar", listOf("intellij.platform.cdsAgent"), productLayout, layout)

  val productPluginSourceModuleName = context.productProperties.productPluginSourceModuleName
                                      ?: context.productProperties.applicationInfoModule
  val productPluginContentModules = getProductPluginContentModules(context, productPluginSourceModuleName)

  val explicit = mutableListOf<ModuleItem>()
  for (moduleName in productLayout.productImplementationModules) {
    if (productLayout.excludedModuleNames.contains(moduleName)) {
      continue
    }

    explicit.add(ModuleItem(moduleName = moduleName,
                            relativeOutputFile = if (isModuleCloseSource(moduleName, context)) PRODUCT_JAR else APP_JAR,
                            reason = "productImplementationModules"))
  }

  explicit.addAll(toModuleItemSequence(PLATFORM_API_MODULES, productLayout, reason = "PLATFORM_API_MODULES"))
  explicit.addAll(toModuleItemSequence(PLATFORM_IMPLEMENTATION_MODULES, productLayout, reason = "PLATFORM_IMPLEMENTATION_MODULES"))
  explicit.addAll(toModuleItemSequence(productLayout.productApiModules, productLayout, reason = "productApiModules"))
  if (hasPlatformCoverage && !productLayout.excludedModuleNames.contains("intellij.platform.coverage")) {
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
                                   relativeOutputFile = APP_JAR,
                                   reason = "<- " + it.second.asReversed().joinToString(separator = " <- "))
                      }).sortedBy { it.moduleName }.toList())

  layout.withProjectLibrary(libraryName = "ion", jarName = UTIL_RT_JAR)

  for (item in additionalProjectLevelLibraries) {
    if (!layout.excludedProjectLibraries.contains(item.libraryName)) {
      layout.includedProjectLibraries.add(item)
    }
  }

  // as a separate step, not a part of computing implicitModules, as we should collect libraries from a such implicitly included modules
  layout.collectProjectLibrariesFromIncludedModules(context) { lib, module ->
    val name = lib.name
    if (module.name == "intellij.platform.buildScripts.downloader" && (name == "zstd-jni" || name == "zstd-jni-windows-aarch64")) {
      return@collectProjectLibrariesFromIncludedModules
    }

    layout.includedProjectLibraries
      .addOrGet(ProjectLibraryData(name, PLATFORM_CUSTOM_PACK_MODE.getOrDefault(name, LibraryPackMode.MERGED)))
      .dependentModules.computeIfAbsent("core") { mutableListOf() }.add(module.name)
  }
  return layout
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
    !Path.of(JpsPathUtil.urlToPath(moduleSourceRoot.url)).startsWith(context.paths.communityHomeDir)
  }
}

private fun toModuleItemSequence(list: Collection<String>, productLayout: ProductModulesLayout, reason: String): Sequence<ModuleItem> {
  return list.asSequence()
    .filter { !productLayout.excludedModuleNames.contains(it) }
    .map { ModuleItem(moduleName = it, relativeOutputFile = APP_JAR, reason = reason) }
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
    result.add(ModuleItem(moduleName = module.attributes.get("name") ?: continue, relativeOutputFile = APP_JAR, reason = "productModule"))
  }
  return result
}