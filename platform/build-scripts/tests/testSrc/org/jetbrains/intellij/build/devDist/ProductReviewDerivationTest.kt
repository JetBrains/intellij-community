package org.jetbrains.intellij.build.devDist

import com.intellij.platform.distributionContent.DevDistProductReport
import com.intellij.platform.distributionContent.FileEntry
import com.intellij.platform.distributionContent.PluginContentReport
import com.intellij.platform.distributionContent.ProductModuleIdentity
import com.intellij.platform.distributionContent.ProductModuleSelection
import com.intellij.platform.distributionContent.ProductModuleSetReference
import com.intellij.platform.distributionContent.writeDevDistProductReport
import com.intellij.platform.distributionContent.writeProductReviewReport
import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginModuleId
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.ApplicationInfoProperties
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.productLayout.ContentModule
import org.jetbrains.intellij.build.productLayout.DeprecatedXmlInclude
import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.ModuleSetWithOverrides
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec
import org.junit.jupiter.api.Test
import java.nio.file.Path

class ProductReviewDerivationTest {
  @Test
  fun `shared membership changes do not change either product review`() {
    val before = ModuleSet("shared", listOf(contentModule("shared.before")))
    val after = before.copy(
      modules = before.modules + contentModule("shared.after"),
      nestedSets = listOf(ModuleSet("nested", listOf(contentModule("nested.member")))),
    )
    for (directName in listOf("product.first", "product.second")) {
      val original = product(before, listOf(contentModule(directName)))
      val changed = product(after, listOf(contentModule(directName)))
      val beforeReport = derive(original)
      assertThat(beforeReport.platform.moduleSets).containsExactly(ProductModuleSetReference("shared"))
      assertThat(writeProductReviewReport(derive(changed))).isEqualTo(writeProductReviewReport(beforeReport))
      assertThat(writeProductReviewReport(beforeReport)).doesNotContain("shared.before", "nested.member")
    }
    val observation = DevDistProductReport(emptyList(), emptyList(), listOf("shared.before"), emptyList())
    assertThat(writeDevDistProductReport(observation.copy(platformContentModules = listOf("shared.before", "shared.after", "nested.member"))))
      .isNotEqualTo(writeDevDistProductReport(observation))
  }

  @Test
  fun `product module choices preserve identity loading and dependency rules`() {
    val direct = contentModule("direct").copy(
      moduleId = PluginModuleId("direct", null),
      loading = ModuleLoadingRuleValue.REQUIRED,
      requiredIfAvailable = PluginModuleId("backend", "private"),
      includeDependencies = true,
    )
    val properties = product(ModuleSet("shared", emptyList()), listOf(direct))
    val selected = derive(properties).platform.modules.single()
    assertThat(selected).isEqualTo(
      ProductModuleSelection(
        name = "direct",
        namespace = null,
        loading = "required",
        requiredIfAvailable = ProductModuleIdentity("backend", "private"),
        includeDependencies = true,
      )
    )
    val baseline = writeProductReviewReport(derive(properties))
    for (changed in listOf(
      direct.copy(moduleId = PluginModuleId("other", null)),
      direct.copy(moduleId = PluginModuleId("direct", "jetbrains")),
      direct.copy(loading = ModuleLoadingRuleValue.EMBEDDED),
      direct.copy(loading = ModuleLoadingRuleValue.ON_DEMAND),
      direct.copy(requiredIfAvailable = null),
      direct.copy(includeDependencies = false),
    )) {
      assertThat(writeProductReviewReport(derive(product(ModuleSet("shared", emptyList()), listOf(changed)))))
        .isNotEqualTo(baseline)
    }
  }

  @Test
  fun `module set references and loading overrides remain visible`() {
    val shared = ModuleSet("shared", listOf(contentModule("member")))
    val properties = product(shared)
    val baseline = writeProductReviewReport(derive(properties))
    val overrideSpec = requireNotNull(properties.descriptor).let { original ->
      ProductModulesContentSpec(
        productModuleAliases = original.productModuleAliases,
        deprecatedXmlIncludes = original.deprecatedXmlIncludes,
        additionalModules = original.additionalModules,
        moduleSets = listOf(ModuleSetWithOverrides(shared, mapOf(ContentModuleName("member") to ModuleLoadingRuleValue.EMBEDDED))),
      )
    }
    properties.descriptor = overrideSpec
    assertThat(derive(properties).platform.moduleSets).containsExactly(ProductModuleSetReference("shared", mapOf("member" to "embedded")))
    assertThat(writeProductReviewReport(derive(properties))).isNotEqualTo(baseline)
    assertThat(writeProductReviewReport(derive(product(shared.copy(name = "other"))))).isNotEqualTo(baseline)
  }

