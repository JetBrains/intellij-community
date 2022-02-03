// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.Pair
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.PluginBundlingRestrictions

import java.nio.file.Files
import java.nio.file.Path
import java.util.function.*

/**
 * Describes layout of a plugin in the product distribution
 */
@CompileStatic
final class PluginLayout extends BaseLayout {
  final String mainModule
  private String mainJarName

  String directoryName
  VersionEvaluator versionEvaluator = { pluginXmlFile, ideVersion, context -> ideVersion } as VersionEvaluator
  UnaryOperator<String> pluginXmlPatcher = UnaryOperator.identity()
  boolean directoryNameSetExplicitly
  PluginBundlingRestrictions bundlingRestrictions
  final List<String> pathsToScramble = new ArrayList<>()
  final Collection<Pair<String /*plugin name*/, String /*relative path*/>> scrambleClasspathPlugins = new ArrayList<>()
  BiPredicate<BuildContext, Path> scrambleClasspathFilter = { context, file -> return true } as BiPredicate<BuildContext, Path>
  /**
   * See {@link org.jetbrains.intellij.build.impl.PluginLayout.PluginLayoutSpec#zkmScriptStub}
   */
  String zkmScriptStub
  Boolean pluginCompatibilityExactVersion = false
  Boolean retainProductDescriptorForBundledPlugin = false

  final List<Pair<BiFunction<Path, BuildContext, Path>, String>> resourceGenerators = new ArrayList<>()
  final List<BiConsumer<ModuleOutputPatcher, BuildContext>> patchers = new ArrayList<>()

  private PluginLayout(@NotNull String mainModule) {
    this.mainModule = mainModule
    mainJarName = "${convertModuleNameToFileName(mainModule)}.jar"
  }

  String getMainJarName() {
    return mainJarName
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
    plugin(mainModuleName, new Consumer<PluginLayoutSpec>() {
      @Override
      void accept(PluginLayoutSpec spec) {
        body.delegate = spec
        body()
      }
    })
  }

  static PluginLayout plugin(@NotNull String mainModuleName, @NotNull Consumer<PluginLayoutSpec> body) {
    if (mainModuleName.isEmpty()) {
      throw new IllegalArgumentException("mainModuleName must be not empty")
    }

    PluginLayout layout = new PluginLayout(mainModuleName)
    PluginLayoutSpec spec = new PluginLayoutSpec(layout)
    body.accept(spec)
    layout.directoryName = spec.directoryName
    if (!layout.getIncludedModuleNames().contains(mainModuleName)) {
      layout.withModule(mainModuleName, layout.mainJarName)
    }
    if (spec.mainJarNameSetExplicitly) {
      layout.explicitlySetJarPaths.add(layout.mainJarName)
    }
    else {
      layout.explicitlySetJarPaths.remove(layout.mainJarName)
    }
    layout.directoryNameSetExplicitly = spec.directoryNameSetExplicitly
    layout.bundlingRestrictions = spec.bundlingRestrictions.build()
    return layout
  }

  static PluginLayout simplePlugin(@NotNull String mainModuleName) {
    if (mainModuleName.isEmpty()) {
      throw new IllegalArgumentException("mainModuleName must be not empty")
    }

    PluginLayout layout = new PluginLayout(mainModuleName)
    layout.directoryName = convertModuleNameToFileName(layout.mainModule)
    layout.withModuleImpl(mainModuleName, layout.mainJarName)
    layout.bundlingRestrictions = PluginBundlingRestrictions.NONE
    return layout
  }

  @Override
  String toString() {
    return "Plugin '$mainModule'"
  }

  @Override
  void withModule(@NotNull String moduleName) {
    if (moduleName.endsWith(".jps") || moduleName.endsWith(".rt")) {
      // must be in a separate JAR
      super.withModule(moduleName)
    }
    else {
      withModuleImpl(moduleName, mainJarName)
    }
  }

  void withGeneratedResources(BiConsumer<Path, BuildContext> generator) {
    resourceGenerators.add(new Pair<>(new BiFunction<Path, BuildContext, Path>() {
      @Override
      Path apply(Path targetDir, BuildContext context) {
        generator.accept(targetDir, context)
        return null
      }
    }, ""))
  }

  @CompileStatic
  static final class PluginLayoutSpec extends BaseLayoutSpec {
    final PluginLayout layout
    private String directoryName
    private boolean mainJarNameSetExplicitly
    private boolean directoryNameSetExplicitly
    private final PluginBundlingRestrictionBuilder bundlingRestrictions = new PluginBundlingRestrictionBuilder()

    @CompileStatic
    final class PluginBundlingRestrictionBuilder {
      /**
       * Change this value if the plugin works in some OS only and therefore don't need to be bundled with distributions for other OS.
       */
      public List<OsFamily> supportedOs = OsFamily.ALL

