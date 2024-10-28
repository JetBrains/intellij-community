// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.intellij.build

import it.unimi.dsi.fastutil.Hash
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenCustomHashSet
import kotlinx.collections.immutable.*
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.PluginLayout

/**
 * Default bundled plugins for all products.
 */
val DEFAULT_BUNDLED_PLUGINS: PersistentList<String> = persistentListOf(
  "intellij.platform.images",
  "intellij.dev"
)

class ProductModulesLayout {
  /**
   * Names of the additional product-specific modules which need to be packed into openapi.jar in the product's 'lib' directory.
   */
  var productApiModules: List<String> = emptyList()

  /**
   * Names of the additional product-specific modules which need to be included in the product's 'lib' directory
   */
  var productImplementationModules: List<String> = emptyList()

  /**
   * These are the names of the main modules (which contain META-INF/plugin.xml).
   * They belong to the plugins that need to be included with the product.
   * You can find the layouts of these bundled plugins in the [pluginLayouts] list.
   * 
   * This property can be used for writing only. 
   * If you need to read the list of plugins which should be bundled, use [BuildContext.bundledPluginModules] instead.  
   */
  var bundledPluginModules: PersistentList<String> = DEFAULT_BUNDLED_PLUGINS

  /**
   * Main module names (containing META-INF/plugin.xml) of the plugins which aren't bundled with the product but may be installed into it.
   * Zip archives of these plugins will be built and placed under [BuildContext.nonBundledPlugins] directory in the build artifacts.
   * Layouts of the plugins are specified in [pluginLayouts] list.
   */
  var pluginModulesToPublish: PersistentSet<String> = persistentSetOf()

  /**
   * Describes the layout of non-trivial plugins which may be included in the product.
   * The actual list of the plugins needs to be bundled with the product is specified by [bundledPluginModules],
   * the actual list of plugins which need to be prepared for publishing is specified by [pluginModulesToPublish].
   */
  var pluginLayouts: PersistentList<PluginLayout> = CommunityRepositoryModules.COMMUNITY_REPOSITORY_PLUGINS
    set(value) {
      val nameGuard = createPluginLayoutSet(value.size)
      for (layout in value) {
        check(nameGuard.add(layout)) {
          val bundlingRestrictionsAsString = if (layout.bundlingRestrictions == PluginBundlingRestrictions.NONE) {
            ""
          }
          else {
            ", bundlingRestrictions=${layout.bundlingRestrictions}"
          }
          "PluginLayout(mainModule=${layout.mainModule}$bundlingRestrictionsAsString) is duplicated"
        }
      }
      field = value
    }

  /**
   * Module name to list of Ant-like patterns describing entries which should be excluded from its output.
   * <strong>This is a temporary property added to keep the layout of some products.
   * If some directory from a module shouldn't be included in the product JAR,
   * it's strongly recommended to move that directory outside the module source roots.</strong>
   */
  internal val moduleExcludes: MutableMap<String, MutableList<String>> = LinkedHashMap()

  /**
   * Additional customizations of platform JARs. **This is a temporary property added to keep layout of some products.**
   */
  internal var platformLayoutSpec = persistentListOf<suspend (PlatformLayout, BuildContext) -> Unit>()
    private set

  fun addPlatformSpec(customizer: suspend (PlatformLayout, BuildContext) -> Unit) {
    platformLayoutSpec += customizer
  }

  fun excludeModuleOutput(module: String, path: String) {
    moduleExcludes.computeIfAbsent(module) { mutableListOf() }.add(path)
  }

  fun excludeModuleOutput(module: String, path: Collection<String>) {
    moduleExcludes.computeIfAbsent(module) { mutableListOf() }.addAll(path)
  }

  /**
   * If `true` a special xml descriptor in custom plugin repository format will be generated for [pluginModulesToPublish] plugins.
   * This descriptor and the plugin *.zip files can be uploaded to the URL specified in 'plugins@builtin-url' attribute in *ApplicationInfo.xml file
   * to allow installing custom plugins directly from the IDE. If [ProprietaryBuildTools.artifactsServer] is specified, `__BUILTIN_PLUGINS_URL__` in
   * *ApplicationInfo.xml file will be automatically replaced by the plugin repository URL provided by the artifact server.
   *
   * @see [pluginModulesToPublish]
   */
  var prepareCustomPluginRepositoryForPublishedPlugins: Boolean = true

  /**
   * If `true` then all plugins that compatible with an IDE will be built.
   * By default, these plugins will be placed to [BuildContext.nonBundledPluginsToBePublished]
   * subdirectory and may be automatically uploaded to plugins.jetbrains.com.
   * <br>
   * If `false` only plugins from [pluginModulesToPublish] will be considered.
   */
  var buildAllCompatiblePlugins: Boolean = true

  /**
   * List of plugin names which should not be built even if they are compatible and [buildAllCompatiblePlugins] is true
   */
  var compatiblePluginsToIgnore: PersistentList<String> = persistentListOf()

  /**
   * Module names which should be excluded from this product.
   * Allows filtering out default platform modules (both api and implementation) as well as product modules.
   * This API is experimental, use it with care
   */
  var excludedModuleNames: PersistentSet<String> = persistentSetOf()
}

// the set is ordered (Linked)
internal fun createPluginLayoutSet(expectedSize: Int): MutableSet<PluginLayout> {
  return ObjectLinkedOpenCustomHashSet(expectedSize, object : Hash.Strategy<PluginLayout?> {
    override fun hashCode(layout: PluginLayout?): Int {
      if (layout == null) {
        return 0
      }

      var result = layout.mainModule.hashCode()
      result = 31 * result + layout.bundlingRestrictions.hashCode()
      return result
    }

    override fun equals(a: PluginLayout?, b: PluginLayout?): Boolean {
      if (a == b) {
        return true
      }
      if (a == null || b == null) {
        return false
      }
      return a.mainModule == b.mainModule && a.bundlingRestrictions == b.bundlingRestrictions
    }
  })
}
