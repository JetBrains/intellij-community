// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.intellij.build

import it.unimi.dsi.fastutil.Hash
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenCustomHashSet
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.PluginLayout
import java.util.function.BiConsumer

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
   * Names of the main modules (containing META-INF/plugin.xml) of the plugins which need to be bundled with the product. Layouts of the
   * bundled plugins are specified in [pluginLayouts] list.
   */
  var bundledPluginModules: MutableList<String> = DEFAULT_BUNDLED_PLUGINS.toMutableList()

  /**
   * Main module names (containing META-INF/plugin.xml) of the plugins which aren't bundled with the product but may be installed
   * into it. Zip archives of these plugins will be built and placed under "&lt;product-code&gt;-plugins" directory in the build artifacts.
   * Layouts of the plugins are specified in [pluginLayouts] list.
   */
  var pluginModulesToPublish: PersistentSet<String> = persistentSetOf()

  /**
   * Describes layout of non-trivial plugins which may be included into the product. The actual list of the plugins need to be bundled
   * with the product is specified by [bundledPluginModules], the actual list of plugins which need to be prepared for publishing
   * is specified by [pluginModulesToPublish].
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
   * <strong>This is a temporary property added to keep layout of some products. If some directory from a module shouldn't be included into the
   * product JAR it's strongly recommended to move that directory outside of the module source roots.</strong>
   */
  internal val moduleExcludes: MutableMap<String, MutableList<String>> = LinkedHashMap()

  /**
   * Additional customizations of platform JARs. **This is a temporary property added to keep layout of some products.**
   */
  internal var platformLayoutSpec = persistentListOf<(PlatformLayout, BuildContext) -> Unit>()

  @Deprecated("PlatformLayout should be immutable", replaceWith = ReplaceWith("addPlatformSpec"))
  fun addPlatformCustomizer(customizer: BiConsumer<PlatformLayout, BuildContext>) {
    platformLayoutSpec = platformLayoutSpec.add { layout, context ->
      customizer.accept(layout, context)
    }
  }

  fun addPlatformSpec(customizer: (PlatformLayout, BuildContext) -> Unit) {
    platformLayoutSpec = platformLayoutSpec.add(customizer)
  }

  fun excludeModuleOutput(module: String, path: String) {
    moduleExcludes.computeIfAbsent(module) { mutableListOf() }.add(path)
  }

  fun excludeModuleOutput(module: String, path: Collection<String>) {
    moduleExcludes.computeIfAbsent(module) { mutableListOf() }.addAll(path)
  }

  /**
   * Names of the modules which classpath will be used to build searchable options index <br>
   * //todo get rid of this property and automatically include all platform and plugin modules to the classpath when building searchable options index
   */
  var mainModules: List<String> = emptyList()

  /**
   * If `true` a special xml descriptor in custom plugin repository format will be generated for [pluginModulesToPublish] plugins.
   * This descriptor and the plugin *.zip files can be uploaded to the URL specified in 'plugins@builtin-url' attribute in *ApplicationInfo.xml file
   * to allow installing custom plugins directly from IDE. If [ProprietaryBuildTools.artifactsServer] is specified, `__BUILTIN_PLUGINS_URL__` in
   * *ApplicationInfo.xml file will be automatically replaced by the plugin repository URL provided by the artifact server.
   *
   * @see [pluginModulesToPublish]
   */
  var prepareCustomPluginRepositoryForPublishedPlugins: Boolean = true

  /**
   * If `true` then all plugins that compatible with an IDE will be built. By default, these plugins will be placed to "auto-uploading"
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
   * Allows to filter out default platform modules (both api and implementation) as well as product modules.
   * This API is experimental, use with care
   */
  var excludedModuleNames: PersistentSet<String> = persistentSetOf()
}

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
