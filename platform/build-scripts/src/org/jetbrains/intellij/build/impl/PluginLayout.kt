// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceNegatedIsEmptyWithIsNotEmpty")

package org.jetbrains.intellij.build.impl

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.PluginBundlingRestrictions
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.io.copyFileToDir
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.function.UnaryOperator

typealias ResourceGenerator = suspend (Path, BuildContext) -> Unit

/**
 * Describes layout of a plugin in the product distribution
 */
class PluginLayout private constructor(val mainModule: String, mainJarNameWithoutExtension: String) : BaseLayout() {
  constructor(mainModule: String) : this(
    mainModule = mainModule,
    mainJarNameWithoutExtension = convertModuleNameToFileName(mainModule),
  )

  private var mainJarName = "$mainJarNameWithoutExtension.jar"

  var directoryName: String = mainJarNameWithoutExtension
    private set

  var versionEvaluator: VersionEvaluator = object : VersionEvaluator {
    override fun evaluate(pluginXml: Path, ideBuildVersion: String, context: BuildContext) = ideBuildVersion
  }

  var pluginXmlPatcher: (String, BuildContext) -> String = { s, _ -> s }
  var directoryNameSetExplicitly: Boolean = false
  var bundlingRestrictions: PluginBundlingRestrictions = PluginBundlingRestrictions.NONE
    internal set

  var pathsToScramble: PersistentList<String> = persistentListOf()
    private set

  var scrambleSkipStatements: PersistentList<Pair<String, String>> = persistentListOf()
    private set

  var scrambleClasspathPlugins: PersistentList<Pair<String /*plugin name*/, String /*relative path*/>> = persistentListOf()
    private set

  var scrambleClasspathFilter: (BuildContext, Path) -> Boolean = { _, _ -> true }

  /**
   * See [org.jetbrains.intellij.build.impl.PluginLayout.PluginLayoutSpec.zkmScriptStub]
   */
  var zkmScriptStub: String? = null
  var pluginCompatibilityExactVersion: Boolean = false
  var retainProductDescriptorForBundledPlugin: Boolean = false
  var enableSymlinksAndExecutableResources: Boolean = false

  internal var resourceGenerators: PersistentList<ResourceGenerator> = persistentListOf()
    private set

  fun getMainJarName(): String = mainJarName

  companion object {
    /**
     * Creates the plugin layout description. The default plugin layout is composed of a jar with name [mainModuleName].jar containing
     * production output of [mainModuleName] module, and the module libraries of [mainModuleName] with scopes 'Compile' and 'Runtime'
     * placed under 'lib' directory in a directory with name [mainModuleName].
     * If you need to include additional resources or modules in the plugin layout, specify them in
     * [body] parameter. If you don't need to change the default layout there is no need to call this method at all, it's enough to
     * specify the plugin module in [org.jetbrains.intellij.build.ProductModulesLayout.bundledPluginModules],
     * [org.jetbrains.intellij.build.ProductModulesLayout.bundledPluginModules],
     * [org.jetbrains.intellij.build.ProductModulesLayout.pluginModulesToPublish] list.
     *
     * <p>Note that project-level libraries on which the plugin modules depend are automatically put to 'IDE_HOME/lib' directory for all IDEs
     * which are compatible with the plugin. If this isn't desired (e.g. a library is used in a single plugin only, or if plugins where
     * a library is used aren't bundled with IDEs, so we don't want to increase the size of the distribution, you may invoke [PluginLayoutSpec.withProjectLibrary]
     * to include such a library to the plugin distribution.</p>
     * @param mainModuleName name of the module containing META-INF/plugin.xml file of the plugin
     */
    @JvmStatic
    fun plugin(mainModuleName: String, body: (PluginLayoutSpec) -> Unit): PluginLayout {
      val layout = PluginLayout(mainModuleName)

      val spec = PluginLayoutSpec(layout)
      body(spec)

      layout.mainJarName = spec.mainJarName
      layout.directoryName = spec.directoryName
      layout.directoryNameSetExplicitly = spec.directoryNameSetExplicitly
      layout.bundlingRestrictions = spec.bundlingRestrictions.build()
      layout.withModule(mainModuleName)

      return layout
    }

    @JvmStatic
    fun plugin(moduleNames: List<String>, body: (SimplePluginLayoutSpec) -> Unit): PluginLayout {
      val layout = PluginLayout(mainModule = moduleNames.first())
      layout.withModules(moduleNames)
      body(SimplePluginLayoutSpec(layout))
      return layout
    }

    @JvmStatic
    fun plugin(moduleNames: List<String>): PluginLayout {
      val layout = PluginLayout(mainModule = moduleNames.first())
      layout.withModules(moduleNames)
      return layout
    }

    @JvmStatic
    fun plugin(mainModule: String): PluginLayout {
      val layout = PluginLayout(mainModule = mainModule)
      layout.withModule(mainModule)
      return layout
    }
  }

  override fun toString() = "Plugin '$mainModule'" + if (bundlingRestrictions != PluginBundlingRestrictions.NONE) ", restrictions: $bundlingRestrictions" else ""

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
     * @param resourcePath path to resource file or directory relative to the plugin's main module content root
     * @param relativeOutputPath target path relative to the plugin root directory
     */
    fun withResource(resourcePath: String, relativeOutputPath: String) {
      layout.withResourceFromModule(layout.mainModule, resourcePath, relativeOutputPath)
    }

    fun withGeneratedResources(generator: ResourceGenerator) {
      layout.resourceGenerators += generator
    }

