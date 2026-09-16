// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.platform.buildScripts.concurrency.TaskFailedException
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.ApplicationInfoProperties
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.classPath.DescriptorSearchScope
import org.jetbrains.intellij.build.classPath.XIncludeElementResolverImpl
import org.jetbrains.intellij.build.classPath.descriptorResolveContext
import org.jetbrains.intellij.build.classPath.getEmbeddedContentModulesOfPluginsWithUseIdeaClassloader
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.JpsJavaLibraryType
import org.jetbrains.jps.model.java.JpsJavaModuleType
import org.jetbrains.jps.model.module.JpsModule
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PluginsCollectorTest {
  @TempDir
  lateinit var tempDirectory: Path

  @Test
  fun `platform source derivation resolves includes without executing resources or patches`(): Unit = runBlocking {
    val project = JpsElementFactory.getInstance().createModel().project
    val properties = platformProperties(project, """
      <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
        <xi:include href="/META-INF/platform.xml"/>
      </idea-plugin>
    """.trimIndent())
    val includes = addSourceModule(project, "demo.includes", "META-INF/platform.xml", """
      <idea-plugin><content>
        <module name="demo.product.embedded" loading="embedded"/>
        <module name="demo.product.regular"/>
      </content></idea-plugin>
    """.trimIndent())
    project.findModuleByName(properties.applicationInfoModule)!!.dependenciesList.addModuleDependency(includes)
    properties.ijentDistributionRegistrar = { error("Source derivation must not register IJent files.") }
    properties.productLayout.addPlatformSpec { layout ->
      layout.withProductModuleOutputFile("demo.product.embedded", "custom.jar")
      layout.withResourceFromModule(properties.applicationInfoModule, "missing-resource", "resource")
      layout.withPatch { _, _ -> error("Source derivation must not run a module patch.") }
    }

    val layout = createPlatformLayout(properties, platformOutputProvider(project))

    assertThat(layout.includedModules.single { it.moduleName == "demo.product.embedded" }.relativeOutputFile).isEqualTo("custom.jar")
    assertThat(layout.includedModules.single { it.moduleName == "demo.product.regular" }.reason).isEqualTo(ModuleIncludeReasons.PRODUCT_MODULES)
    assertThat(layout.patchers).hasSize(2)
    assertThat(layout.resourcePaths).hasSize(1)
    assertThat(layout.descriptorCacheContainer.forPlatform(layout).getCachedFileData(PRODUCT_DESCRIPTOR_META_PATH)!!.decodeToString())
      .contains("demo.product.embedded", "demo.product.regular")
      .doesNotContain("xi:include", "CDATA")
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `implicit platform validation uses sources for either loader`(useModularLoader: Boolean): Unit = runBlocking {
    val project = JpsElementFactory.getInstance().createModel().project
    val properties = platformProperties(project)
    val dependency = project.addModule("demo.implicit", JpsJavaModuleType.INSTANCE)
    val dependencyElement = project.findModuleByName(properties.applicationInfoModule)!!.dependenciesList.addModuleDependency(dependency)
    JpsJavaExtensionService.getInstance().getOrCreateDependencyExtension(dependencyElement).scope = JpsJavaDependencyScope.COMPILE

    val layout = createPlatformLayout(
      productProperties = properties,
      outputProvider = platformOutputProvider(project),
      validateImplicitPlatformModule = true,
      useModularLoader = useModularLoader,
    )

    assertThat(layout.includedModules.map { it.moduleName }).contains(dependency.name)
  }

  @ParameterizedTest
  @ValueSource(strings = ["META-INF/plugin.xml", "demo.implicit.xml"])
  fun `source validation rejects implicit plugin and content modules`(descriptorPath: String) {
    val project = JpsElementFactory.getInstance().createModel().project
    val properties = platformProperties(project)
    val dependency = addSourceModule(project, "demo.implicit", descriptorPath, "<idea-plugin/>")
    val dependencyElement = project.findModuleByName(properties.applicationInfoModule)!!.dependenciesList.addModuleDependency(dependency)
    JpsJavaExtensionService.getInstance().getOrCreateDependencyExtension(dependencyElement).scope = JpsJavaDependencyScope.COMPILE

    assertThatThrownBy {
      runBlocking {
        createPlatformLayout(
          productProperties = properties,
          outputProvider = platformOutputProvider(project),
          validateImplicitPlatformModule = true,
        )
      }
    }
      .isInstanceOf(TaskFailedException::class.java)
      .hasRootCauseInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("Module ${dependency.name}")
      .hasMessageContaining(if (descriptorPath == "META-INF/plugin.xml") "it is a plugin" else "is a content module")
  }

  @Test
  fun `source layout honors an explicit embedded frontend override`(): Unit = runBlocking {
    val project = JpsElementFactory.getInstance().createModel().project
    val properties = platformProperties(project).apply { embeddedFrontendRootModule = "demo.frontend" }
    val outputProvider = platformOutputProvider(project)

    val enabled = createPlatformLayout(properties, outputProvider)
    val disabled = createPlatformLayout(properties, outputProvider, isEmbeddedFrontendEnabled = false)

    assertThat(enabled.includedModules.single { it.moduleName == "intellij.platform.starter" }.relativeOutputFile)
      .isEqualTo("ext/platform-main.jar")
    assertThat(disabled.includedModules.map { it.moduleName }).doesNotContain("intellij.platform.starter")
  }

  @Test
  fun `source descriptor search rejects a missing include without reading output`() {
    val project = JpsElementFactory.getInstance().createModel().project
    val module = project.addModule("demo.product", JpsJavaModuleType.INSTANCE)
    val platform = PlatformLayout()
    val resolver = XIncludeElementResolverImpl(
      searchPath = listOf(DescriptorSearchScope(listOf(module.name), platform.descriptorCacheContainer.forPlatform(platform))),
      context = descriptorResolveContext(SourceOnlyCollectorOutputProvider(project), "CollectorProductProperties", sourceOnly = true),
    )

    assertThatThrownBy { resolver.resolveElement("/META-INF/missing.xml", isOptional = false, isDynamic = false) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("Cannot resolve 'META-INF/missing.xml'")
  }

  @ParameterizedTest
  @ValueSource(strings = ["scope", "dependency", "global"])
  fun `source descriptors take precedence over library descriptors`(sourceLocation: String) {
    val project = JpsElementFactory.getInstance().createModel().project
    val descriptorPath = "META-INF/wizard-template-impl.xml"
    val module = addSourceModule(project, "demo.product", "META-INF/plugin.xml", "<idea-plugin/>")
    val sourceModule = when (sourceLocation) {
      "scope" -> module
      else -> addSourceModule(project, "demo.source", "META-INF/plugin.xml", "<idea-plugin/>")
    }
    writeSourceFile(sourceModule, descriptorPath, "<idea-plugin><id>source.descriptor</id></idea-plugin>")
    if (sourceLocation == "dependency") {
      module.dependenciesList.addModuleDependency(sourceModule)
    }
    val libraryJar = addDescriptorLibrary(module, descriptorPath)
    val libraryReads = ArrayList<String>()
    val platform = PlatformLayout()
    val resolver = XIncludeElementResolverImpl(
      searchPath = listOf(DescriptorSearchScope(listOf(module.name), platform.descriptorCacheContainer.forPlatform(platform))),
      context = descriptorResolveContext(
        libraryOutputProvider(project, libraryJar, libraryReads), "CollectorProductProperties", sourceOnly = true,
      ),
    )

    val descriptor = resolver.resolveElement("/$descriptorPath", isOptional = false, isDynamic = false)

    assertThat(descriptor!!.getChildText("id")).isEqualTo("source.descriptor")
    assertThat(libraryReads).isEmpty()
  }

  @Test
  fun `source descriptor search uses declared libraries without reading module output`() {
    val project = JpsElementFactory.getInstance().createModel().project
    val descriptorPath = "META-INF/wizard-template-impl.xml"
    val module = project.addModule("demo.product", JpsJavaModuleType.INSTANCE)
    val libraryJar = addDescriptorLibrary(module, descriptorPath)
    val libraryReads = ArrayList<String>()
    val platform = PlatformLayout()
    val resolver = XIncludeElementResolverImpl(
      searchPath = listOf(DescriptorSearchScope(listOf(module.name), platform.descriptorCacheContainer.forPlatform(platform))),
      context = descriptorResolveContext(
        libraryOutputProvider(project, libraryJar, libraryReads), "CollectorProductProperties", sourceOnly = true,
      ),
    )

    val descriptor = resolver.resolveElement("/$descriptorPath", isOptional = false, isDynamic = false)
    val cachedDescriptor = resolver.resolveElement("/$descriptorPath", isOptional = false, isDynamic = false)

    assertThat(descriptor!!.getChildText("id")).isEqualTo("library.descriptor")
    assertThat(cachedDescriptor!!.getChildText("id")).isEqualTo("library.descriptor")
    assertThat(libraryReads).containsExactly("demo.descriptors")
    assertThatThrownBy { resolver.resolveElement("/META-INF/missing.xml", isOptional = false, isDynamic = false) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("Cannot resolve 'META-INF/missing.xml'")
    assertThat(libraryReads).containsExactly("demo.descriptors", "demo.descriptors")
  }

  @Test
  fun `runtime descriptor search retains its compiled output fallback`() {
    val project = JpsElementFactory.getInstance().createModel().project
    val module = project.addModule("demo.product", JpsJavaModuleType.INSTANCE)
    val reads = ArrayList<String>()
    val outputProvider = object : ModuleOutputProvider by SourceOnlyCollectorOutputProvider(project) {
      override fun readFileContentFromModuleOutput(module: JpsModule, relativePath: String, forTests: Boolean): ByteArray? {
        reads.add(relativePath)
        return "<idea-plugin><id>compiled.descriptor</id></idea-plugin>".toByteArray()
      }
    }
    val platform = PlatformLayout()
    val resolver = XIncludeElementResolverImpl(
      searchPath = listOf(DescriptorSearchScope(listOf(module.name), platform.descriptorCacheContainer.forPlatform(platform))),
      context = descriptorResolveContext(outputProvider, "CollectorProductProperties"),
    )

    val descriptor = resolver.resolveElement("/META-INF/missing.xml", isOptional = false, isDynamic = false)

    assertThat(descriptor!!.getChildText("id")).isEqualTo("compiled.descriptor")
    assertThat(reads).containsExactly("META-INF/missing.xml")
  }

  @Test
  fun `source classpath selection reads only the source plugin descriptor`(): Unit = runBlocking {
    val project = JpsElementFactory.getInstance().createModel().project
    val module = addSourceModule(project, "demo.plugin", "META-INF/plugin.xml", """
      <idea-plugin use-idea-classloader="true"><content>
        <module name="demo.plugin.embedded" loading="embedded"/>
        <module name="demo.plugin.regular"/>
      </content></idea-plugin>
    """.trimIndent())

    val modules = getEmbeddedContentModulesOfPluginsWithUseIdeaClassloader(
      pluginMainModule = module.name,
      cacheContainer = null,
      outputProvider = SourceOnlyCollectorOutputProvider(project),
      contentModuleFilter = IncludeAllContentModuleFilter,
      sourceOnly = true,
    )

    assertThat(modules).containsExactly(module.name, "demo.plugin.embedded")
  }

  @Test
  fun `compatible plugins use source includes and bundled content aliases`() {
    runBlocking {
      val project = JpsElementFactory.getInstance().createModel().project
      addSourceModule(project, "demo.product.content", "demo.product.content.xml", "<idea-plugin><module value=\"demo.content.alias\"/></idea-plugin>")
      addSourceModule(
        project, "demo.bundled", "META-INF/plugin.xml",
        "<idea-plugin implementation-detail=\"true\"><id>demo.bundled.id</id><module value=\"demo.bundled.alias\"/></idea-plugin>",
      )
      val candidate = addSourceModule(
        project, "demo.candidate", "META-INF/plugin.xml",
        "<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\"><id>demo.candidate.id</id><xi:include href=\"/META-INF/dependencies.xml\"/></idea-plugin>",
      )
      writeSourceFile(candidate, "META-INF/dependencies.xml", """
        <idea-plugin>
          <depends>demo.product.alias</depends>
          <depends>demo.content.alias</depends>
          <depends>demo.bundled.alias</depends>
        </idea-plugin>
      """.trimIndent())
      addSourceModule(
        project, "demo.incompatible", "META-INF/plugin.xml",
        "<idea-plugin><id>demo.incompatible.id</id><depends>missing.id</depends></idea-plugin>",
      )
      val properties = CollectorProductProperties().apply {
        productLayout.bundledPluginModules = persistentListOf("demo.bundled")
      }
      val platform = PlatformLayout()
      platform.descriptorCacheContainer.forPlatform(platform).put(PRODUCT_DESCRIPTOR_META_PATH, """
        <idea-plugin>
          <module value="demo.product.alias"/>
          <content><module name="demo.product.content"/></content>
        </idea-plugin>
      """.trimIndent().toByteArray())
      val published = LinkedHashSet<PluginLayout>()

      collectCompatiblePluginsToPublish(
        pluginsToPublish = published,
        platformLayout = platform,
        productProperties = properties,
        outputProvider = SourceOnlyCollectorOutputProvider(project),
      )

      assertThat(published.map { it.mainModule }).containsExactly("demo.candidate")
      assertThat(properties.productLayout.pluginModulesToPublish).isEmpty()
    }
  }

  @Test
  fun `source compatibility excludes ignored plugins and their dependents`() {
    runBlocking {
      val project = JpsElementFactory.getInstance().createModel().project
      addSourceModule(project, "demo.ignored", "META-INF/plugin.xml", "<idea-plugin><id>demo.ignored.id</id></idea-plugin>")
      addSourceModule(
        project, "demo.dependent", "META-INF/plugin.xml",
        "<idea-plugin><id>demo.dependent.id</id><depends>demo.ignored.id</depends></idea-plugin>",
      )
      val properties = CollectorProductProperties().apply {
        productLayout.compatiblePluginsToIgnore = persistentListOf("demo.ignored")
      }
      val platform = PlatformLayout()
      platform.descriptorCacheContainer.forPlatform(platform).put(PRODUCT_DESCRIPTOR_META_PATH, "<idea-plugin/>".toByteArray())
      val published = LinkedHashSet<PluginLayout>()

      collectCompatiblePluginsToPublish(
        pluginsToPublish = published,
        platformLayout = platform,
        productProperties = properties,
        outputProvider = SourceOnlyCollectorOutputProvider(project),
      )

      assertThat(published).isEmpty()
    }
  }

  @Test
  fun `plugin is compatible when required module is included in its layout`() {
    val mainModule = "test.plugin"
    val includedModule = "test.plugin.content"
    val layout = PluginLayout.pluginAuto(listOf(mainModule, includedModule))
    val descriptor = PluginDescriptor(
      id = "test.plugin.id",
      description = null,
      declaredModules = emptySet(),
      requiredDependencies = setOf(includedModule),
      incompatiblePlugins = emptySet(),
      optionalDependencies = emptyList(),
      mainModule = mainModule,
      pluginLayouts = listOf(layout),
    )

    val compatible = isPluginCompatible(
      plugin = descriptor,
      availableModulesAndPlugins = HashSet(),
      nonCheckedModules = HashMap(),
      bundledPluginIds = emptySet(),
    )

    assertThat(compatible).isTrue()
  }

  private fun addDescriptorLibrary(module: JpsModule, descriptorPath: String): Path {
    val library = module.project.libraryCollection.addLibrary("demo.descriptors", JpsJavaLibraryType.INSTANCE)
    module.dependenciesList.addLibraryDependency(library)
    val libraryJar = tempDirectory.resolve("descriptors.jar")
    ZipOutputStream(Files.newOutputStream(libraryJar)).use { zip ->
      zip.putNextEntry(ZipEntry(descriptorPath))
      zip.write("<idea-plugin><id>library.descriptor</id></idea-plugin>".toByteArray())
      zip.closeEntry()
    }
    return libraryJar
  }

  private fun libraryOutputProvider(project: JpsProject, libraryJar: Path, libraryReads: MutableList<String>): ModuleOutputProvider {
    return object : ModuleOutputProvider by SourceOnlyCollectorOutputProvider(project) {
      override fun findDeclaredLibraryRoots(libraryName: String, moduleLibraryModuleName: String?): List<Path> {
        check(libraryName == "demo.descriptors" && moduleLibraryModuleName == null)
        libraryReads.add(libraryName)
        return listOf(libraryJar)
      }
    }
  }

  private fun platformProperties(project: JpsProject, descriptor: String = "<idea-plugin/>"): CollectorProductProperties {
    val applicationInfoModule = addSourceModule(project, "demo.product", "META-INF/plugin.xml", descriptor)
    return CollectorProductProperties().apply {
      this.applicationInfoModule = applicationInfoModule.name
      productLayout.productImplementationModules = listOf(applicationInfoModule.name)
    }
  }

  private fun platformOutputProvider(project: JpsProject): ModuleOutputProvider {
    return object : ModuleOutputProvider by SourceOnlyCollectorOutputProvider(project) {
      override fun findRequiredModule(name: String): JpsModule {
        return project.findModuleByName(name) ?: project.addModule(name, JpsJavaModuleType.INSTANCE)
      }
    }
  }

  private fun addSourceModule(project: JpsProject, moduleName: String, relativePath: String, text: String): JpsModule {
    val module = project.addModule(moduleName, JpsJavaModuleType.INSTANCE)
    val root = Files.createDirectories(tempDirectory.resolve(moduleName))
    module.addSourceRoot(root.toUri().toString(), JavaResourceRootType.RESOURCE)
    writeSourceFile(module, relativePath, text)
    return module
  }

  private fun writeSourceFile(module: JpsModule, relativePath: String, text: String) {
    val file = module.sourceRoots.single().path.resolve(relativePath)
    Files.createDirectories(file.parent)
    Files.writeString(file, text)
  }
}

private class CollectorProductProperties : ProductProperties() {
  init {
    productLayout.bundledPluginModules = persistentListOf()
    productLayout.pluginLayouts = lazyOf(persistentListOf())
  }

  override val baseFileName: String = "demo"
  override fun getBaseArtifactName(appInfo: ApplicationInfoProperties, buildNumber: String): String = "demo"
  override fun createWindowsCustomizer(projectHome: Path) = null
  override fun createLinuxCustomizer(projectHome: Path) = null
  override fun createMacCustomizer(projectHome: Path) = null
  override fun getProductContentDescriptor() = null
  override fun copyAdditionalFiles(targetDir: Path, context: BuildContext) {
    error("Source derivation must not copy shared files.")
  }

  override fun copyAdditionalOsSpecificFiles(runDir: Path, os: OsFamily, arch: JvmArchitecture, context: BuildContext) {
    error("Source derivation must not copy OS-specific files.")
  }
}

private class SourceOnlyCollectorOutputProvider(private val project: JpsProject) : ModuleOutputProvider {
  override val useTestCompilationOutput: Boolean = false
  override fun getAllModules(): List<JpsModule> = project.modules
  override fun findModule(name: String): JpsModule? = project.findModuleByName(name)
  override fun findRequiredModule(name: String): JpsModule = requireNotNull(findModule(name))
  override fun getModuleImlFile(module: JpsModule): Path = error("The collector must not read an IML file.")
  override fun findLibraryRoots(libraryName: String, moduleLibraryModuleName: String?): List<Path> = error("The collector must not read a library.")
  override fun getModuleOutputRoots(module: JpsModule, forTests: Boolean): List<Path> = error("The collector must not read compiled output.")
  override fun readFileContentFromModuleOutput(module: JpsModule, relativePath: String, forTests: Boolean): ByteArray? {
    error("The collector must not read compiled output.")
  }

  override fun findFileInAnyModuleOutput(relativePath: String, moduleNamePrefix: String?, processedModules: MutableSet<String>?): ByteArray? {
    error("The collector must not search compiled output.")
  }
}
