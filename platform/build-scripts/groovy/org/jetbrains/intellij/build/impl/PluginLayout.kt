// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.util.containers.MultiMap
import kotlinx.collections.immutable.PersistentList
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.PluginBundlingRestrictions
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.io.copyFileToDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.*

/**
 * Describes layout of a plugin in the product distribution
 */
class PluginLayout(val mainModule: String): BaseLayout() {
  private var mainJarName = "${convertModuleNameToFileName(mainModule)}.jar"

  lateinit var directoryName: String

  var versionEvaluator: VersionEvaluator = object : VersionEvaluator {
    override fun evaluate(pluginXml: Path, ideBuildVersion: String, context: BuildContext) = ideBuildVersion
  }

  var pluginXmlPatcher: (String, BuildContext) -> String = { s, _ -> s }
  var directoryNameSetExplicitly: Boolean = false
  @JvmField
  internal var bundlingRestrictions: PluginBundlingRestrictions = PluginBundlingRestrictions.NONE
  val pathsToScramble: MutableList<String> = mutableListOf()
  val scrambleClasspathPlugins: MutableList<Pair<String /*plugin name*/, String /*relative path*/>> = mutableListOf()
  var scrambleClasspathFilter: BiPredicate<BuildContext, Path> = BiPredicate { _, _ -> true }

  /**
   * See {@link org.jetbrains.intellij.build.impl.PluginLayout.PluginLayoutSpec#zkmScriptStub}
   */
  var zkmScriptStub: String? = null
  var pluginCompatibilityExactVersion = false
  var retainProductDescriptorForBundledPlugin = false

  internal val resourceGenerators: MutableList<BiFunction<Path, BuildContext, Path?>> = mutableListOf()
  internal val patchers: MutableList<BiConsumer<ModuleOutputPatcher, BuildContext>> = mutableListOf()

  fun getMainJarName() = mainJarName

