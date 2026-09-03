// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.pluginModel

import com.intellij.platform.pluginSystem.parser.impl.LoadPathUtil
import com.intellij.platform.pluginSystem.parser.impl.parseContentAndXIncludes
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.PLUGIN_XML_RELATIVE_PATH
import org.jetbrains.intellij.build.findFileInModuleSources
import org.jetbrains.intellij.build.getProductionLibraryDependencies
import org.jetbrains.intellij.build.impl.BaseLayout
import org.jetbrains.intellij.build.impl.DistributionBuilderState
import org.jetbrains.intellij.build.impl.PlatformJarNames
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.getPluginLayoutsByJpsModuleNames
import org.jetbrains.intellij.build.inferredAutoLayoutChildren
import org.jetbrains.intellij.build.mapConcurrent
import org.jetbrains.intellij.build.productLayout.util.getProductionModuleDependencies
import org.jetbrains.intellij.build.resolveDescriptor
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleReference
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * The name of the validation that compares this provider against the packaged content report.
 *
 * The message this provider gives the validator names it, so a reader of a red plugin-dependency test knows which
 * test states that the two agree.
 */
const val PLUGIN_LAYOUT_PARITY_VALIDATION_NAME: String = "plugin-layout-parity"

/**
 * Describes the plugins of one product from the layout the build computed, before the build packs a jar.
 *
 * [createLayoutProviderByContentReport] answers the same question from the packaged content report, so a validation
 * that uses it has to wait for the packaging of the whole product. This provider reads [DistributionBuilderState],
 * which the build computes first, so the validation runs beside the packaging instead of after it.
 *
 * The two providers must agree. The `plugin-layout-parity` validation compares them for the core and for every plugin
 * of the report, and it is the reason this provider may state a rule and not copy the packaged answer.
 *
 * @param state the layout of the platform and of the plugins to publish
 * @param mainModuleOfCorePlugin the module that holds the core plugin descriptor
 * @param corePluginDescriptorPath the path of the core plugin descriptor, relative to a resource root
 */
suspend fun createLayoutProviderByDistributionState(
  state: DistributionBuilderState,
  context: BuildContext,
  mainModuleOfCorePlugin: String,
  corePluginDescriptorPath: String,
): PluginLayoutProvider {
  val mainModulesOfBundledPlugins = context.getBundledPluginModules()
  val layoutByMainModule = LinkedHashMap<String, PluginLayout>()
  // one main module can have two layouts, which differ by their bundling restrictions alone. Both state one member
  // list, so the first one answers for the plugin.
  for (layout in getPluginLayoutsByJpsModuleNames(modules = mainModulesOfBundledPlugins, productLayout = context.productProperties.productLayout)) {
    layoutByMainModule.putIfAbsent(layout.mainModule, layout)
  }
  for (layout in state.pluginsToPublish) {
    layoutByMainModule.putIfAbsent(layout.mainModule, layout)
  }

  return DistributionStatePluginLayoutProvider(
    platformLayout = state.platformLayout,
    layoutByMainModule = layoutByMainModule,
    allPluginLayouts = context.productProperties.productLayout.pluginLayouts,
    mainModulesOfBundledPlugins = mainModulesOfBundledPlugins,
    mainModuleOfCorePlugin = mainModuleOfCorePlugin,
    corePluginDescriptorPath = corePluginDescriptorPath,
    outputProvider = context.outputProvider,
    mainModulesWithPluginDescriptor = collectMainModulesWithPluginDescriptor(
      mainModules = layoutByMainModule.keys,
      outputProvider = context.outputProvider,
    ),
    contentModulesByMainModule = collectContentModulesByMainModule(
      mainModules = layoutByMainModule.keys,
      outputProvider = context.outputProvider,
    ),
  )
}

/**
 * The JPS modules that own the content modules of each plugin, read from the plugin descriptor and its includes.
 *
 * The build packs the libraries of a content module beside the plugin, so the packaged content report lists their
 * roots. The provider reads no descriptor for the members, and it needs none. The library roots are the one field
 * where a content module counts, so this pre-pass reads the descriptor for them alone.
 *
 * An include that no module answers is skipped. The roots are wider than the packed set already, and the
 * `plugin-layout-parity` validation reports a root the report has and the provider does not.
 */
