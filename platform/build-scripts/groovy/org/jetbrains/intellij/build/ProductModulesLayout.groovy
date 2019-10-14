// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import com.intellij.openapi.util.MultiValuesMap
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.impl.DistributionJARsBuilder
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.PluginLayout

import java.util.function.Consumer
import java.util.function.Function

/**
 * @author nik
 */
@CompileStatic
class ProductModulesLayout {
  /**
   * Name of the main product JAR file. Outputs of {@link #productImplementationModules} will be packed into it.
   */
  String mainJarName

  /**
   * Names of the modules which need to be packed into openapi.jar in the product's 'lib' directory.
   * @see CommunityRepositoryModules#PLATFORM_API_MODULES
   * @deprecated if you need to pack additional modules into the product, use {@link #productApiModules} instead; {@link CommunityRepositoryModules#PLATFORM_API_MODULES}
   * will be packed into platform-api.jar in the product's 'lib' directory automatically then.
   */
  List<String> platformApiModules = []

  /**
   * Names of the modules which need to be included into {@link #mainJarName} in the product's 'lib' directory
   * @see CommunityRepositoryModules#PLATFORM_IMPLEMENTATION_MODULES
   * @deprecated if you need to pack additional modules into the product, use {@link #productImplementationModules} instead; {@link CommunityRepositoryModules#PLATFORM_IMPLEMENTATION_MODULES}
   * will be packed into platform-impl.jar in the product's 'lib' directory automatically then.   */
  List<String> platformImplementationModules = []

  /**
   * Names of the additional product-specific modules which need to be packed into openapi.jar in the product's 'lib' directory.
   */
  List<String> productApiModules = []

  /**
   * Names of the additional product-specific modules which need to be included into {@link #mainJarName} in the product's 'lib' directory
   */
  List<String> productImplementationModules = []

  /**
   * Names of the main modules (containing META-INF/plugin.xml) of the plugins which need to be bundled with the product. It may also
   * includes names of optional modules (added via {@link org.jetbrains.intellij.build.impl.PluginLayout.PluginLayoutSpec#withOptionalModule})
   * from these plugins which need to be included into the plugin distribution for this product. Layouts of the bundled plugins are specified
   * in {@link #allNonTrivialPlugins} list.
   */
  List<String> bundledPluginModules = []

  /**
   * Names of the main modules of the plugins which need to be bundled in windows distribution of the product.
   *
   * @deprecated specify supported OS in {@link PluginBundlingRestrictions#supportedOs bundlingRestrictions.supportedOs} property in
   * {@link org.jetbrains.intellij.build.impl.PluginLayout.PluginLayoutSpec#bundlingRestrictions the plugin layout} and add the plugin to
   * the standard {@link #bundledPluginModules} instead
   */
  final Map<OsFamily, List<String>> bundledOsPluginModules = [:]

  Set<String> getAllBundledPluginsModules() {
    return (bundledOsPluginModules.values().flatten() as Set<String>) + bundledPluginModules
  }

  private LinkedHashMap<String, Function<PluginLayout, PluginPublishingSpec>> pluginsToPublish = []

  /**
   * Names of the main modules (containing META-INF/plugin.xml) of the plugins which aren't bundled with the product but may be installed
   * into it. Zip archives of these plugins will be built and placed under "&lt;product-code&gt;-plugins" directory in the build artifacts.
   * Layouts of the plugins are specified in {@link #allNonTrivialPlugins} list.
   * 
   * @see #setPluginPublishingSpec
   */
  void setPluginModulesToPublish(List<String> plugins) {
    pluginsToPublish = new LinkedHashMap<>()
    for (String each : plugins) {
      pluginsToPublish[each] = { PluginLayout layout -> layout.defaultPublishingSpec ?: new PluginPublishingSpec()} as Function<PluginLayout, PluginPublishingSpec>
    }
  }

  /**
   * @see #setPluginModulesToPublish 
   */
  List<String> getPluginModulesToPublish() {
    return pluginsToPublish.keySet().toList()
  }

  /**
   * Specifies that a plugin with main module name {@code mainModule} should be prepared for publishing under 'plugins' directory in the build
   * artifacts. The passed {@code spec} overrides {@link org.jetbrains.intellij.build.impl.PluginLayout.PluginLayoutSpec#setDefaultPublishingSpec the plugin's defaults}.
   * Layouts of the plugin is specified in {@link #allNonTrivialPlugins} list.
   * @see #setPluginModulesToPublish
   */
  void setPluginPublishingSpec(String mainModule, PluginPublishingSpec spec) {
    pluginsToPublish[mainModule] = {spec} as Function<PluginLayout, PluginPublishingSpec>
  }

  /**
   * @see #setPluginPublishingSpec
   * @see #setPluginModulesToPublish 
   */
  PluginPublishingSpec getPluginPublishingSpec(PluginLayout pluginLayout) {
    return pluginsToPublish[pluginLayout.mainModule]?.apply(pluginLayout)
  }