    /**
     * @param resourcePath path to resource file or directory relative to `moduleName` module content root
     * @param relativeOutputPath target path relative to the plugin root directory
     */
    fun withResourceFromModule(moduleName: String, resourcePath: String, relativeOutputPath: String) {
      layout.withResourceFromModule(moduleName, resourcePath, relativeOutputPath)
    }
  }

  @ApiStatus.Experimental
  class SimplePluginLayoutSpec(layout: PluginLayout) : PluginLayoutBuilder(layout)

  // as a builder for PluginLayout, that ideally should be immutable
  class PluginLayoutSpec(layout: PluginLayout) : PluginLayoutBuilder(layout) {
    var directoryName: String = convertModuleNameToFileName(layout.mainModule)
      /**
       * Custom name of the directory (under 'plugins' directory) where the plugin should be placed. By default, the main module name is used
       * (with stripped `intellij` prefix and dots replaced by dashes).
       * <strong>Don't set this property for new plugins</strong>; it is temporary added to keep the layout of old plugins unchanged.
       */
      set(value) {
        field = value
        directoryNameSetExplicitly = true
      }

    var directoryNameSetExplicitly: Boolean = false
      private set

    val mainModule
      get() = layout.mainModule

    /**
     * Returns [PluginBundlingRestrictions] instance which can be used to exclude the plugin from some distributions.
     */
    val bundlingRestrictions: PluginBundlingRestrictions.Builder = PluginBundlingRestrictions.Builder()

    var mainJarName: String
      get() = layout.mainJarName
      /**
       * Custom name of the main plugin JAR file.
       * By default, the main module name with 'jar' an extension is used (with stripped `intellij`
       * prefix and dots replaced by dashes).
       * <strong>Don't set this property for new plugins</strong>; it is temporary added to keep the layout of old plugins unchanged.
       */
      set(value) {
        layout.mainJarName = value
      }

    /**
     * @param binPathRelativeToCommunity path to resource file or directory relative to the intellij-community repo root
     * @param outputPath target path relative to the plugin root directory
     */
    @JvmOverloads
    fun withBin(binPathRelativeToCommunity: String, outputPath: String, skipIfDoesntExist: Boolean = false) {
      withGeneratedResources { targetDir, context ->
        val source = context.paths.communityHomeDir.resolve(binPathRelativeToCommunity).normalize()
        val attributes = try {
          Files.readAttributes(source, BasicFileAttributes::class.java)
        }
        catch (ignored: FileSystemException) {
          if (skipIfDoesntExist) {
            return@withGeneratedResources
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
    fun withCustomVersion(versionEvaluator: VersionEvaluator) {
      layout.versionEvaluator = versionEvaluator
    }

    fun withPluginXmlPatcher(pluginXmlPatcher: UnaryOperator<String>) {
      layout.pluginXmlPatcher = { text, _ -> pluginXmlPatcher.apply(text) }
    }

    /**
     * This plugin will be compatible only with exactly the same IDE version.
     * See [org.jetbrains.intellij.build.CompatibleBuildRange.EXACT]
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
     * Do not automatically include module libraries from `moduleNames`
     * <strong>Do not use this for new plugins, this method is temporary added to keep layout of old plugins</strong>.
     */
    fun doNotCopyModuleLibrariesAutomatically(moduleNames: List<String>) {
      layout.modulesWithExcludedModuleLibraries.addAll(moduleNames)
    }

    /**
     * Specifies a relative path to a plugin jar that should be scrambled.
     * Scrambling is performed by the [org.jetbrains.intellij.build.ProprietaryBuildTools.scrambleTool]
     * If scramble tool is not defined, scrambling will not be performed
     * Multiple invocations of this method will add corresponding paths to a list of paths to be scrambled
     *
     * @param relativePath - a path to a jar file relative to plugin root directory
     */
    fun scramble(relativePath: String) {
      layout.pathsToScramble = layout.pathsToScramble.add(relativePath)
    }

    /**
     * Specifies a relative to [org.jetbrains.intellij.build.BuildPaths.communityHome] path to a zkm script stub file.
     * If scramble tool is not defined, scramble toot will expect to find the script stub file at "[org.jetbrains.intellij.build.BuildPaths.projectHome]/plugins/`pluginName`/build/script.zkm.stub".
     * Project home cannot be used since it is not constant (for example for Rider).
     *
     * @param communityRelativePath - a path to a jar file relative to community project home directory
     */
    fun zkmScriptStub(communityRelativePath: String) {
      layout.zkmScriptStub = communityRelativePath
    }

    /**
     * Specifies a dependent plugin name to be added to scrambled classpath
     * Scrambling is performed by the [org.jetbrains.intellij.build.ProprietaryBuildTools.scrambleTool]
     * If scramble tool is not defined, scrambling will not be performed
     * Multiple invocations of this method will add corresponding plugin names to a list of name to be added to scramble classpath
     *
     * @param pluginName - a name of dependent plugin, whose jars should be added to scramble classpath
     * @param relativePath - a directory where jars should be searched (relative to plugin home directory, "lib" by default)
     */
    fun scrambleClasspathPlugin(pluginName: String, relativePath: String = "lib") {
      layout.scrambleClasspathPlugins = layout.scrambleClasspathPlugins.add(Pair(pluginName, relativePath))
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
     * @param classFilter - in the following format: `com/mycompany/MyClass.class`
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

        for (moduleName in layout.includedModules.asSequence().filter { it.relativeOutputFile ==  layout.mainJarName }.map { it.moduleName }.distinct()) {
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
          patcher.patchModuleOutput(moduleName = serviceFiles.first().first, // first one wins
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

  interface VersionEvaluator {
    fun evaluate(pluginXml: Path, ideBuildVersion: String, context: BuildContext): String
  }
}

private fun convertModuleNameToFileName(moduleName: String): String = moduleName.removePrefix("intellij.").replace('.', '-')