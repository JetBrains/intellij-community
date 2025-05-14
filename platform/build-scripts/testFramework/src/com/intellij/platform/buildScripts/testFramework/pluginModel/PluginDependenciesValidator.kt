// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.pluginModel

import com.intellij.ide.plugins.DataLoader
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.ModuleLoadingRule
import com.intellij.ide.plugins.PathResolver
import com.intellij.ide.plugins.PluginDescriptorLoadingContext
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginSet
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.ide.plugins.loadPluginSubDescriptors
import com.intellij.platform.ide.bootstrap.ZipFilePoolImpl
import com.intellij.platform.plugins.parser.impl.LoadPathUtil
import com.intellij.platform.plugins.parser.impl.PluginDescriptorBuilder
import com.intellij.platform.plugins.parser.impl.PluginDescriptorFromXmlStreamConsumer
import com.intellij.platform.plugins.parser.impl.RawPluginDescriptor
import com.intellij.platform.plugins.parser.impl.ReadModuleContext
import com.intellij.platform.plugins.parser.impl.XIncludeLoader
import com.intellij.platform.plugins.parser.impl.consume
import com.intellij.platform.plugins.testFramework.PluginSetTestBuilder
import com.intellij.platform.plugins.testFramework.ValidationReadModuleContext
import com.intellij.util.lang.UrlClassLoader
import com.intellij.util.xml.dom.createNonCoalescingXmlStreamReader
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleSourceRoot
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.pathString

