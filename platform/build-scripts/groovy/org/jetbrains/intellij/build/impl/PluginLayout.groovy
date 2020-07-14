// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.Pair
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.PluginBundlingRestrictions
import org.jetbrains.intellij.build.ResourcesGenerator

import java.util.function.BiFunction

/**
 * Describes layout of a plugin in the product distribution
 */
class PluginLayout extends BaseLayout {
  final String mainModule
  String directoryName
  private boolean doNotCreateSeparateJarForLocalizableResources
  BiFunction<File, String, String> versionEvaluator = { pluginXmlFile, ideVersion -> ideVersion } as BiFunction<File, String, String>
  boolean directoryNameSetExplicitly
  PluginBundlingRestrictions bundlingRestrictions
  Collection<String> pathsToScramble = []
  BiFunction<BuildContext, File, Boolean> scrambleClasspathFilter = { context, file -> return true } as BiFunction<BuildContext, File, Boolean>

  private PluginLayout(String mainModule) {
    this.mainModule = mainModule
  }

  /**
   * Creates the plugin layout description. The default plugin layout is composed of a jar with name {@code mainModuleName}.jar containing
   * production output of {@code mainModuleName} module, resources_en.jar containing translatable resources from {@code mainModuleName},
   * and the module libraries of {@code mainModuleName} with scopes 'Compile' and 'Runtime' placed under 'lib' directory in a directory
   * with name {@code mainModuleName}. In you need to include additional resources or modules into the plugin layout specify them in
   * {@code body} parameter. If you don't need to change the default layout there is no need to call this method at all, it's enough to
   * specify the plugin module in {@link org.jetbrains.intellij.build.ProductModulesLayout#bundledPluginModules bundledPluginModules/pluginModulesToPublish} list.
   *
   * <p>Note that project-level libraries on which the plugin modules depend, are automatically put to 'IDE_HOME/lib' directory for all IDEs
   * which are compatible with the plugin. If this isn't desired (e.g. a library is used in a single plugin only, or if plugins where
   * a library is used aren't bundled with IDEs so we don't want to increase size of the distribution, you may invoke {@link PluginLayoutSpec#withProjectLibrary}
   * to include such a library to the plugin distribution.</p>
   * @param mainModuleName name of the module containing META-INF/plugin.xml file of the plugin
   */
  static PluginLayout plugin(String mainModuleName, @DelegatesTo(PluginLayoutSpec) Closure body = {}) {
    def layout = new PluginLayout(mainModuleName)
    def spec = new PluginLayoutSpec(layout)
    body.delegate = spec
    body()
    layout.directoryName = spec.directoryName
    spec.withModule(mainModuleName, spec.mainJarName)
    if (spec.mainJarNameSetExplicitly) {
      layout.explicitlySetJarPaths.add(spec.mainJarName)
    }
    else {
      layout.explicitlySetJarPaths.remove(spec.mainJarName)
    }
    layout.directoryNameSetExplicitly = spec.directoryNameSetExplicitly
    if (layout.doNotCreateSeparateJarForLocalizableResources) {
      layout.localizableResourcesJars.clear()
    }
    layout.bundlingRestrictions = spec.bundlingRestrictions
    return layout
  }

  @Override
  String toString() {
    return "Plugin '$mainModule'"
  }

  static class PluginLayoutSpec extends BaseLayoutSpec {
    private final PluginLayout layout
    private String directoryName
    private String mainJarName
    private boolean mainJarNameSetExplicitly
    private boolean directoryNameSetExplicitly
    private PluginBundlingRestrictions bundlingRestrictions = new PluginBundlingRestrictions()

    PluginLayoutSpec(PluginLayout layout) {
      super(layout)
      this.layout = layout
      directoryName = convertModuleNameToFileName(layout.mainModule)
      mainJarName = "${convertModuleNameToFileName(layout.mainModule)}.jar"
    }

    /**
     * Custom name of the directory (under 'plugins' directory) where the plugin should be placed. By default the main module name is used
     * (with stripped {@code intellij} prefix and dots replaced by dashes).
     * <strong>Don't set this property for new plugins</strong>; it is temporary added to keep layout of old plugins unchanged.
     */
    void setDirectoryName(String directoryName) {
      this.directoryName = directoryName
      directoryNameSetExplicitly = true
    }

