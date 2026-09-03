// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.intellij.build.productLayout

import it.unimi.dsi.fastutil.Hash
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenCustomHashSet
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.plus
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.PluginBundlingRestrictions
import org.jetbrains.intellij.build.getCommunityRepositoryPlugins
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.PluginLayout

/**
 * Default bundled plugins for all products.
 */
val DEFAULT_BUNDLED_PLUGINS: PersistentList<String> = persistentListOf(
  "intellij.dev",
  "intellij.java.aetherDependencyResolver.plugin",
  "intellij.jcef.plugin",
  "intellij.platform.bookmarks.plugin",
  "intellij.grid.core.plugin",
  "intellij.platform.navbar.plugin",
  "intellij.platform.problemView.plugin",
  "intellij.platform.testRunner.plugin",
  "intellij.platform.recentFiles.plugin",
  "intellij.platform.structuralSearch.plugin",
  "intellij.platform.structureView.plugin",
  "intellij.platform.tasks.plugin",
  "intellij.platform.execution.serviceView.plugin",
  "intellij.platform.todo.plugin",
  "intellij.platform.vcs.plugin",
  "intellij.xml.plugin",
  "intellij.platform.images",
)

/**
 * The main modules of the plugins that only a development run uses.
 *
 * A user must never install one of them, so no product publishes one.
 * A development run still loads such a plugin, because `-Dadditional.modules` names the module.
 *
 * The build leaves such a plugin out of the pipeline, so no check runs on it.
 *
 * A community product reads this list too, so `community/.idea/modules.xml` must register each entry.
 * A module of the ultimate project belongs in `ULTIMATE_COMPATIBLE_PLUGINS_TO_IGNORE` instead.
 *
 * @see ProductModulesLayout.compatiblePluginsToIgnore
 */
val COMPATIBLE_PLUGINS_TO_IGNORE: PersistentList<String> = persistentListOf(
  // it registers a file type named Dart, Lua, Rust and Swift, and takes the name from the real plugin
  "intellij.lsp.client.playground",
)

/**
 * The main modules of the plugins that get no searchable options in any product.
 *
 * A community product reads this set too, so `community/.idea/modules.xml` must register each entry.
 * A module of the ultimate project belongs in `ULTIMATE_PLUGINS_WITHOUT_SEARCHABLE_OPTIONS` instead.
 *
 * @see ProductModulesLayout.pluginModulesWithoutSearchableOptions
 */
val PLUGINS_WITHOUT_SEARCHABLE_OPTIONS: PersistentSet<String> = persistentSetOf()

/**
 * Add the default entries to [ProductModulesLayout.compatiblePluginsToIgnore] and to
 * [ProductModulesLayout.pluginModulesWithoutSearchableOptions].
 *
 * A product calls this function, so each product switches its own behavior. A default value of the property
 * would switch every product at once.
 *
 * A commercial product calls `addCommercialProductDefaults` instead, and that function calls this one.
 *
 * @see COMPATIBLE_PLUGINS_TO_IGNORE
 * @see PLUGINS_WITHOUT_SEARCHABLE_OPTIONS
 */
fun ProductModulesLayout.addProductDefaults() {
  compatiblePluginsToIgnore += COMPATIBLE_PLUGINS_TO_IGNORE
  pluginModulesWithoutSearchableOptions += PLUGINS_WITHOUT_SEARCHABLE_OPTIONS
}

class ProductModulesLayout {
  /**
   * Names of the additional product-specific modules that need to be included in the product's 'lib' directory
   *
   * **Note that these modules will be loaded by the core classloader.**
   *
   * It's better to include them as content modules in a regular or the core plugin instead, this way they'll be loaded by separate classloaders, and you won't need to register
   * them explicitly in the build scripts.
   */
  var productImplementationModules: List<String> = emptyList()

  /**
   * These are the names of the main modules (which contain META-INF/plugin.xml).
   * They belong to the plugins that need to be included with the product.
   * You can find the layouts of these bundled plugins in the [pluginLayouts] list.
   * 
   * This property can be used for writing only. 
   * If you need to read the list of plugins which should be bundled, use [org.jetbrains.intellij.build.BuildContext.getBundledPluginModules] instead.
   */
  var bundledPluginModules: PersistentList<String> = DEFAULT_BUNDLED_PLUGINS

  /**
   * Main module names (containing META-INF/plugin.xml) of the plugins that aren't bundled with the product but may be installed into it.
   * Zip archives of these plugins will be built and placed under [org.jetbrains.intellij.build.BuildContext.nonBundledPlugins] directory in the build artifacts.
   * Layouts of the plugins are specified in [pluginLayouts] list.
   */
  var pluginModulesToPublish: PersistentSet<String> = persistentSetOf()

