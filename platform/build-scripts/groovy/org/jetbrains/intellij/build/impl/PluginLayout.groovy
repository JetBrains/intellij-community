// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.Pair
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.PluginBundlingRestrictions
import org.jetbrains.intellij.build.ResourcesGenerator

import java.nio.file.Path
import java.util.function.BiFunction
import java.util.function.BiPredicate
import java.util.function.Consumer

/**
 * Describes layout of a plugin in the product distribution
 */
@CompileStatic
final class PluginLayout extends BaseLayout {
  final String mainModule
  String directoryName
  VersionEvaluator versionEvaluator = { pluginXmlFile, ideVersion, context -> ideVersion } as VersionEvaluator
  Consumer<Path> pluginXmlPatcher = { } as Consumer<Path>
  List<Pair<String, ResourcesGenerator>> moduleOutputPatches = []
  boolean directoryNameSetExplicitly
  PluginBundlingRestrictions bundlingRestrictions
  final List<String> pathsToScramble = new ArrayList<>()
  Collection<String> scrambleClasspathPlugins = []
  BiPredicate<BuildContext, File> scrambleClasspathFilter = { context, file -> return true } as BiPredicate<BuildContext, File>
  /**
   * See {@link org.jetbrains.intellij.build.impl.PluginLayout.PluginLayoutSpec#zkmScriptStub}
   */
  String zkmScriptStub
  Boolean pluginCompatibilityExactVersion = false
  Boolean retainProductDescriptorForBundledPlugin = false

  private PluginLayout(String mainModule) {
    this.mainModule = mainModule
  }

  /**
   * Creates the plugin layout description. The default plugin layout is composed of a jar with name {@code mainModuleName}.jar containing
   * production output of {@code mainModuleName} module, and the module libraries of {@code mainModuleName} with scopes 'Compile' and 'Runtime'
   * placed under 'lib' directory in a directory with name {@code mainModuleName}.
   * If you need to include additional resources or modules into the plugin layout specify them in
   * {@code body} parameter. If you don't need to change the default layout there is no need to call this method at all, it's enough to
   * specify the plugin module in {@link org.jetbrains.intellij.build.ProductModulesLayout#bundledPluginModules bundledPluginModules/pluginModulesToPublish} list.
   *
   * <p>Note that project-level libraries on which the plugin modules depend, are automatically put to 'IDE_HOME/lib' directory for all IDEs
   * which are compatible with the plugin. If this isn't desired (e.g. a library is used in a single plugin only, or if plugins where
   * a library is used aren't bundled with IDEs so we don't want to increase size of the distribution, you may invoke {@link PluginLayoutSpec#withProjectLibrary}
   * to include such a library to the plugin distribution.</p>
   * @param mainModuleName name of the module containing META-INF/plugin.xml file of the plugin
   */
  static PluginLayout plugin(@NotNull String mainModuleName, @DelegatesTo(PluginLayoutSpec) Closure body = {}) {
    if (mainModuleName.isEmpty()) {
      throw new IllegalArgumentException("mainModuleName must be not empty")
    }

    PluginLayout layout = new PluginLayout(mainModuleName)
    PluginLayoutSpec spec = new PluginLayoutSpec(layout)
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
    layout.bundlingRestrictions = spec.bundlingRestrictions
    return layout
  }

  @Override
  String toString() {
    return "Plugin '$mainModule'"
  }

  static final class PluginLayoutSpec extends BaseLayoutSpec {
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

    @Override
    void withModule(String moduleName) {
      if (moduleName.endsWith(".jps") || moduleName.endsWith(".rt")) {
        // must be in a separate JAR
        super.withModule(moduleName)
      }
      else {
        layout.moduleJars.putValue(mainJarName, moduleName)
      }
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
     * @param binPathRelativeToCommunity path to resource file or directory relative to the intellij-community repo root
     * @param outputPath target path relative to the plugin root directory
     */
    def withBin(String binPathRelativeToCommunity, String outputPath, boolean skipIfDoesntExist = false) {
      withGeneratedResources(new ResourcesGenerator() {
        @Override
        File generateResources(BuildContext context) {
          def file = context.paths.communityHomeDir.resolve(binPathRelativeToCommunity).toFile()
          if (!skipIfDoesntExist && !file.exists()) {
            throw new IllegalStateException("'$file' doesn't exist")
          }
          return file.exists() ? file : null
        }
      }, outputPath)
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
     * Patches module output with content produced {@code generator}
     */
    void withModuleOutputPatches(String moduleName, ResourcesGenerator generator) {
      layout.moduleOutputPatches.add(Pair.create(moduleName, generator))
    }

    /**
     * By default, version of a plugin is equal to the build number of the IDE it's built with. This method allows to specify custom version evaluator.
     */
    void withCustomVersion(VersionEvaluator versionEvaluator) {
      layout.versionEvaluator = versionEvaluator
    }

    void withPluginXmlPatcher(Consumer<Path> pluginXmlPatcher) {
      layout.pluginXmlPatcher = pluginXmlPatcher
    }

    /**
     * @deprecated localizable resources are always put to the module JAR, so there is no need to call this method anymore
     */
    @Deprecated
    void doNotCreateSeparateJarForLocalizableResources() {
    }

    /**
     * This plugin will be compatible only with exactly the same IDE version.
     * See {@link org.jetbrains.intellij.build.CompatibleBuildRange#EXACT}
     */
    void pluginCompatibilityExactVersion() {
      layout.pluginCompatibilityExactVersion = true
    }

    /**
     * <product-description> is usually removed for bundled plugins.
     * Call this method to retain it in plugin.xml
     */
    void retainProductDescriptorForBundledPlugin() {
      layout.retainProductDescriptorForBundledPlugin = true
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
     * Multiple invocations of this method will add corresponding paths to a list of paths to be scrambled
     *
     * @param relativePath - a path to a jar file relative to plugin root directory
     */
    void scramble(String relativePath) {
      layout.pathsToScramble.add(relativePath)
    }

    /**
     * Specifies a relative to {@link org.jetbrains.intellij.build.BuildPaths#communityHome} path to a zkm script stub file.
     * If scramble tool is not defined, scramble toot will expect to find the script stub file at "{@link org.jetbrains.intellij.build.BuildPaths#projectHome}/plugins/{@code pluginName}/build/script.zkm.stub".
     * Project home cannot be used since it is not constant (for example for Rider).
     *
     * @param communityRelativePath - a path to a jar file relative to community project home directory
     */
    void zkmScriptStub(String communityRelativePath) {
      layout.zkmScriptStub = communityRelativePath
    }

    /**
     * Specifies a dependent plugin name to be added to scrambled classpath
     * Scrambling is performed by the {@link org.jetbrains.intellij.build.ProprietaryBuildTools#scrambleTool}
     * If scramble tool is not defined, scrambling will not be performed
     * Multiple invocations of this method will add corresponding plugin names to a list of name to be added to scramble classpath
     *
     * @param pluginName - a name of dependent plugin, whose jars should be added to scramble classpath
     */
    void scrambleClasspathPlugin(String pluginName) {
      layout.scrambleClasspathPlugins.add(pluginName)
    }

    /**
     * Allows control over classpath entries that will be used by the scrambler to resolve references from jars being scrambled.
     * By default all platform jars are added to the 'scramble classpath'
     */
    void filterScrambleClasspath(BiPredicate<BuildContext, File> filter) {
      layout.scrambleClasspathFilter = filter
    }
  }

  interface VersionEvaluator {
    String evaluate(Path pluginXml, String ideBuildVersion, BuildContext context)
  }
}