  /**
   * Describes non-trivial layout of all plugins which may be included into the product. The actual list of the plugins need to be bundled
   * with the product is specified by {@link #bundledPluginModules}, the actual list of plugins which need to be prepared for publishing
   * is specified by {@link #setPluginModulesToPublish pluginModulesToPublish}. There is no need to specify layout for plugins where it's trivial,
   * i.e. for plugins which include an output of a single module and its module libraries, it's enough to specify module names of such plugins
   * in {@link #bundledPluginModules} and {@link #setPluginModulesToPublish pluginModulesToPublish}.
   */
  List<PluginLayout> allNonTrivialPlugins = CommunityRepositoryModules.COMMUNITY_REPOSITORY_PLUGINS

  /**
   * Names of the project libraries which JARs' contents should be extracted into {@link #mainJarName} JAR.
   */
  List<String> projectLibrariesToUnpackIntoMainJar = []

  /**
   * Maps names of JARs to names of the modules; these modules will be packed into these JARs and copied to the product's 'lib' directory.
   */
  MultiValuesMap<String, String> additionalPlatformJars = new MultiValuesMap<>(true)

  /**
   * Module name to list of Ant-like patterns describing entries which should be excluded from its output.
   * <strong>This is a temporary property added to keep layout of some products. If some directory from a module shouldn't be included into the
   * product JAR it's strongly recommended to move that directory outside of the module source roots.</strong>
   */
  MultiValuesMap<String, String> moduleExcludes = new MultiValuesMap<>(true)

  /**
   * Additional customizations of platform JARs. <strong>This is a temporary property added to keep layout of some products.</strong>
   */
  Consumer<PlatformLayout> platformLayoutCustomizer = {} as Consumer<PlatformLayout>

  /**
   * Names of the modules which classpath will be used to build searchable options index <br>
   * //todo[nik] get rid of this property and automatically include all platform and plugin modules to the classpath when building searchable options index
   */
  List<String> mainModules = []

  /**
   * Name of the module containing search/searchableOptions.xml file.
   * @deprecated not used anymore as searchable options are split between modules
   */
  @Deprecated
  String searchableOptionsModule = ""

  /**
   * If {@code true} a special xml descriptor in custom plugin repository format will be generated for {@link #setPluginModulesToPublish} plugins.
   * This descriptor and the plugin *.zip files can be uploaded to the URL specified in 'plugins@builtin-url' attribute in *ApplicationInfo.xml file
   * to allow installing custom plugins directly from IDE. If {@link ProprietaryBuildTools#artifactsServer} is specified, {@code __BUILTIN_PLUGINS_URL__} in
   * *ApplicationInfo.xml file will be automatically replaced by the plugin repository URL provided by the artifact server.
   *
   * @see #setPluginModulesToPublish
   * @see #setPluginPublishingSpec
   * @see org.jetbrains.intellij.build.PluginPublishingSpec#includeInCustomPluginRepository
   */
  boolean prepareCustomPluginRepositoryForPublishedPlugins = true
  

  /**
   * @deprecated use {@link #setPluginPublishingSpec} instead 
   */
  @Deprecated
  List<String> getPluginModulesWithRestrictedCompatibleBuildRange() {
    def error = "`ProductModulesLayout.pluginModulesWithRestrictedCompatibleBuildRange` property has been replaced with `ProductModulesLayout.setPluginPublishingSpec`"
    System.err.println(error)
    throw new UnsupportedOperationException(error)
  }

  /**
   * @deprecated use {@link #setPluginPublishingSpec} instead 
   */
  @Deprecated
  void setPluginModulesWithRestrictedCompatibleBuildRange(List<String> __) {
    //noinspection GrDeprecatedAPIUsage
    getPluginModulesWithRestrictedCompatibleBuildRange() // to rethrow
  }

  /**
   * If {@code true} then all plugins that compatible with an IDE will be built. By default these plugins will be placed to "auto-uploading"
   * subdirectory and may be automatically uploaded to plugins.jetbrains.com; use {@link org.jetbrains.intellij.build.impl.PluginLayout.PluginLayoutSpec#setDefaultPublishingSpec}
   * or {@link #setPluginPublishingSpec} to override this behavior for specific plugins if needed.
   * <br>
   * If {@code false} only plugins from {@link #setPluginModulesToPublish} will be considered.
   * 
   * @see #setPluginPublishingSpec
   */
  boolean buildAllCompatiblePlugins = false

  /**
   * List of plugin names which should not be built even if they are compatible and {@link #buildAllCompatiblePlugins} is true
   */
  List<String> compatiblePluginsToIgnore = []

  /**
   * @deprecated we generate the order file automatically based on the application startup statistics
   * 
   * Specifies path to a text file containing list of classes in order they are loaded by the product. Entries in the produces *.jar files
   * will be reordered accordingly to reduct IDE startup time. If {@code null} no reordering will be performed.
   */
  @Deprecated
  String classesLoadingOrderFilePath = null

  /**
   * @return list of all modules which output is included into the plugin's JARs
   */
  List<String> getIncludedPluginModules(Set<String> enabledPluginModules) {
    def modulesFromNonTrivialPlugins = allNonTrivialPlugins.findAll { enabledPluginModules.contains(it.mainModule) }.
      collectMany { it.getActualModules(enabledPluginModules).values() }
    (enabledPluginModules + modulesFromNonTrivialPlugins) as List<String>
  }

  /**
   * @deprecated this method isn't supposed to be used in product build scripts
   */
  List<String> getIncludedPlatformModules() {
    DistributionJARsBuilder.getIncludedPlatformModules(this)
  }
}