package org.jetbrains.intellij.build.devDist

import com.intellij.platform.distributionContent.DevDistPlatformJars
import com.intellij.platform.distributionContent.NonBundledPluginRow
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.ApplicationInfoProperties
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.impl.JpsModuleOutputProviderState
import org.jetbrains.intellij.build.impl.PRODUCT_DESCRIPTOR_META_PATH
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.collectCompatiblePluginsToPublish
import org.jetbrains.intellij.build.impl.getBundledPluginModules
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JpsJavaModuleType
import org.jetbrains.jps.model.module.JpsModule
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PluginPopulationDerivationTest {
  @TempDir
  lateinit var tempDirectory: Path

  @Test
  fun `modular declarations supply bundled membership and packing without compiled outputs`() {
    val project = JpsElementFactory.getInstance().createModel().project
    val properties = modularProduct(project)
    val outputProvider = sourceOnlyProvider(project)
    val platformJars = DevDistPlatformJars(
      platformJars = emptyList(),
      platformLibraries = emptyList(),
      platformMergedLibraries = emptyList(),
      platformContentModules = emptyList(),
      nonBundledPlugins = listOf(NonBundledPluginRow(product = "demo", mainModule = "demo.compatible")),
    )

    val derived = derivePluginJars(
      products = listOf(properties),
      extraPopulation = setOf("demo.extra", "demo.absent"),
      platformJars = platformJars,
      outputProvider = outputProvider,
    )

    assertThat(getBundledPluginModules(properties, outputProvider)).containsExactlyInAnyOrder("demo.direct", "demo.inherited")
    assertThat(derived.bundledPluginModules(properties)).containsExactlyInAnyOrder("demo.direct", "demo.inherited")
    assertThat(derived.population).containsExactlyInAnyOrder("demo.direct", "demo.inherited", "demo.published", "demo.compatible", "demo.extra")
    assertThat(derived.plugins.map { it.mainModule }).containsExactlyElementsOf(derived.population)
    assertThat(properties.productLayout.bundledPluginModules).containsExactly("demo.legacy")

    properties.rootModuleForModularLoader = null
    assertThat(getBundledPluginModules(properties, outputProvider)).containsExactly("demo.legacy")
  }

  @Test
  fun `source compatibility uses modular bundled aliases and incompatibilities`(): Unit = runBlocking {
    val project = JpsElementFactory.getInstance().createModel().project
    val properties = modularProduct(project)
    addSourceModule(
      project, "demo.candidate", "META-INF/plugin.xml", """
      <idea-plugin><id>demo.candidate.id</id><depends>demo.inherited.alias</depends></idea-plugin>
    """.trimIndent()
    )
    addSourceModule(
      project, "demo.incompatible", "META-INF/plugin.xml", """
      <idea-plugin><id>demo.incompatible.id</id><incompatible-with>demo.inherited.id</incompatible-with></idea-plugin>
    """.trimIndent()
    )
    val platform = PlatformLayout()
    platform.descriptorCacheContainer.forPlatform(platform).put(PRODUCT_DESCRIPTOR_META_PATH, "<idea-plugin/>".toByteArray())
    val published = LinkedHashSet<PluginLayout>()

    collectCompatiblePluginsToPublish(
      pluginsToPublish = published,
      platformLayout = platform,
      productProperties = properties,
      outputProvider = sourceOnlyProvider(project),
    )

    assertThat(published.map { it.mainModule })
      .contains("demo.candidate")
      .doesNotContain("demo.direct", "demo.inherited", "demo.incompatible")
  }

  @Test
  fun `missing modular declaration does not fall back to the legacy bundled list`() {
    val project = JpsElementFactory.getInstance().createModel().project
    project.addModule("demo.root", JpsJavaModuleType.INSTANCE)
    val properties = PopulationProductProperties().apply {
      rootModuleForModularLoader = "demo.root"
      productLayout.bundledPluginModules = persistentListOf("demo.legacy")
    }

    assertThatThrownBy { getBundledPluginModules(properties, sourceOnlyProvider(project)) }
      .hasMessageContaining("Cannot find product-modules.xml file in demo.root")
  }

  private fun modularProduct(project: JpsProject): ProductProperties {
    addSourceModule(
      project, "demo.root", "META-INF/demo.root/product-modules.xml", """
      <product-modules>
        <include><from-module>demo.base</from-module><without-module>demo.excluded</without-module></include>
        <bundled-plugins><module>demo.direct</module></bundled-plugins>
      </product-modules>
    """.trimIndent()
    )
    addSourceModule(
      project, "demo.base", "META-INF/demo.base/product-modules.xml", """
      <product-modules><include><from-module>demo.nested</from-module></include></product-modules>
    """.trimIndent()
    )
    addSourceModule(
      project, "demo.nested", "META-INF/demo.nested/product-modules.xml", """
      <product-modules><bundled-plugins><module>demo.inherited</module><module>demo.excluded</module></bundled-plugins></product-modules>
    """.trimIndent()
    )
    for (plugin in listOf("demo.direct", "demo.inherited", "demo.excluded", "demo.legacy", "demo.published", "demo.compatible", "demo.extra")) {
      addSourceModule(project, plugin, "META-INF/plugin.xml", "<idea-plugin><id>$plugin.id</id><module value=\"$plugin.alias\"/></idea-plugin>")
    }
    return PopulationProductProperties().apply {
      rootModuleForModularLoader = "demo.root"
      productLayout.bundledPluginModules = persistentListOf("demo.legacy")
      productLayout.pluginModulesToPublish = persistentSetOf("demo.published")
    }
  }

  private fun addSourceModule(project: JpsProject, name: String, relativePath: String, content: String) {
    val module = project.addModule(name, JpsJavaModuleType.INSTANCE)
    val root = Files.createDirectories(tempDirectory.resolve(name))
    module.addSourceRoot(root.toUri().toString(), JavaResourceRootType.RESOURCE)
    val file = root.resolve(relativePath)
    Files.createDirectories(file.parent)
    Files.writeString(file, content)
  }

  private fun sourceOnlyProvider(project: JpsProject): ModuleOutputProvider {
    return object : ModuleOutputProvider by JpsModuleOutputProviderState(project).createProvider(useTestCompilationOutput = false) {
      override fun getModuleOutputRoots(module: JpsModule, forTests: Boolean): List<Path> = error("Unexpected compiled output read.")
      override fun findLibraryRoots(libraryName: String, moduleLibraryModuleName: String?): List<Path> = error("Unexpected library read.")
      override fun readFileContentFromModuleOutput(module: JpsModule, relativePath: String, forTests: Boolean): ByteArray? {
        error("Unexpected compiled output read.")
      }

      override fun findFileInAnyModuleOutput(relativePath: String, moduleNamePrefix: String?, processedModules: MutableSet<String>?): ByteArray? {
        error("Unexpected compiled output search.")
      }
    }
  }
}

private class PopulationProductProperties : ProductProperties() {
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
}