  /**
   * Whether the searchable options step also indexes the plugins that the product publishes and does not bundle.
   *
   * The step starts the IDE with those plugins, so each of them must load in the product.
   * A product whose publish set still holds a plugin that cannot load keeps this off. The step then indexes the
   * bundled plugins only, and a published plugin gets no searchable options.
   *
   * The `buildNonBundledPlugins` task ignores this flag, because it builds the published plugins and nothing else.
   * That task always indexes them.
   *
   * @see pluginExclusionVariants
   * @see compatiblePluginsToIgnore
   */
  var buildSearchableOptionsForPluginsToPublish: Boolean = false

  /**
   * The plugin ids that each variant of the plugin set leaves out.
   *
   * It is used only if [buildAllCompatiblePlugins] is set to `true`.
   *
   * Some plugins do not load next to each other. One IDE cannot hold them all, so a consumer that loads the plugin set
   * runs once per variant. The searchable options step starts one `traverseUI` run per variant, and the plugin
   * dependency validation checks one plugin set per variant.
   *
   * A variant must name each dependent of a plugin that it leaves out, because a plugin does not load without a
   * required dependency. A variant that keeps such a dependent makes the consumer fail.
   *
   * An empty list means one variant that excludes nothing.
   * A plugin that every variant excludes gets no searchable options and no validation.
   * State the conflict next to each entry, because only a full product build reveals it.
   */
  var pluginExclusionVariants: List<Set<String>> = emptyList()

  /**
   * The main modules of the plugins that get no searchable options.
   *
   * The searchable options step starts the IDE in headless mode. A plugin that needs a runtime which that mode does
   * not give makes the step fail. A backend process is such a runtime.
   * A plugin that requires an entry gets no searchable options either.
   *
   * Add to this set, and do not replace it, because [addProductDefaults] adds the default entries.
   * State the reason next to each entry, because only a full product build reveals it.
   */
  var pluginModulesWithoutSearchableOptions: PersistentSet<String> = persistentSetOf()

  /**
   * Describes the layout of non-trivial plugins which may be included in the product.
   * [bundledPluginModules] specifies the plugins to bundle, and [pluginModulesToPublish] specifies the plugins to publish.
   * The producer runs on the first read of its [Lazy.value]. Assigned producers check for duplicate layouts before they cache their lists.
   */
  var pluginLayouts: Lazy<PersistentList<PluginLayout>> = lazy { getCommunityRepositoryPlugins() }
    set(value) {
      field = lazy {
        val layouts = value.value
        val nameGuard = createPluginLayoutSet(layouts.size)
        for (layout in layouts) {
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
        layouts
      }
    }

  /**
   * Module name to list of Ant-like patterns describing entries which should be excluded from its output.
   * <strong>This is a temporary property added to keep the layout of some products.
   * If some directory from a module shouldn't be included in the product JAR,
   * it's strongly recommended to move that directory outside the module source roots.</strong>
   */
  internal val moduleExcludes: MutableMap<String, MutableList<String>> = LinkedHashMap()

  @ApiStatus.Internal
  fun getModuleExcludesModuleNames(): Set<String> = moduleExcludes.keys

  /**
   * Additional customizations of platform JARs. **This is a temporary property added to keep layout of some products.**
   */
  internal var platformLayoutSpec = persistentListOf<(PlatformLayout) -> Unit>()
    private set

  fun addPlatformSpec(customizer: (PlatformLayout) -> Unit) {
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
   * to allow installing custom plugins directly from the IDE. If [org.jetbrains.intellij.build.ProprietaryBuildTools.artifactsServer] is specified, `__BUILTIN_PLUGINS_URL__` in
   * *ApplicationInfo.xml file will be automatically replaced by the plugin repository URL provided by the artifact server.
   *
   * @see [pluginModulesToPublish]
   */
  var prepareCustomPluginRepositoryForPublishedPlugins: Boolean = true

  /**
   * If `true` then all plugins that compatible with an IDE will be built.
   * Then the plugins matching [BuildContext.pluginAutoPublishList] will be placed to [BuildContext.nonBundledPluginsToBePublished]
   * subdirectory to be uploaded to plugins.jetbrains.com upon a release.
   */
  var buildAllCompatiblePlugins: Boolean = true

  /**
   * The main modules of the plugins that the build must not build, even when the plugin is compatible
   * and [buildAllCompatiblePlugins] is `true`.
   *
   * Add to this list, and do not replace it, because [addProductDefaults] adds the default entries.
   */
  var compatiblePluginsToIgnore: PersistentList<String> = persistentListOf()

  /**
   * If this property is set to `true`, modules registered as optional in `content` tag in `plugin.xml` files which doesn't exist in the JPS project configuration, will be excluded 
   * from the distribution (by default, in such cases build scripts fail with an error).
   * This can be used to build a product from a subset of a source repository. E.g., a plugin from intellij-community may refer to some additional modules located in the ultimate
   * part of the project, and they should be skipped while building from intellij-community sources. 
   */
  @ApiStatus.Internal
  var skipUnresolvedContentModules: Boolean = false

  /**
   * Module names which should be excluded from this product.
   * Allows filtering out default platform modules (both api and implementation) as well as product modules.
   */
  @set:Deprecated("Modules which aren't included in some product must be converted to content modules instead of excluding them in the build scripts")
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