private suspend fun collectContentModulesByMainModule(
  mainModules: Collection<String>,
  outputProvider: ModuleOutputProvider,
): Map<String, List<String>> {
  return mainModules.mapConcurrent { mainModule ->
    val module = outputProvider.findModule(mainModule) ?: return@mapConcurrent null
    val pluginXml = findFileInModuleSources(module = module, relativePath = PLUGIN_XML_RELATIVE_PATH, onlyProductionSources = true)
                    ?: return@mapConcurrent null
    val contentModules = LinkedHashSet<String>()
    val visited = HashSet<String>()
    var pending: List<ByteArray> = listOf(Files.readAllBytes(pluginXml))
    while (pending.isNotEmpty()) {
      val next = ArrayList<ByteArray>()
      for (data in pending) {
        val parsed = parseContentAndXIncludes(input = data, locationSource = null)
        parsed.contentModules.mapTo(contentModules) { it.name.substringBeforeLast('/') }
        for (include in parsed.xIncludePaths) {
          if (include.isEmpty() || !visited.add(include)) {
            continue
          }
          resolveDescriptor(
            module = module,
            path = LoadPathUtil.toLoadPath(include),
            outputProvider = outputProvider,
            searchAnyModuleOutput = false,
          )?.let(next::add)
        }
      }
      pending = next
    }
    mainModule to contentModules.toList()
  }.filterNotNull().toMap()
}

private suspend fun collectMainModulesWithPluginDescriptor(
  mainModules: Collection<String>,
  outputProvider: ModuleOutputProvider,
): Set<String> {
  return mainModules.mapConcurrent { mainModule ->
    val module = outputProvider.findModule(mainModule) ?: return@mapConcurrent null
    val descriptorContent = outputProvider.readFileContentFromModuleOutput(
      module = module,
      relativePath = PLUGIN_XML_RELATIVE_PATH,
      forTests = false,
    )
    mainModule.takeIf { descriptorContent != null }
  }.filterNotNullTo(HashSet())
}