class PluginDependenciesValidator private constructor(
  private val tempDir: Path,
  project: JpsProject,
  pluginLayoutProvider: PluginLayoutProvider,
  private val missingDependenciesToIgnore: List<Pair<String, String>>,
  private val pathsIncludedFromLibrariesViaXiInclude: Set<String>,
) {
  companion object {
    fun validatePluginDependencies(
      project: JpsProject,
      pluginLayoutProvider: PluginLayoutProvider,
      tempDir: Path,
      missingDependenciesToIgnore: List<Pair<String, String>>,
      pathsIncludedFromLibrariesViaXiInclude: Set<String>,
    ): List<PluginModuleConfigurationError> {
      val validator = PluginDependenciesValidator(tempDir, project, pluginLayoutProvider, missingDependenciesToIgnore, pathsIncludedFromLibrariesViaXiInclude)
      validator.verifyClassLoaderConfigurations()
      return validator.errors
    }
  }
  
  private val corePluginDescription = pluginLayoutProvider.loadCorePluginLayout()
  private val mainModulesOfBundledPlugins = run {
    val set = LinkedHashSet<String>()
    set.add(corePluginDescription.mainJpsModule)
    set.addAll(pluginLayoutProvider.loadMainModulesOfBundledPlugins())
    set
  }
  private val messageDescribingHowToUpdateLayoutData = pluginLayoutProvider.messageDescribingHowToUpdateLayoutData
  private val moduleNameToPluginLayout: Map<String, PluginLayoutDescription> = project.modules
    .asSequence()
    .mapNotNull {
      if (corePluginDescription.mainJpsModule == it.name) {
        corePluginDescription
      }
      else {
        pluginLayoutProvider.loadPluginLayout(it)
      }
    }
    .associateBy { it.mainJpsModule }

  private val jpsModules = project.modules.associateBy { it.name }
  private val zipPool = ZipFilePoolImpl()
  private val errors = ArrayList<PluginModuleConfigurationError>()

  fun verifyClassLoaderConfigurations() {
    val pluginSet = loadPluginSet()
    checkPluginSet(pluginSet)
  }

  private fun checkPluginSet(pluginSet: PluginSet) {
    val jpsModuleToRuntimeDescriptors = LinkedHashMap<String, MutableList<IdeaPluginDescriptorImpl>>()
    for (descriptor in pluginSet.getEnabledModules()) {
      val jarFiles = descriptor.jarFiles ?: continue
      jarFiles.groupByTo(jpsModuleToRuntimeDescriptors, { 
        getModuleName(it) ?: error("Cannot detect module name for $it in $descriptor")  
      }, { descriptor })
    }
    
    for ((sourceModuleName, sourceDescriptors) in jpsModuleToRuntimeDescriptors) {
      val sourceModule = jpsModules.getValue(sourceModuleName)
      if (sourceModule.getSourceRoots(JavaSourceRootType.SOURCE).toList().isEmpty()) {
        //for now only dependencies used in source code are checked
        continue
      }
      
      val moduleDependenciesAtRuntime =
        sourceDescriptors
          .asSequence()
          .map { it.classLoader }
          .flatMap { sequenceOf(it) + ((it as? PluginClassLoader)?.getAllParentsClassLoaders() ?: emptyArray()).asSequence() }
          .flatMap { (it as UrlClassLoader).files.asSequence() }
          .mapTo(HashSet()) { getModuleName(it) }

      val enumerator = JpsJavaExtensionService.dependencies(sourceModule).satisfying {
        //for now only dependencies used in source code are checked; in the future, we can check dependencies with 'Runtime' scope as well
        JpsJavaExtensionService.getInstance().getDependencyExtension(it)?.scope == JpsJavaDependencyScope.COMPILE 
      }
      enumerator.processModules { targetModule ->
        val targetModuleName = targetModule.name
        if (targetModuleName !in moduleDependenciesAtRuntime && !isIgnoredDependency(sourceModuleName, targetModuleName)) {
          val allExpectedTargets = jpsModuleToRuntimeDescriptors[targetModuleName]
          if (allExpectedTargets == null) {
            //println("Skipping reporting '$jpsModuleName' -> '${dependency.name}' because no runtime descriptors found\n")
            return@processModules
          }
          val expectedTargets = allExpectedTargets.filter { it.moduleName?.contains("/") != true }.takeIf { it.isNotEmpty() } ?: allExpectedTargets
          val sourceDescriptorsString = if (sourceDescriptors.size == 1) {
            "${sourceDescriptors.first().shortPresentation} doesn't have dependency"
          }
          else {
            "none of ${sourceDescriptors.joinToString { it.shortPresentation }} have dependency"
          }
          val expectedTargetsString = if (expectedTargets.size == 1) {
            expectedTargets.first().shortPresentation
          }
          else {
            "any of ${expectedTargets.joinToString { it.shortPresentation }}"
          }

          val fix = suggestFix(sourceModule, sourceDescriptors, targetModule, expectedTargets)

          val errorMessage = """
            |'$sourceModuleName' has compile dependency on '$targetModuleName' in *.iml,
            |but at runtime $sourceDescriptorsString on $expectedTargetsString.
            |This may cause NoClassDefFoundError at runtime.
            |Check if classes from '$sourceModuleName' really use classes from '$targetModuleName' using 'Analyze This Dependency' action in the Project Structure dialog:
            |If no, remove the dependency. 
            |${if (sourceModule.getSourceRoots(JavaSourceRootType.TEST_SOURCE).toList().isNotEmpty()) 
              "If only tests of '$sourceModuleName' use it, use 'Test' scope for the dependency.\n" else ""}
            |If the dependency is really used, ensure that it'll be added at runtime${if (fix == null) "." else ":\n$fix"}
            |
            |$messageDescribingHowToUpdateLayoutData 
            |""".trimMargin()
          errors.add(PluginModuleConfigurationError(moduleName = sourceModuleName, errorMessage = errorMessage))
        }
      }
    }
  }

  private fun suggestFix(sourceModule: JpsModule, sourceDescriptors: List<IdeaPluginDescriptorImpl>, targetModule: JpsModule, targetDescriptors: List<IdeaPluginDescriptorImpl>): String? {
    val source = sourceDescriptors.singleOrNull() ?: return null
    val target = targetDescriptors.singleOrNull() ?: return null

    val dependencyTag = when (target.type) {
      IdeaPluginDescriptorImpl.Type.ContentModuleDescriptor -> "<module name=\"${target.moduleName}\"/>"
      IdeaPluginDescriptorImpl.Type.PluginMainDescriptor -> "<plugin id=\"${target.pluginId.idString}\"/>"
      else -> return null
    }
    val dependenciesTag =
      """
        |<dependencies>
        |  $dependencyTag
        |</dependencies>
      """.trimMargin()
    val extractToContentModule = """
      |'${sourceModule.name}' should be extracted to a content module as described in https://youtrack.jetbrains.com/articles/IJPL-A-956,
      |and the following tag should be added in it:
      |$dependenciesTag
      """.trimMargin()
    return when (source.type) {
      IdeaPluginDescriptorImpl.Type.PluginMainDescriptor -> {
        when (source.pluginId) {
          PluginManagerCore.CORE_ID -> {
            """|since the main module on the core plugin cannot depend on other modules,
               |$extractToContentModule""".trimMargin()
          }
          target.pluginId -> {
            """since the main module of the plugin cannot depend on content modules from the same plugin,
               |$extractToContentModule""".trimMargin()
          }
          else -> buildString {
            append("""add the following tag in plugin.xml for '${source.pluginId.idString}' plugin:
                      |$dependenciesTag""")
            if (target.pluginId != PluginManagerCore.CORE_ID) {
              append(
                """|
                   |If you don't want to have a required dependency on '${target.pluginId.idString}' in '${source.pluginId.idString},
                   |you may extract the part which depends on '${targetModule.name}' to a separate module and register it as a content module
                   |as described in https://youtrack.jetbrains.com/articles/IJPL-A-956""".trimMargin()
              )
            }
          }
        }
      }
      IdeaPluginDescriptorImpl.Type.ContentModuleDescriptor -> {
        "add the following tag in ${source.moduleName}.xml:\n$dependenciesTag"
      }
      IdeaPluginDescriptorImpl.Type.DependsSubDescriptor -> {
        """since files included via <depends> tag cannot declare additional dependencies,
          |$extractToContentModule""".trimMargin()
      }
    }
  }

  private fun isIgnoredDependency(fromModule: String, toModule: String): Boolean {
    fun String.matches(pattern: String): Boolean {
      if (pattern.endsWith("*")) {
        return startsWith(pattern.removeSuffix("*"))
      }
      else {
        return this == pattern
      }
    }
    return missingDependenciesToIgnore.any { (fromPattern, toPattern) -> fromModule.matches(fromPattern) && toModule.matches(toPattern) }
  }

  private val IdeaPluginDescriptorImpl.shortPresentation: String
    get() = when (type) {
      IdeaPluginDescriptorImpl.Type.PluginMainDescriptor -> "main plugin module of '${pluginId}'"
      IdeaPluginDescriptorImpl.Type.ContentModuleDescriptor -> "content module '${moduleName}' of plugin '${pluginId}'"
      IdeaPluginDescriptorImpl.Type.DependsSubDescriptor -> "depends sub descriptor of plugin '${pluginId}'"
    }

  private fun loadPluginSet(): PluginSet {
    val pluginSetBuilder = PluginSetTestBuilder.fromDescriptors { loadingContext -> 
      moduleNameToPluginLayout.values.mapNotNull {
        try {
          createPluginDescriptor(it, loadingContext)
        }
        catch (e: Exception) {
          errors.add(PluginModuleConfigurationError(moduleName = it.mainJpsModule, errorMessage = e.message ?: e.toString(), cause = e))
          null
        } 
      }
    }
      .withCustomCoreLoader(UrlClassLoader.build().files(corePluginDescription.jpsModulesInClasspath.map { getModuleOutputDir(it) }).get())
    
    return pluginSetBuilder.build()
  }

  private fun createPluginDescriptor(pluginLayout: PluginLayoutDescription, loadingContext: PluginDescriptorLoadingContext): IdeaPluginDescriptorImpl {
    val mainModule = jpsModules[pluginLayout.mainJpsModule] ?: error("Cannot find module ${pluginLayout.mainJpsModule}")
    val pluginDir = tempDir.resolve("plugin").resolve(mainModule.name)
    val pluginDescriptorPath = findResourceFile(mainModule, "META-INF/plugin.xml")
    require(pluginDescriptorPath != null) { "Cannot find plugin descriptor file in '${mainModule.name}' module" }
    val xIncludeLoader = PluginMainModuleFromSourceXIncludeLoader(pluginLayout)
    val descriptor = IdeaPluginDescriptorImpl(
      raw = loadRawPluginDescriptor(pluginDescriptorPath, xIncludeLoader),
      pluginPath = pluginDir,
      isBundled = pluginLayout.mainJpsModule in mainModulesOfBundledPlugins,
      useCoreClassLoader = false
    )
    val embeddedContentModules = descriptor.content.modules.filter { it.loadingRule == ModuleLoadingRule.EMBEDDED }.map { it.name }
    val customConfigFileToModule = descriptor.content.modules.mapNotNull { 
      moduleItem -> moduleItem.configFile?.let { it to moduleItem.name.substringBefore('/') } 
    }.toMap()
    val pathResolver = LoadFromSourcePathResolver(pluginLayout, customConfigFileToModule, embeddedContentModules, xIncludeLoader)
    val dataLoader = LoadFromSourceDataLoader(mainPluginModule = mainModule) 
    loadPluginSubDescriptors(descriptor, pathResolver, loadingContext = loadingContext, dataLoader = dataLoader, pluginDir = pluginDir, pool = zipPool)
    descriptor.jarFiles = (pluginLayout.jpsModulesInClasspath + embeddedContentModules).map { getModuleOutputDir(it) }
    return descriptor
  }

  //todo reuse?
  private fun loadRawPluginDescriptor(file: Path, xIncludeLoader: PluginMainModuleFromSourceXIncludeLoader): RawPluginDescriptor {
    val xmlInput = createNonCoalescingXmlStreamReader(file.inputStream(), file.pathString)
    val rawPluginDescriptor = PluginDescriptorFromXmlStreamConsumer(ValidationReadModuleContext, xIncludeLoader).let {
      it.consume(xmlInput)
      it.build()
    }

    return rawPluginDescriptor
  }

  private fun getModuleOutputDir(moduleName: String): Path = tempDir.resolve("module-output").resolve(moduleName)
  private fun getModuleName(outputDir: Path): String? = outputDir.name.takeIf { outputDir.parent.name == "module-output" }

  private inner class LoadFromSourceDataLoader(private val mainPluginModule: JpsModule) : DataLoader {
    override fun load(path: String, pluginDescriptorSourceOnly: Boolean): InputStream {
      TODO("not implemented")
    }

    override fun toString(): String {
      return "LoadFromSourceDataLoader(mainModule=${mainPluginModule.name})"
    }
  }
  
  private inner class PluginMainModuleFromSourceXIncludeLoader(private val layout: PluginLayoutDescription): XIncludeLoader {
    override fun loadXIncludeReference(path: String): XIncludeLoader.LoadedXIncludeReference? {
      if (path in pathsIncludedFromLibrariesViaXiInclude || path.startsWith("META-INF/tips-")) {
        //todo: support loading from libraries
        return XIncludeLoader.LoadedXIncludeReference("<idea-plugin/>".byteInputStream(), "dummy tag for external $path")

      }
      val file = layout.jpsModulesInClasspath
        .asSequence()
        .mapNotNull { jpsModules[it] }
        .flatMap { it.productionSourceRoots }
        .firstNotNullOfOrNull { it.findFile(path) }
      if (file != null) {
        return XIncludeLoader.LoadedXIncludeReference(file.inputStream(), file.pathString)
      }
      return null
    }

    override fun toString(): String {
      return "PluginMainModuleFromSourceXIncludeLoader(plugin=${layout.mainJpsModule})"
    }
  }
  
  private inner class PluginContentModuleFromSourceXIncludeLoader(private val jpsModule: JpsModule,
                                                                  private val parentXIncludeLoader: PluginMainModuleFromSourceXIncludeLoader): XIncludeLoader {
    override fun loadXIncludeReference(path: String): XIncludeLoader.LoadedXIncludeReference? {
      val file = jpsModule.productionSourceRoots.firstNotNullOfOrNull { it.findFile(path) }
      if (file != null) {
        return XIncludeLoader.LoadedXIncludeReference(file.inputStream(), file.pathString)
      }
      return parentXIncludeLoader.loadXIncludeReference(path)
    }

    override fun toString(): String {
      return "PluginContentModuleFromSourceXIncludeLoader(module=${jpsModule.name})"
    }
  }
  
  private inner class LoadFromSourcePathResolver(
    private val layout: PluginLayoutDescription,
    private val customConfigFileToModule: Map<String, String>,
    embeddedContentModules: List<String>,
    private val xIncludeLoader: PluginMainModuleFromSourceXIncludeLoader
  ) : PathResolver {
    
    private val embeddedContentModules = embeddedContentModules.toSet()
    
    override fun loadXIncludeReference(dataLoader: DataLoader, path: String): XIncludeLoader.LoadedXIncludeReference? {
      return xIncludeLoader.loadXIncludeReference(path)
    }

    override fun resolvePath(readContext: ReadModuleContext, dataLoader: DataLoader, relativePath: String): PluginDescriptorBuilder? {
      val path = LoadPathUtil.toLoadPath(relativePath)
      for (pluginModule in layout.jpsModulesInClasspath) {
        val module = jpsModules[pluginModule] ?: continue
        for (root in module.productionSourceRoots) {
          val file = root.findFile(path)
          if (file != null) {
            return PluginDescriptorFromXmlStreamConsumer(readContext, xIncludeLoader).let {
              it.consume(file.inputStream(), null)
              it.getBuilder()
            }
          }
        }
      }
      return null
    }

    override fun resolveModuleFile(readContext: ReadModuleContext, dataLoader: DataLoader, path: String): PluginDescriptorBuilder {
      val jpsModuleName = customConfigFileToModule[path] ?: path.removeSuffix(".xml")
      val jpsModule = jpsModules[jpsModuleName] 
                      ?: error("Cannot find module '$jpsModuleName' referenced in '${layout.mainJpsModule}' plugin")
      val configFilePath = path.replace('/', '.')
      val moduleDescriptor = findResourceFile(jpsModule, configFilePath)
      require(moduleDescriptor != null) { "Cannot find module descriptor '$configFilePath' in '$jpsModuleName' module" }
      val xIncludeLoader = PluginContentModuleFromSourceXIncludeLoader(jpsModule, xIncludeLoader)
      return PluginDescriptorFromXmlStreamConsumer(readContext, xIncludeLoader).let {
        it.consume(moduleDescriptor.inputStream(), null)
        it.getBuilder()
      }
    }

    override fun resolveCustomModuleClassesRoots(moduleName: String): List<Path> {
      if (moduleName in embeddedContentModules) {
        return emptyList()
      }
      return listOf(getModuleOutputDir(moduleName.substringBefore('/')))
    }
  }

  private fun findResourceFile(jpsModule: JpsModule, configFilePath: String): Path? {
    return jpsModule.getSourceRoots().filter { !it.rootType.isForTests }.firstNotNullOfOrNull {
      JpsJavaExtensionService.getInstance().findSourceFile(it, configFilePath)
    }
  }
}

private val JpsModule.productionSourceRoots: Sequence<JpsModuleSourceRoot>
  get() = sourceRoots.asSequence().filter { !it.rootType.isForTests }

private fun JpsModuleSourceRoot.findFile(relativePath: String): Path? {
  return JpsJavaExtensionService.getInstance().findSourceFile(this, relativePath)
}