  @Test
  fun `publishing choices and discovered compatible plugins remain visible`() {
    val properties = product(ModuleSet("shared", emptyList()))
    properties.productLayout.pluginModulesToPublish = persistentSetOf("explicit")
    properties.productLayout.compatiblePluginsToIgnore = persistentListOf("ignored")
    val baseline = writeProductReviewReport(derive(properties))
    val discovered = deriveProductReviewReport(properties, emptyList(), listOf(PluginContentReport("discovered")), emptyList(), emptyList())
    assertThat(discovered.nonBundledPlugins).containsExactly(PluginContentReport("discovered"))
    assertThat(writeProductReviewReport(discovered)).isNotEqualTo(baseline).contains("discovered")
    properties.productLayout.buildAllCompatiblePlugins = false
    assertThat(writeProductReviewReport(derive(properties))).isNotEqualTo(baseline)
    properties.productLayout.buildAllCompatiblePlugins = true
    properties.productLayout.pluginModulesToPublish = persistentSetOf("another")
    assertThat(writeProductReviewReport(derive(properties))).isNotEqualTo(baseline)
    properties.productLayout.pluginModulesToPublish = persistentSetOf("explicit")
    properties.productLayout.compatiblePluginsToIgnore = persistentListOf("another")
    assertThat(writeProductReviewReport(derive(properties))).isNotEqualTo(baseline)
  }

  @Test
  fun `legacy XML references stay unexpanded`() {
    val properties = ReviewProductProperties(
      ProductModulesContentSpec(
        productModuleAliases = emptyList(),
        deprecatedXmlIncludes = listOf(DeprecatedXmlInclude(ContentModuleName("legacy"), "META-INF/legacy.xml", optional = true)),
        moduleSets = emptyList(),
        additionalModules = emptyList(),
      )
    )
    val reference = derive(properties).platform.xmlIncludes.single()
    assertThat(reference.module).isEqualTo("legacy")
    assertThat(reference.resourcePath).isEqualTo("META-INF/legacy.xml")
    assertThat(reference.optional).isTrue()
  }

  @Test
  fun `XML only products retain compact packed choices`() {
    val properties = ReviewProductProperties(null)
    val packed = FileEntry("plugins", productModules = listOf("shared.set", "direct"), productEmbeddedModules = listOf("embedded"))
    val report = deriveProductReviewReport(properties, emptyList(), emptyList(), emptyList(), listOf(packed))
    assertThat(report.platform.legacyProductModules).containsExactly("shared.set", "direct")
    assertThat(report.platform.legacyProductEmbeddedModules).containsExactly("embedded")
    assertThatThrownBy { derive(properties) }.isInstanceOf(NoSuchElementException::class.java)
  }

  private fun contentModule(name: String): ContentModule = ContentModule(PluginModuleId(name, PluginModuleId.DEFAULT_NAMESPACE))

  private fun product(moduleSet: ModuleSet, modules: List<ContentModule> = emptyList()): ReviewProductProperties {
    return ReviewProductProperties(
      ProductModulesContentSpec(
        productModuleAliases = emptyList(),
        deprecatedXmlIncludes = emptyList(),
        moduleSets = listOf(ModuleSetWithOverrides(moduleSet)),
        additionalModules = modules,
      )
    )
  }

  private fun derive(properties: ProductProperties) = deriveProductReviewReport(properties, emptyList(), emptyList(), emptyList(), emptyList())
}

private class ReviewProductProperties(var descriptor: ProductModulesContentSpec?) : ProductProperties() {
  override val baseFileName: String = "review"
  override fun getBaseArtifactName(appInfo: ApplicationInfoProperties, buildNumber: String): String = "review"
  override fun createWindowsCustomizer(projectHome: Path) = null
  override fun createLinuxCustomizer(projectHome: Path) = null
  override fun createMacCustomizer(projectHome: Path) = null
  override fun getProductContentDescriptor(): ProductModulesContentSpec? = descriptor
}