  companion object {
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
     * a library is used aren't bundled with IDEs, so we don't want to increase size of the distribution, you may invoke {@link PluginLayoutSpec#withProjectLibrary}
     * to include such a library to the plugin distribution.</p>
     * @param mainModuleName name of the module containing META-INF/plugin.xml file of the plugin
     */
    @JvmStatic
    fun plugin(mainModuleName: String, body: Consumer<PluginLayoutSpec>): PluginLayout {
      if (mainModuleName.isEmpty()) {
        error("mainModuleName must be not empty")
      }

      val layout = PluginLayout(mainModuleName)
      val spec = PluginLayoutSpec(layout)
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

    @JvmStatic
    fun simplePlugin(mainModuleName: String): PluginLayout {
      if (mainModuleName.isEmpty()) {
        error("mainModuleName must be not empty")
      }

      val layout = PluginLayout(mainModuleName)
      layout.directoryName = convertModuleNameToFileName(layout.mainModule)
      layout.withModuleImpl(mainModuleName, layout.mainJarName)
      layout.bundlingRestrictions = PluginBundlingRestrictions.NONE
      return layout
    }
  }

  override fun toString() = "Plugin '$mainModule'"

  override fun withModule(moduleName: String) {
    if (moduleName.endsWith(".jps") || moduleName.endsWith(".rt")) {
      // must be in a separate JAR
      super.withModule(moduleName)
    }
    else {
      withModuleImpl(moduleName, mainJarName)
    }
  }

  fun withGeneratedResources(generator: BiConsumer<Path, BuildContext>) {
    resourceGenerators.add(BiFunction<Path, BuildContext, Path?> { targetDir, context ->
          generator.accept(targetDir, context)
          null
        })
  }

  fun mergeServiceFiles() {
    patchers.add(BiConsumer { patcher, context ->
      val discoveredServiceFiles: MultiMap<String, Pair<String, Path>> = MultiMap.createLinkedSet()

      for (moduleName in moduleJars.get(mainJarName)) {
        val path = context.findFileInModuleSources(moduleName, "META-INF/services") ?: continue
        Files.list(path).use { stream ->
          stream
            .filter { Files.isRegularFile(it) }
            .forEach { serviceFile: Path ->
              discoveredServiceFiles.putValue(serviceFile.fileName.toString(), Pair(moduleName, serviceFile))
            }
        }
      }

      discoveredServiceFiles.entrySet().forEach { entry: Map.Entry<String, Collection<Pair<String, Path>>> ->
        val serviceFileName = entry.key
        val serviceFiles: Collection<Pair<String, Path>> = entry.value

        if (serviceFiles.size <= 1) return@forEach
        val content = serviceFiles.joinToString("\n") { Files.readString(it.second) }

        context.messages.info("Merging service file " + serviceFileName + " (" + serviceFiles.joinToString(", ") { it.first } + ")")
        patcher.patchModuleOutput(serviceFiles.first().first, // first one wins
                                  "META-INF/services/$serviceFileName",
                                  content)
      }
    })
    }


  class PluginLayoutSpec(private val layout: PluginLayout): BaseLayoutSpec(layout) {
    var directoryName: String = convertModuleNameToFileName(layout.mainModule)
      /**
       * Custom name of the directory (under 'plugins' directory) where the plugin should be placed. By default, the main module name is used
       * (with stripped {@code intellij} prefix and dots replaced by dashes).
       * <strong>Don't set this property for new plugins</strong>; it is temporary added to keep layout of old plugins unchanged.
       */
      set(value) {
        field = value
        directoryNameSetExplicitly = true
      }

    var mainJarNameSetExplicitly: Boolean = false
      private set
    var directoryNameSetExplicitly: Boolean = false
      private set

    val mainModule
      get() = layout.mainModule

    /**
     * Returns {@link PluginBundlingRestrictions} instance which can be used to exclude the plugin from some distributions.
     */
    val bundlingRestrictions: PluginBundlingRestrictionBuilder = PluginBundlingRestrictionBuilder()

    class PluginBundlingRestrictionBuilder {
      /**
       * Change this value if the plugin works in some OS only and therefore don't need to be bundled with distributions for other OS.
       */
      var supportedOs: PersistentList<OsFamily> = OsFamily.ALL

      /**
       * Change this value if the plugin works on some architectures only and
       * therefore don't need to be bundled with distributions for other architectures.
       */
      var supportedArch: List<JvmArchitecture> = JvmArchitecture.ALL

      /**
       * Set to {@code true} if the plugin should be included in distribution for EAP builds only.
       */
      var includeInEapOnly: Boolean = false

      internal fun build(): PluginBundlingRestrictions {
        return PluginBundlingRestrictions(supportedOs, supportedArch, includeInEapOnly)
      }
    }

    var mainJarName: String
      get() = layout.mainJarName
      /**
       * Custom name of the main plugin JAR file. By default, the main module name with 'jar' extension is used (with stripped {@code intellij}
       * prefix and dots replaced by dashes).
       * <strong>Don't set this property for new plugins</strong>; it is temporary added to keep layout of old plugins unchanged.
       */
      set(value) {
        layout.mainJarName = value
        mainJarNameSetExplicitly = true
      }

    /**
     * @param binPathRelativeToCommunity path to resource file or directory relative to the intellij-community repo root
     * @param outputPath target path relative to the plugin root directory
     */
    @JvmOverloads
    fun withBin(binPathRelativeToCommunity: String, outputPath: String, skipIfDoesntExist: Boolean = false) {
      withGeneratedResources(BiConsumer { targetDir, context ->
        val source = context.paths.communityHomeDir.communityRoot.resolve(binPathRelativeToCommunity).normalize()
        if (Files.notExists(source)) {
          if (skipIfDoesntExist) {
            return@BiConsumer
          }
          error("'$source' doesn't exist")
        }

        if (Files.isRegularFile(source)) {
          copyFileToDir(source, targetDir.resolve(outputPath))
        }
        else {
          copyDir(source, targetDir.resolve(outputPath))
        }
      })
    }

    /**
     * @param resourcePath path to resource file or directory relative to the plugin's main module content root
     * @param relativeOutputPath target path relative to the plugin root directory
     */
    fun withResource(resourcePath: String, relativeOutputPath: String) {
      withResourceFromModule(layout.mainModule, resourcePath, relativeOutputPath)
    }

    /**
     * @param resourcePath path to resource file or directory relative to {@code moduleName} module content root
     * @param relativeOutputPath target path relative to the plugin root directory
     */
    fun withResourceFromModule(moduleName: String, resourcePath: String, relativeOutputPath: String) {
      layout.resourcePaths.add(ModuleResourceData(moduleName = moduleName,
                                                  resourcePath = resourcePath,
                                                  relativeOutputPath = relativeOutputPath,
                                                  packToZip = false))
    }

    /**
     * @param resourcePath path to resource file or directory relative to the plugin's main module content root
     * @param relativeOutputFile target path relative to the plugin root directory
     */
    fun withResourceArchive(resourcePath: String, relativeOutputFile: String) {
      withResourceArchiveFromModule(layout.mainModule, resourcePath, relativeOutputFile)
    }

    /**
     * @param resourcePath path to resource file or directory relative to {@code moduleName} module content root
     * @param relativeOutputFile target path relative to the plugin root directory
     */
    fun withResourceArchiveFromModule(moduleName: String, resourcePath: String, relativeOutputFile: String) {
      layout.resourcePaths.add(ModuleResourceData(moduleName, resourcePath, relativeOutputFile, true))
    }

    fun withPatch(patcher: BiConsumer<ModuleOutputPatcher, BuildContext> ) {
      layout.patchers.add(patcher)
    }

    fun withGeneratedResources(generator: BiConsumer<Path, BuildContext>) {
      layout.withGeneratedResources(generator)
    }

    /**
     * By default, version of a plugin is equal to the build number of the IDE it's built with. This method allows to specify custom version evaluator.
     */
    fun withCustomVersion(versionEvaluator: VersionEvaluator) {
      layout.versionEvaluator = versionEvaluator
    }

    fun withPluginXmlPatcher(pluginXmlPatcher: UnaryOperator<String>) {
      layout.pluginXmlPatcher = { text, _ -> pluginXmlPatcher.apply(text) }
    }

    @Deprecated(message = "localizable resources are always put to the module JAR, so there is no need to call this method anymore")
    fun doNotCreateSeparateJarForLocalizableResources() {
    }

    /**
     * This plugin will be compatible only with exactly the same IDE version.
     * See {@link org.jetbrains.intellij.build.CompatibleBuildRange#EXACT}
     */
    fun pluginCompatibilityExactVersion() {
      layout.pluginCompatibilityExactVersion = true
    }

    /**
     * <product-description> is usually removed for bundled plugins.
     * Call this method to retain it in plugin.xml
     */
    fun retainProductDescriptorForBundledPlugin() {
      layout.retainProductDescriptorForBundledPlugin = true
    }

    /**
     * Do not automatically include module libraries from {@code moduleNames}
     * <strong>Do not use this for new plugins, this method is temporary added to keep layout of old plugins</strong>.
     */
    fun doNotCopyModuleLibrariesAutomatically(moduleNames: List<String>) {
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
    fun scramble(relativePath: String) {
      layout.pathsToScramble.add(relativePath)
    }

    /**
     * Specifies a relative to {@link org.jetbrains.intellij.build.BuildPaths#communityHome} path to a zkm script stub file.
     * If scramble tool is not defined, scramble toot will expect to find the script stub file at "{@link org.jetbrains.intellij.build.BuildPaths#projectHome}/plugins/{@code pluginName}/build/script.zkm.stub".
     * Project home cannot be used since it is not constant (for example for Rider).
     *
     * @param communityRelativePath - a path to a jar file relative to community project home directory
     */
    fun zkmScriptStub(communityRelativePath: String) {
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
    @JvmOverloads
    fun scrambleClasspathPlugin(pluginName: String, relativePath: String = "lib") {
      layout.scrambleClasspathPlugins.add(Pair(pluginName, relativePath))
    }

    /**
     * Allows control over classpath entries that will be used by the scrambler to resolve references from jars being scrambled.
     * By default, all platform jars are added to the 'scramble classpath'
     */
    fun filterScrambleClasspath(filter: BiPredicate<BuildContext, Path>) {
      layout.scrambleClasspathFilter = filter
    }

    /**
     * Concatenates `META-INF/services` files with the same name from different modules together.
     * By default, the first service file silently wins.
     */
    fun mergeServiceFiles() {
      layout.mergeServiceFiles()
    }
  }

  interface VersionEvaluator {
    fun evaluate(pluginXml: Path, ideBuildVersion: String, context: BuildContext): String
  }
}