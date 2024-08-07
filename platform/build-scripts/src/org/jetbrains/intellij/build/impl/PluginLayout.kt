// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.collections.immutable.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.Obsolete
import org.jetbrains.annotations.TestOnly
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.io.copyFileToDir
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

typealias ResourceGenerator = suspend (Path, BuildContext) -> Unit

/**
 * Describes layout of a plugin in the product distribution
 */
class PluginLayout private constructor(
  val mainModule: String,
  mainJarNameWithoutExtension: String,
  @Internal @JvmField val auto: Boolean = false,
) : BaseLayout() {
  constructor(mainModule: String, auto: Boolean = false) : this(
    mainModule = mainModule,
    mainJarNameWithoutExtension = convertModuleNameToFileName(mainModule),
    auto = auto,
  )

  private var mainJarName = "$mainJarNameWithoutExtension.jar"

  /** module name to name of the library */
  @JvmField
  internal val excludedLibraries: MutableMap<String?, MutableList<String>> = HashMap()

  internal fun excludeProjectLibrary(libraryName: String) {
    excludedLibraries.computeIfAbsent(null) { ArrayList() }.add(libraryName)
  }

  @TestOnly
  fun isLibraryExcluded(name: String): Boolean = excludedLibraries.get(null)?.contains(name) ?: false

  var directoryName: String = mainJarNameWithoutExtension
    private set

  var versionEvaluator: PluginVersionEvaluator = PLUGIN_VERSION_AS_IDE

  @Internal
  @JvmField
  var rawPluginXmlPatcher: (String, BuildContext) -> String = { s, _ -> s }

  var pluginXmlPatcher: (String, BuildContext) -> String = { s, _ -> s }

  var directoryNameSetExplicitly: Boolean = false
  var bundlingRestrictions: PluginBundlingRestrictions = PluginBundlingRestrictions.NONE
    internal set

  var pathsToScramble: PersistentList<String> = persistentListOf()
    private set

  var scrambleSkipStatements: PersistentList<Pair<String, String>> = persistentListOf()
    private set

  data class ScrambleClasspathPluginEntry(@JvmField val pluginMainModuleName: String, @JvmField val relativePath: String?)

  var scrambleClasspathPlugins: PersistentList<ScrambleClasspathPluginEntry> = persistentListOf()
    private set

  var scrambleClasspathFilter: (BuildContext, Path) -> Boolean = { _, _ -> true }

  /**
   * See [org.jetbrains.intellij.build.impl.PluginLayout.PluginLayoutSpec.zkmScriptStub]
   */
  var zkmScriptStub: String? = null
  var pluginCompatibilityExactVersion: Boolean = false
  var pluginCompatibilitySameRelease: Boolean = false
  var retainProductDescriptorForBundledPlugin: Boolean = false
  var enableSymlinksAndExecutableResources: Boolean = false

  @JvmField
  internal var modulesWithExcludedModuleLibraries: Set<String> = persistentSetOf()

  internal var resourceGenerators: PersistentList<ResourceGenerator> = persistentListOf()
    private set

  internal var customAssets: PersistentList<CustomAssetDescriptor> = persistentListOf()
    private set

  internal var platformResourceGenerators: PersistentMap<SupportedDistribution, PersistentList<ResourceGenerator>> = persistentMapOf()
    private set

  fun getMainJarName(): String = mainJarName

  companion object {
    /**
     * Creates the plugin layout description.
     * The default plugin layout is composed of a jar with name [mainModuleName].jar containing
     * production output of [mainModuleName] module, and the module libraries of [mainModuleName] with scopes 'Compile' and 'Runtime'
     * placed under 'lib' directory in a directory with name [mainModuleName].
     * If you need to include additional resources or modules in the plugin layout, specify them in the [body] parameter.
     * If you don't need to change the default layout, there is no need to call this method at all;
     * it's enough to specify the plugin module in [org.jetbrains.intellij.build.ProductModulesLayout.bundledPluginModules],
     * [org.jetbrains.intellij.build.ProductModulesLayout.bundledPluginModules],
     * [org.jetbrains.intellij.build.ProductModulesLayout.pluginModulesToPublish] list.
     *
     * Note that project-level libraries on which the plugin modules depend are automatically put to 'IDE_HOME/lib' directory
     * for all IDEs that are compatible with the plugin.
     * If this isn't desired (e.g., a library is used in a single plugin only or isn't bundled with IDEs to reduce the distribution size),
     * you may invoke [PluginLayoutSpec.withProjectLibrary] to include such a library to the plugin distribution.
     *
     * @param mainModuleName name of the module containing META-INF/plugin.xml file of the plugin
     */
    @JvmStatic
    @Deprecated("Please use pluginAuto")
    fun plugin(mainModuleName: String, auto: Boolean = false, body: (PluginLayoutSpec) -> Unit): PluginLayout {
      val layout = PluginLayout(mainModuleName, auto = auto)

      val spec = PluginLayoutSpec(layout)
      body(spec)

      layout.mainJarName = spec.mainJarName
      layout.directoryName = spec.directoryName
      layout.directoryNameSetExplicitly = spec.directoryNameSetExplicitly
      layout.bundlingRestrictions = spec.bundlingRestrictions.build()
      layout.withModule(mainModuleName)

      return layout
    }

    // we cannot break compatibility / risk to change existing plugin dir name
    fun pluginAutoWithDeprecatedCustomDirName(mainModuleName: String, body: (PluginLayoutSpec) -> Unit): PluginLayout {
      @Suppress("DEPRECATION")
      return plugin(mainModuleName, auto = true, body = body)
    }

    fun pluginAuto(moduleName: String, body: (SimplePluginLayoutSpec) -> Unit): PluginLayout = pluginAuto(listOf(moduleName), body)

    fun pluginAuto(moduleNames: List<String>, body: (SimplePluginLayoutSpec) -> Unit): PluginLayout {
      val layout = PluginLayout(mainModule = moduleNames.first(), auto = true)
      layout.withModules(moduleNames)
      val spec = SimplePluginLayoutSpec(layout)
      body(spec)
      layout.bundlingRestrictions = spec.bundlingRestrictions.build()
      return layout
    }

    @Deprecated("Please use pluginAuto")
    fun plugin(moduleNames: List<String>): PluginLayout {
      val layout = PluginLayout(mainModule = moduleNames.first())
      layout.withModules(moduleNames)
      return layout
    }

    /**
     * Project-level library is included in the plugin by default, if not yet included in the platform.
     * Direct main module dependencies in the same module group are included automatically.
     */
    fun pluginAuto(moduleNames: List<String>): PluginLayout {
      val layout = PluginLayout(mainModule = moduleNames.first(), auto = true)
      layout.withModules(moduleNames)
      return layout
    }

    fun plugin(mainModule: String): PluginLayout {
      val layout = PluginLayout(mainModule = mainModule)
      layout.withModule(mainModule)
      return layout
    }
  }

  override fun toString(): String {
    return "Plugin '$mainModule'" +
           if (bundlingRestrictions == PluginBundlingRestrictions.NONE) "" else ", restrictions: $bundlingRestrictions"
  }

  override fun withModule(moduleName: String) {
    if (moduleName.endsWith(".jps") || moduleName.endsWith(".rt")) {
      // must be in a separate JAR
      withModule(moduleName, "${convertModuleNameToFileName(moduleName)}.jar")
    }
    else {
      withModule(moduleName, mainJarName)
    }
  }

  sealed class PluginLayoutBuilder(@JvmField protected val layout: PluginLayout) : BaseLayoutSpec(layout) {
    /**
     * Returns [PluginBundlingRestrictions] instance which can be used to exclude the plugin from some distributions.
     */
    val bundlingRestrictions: PluginBundlingRestrictions.Builder = PluginBundlingRestrictions.Builder()

    fun excludeModuleLibrary(libraryName: String, moduleName: String) {
      layout.excludedLibraries.computeIfAbsent(moduleName) { ArrayList() }.add(libraryName)
    }

    fun excludeProjectLibrary(libraryName: String) {
      layout.excludeProjectLibrary(libraryName)
    }

    /**
     * @param resourcePath path to resource file or directory relative to the plugin's main module content root
     * @param relativeOutputPath target path relative to the plugin root directory
     */
    fun withResource(resourcePath: String, relativeOutputPath: String) {
      layout.withResourceFromModule(moduleName = layout.mainModule, resourcePath = resourcePath, relativeOutputPath = relativeOutputPath)
    }

    fun withGeneratedResources(generator: ResourceGenerator) {
      layout.resourceGenerators += generator
    }

    fun withCustomAsset(lazySourceSupplier: (context: BuildContext) -> LazySource?) {
      layout.customAssets += object : CustomAssetDescriptor {
        override suspend fun getSources(context: BuildContext): Sequence<Source>? {
          return sequenceOf(lazySourceSupplier(context) ?: return null)
        }
      }
    }

    fun withGeneratedPlatformResources(os: OsFamily, arch: JvmArchitecture, generator: ResourceGenerator) {
      val key = SupportedDistribution(os, arch)
      val newValue = layout.platformResourceGenerators.get(key)?.let { it + generator } ?: persistentListOf(generator)
      layout.platformResourceGenerators += key to newValue
    }

    /**
     * @param resourcePath path to resource file or directory relative to `moduleName` module content root
     * @param relativeOutputPath target path relative to the plugin root directory
     */
    fun withResourceFromModule(moduleName: String, resourcePath: String, relativeOutputPath: String) {
      layout.withResourceFromModule(moduleName = moduleName, resourcePath = resourcePath, relativeOutputPath = relativeOutputPath)
    }

    fun withRawPluginXmlPatcher(pluginXmlPatcher: (String, BuildContext) -> String) {
      layout.rawPluginXmlPatcher = pluginXmlPatcher
    }
  }

  class SimplePluginLayoutSpec internal constructor(layout: PluginLayout) : PluginLayoutBuilder(layout)

  // as a builder for PluginLayout, that ideally should be immutable
  class PluginLayoutSpec(layout: PluginLayout) : PluginLayoutBuilder(layout) {
    var directoryName: String = convertModuleNameToFileName(layout.mainModule)
      /**
       * Custom name of the directory (under 'plugins' directory) where the plugin should be placed. By default, the main module name is used
       * (with stripped `intellij` prefix and dots replaced by dashes).
       * **Don't set this property for new plugins**; it is temporarily added to keep the layout of old plugins unchanged.
       */
      set(value) {
        field = value
        directoryNameSetExplicitly = true
      }

    var directoryNameSetExplicitly: Boolean = false
      private set

    val mainModule
      get() = layout.mainModule

    var mainJarName: String
      get() = layout.mainJarName
      /**
       * Custom name of the main plugin JAR file.
       * By default, the main module name with 'jar' an extension is used (with stripped `intellij`
       * prefix and dots replaced by dashes).
       * **Don't set this property for new plugins**; it is temporarily added to keep the layout of old plugins unchanged.
       */
      set(value) {
        layout.mainJarName = value
      }

    /**
     * @param binPathRelativeToCommunity path to resource file or directory relative to the intellij-community repo root
     * @param outputPath target path relative to the plugin root directory
     */
    fun withBin(binPathRelativeToCommunity: String, outputPath: String, skipIfDoesntExist: Boolean = false) {
      withGeneratedResources { targetDir, context ->
        copyBinaryResource(binPathRelativeToCommunity, outputPath, skipIfDoesntExist, targetDir, context)
      }
    }

    fun withPlatformBin(os: OsFamily, arch: JvmArchitecture, binPathRelativeToCommunity: String, outputPath: String, skipIfDoesntExist: Boolean = false) {
      withGeneratedPlatformResources(os, arch) { targetDir, context ->
        copyBinaryResource(binPathRelativeToCommunity, outputPath, skipIfDoesntExist, targetDir, context)
      }
    }

    private fun copyBinaryResource(
      binPathRelativeToCommunity: String,
      outputPath: String,
      skipIfDoesntExist: Boolean,
      targetDir: Path,
      context: BuildContext
    ) {
      val source = context.paths.communityHomeDir.resolve(binPathRelativeToCommunity).normalize()
      val attributes = try {
        Files.readAttributes(source, BasicFileAttributes::class.java)
      }
      catch (_: FileSystemException) {
        if (skipIfDoesntExist) {
          return
        }
        error("$source doesn't exist")
      }

      if (attributes.isRegularFile) {
        copyFileToDir(source, targetDir.resolve(outputPath))
      }
      else {
        copyDir(source, targetDir.resolve(outputPath))
      }
    }

    /**
     * @param resourcePath path to resource file or directory relative to the plugin's main module content root
     * @param relativeOutputFile target path relative to the plugin root directory
     */
    fun withResourceArchive(resourcePath: String, relativeOutputFile: String) {
      withResourceArchiveFromModule(layout.mainModule, resourcePath, relativeOutputFile)
    }

    /**
     * @param resourcePath path to resource file or directory relative to `moduleName` module content root
     * @param relativeOutputFile target path relative to the plugin root directory
     */
    fun withResourceArchiveFromModule(moduleName: String, resourcePath: String, relativeOutputFile: String) {
      layout.resourcePaths = layout.resourcePaths.add(ModuleResourceData(moduleName = moduleName,
                                                                         resourcePath = resourcePath,
                                                                         relativeOutputPath = relativeOutputFile,
                                                                         packToZip = true))
    }

    /**
     * By default, a version of a plugin is equal to the build number of the IDE it's built with.
     * This method allows specifying custom version evaluator.
     */
    fun withCustomVersion(versionEvaluator: PluginVersionEvaluator) {
      layout.versionEvaluator = versionEvaluator
    }

    /**
     * This plugin will be compatible only with exactly the same IDE version.
     * See [org.jetbrains.intellij.build.CompatibleBuildRange.EXACT]
     */
    fun pluginCompatibilityExactVersion() {
      layout.pluginCompatibilityExactVersion = true
    }

    /**
     * This plugin will be compatible with IDE versions with the same two digits of the build number.
     * See [org.jetbrains.intellij.build.CompatibleBuildRange.RESTRICTED_TO_SAME_RELEASE]
     */
    fun pluginCompatibilitySameRelease() {
      layout.pluginCompatibilitySameRelease = true
    }

    /**
     * `<product-description>` is usually removed for bundled plugins.
     * Call this method to retain it in plugin.xml
     */
    fun retainProductDescriptorForBundledPlugin() {
      layout.retainProductDescriptorForBundledPlugin = true
    }

    /**
     * Do not automatically include module libraries from `moduleNames`
     * **Don't set this property for new plugins**; it is temporarily added to keep the layout of old plugins unchanged.
     */
    @Obsolete
    fun doNotCopyModuleLibrariesAutomatically(moduleNames: List<String>) {
      layout.modulesWithExcludedModuleLibraries = layout.modulesWithExcludedModuleLibraries.toPersistentSet().addAll(moduleNames)
    }

    /**
     * Specifies a relative path to a plugin jar that should be scrambled.
     * Scrambling is performed by the [org.jetbrains.intellij.build.ProprietaryBuildTools.scrambleTool]
     * If scramble tool is not defined, scrambling will not be performed
     * Multiple invocations of this method will add corresponding paths to a list of paths to be scrambled
     *
     * @param relativePath a path to a .jar file relative to the plugin root directory
     */
    fun scramble(relativePath: String) {
      layout.pathsToScramble = layout.pathsToScramble.add(relativePath)
    }

    /**
     * Specifies a relative to [org.jetbrains.intellij.build.BuildPaths.communityHomeDir] path to a zkm script stub file.
     * If scramble tool is not defined, scramble toot will expect to find the script stub file at "[org.jetbrains.intellij.build.BuildPaths.projectHome]/plugins/`pluginName`/build/script.zkm.stub".
     * Project home cannot be used since it is not constant (for example, for Rider).
     *
     * @param communityRelativePath - a path to a jar file relative to community project home directory
     */
    fun zkmScriptStub(communityRelativePath: String) {
      layout.zkmScriptStub = communityRelativePath
    }

    /**
     * Specifies a dependent plugin name to be added to the scrambled classpath
     * Scrambling is performed by the [org.jetbrains.intellij.build.ProprietaryBuildTools.scrambleTool]
     * If scramble tool is not defined, scrambling will not be performed
     * Multiple invocations of this method will add corresponding plugin names to a list of name to be added to scramble classpath
     *
     * @param pluginMainModuleName - a name of the dependent plugin's directory, whose jars should be added to scramble classpath
     * @param relativePath - a directory where jars should be searched (relative to plugin home directory, "lib" by default)
     */
    fun scrambleClasspathPlugin(pluginMainModuleName: String) {
      layout.scrambleClasspathPlugins = layout.scrambleClasspathPlugins.add(ScrambleClasspathPluginEntry(pluginMainModuleName = pluginMainModuleName, relativePath = null))
    }

    fun scrambleClasspathPlugin(pluginId: String, relativePath: String) {
      layout.scrambleClasspathPlugins = layout.scrambleClasspathPlugins.add(ScrambleClasspathPluginEntry(pluginMainModuleName = pluginId, relativePath = relativePath))
    }

    /**
     * Allows control over classpath entries that will be used by the scrambler to resolve references from jars being scrambled.
     * By default, all platform jars are added to the 'scramble classpath'
     */
    fun filterScrambleClasspath(filter: (BuildContext, Path) -> Boolean) {
      layout.scrambleClasspathFilter = filter
    }

    /**
     * Adds a "skip" element to the open statement. See: [Open Statement documentation](https://www.zelix.com/klassmaster/docs/openStatement.html)
     *
     * Note: zkm open statement for the jar must be declared.
     *
     * @param jar - name of the jar file
     * @param classFilter - in the following format: `com/acme/MyClass.class`
     */
    fun scrambleSkip(jar: String, classFilter: String) {
      layout.scrambleSkipStatements += Pair(jar, classFilter)
    }

    /**
     * Concatenates `META-INF/services` files with the same name from different modules together.
     * By default, the first service file silently wins.
     */
    fun mergeServiceFiles() {
      withPatch { patcher, context ->
        val discoveredServiceFiles = LinkedHashMap<String, LinkedHashSet<Pair<String, Path>>>()

        for (moduleName in layout.includedModules.asSequence().filter { it.relativeOutputFile == layout.mainJarName }.map { it.moduleName }.distinct()) {
          val path = context.findFileInModuleSources(moduleName, "META-INF/services") ?: continue
          Files.newDirectoryStream(path).use { dirStream ->
            dirStream
              .asSequence()
              .filter { Files.isRegularFile(it) }
              .forEach { serviceFile ->
                discoveredServiceFiles.computeIfAbsent(serviceFile.fileName.toString()) { LinkedHashSet() }
                  .add(Pair(moduleName, serviceFile))
              }
          }
        }

        for ((serviceFileName, serviceFiles) in discoveredServiceFiles) {
          if (serviceFiles.size <= 1) {
            continue
          }

          val content = serviceFiles.joinToString(separator = "\n") { Files.readString(it.second) }
          Span.current().addEvent("merge service file)", Attributes.of(
            AttributeKey.stringKey("serviceFile"), serviceFileName,
            AttributeKey.stringArrayKey("serviceFiles"), serviceFiles.map { it.first },
          ))
          patcher.patchModuleOutput(moduleName = serviceFiles.first().first, // the first one wins
                                    path = "META-INF/services/$serviceFileName",
                                    content = content)
        }
      }
    }

    /**
     * Enables support for symlinks and files with a posix executable bit set, such as required by macOS.
     */
    fun enableSymlinksAndExecutableResources() {
      layout.enableSymlinksAndExecutableResources = true
    }
  }
}

private val PLUGIN_VERSION_AS_IDE = PluginVersionEvaluator { _, ideBuildVersion, _ -> PluginVersionEvaluatorResult(pluginVersion = ideBuildVersion) }

data class PluginVersionEvaluatorResult(@JvmField val pluginVersion: String, @JvmField val sinceUntil: Pair<String, String>? = null)

/**
 * Think twice before using this API.
 */
fun interface PluginVersionEvaluator {
  fun evaluate(pluginXmlSupplier: () -> String, ideBuildVersion: String, context: BuildContext): PluginVersionEvaluatorResult
}

private fun convertModuleNameToFileName(moduleName: String): String = moduleName.removePrefix("intellij.").replace('.', '-')