private class DistributionStatePluginLayoutProvider(
  private val platformLayout: PlatformLayout,
  private val layoutByMainModule: Map<String, PluginLayout>,
  private val allPluginLayouts: Collection<PluginLayout>,
  private val mainModulesOfBundledPlugins: List<String>,
  private val mainModuleOfCorePlugin: String,
  private val corePluginDescriptorPath: String,
  private val outputProvider: ModuleOutputProvider,
  private val mainModulesWithPluginDescriptor: Set<String>,
  private val contentModulesByMainModule: Map<String, List<String>>,
) : PluginLayoutProvider {
  private val libraryRootCache = ConcurrentHashMap<String, List<Path>>()

  override fun loadCorePluginLayout(): PluginLayoutDescription {
    val members = collectLibDirMembers(platformLayout, jarToIgnore = PlatformJarNames.TEST_FRAMEWORK_JAR)
    return PluginLayoutDescription(
      mainJpsModule = mainModuleOfCorePlugin,
      pluginDescriptorPath = corePluginDescriptorPath,
      jpsModulesInClasspath = members,
      libraryRootsInClasspath = collectLibraryRoots(layout = platformLayout, members = members),
    )
  }

  override fun loadMainModulesOfBundledPlugins(): List<String> = mainModulesOfBundledPlugins

  /**
   * The layout of one plugin, or `null` when this product ships no plugin with [mainModule] as its main module.
   *
   * The members are the main module, the layout members that go directly into `lib/`, and, for an `auto` layout, the
   * modules [inferredAutoLayoutChildren] adds. A member of a jar below `lib/` keeps its own classloader, so it is not
   * on the classpath of the main module and the `/` in its output path holds it out.
   *
   * The content modules of the plugin are not members. The build reads them from the plugin descriptor while it packs,
   * and [PluginDependenciesValidator] reads the same descriptor: it drops a content module that is not embedded and it
   * adds one that is. An `auto` layout can still name a content module here, because this provider reads no descriptor
   * and the child rule cannot tell one from an ordinary dependency.
   *
   * [PluginLayoutDescription.libraryRootsInClasspath] is wider than the packed set on purpose. It holds every library
   * the members and the content modules declare, and not only the ones the build packs into `lib/`. The one reader
   * resolves an `xi:include` against the roots, so a root too many costs a lookup and a root too few loses a descriptor.
   */
  override fun loadPluginLayout(mainModule: JpsModule): PluginLayoutDescription? {
    val layout = layoutByMainModule[mainModule.name] ?: return null
    if (mainModule.name !in mainModulesWithPluginDescriptor) {
      throw PluginModuleConfigurationError(
        pluginModelModuleName = mainModule.name,
        errorMessage = """
                '$PLUGIN_XML_RELATIVE_PATH' file is not found in production output of module '${mainModule.name}'.
                The product layout names the module as the main module of a plugin; if it is not one anymore,
                update the product layout to avoid confusion.
              """.trimIndent(),
      )
    }

    val members = LinkedHashSet<String>()
    members.add(layout.mainModule)
    members.addAll(collectLibDirMembers(layout, jarToIgnore = null))
    if (layout.auto) {
      members.addAll(inferAutoChildren(layout))
    }
    // the build packs the libraries of a content module beside the plugin, so its roots count and the module does not
    val modulesWithLibraries = members + contentModulesByMainModule.get(mainModule.name).orEmpty()
    return PluginLayoutDescription(
      mainJpsModule = mainModule.name,
      pluginDescriptorPath = PLUGIN_XML_RELATIVE_PATH,
      jpsModulesInClasspath = members,
      libraryRootsInClasspath = collectLibraryRoots(layout = layout, members = modulesWithLibraries),
    )
  }

  override val messageDescribingHowToUpdateLayoutData: String
    get() = "Note that the validation uses the layout the build computes before it packs a jar, " +
            "so the '$PLUGIN_LAYOUT_PARITY_VALIDATION_NAME' validation checks it against the generated content report."

  private fun inferAutoChildren(layout: PluginLayout): List<String> {
    val mainModule = outputProvider.findModule(layout.mainModule) ?: return emptyList()
    return inferredAutoLayoutChildren(
      layout = layout,
      directDependencies = mainModule.getProductionModuleDependencies(withTests = false).map { it.moduleReference.moduleName },
      addedModules = layout.includedModules.mapTo(HashSet()) { it.moduleName },
      platformLayout = platformLayout,
      pluginLayouts = allPluginLayouts,
    )
  }

  /** The layout members of a jar directly in the `lib/` directory, which is the main classloader of the owner. */
  private fun collectLibDirMembers(layout: BaseLayout, jarToIgnore: String?): Set<String> {
    return layout.includedModules
      .asSequence()
      .filter { !it.relativeOutputFile.contains('/') && it.relativeOutputFile != jarToIgnore }
      .mapTo(LinkedHashSet()) { it.moduleName }
  }

  private fun collectLibraryRoots(layout: BaseLayout, members: Collection<String>): List<Path> {
    val result = LinkedHashSet<Path>()
    for (libraryName in layout.getIncludedProjectLibraryNames()) {
      result.addAll(resolveLibraryRoots(libraryName = libraryName, moduleLibraryModuleName = null))
    }
    for ((libraryName, moduleName) in layout.getIncludedModuleLibraryNames()) {
      result.addAll(resolveLibraryRoots(libraryName = libraryName, moduleLibraryModuleName = moduleName))
    }
    for (moduleName in members) {
      val module = outputProvider.findModule(moduleName) ?: continue
      for (dependency in getProductionLibraryDependencies(module)) {
        val reference = dependency.libraryReference
        val owner = (reference.parentReference as? JpsModuleReference)?.moduleName
        result.addAll(resolveLibraryRoots(libraryName = reference.libraryName, moduleLibraryModuleName = owner))
      }
    }
    return result.toList()
  }

  /**
   * The roots of one library, or an empty list when the project model states none.
   *
   * A library the model cannot resolve is not an error here. The roots are wider than the packed set already, and the
   * `plugin-layout-parity` validation reports a root the packaged report has and this provider does not.
   */
  private fun resolveLibraryRoots(libraryName: String, moduleLibraryModuleName: String?): List<Path> {
    return libraryRootCache.computeIfAbsent("$moduleLibraryModuleName/$libraryName") {
      try {
        outputProvider.findLibraryRoots(libraryName = libraryName, moduleLibraryModuleName = moduleLibraryModuleName)
      }
      catch (_: Throwable) {
        emptyList()
      }
    }
  }
}
