// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.intellij.build

import com.intellij.openapi.util.MultiValuesMap
import com.intellij.util.containers.MultiMap
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.PluginLayout
import java.util.function.BiConsumer

class ProductModulesLayout {
  companion object {
    @JvmField
    val DEFAULT_BUNDLED_PLUGINS: PersistentList<String> = persistentListOf(
      "intellij.platform.images",
      "intellij.dev",
    )
  }

  /**
   * Name of the main product JAR file. Outputs of {@link #productImplementationModules} will be packed into it.
   */
  lateinit var mainJarName: String

  /**
   * Names of the additional product-specific modules which need to be packed into openapi.jar in the product's 'lib' directory.
   */
  var productApiModules: List<String> = emptyList()

  /**
   * Names of the additional product-specific modules which need to be included into {@link #mainJarName} in the product's 'lib' directory
   */
  var productImplementationModules: List<String> = emptyList()

  /**
   * Names of the main modules (containing META-INF/plugin.xml) of the plugins which need to be bundled with the product. Layouts of the
   * bundled plugins are specified in {@link #pluginLayouts} list.
   */
  var bundledPluginModules: MutableList<String> = DEFAULT_BUNDLED_PLUGINS.toMutableList()

  private var pluginsToPublish: LinkedHashSet<String> = LinkedHashSet()

  fun addPluginToPublish(id: String) {
    pluginsToPublish.add(id)
  }

  /**
   * Names of the main modules (containing META-INF/plugin.xml) of the plugins which aren't bundled with the product but may be installed
   * into it. Zip archives of these plugins will be built and placed under "&lt;product-code&gt;-plugins" directory in the build artifacts.
   * Layouts of the plugins are specified in {@link #pluginLayouts} list.
   */
  fun setPluginModulesToPublish(plugins: List<String>) {
    pluginsToPublish = LinkedHashSet(plugins)
  }

  /**
   * @see #setPluginModulesToPublish
   */
  fun getPluginModulesToPublish(): List<String> {
    return java.util.List.copyOf(pluginsToPublish)
  }

  /**
   * Describes layout of all plugins which may be included into the product. The actual list of the plugins need to be bundled
   * with the product is specified by {@link #bundledPluginModules}, the actual list of plugins which need to be prepared for publishing
   * is specified by {@link #setPluginModulesToPublish pluginModulesToPublish}.
   *
   * For trivial plugins, i.e. for plugins which include an output of a single module and its module libraries, it's enough to use
   * [org.jetbrains.intellij.build.impl.PluginLayout.Companion.simplePlugin] as layout.
   */
  var pluginLayouts: List<PluginLayout> = CommunityRepositoryModules.COMMUNITY_REPOSITORY_PLUGINS

  /**
   * Fail when plugin should be build, but its definition is missing from [org.jetbrains.intellij.build.ProductModulesLayout.pluginLayouts]
   */
  var failOnUnspecifiedPluginLayout: Boolean = false

  /**
   * Names of the project libraries which JARs' contents should be extracted into {@link #mainJarName} JAR.
   */
  var projectLibrariesToUnpackIntoMainJar: PersistentList<String> = persistentListOf()

  /**
   * Maps names of JARs to names of the modules; these modules will be packed into these JARs and copied to the product's 'lib' directory.
   */
  val additionalPlatformJars: MultiMap<String, String> = MultiMap.createLinkedSet()

  /**
   * Module name to list of Ant-like patterns describing entries which should be excluded from its output.
   * <strong>This is a temporary property added to keep layout of some products. If some directory from a module shouldn't be included into the
   * product JAR it's strongly recommended to move that directory outside of the module source roots.</strong>
   */
  val moduleExcludes: MultiValuesMap<String, String> = MultiValuesMap<String, String>(true)

  /**
   * Additional customizations of platform JARs. <strong>This is a temporary property added to keep layout of some products.</strong>
   */
  internal var platformLayoutCustomizers = persistentListOf<BiConsumer<PlatformLayout, BuildContext>>()

  fun addPlatformCustomizer(customizer: BiConsumer<PlatformLayout, BuildContext>) {
    platformLayoutCustomizers = platformLayoutCustomizers.add(customizer)
  }

  /**
   * Names of the modules which classpath will be used to build searchable options index <br>
   * //todo[nik] get rid of this property and automatically include all platform and plugin modules to the classpath when building searchable options index
   */
  var mainModules: List<String> = emptyList()

  /**
   * If {@code true} a special xml descriptor in custom plugin repository format will be generated for {@link #setPluginModulesToPublish} plugins.
   * This descriptor and the plugin *.zip files can be uploaded to the URL specified in 'plugins@builtin-url' attribute in *ApplicationInfo.xml file
   * to allow installing custom plugins directly from IDE. If {@link ProprietaryBuildTools#artifactsServer} is specified, {@code __BUILTIN_PLUGINS_URL__} in
   * *ApplicationInfo.xml file will be automatically replaced by the plugin repository URL provided by the artifact server.
   *
   * @see #setPluginModulesToPublish
   */
  var prepareCustomPluginRepositoryForPublishedPlugins = true

  /**
   * If {@code true} then all plugins that compatible with an IDE will be built. By default these plugins will be placed to "auto-uploading"
   * subdirectory and may be automatically uploaded to plugins.jetbrains.com.
   * <br>
   * If {@code false} only plugins from {@link #setPluginModulesToPublish} will be considered.
   */
  var buildAllCompatiblePlugins = true

  /**
   * List of plugin names which should not be built even if they are compatible and {@link #buildAllCompatiblePlugins} is true
   */
  var compatiblePluginsToIgnore: List<String> = emptyList()

  /**
   * Module names which should be excluded from this product.
   * Allows to filter out default platform modules (both api and implementation) as well as product modules.
   * This API is experimental, use with care
   */
  var excludedModuleNames: PersistentSet<String> = persistentSetOf()

  /**
   * @return list of all modules which output is included into the plugin's JARs
   */
  fun getIncludedPluginModules(enabledPluginModules: Collection<String>): Collection<String> {
    val result = LinkedHashSet<String>()
    result.addAll(enabledPluginModules)
    pluginLayouts.asSequence()
      .filter { enabledPluginModules.contains(it.mainModule) }
      .flatMapTo(result) { it.getIncludedModuleNames() }
    return result
  }

  /**
   * Map name of JAR to names of the modules; these modules will be packed into these JARs and copied to the product's 'lib' directory.
   */
  fun withAdditionalPlatformJar(jarName: String, vararg moduleNames: String) {
    additionalPlatformJars.putValues(jarName, moduleNames.toList())
  }

  fun withoutAdditionalPlatformJar(jarName: String, moduleName: String) {
    additionalPlatformJars.remove(jarName, moduleName)
  }
}