      /**
       * Set to {@code true} if the plugin should be included in distribution for EAP builds only.
       */
      public boolean includeInEapOnly

      PluginBundlingRestrictions build() {
        if (supportedOs == OsFamily.ALL && !includeInEapOnly) {
          return PluginBundlingRestrictions.NONE
        }
        else {
          return new PluginBundlingRestrictions(supportedOs, includeInEapOnly)
        }
      }
    }


    PluginLayoutSpec(PluginLayout layout) {
      super(layout)
      this.layout = layout
      directoryName = convertModuleNameToFileName(layout.mainModule)
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
      layout.mainJarName = mainJarName
      mainJarNameSetExplicitly = true
    }

    String getMainJarName() {
      return layout.mainJarName
    }

    /**
     * Returns {@link PluginBundlingRestrictions} instance which can be used to exclude the plugin from some distributions.
     */
    PluginBundlingRestrictionBuilder getBundlingRestrictions() {
      return bundlingRestrictions
    }

    /**
     * @param binPathRelativeToCommunity path to resource file or directory relative to the intellij-community repo root
     * @param outputPath target path relative to the plugin root directory
     */
    def withBin(String binPathRelativeToCommunity, String outputPath, boolean skipIfDoesntExist = false) {
      withGeneratedResources(new BiConsumer<Path, BuildContext>() {
        @Override
        void accept(Path targetDir, BuildContext context) {
          Path source = context.paths.communityHomeDir.resolve(binPathRelativeToCommunity).normalize()
          if (Files.notExists(source)) {
            if (skipIfDoesntExist) {
              return
            }
            throw new IllegalStateException("'$source' doesn't exist")
          }

          if (Files.isRegularFile(source)) {
            BuildHelper.copyFileToDir(source, targetDir.resolve(outputPath))
          }
          else {
            BuildHelper.getInstance(context).copyDir(source, targetDir.resolve(outputPath))
          }
        }
      })
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
      layout.resourcePaths.add(new ModuleResourceData(moduleName, resourcePath, relativeOutputPath, false))
    }

    /**
     * @param resourcePath path to resource file or directory relative to the plugin's main module content root
     * @param relativeOutputFile target path relative to the plugin root directory
     */
    void withResourceArchive(String resourcePath, String relativeOutputFile) {
      withResourceArchiveFromModule(layout.mainModule, resourcePath, relativeOutputFile)
    }

    /**
     * @param resourcePath path to resource file or directory relative to {@code moduleName} module content root
     * @param relativeOutputFile target path relative to the plugin root directory
     */
    void withResourceArchiveFromModule(String moduleName, String resourcePath, String relativeOutputFile) {
      layout.resourcePaths.add(new ModuleResourceData(moduleName, resourcePath, relativeOutputFile, true))
    }

    /**
     * Copy output produced by {@code generator} to the directory specified by {@code relativeOutputPath} under the plugin directory.
     */
    @SuppressWarnings(["GrDeprecatedAPIUsage", "UnnecessaryQualifiedReference"])
    void withGeneratedResources(org.jetbrains.intellij.build.ResourcesGenerator generator, String relativeOutputPath) {
      layout.resourceGenerators.add(new Pair<>(new BiFunction<Path, BuildContext, Path>() {
        @Override
        Path apply(Path targetDir, BuildContext context) {
          return generator.generateResources(context)?.toPath()
        }
      }, relativeOutputPath))
    }

    void withPatch(BiConsumer<ModuleOutputPatcher, BuildContext> patcher) {
      layout.patchers.add(patcher)
    }

    void withGeneratedResources(BiConsumer<Path, BuildContext> generator) {
      layout.withGeneratedResources(generator)
    }

    /**
     * By default, version of a plugin is equal to the build number of the IDE it's built with. This method allows to specify custom version evaluator.
     */
    void withCustomVersion(VersionEvaluator versionEvaluator) {
      layout.versionEvaluator = versionEvaluator
    }

    void withPluginXmlPatcher(UnaryOperator<String> pluginXmlPatcher) {
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
     * @param relativePath - a directory where jars should be searched (relative to plugin home directory, "lib" by default)
     */
    void scrambleClasspathPlugin(String pluginName, String relativePath = "lib") {
      layout.scrambleClasspathPlugins.add(new Pair(pluginName, relativePath))
    }

    /**
     * Allows control over classpath entries that will be used by the scrambler to resolve references from jars being scrambled.
     * By default all platform jars are added to the 'scramble classpath'
     */
    void filterScrambleClasspath(BiPredicate<BuildContext, Path> filter) {
      layout.scrambleClasspathFilter = filter
    }
  }

  interface VersionEvaluator {
    String evaluate(Path pluginXml, String ideBuildVersion, BuildContext context)
  }
}