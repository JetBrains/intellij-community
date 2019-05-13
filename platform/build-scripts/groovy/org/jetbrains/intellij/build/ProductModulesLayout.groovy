// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import com.intellij.openapi.util.MultiValuesMap
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.impl.DistributionJARsBuilder
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.PluginLayout

import java.util.function.Consumer

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
   * from these plugins which need to be included into the plugin distribution for this product.
   */
  List<String> bundledPluginModules = []

  /**
   * Names of the main modules of the plugins which need to be bundled in windows distribution of the product.
   */
  final Map<OsFamily, List<String>> bundledOsPluginModules = [:]

  Set<String> getAllBundledPluginsModules() {
    return (bundledOsPluginModules.values().flatten() as Set<String>) + bundledPluginModules
  }

  private LinkedHashMap<String, PluginPublishingSpec> pluginsToPublish = []

  /**
   * Names of the main modules (containing META-INF/plugin.xml) of the plugins which aren't bundled with the product but may be installed
   * into it. Zip archives of these plugins will be built and placed under 'plugins' directory in the build artifacts.
   * 
   * @see #setPluginPublishingSpec
   */
  void setPluginModulesToPublish(List<String> plugins) {
    pluginsToPublish = new LinkedHashMap<>()
    for (String each : plugins) {
      pluginsToPublish[each] = new PluginPublishingSpec()
    }
  }

  /**
   * @see #setPluginModulesToPublish 
   */
  List<String> getPluginModulesToPublish() {
    return pluginsToPublish.keySet().toList()
  }

  /**
   * Specification ({@link PluginPublishingSpec}) for the published plugin. 
   * @see #setPluginModulesToPublish
   */
  void setPluginPublishingSpec(String mainModule, PluginPublishingSpec spec) {
    pluginsToPublish[mainModule] = spec
  }

  /**
   * @see #setPluginPublishingSpec
   * @see #setPluginModulesToPublish 
   */
  PluginPublishingSpec getPluginPublishingSpec(String mainModule) {
    return pluginsToPublish[mainModule]
  }

  /**
   * Describes non-trivial layout of all plugins which may be included into the product. The actual list of the plugins need to be bundled
   * with the product is specified by {@link #bundledPluginModules}. There is no need to specify layout for plugins where it's trivial,
   * i.e. for plugins which include an output of a single module and its module libraries, it's enough to specify module names of such plugins
   * in {@link #bundledPluginModules}.
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
   * This descriptor and the plugin *.zip files need to be uploaded to the URL specified in 'plugins@builtin-url' attribute in *ApplicationInfo.xml file.
   *
   * @see #setPluginModulesToPublish
   * @see #setPluginPublishingSpec
   * @see org.jetbrains.intellij.build.PluginPublishingSpec#includeInCustomPluginRepository
   */
  boolean prepareCustomPluginRepositoryForPublishedPlugins = false
  

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
   * subdirectory and may be automatically uploaded to plugins.jetbrains.com; use {@link #setPluginPublishingSpec} to override this behavior
   * for specific plugins if needed.
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
   * Specifies path to a text file containing list of classes in order they are loaded by the product. Entries in the produces *.jar files
   * will be reordered accordingly to reduct IDE startup time. If {@code null} no reordering will be performed.
   */
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