    String getDirectoryName() {
      return directoryName
    }

    /**
     * Custom name of the main plugin JAR file. By default the main module name with 'jar' extension is used (with stripped {@code intellij}
     * prefix and dots replaced by dashes).
     * <strong>Don't set this property for new plugins</strong>; it is temporary added to keep layout of old plugins unchanged.
     */
    void setMainJarName(String mainJarName) {
      this.mainJarName = mainJarName
      mainJarNameSetExplicitly = true
    }

    String getMainJarName() {
      return mainJarName
    }

    /**
     * Returns {@link PluginBundlingRestrictions} instance which can be used to exclude the plugin from some distributions.
     */
    PluginBundlingRestrictions getBundlingRestrictions() {
      return bundlingRestrictions
    }

    /**
     * @param resourcePath path to resource file or directory relative to the plugin's main module content root
     * @param relativeOutputPath target path relative to the plugin root directory
     */
    void withResource(String resourcePath, String relativeOutputPath) {
      withResourceFromModule(layout.mainModule, resourcePath, relativeOutputPath)
    }

    /**
     * @param resourcePath path to resource file or directory relative to {@code moduleName} module content root
     * @param relativeOutputPath target path relative to the plugin root directory
     */
    void withResourceFromModule(String moduleName, String resourcePath, String relativeOutputPath) {
      layout.resourcePaths << new ModuleResourceData(moduleName, resourcePath, relativeOutputPath, false)
    }

    /**
     * @param resourcePath path to resource file or directory relative to the plugin's main module content root
     * @param relativeOutputFile target path relative to the plugin root directory
     */
    void withResourceArchive(String resourcePath, String relativeOutputFile) {
      layout.resourcePaths << new ModuleResourceData(layout.mainModule, resourcePath, relativeOutputFile, true)
    }

    /**
     * Copy output produced by {@code generator} to the directory specified by {@code relativeOutputPath} under the plugin directory.
     */
    void withGeneratedResources(ResourcesGenerator generator, String relativeOutputPath) {
      layout.resourceGenerators << Pair.create(generator, relativeOutputPath)
    }

    /**
     * @deprecated use {@link #withModule} instead
     */
    void withJpsModule(String moduleName) {
      withModule(moduleName, "jps/${moduleName}.jar")
    }

    /**
     * By default, version of a plugin is equal to the build number of the IDE it's built with. This method allows to specify custom version evaluator.
     * In {@linkplain BiFunction}:
     * <ol>
     *   <li> the first {@linkplain File} argument is the plugin.xml file.
     *   <li> the second {@linkplain String} argument is the default version (build number of the IDE).
     * </ol>
     */
    void withCustomVersion(BiFunction<File, String, String> versionEvaluator) {
      layout.versionEvaluator = versionEvaluator
    }

    /**
     * Do not create 'resources_en.jar' and pack all resources into corresponding module JARs.
     * <strong>Do not use this for new plugins, this method is temporary added to keep layout of old plugins</strong>.
     */
    void doNotCreateSeparateJarForLocalizableResources() {
      layout.doNotCreateSeparateJarForLocalizableResources = true
    }

    /**
     * Do not automatically include module libraries from {@code moduleNames}
     * <strong>Do not use this for new plugins, this method is temporary added to keep layout of old plugins</strong>.
     */
    void doNotCopyModuleLibrariesAutomatically(List<String> moduleNames) {
      layout.modulesWithExcludedModuleLibraries.addAll(moduleNames)
    }

    /**
     * Specifies a relative path to a plugin jar that should be scrambled.
     * Scrambling is performed by the {@link org.jetbrains.intellij.build.ProprietaryBuildTools#scrambleTool}
     * If scramble tool is not defined, scrambling will not be performed
     * Multiple invications of this method will add corresponding paths to a list of paths to be scrambled
     *
     * @param relativePath - a path to a jar file relative to plugin root directory
     */
    void scramble(String relativePath) {
      layout.pathsToScramble.add(relativePath)
    }

    /**
     * Allows control over classpath entries that will be used by the scrambler to resolve references from jars being scrambled.
     * By default all platform jars are added to the 'scramble classpath'
     */
    void filterScrambleClasspath(BiFunction<BuildContext, File, Boolean> filter) {
      layout.scrambleClasspathFilter = filter
    }
  }
}