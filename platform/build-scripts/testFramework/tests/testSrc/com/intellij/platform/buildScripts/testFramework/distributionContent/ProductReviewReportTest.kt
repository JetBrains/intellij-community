package com.intellij.platform.buildScripts.testFramework.distributionContent

import com.intellij.platform.distributionContent.DevDistProductReport
import com.intellij.platform.distributionContent.DistFileRow
import com.intellij.platform.distributionContent.FileEntry
import com.intellij.platform.distributionContent.PluginContentReport
import com.intellij.platform.distributionContent.ProductModuleIdentity
import com.intellij.platform.distributionContent.ProductModuleSelection
import com.intellij.platform.distributionContent.ProductModuleSetReference
import com.intellij.platform.distributionContent.ProductPlatformReview
import com.intellij.platform.distributionContent.ProductPublishingReview
import com.intellij.platform.distributionContent.ProductReviewReport
import com.intellij.platform.distributionContent.ProductXmlReference
import com.intellij.platform.distributionContent.readDevDistProductReport
import com.intellij.platform.distributionContent.readProductReviewReport
import com.intellij.platform.distributionContent.writeProductReviewReport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

class ProductReviewReportTest {
  private val emptyReport = ProductReviewReport(
    bundledPlugins = emptyList(),
    nonBundledPlugins = emptyList(),
    publishing = ProductPublishingReview(true, emptyList(), emptyList()),
    platform = ProductPlatformReview(),
    distFiles = emptyList(),
  )

  @Test
  fun `empty choices retain the five review sections`() {
    val text = writeProductReviewReport(emptyReport)
    assertThat(text).contains("bundledPlugins: []", "nonBundledPlugins: []", "publishing:", "platform: {}", "distFiles: []")
      .doesNotContain("platformContentModules")
    assertThat(readProductReviewReport(text)).isEqualTo(emptyReport)
  }

  @Test
  fun `optional loading is omitted except in explicit overrides`() {
    val report = emptyReport.copy(platform = ProductPlatformReview(
      modules = listOf(ProductModuleSelection("direct", loading = "optional")),
      moduleSets = listOf(ProductModuleSetReference("shared", mapOf("member" to "optional"))),
    ))
    val text = writeProductReviewReport(report)
    assertThat(text).doesNotContain("loading: optional").contains("member: optional")
    assertThat(readProductReviewReport(text)).isEqualTo(report)
    val explicitDefault = text.replace("name: direct", "name: direct\n    loading: optional")
    assertThat(writeProductReviewReport(readProductReviewReport(explicitDefault))).isEqualTo(text)
  }

  @Test
  fun `the writer preserves choices and canonicalizes exact duplicates`() {
    val overrides = linkedMapOf("z.module" to "required", "a.module" to "embedded")
    val moduleSet = ProductModuleSetReference("shared", overrides)
    val module = ProductModuleSelection("direct", namespace = null, loading = "required", requiredIfAvailable = ProductModuleIdentity("backend"))
    val included = ProductXmlReference("legacy", "META-INF/legacy.xml", optional = true)
    val report = emptyReport.copy(
      platform = ProductPlatformReview(
        moduleSets = listOf(moduleSet, ProductModuleSetReference("another"), moduleSet),
        modules = listOf(module, module.copy(namespace = "private", includeDependencies = true), module),
        xmlIncludes = listOf(included, included),
        legacyProductModules = listOf("z", "a", "a"),
        legacyProductEmbeddedModules = listOf("z", "a", "a"),
      ),
      publishing = ProductPublishingReview(false, listOf("z", "a", "a"), listOf("z", "a", "a")),
    )
    val text = writeProductReviewReport(report)
    val restored = readProductReviewReport(text)
    assertThat(restored.platform.moduleSets).containsExactly(ProductModuleSetReference("another"), moduleSet)
    assertThat(restored.platform.modules).containsExactly(module, module.copy(namespace = "private", includeDependencies = true))
    assertThat(restored.platform.xmlIncludes).containsExactly(included)
    assertThat(restored.platform.legacyProductModules).containsExactly("a", "z")
    assertThat(restored.platform.legacyProductEmbeddedModules).containsExactly("a", "z")
    assertThat(restored.publishing).isEqualTo(ProductPublishingReview(false, listOf("a", "z"), listOf("a", "z")))
    assertThat(writeProductReviewReport(restored)).isEqualTo(text)
    assertThat(
      writeProductReviewReport(
        report.copy(
          platform = report.platform.copy(
            moduleSets = report.platform.moduleSets.reversed().map { it.copy(loadingOverrides = it.loadingOverrides.toSortedMap()) },
            modules = report.platform.modules.reversed(),
          )
        )
      )
    ).isEqualTo(text)
    assertThat(overrides.keys).containsExactly("z.module", "a.module")
  }

  @Test
  fun `plugin and file restrictions preserve pairs without forming a cross product`() {
    val plugins = listOf(
      PluginContentReport("plugin", os = "linux", arch = "x64", content = listOf(FileEntry("lib/detail.jar"))),
      PluginContentReport("plugin", os = "mac", arch = "aarch64"),
    )
    val files = listOf(
      DistFileRow("linux", "amd64", "GLIBC", "bin/tool"),
      DistFileRow("mac", "aarch64", null, "bin/tool"),
    )
    val report = emptyReport.copy(bundledPlugins = plugins + plugins, nonBundledPlugins = plugins + plugins, distFiles = files + files)
    val text = writeProductReviewReport(report)
    val restored = readProductReviewReport(text)
    assertThat(restored.bundledPlugins).containsExactlyElementsOf(plugins.map { it.copy(content = emptyList()) })
    assertThat(restored.nonBundledPlugins).containsExactlyElementsOf(plugins.map { it.copy(content = emptyList()) })
    assertThat(restored.distFiles).containsExactlyElementsOf(files)
    assertThat(text).doesNotContain("lib/detail.jar")
    assertThat(writeProductReviewReport(report.copy(bundledPlugins = plugins.reversed(), distFiles = files.reversed()))).isEqualTo(text)
    assertThat(writeProductReviewReport(report.copy(distFiles = listOf(files.first())))).isNotEqualTo(text)
    assertThat(writeProductReviewReport(report.copy(bundledPlugins = listOf(plugins.first())))).isNotEqualTo(text)
    assertThat(writeProductReviewReport(report.copy(nonBundledPlugins = plugins.reversed()))).isEqualTo(text)
    assertThat(writeProductReviewReport(report.copy(nonBundledPlugins = listOf(plugins.first())))).isNotEqualTo(text)
  }

  @Test
  fun `new compatible plugins fail the snapshot check`(@TempDir directory: Path) {
    directory.resolve("product.yaml").writeText(writeProductReviewReport(emptyReport))
    val report = emptyReport.copy(nonBundledPlugins = listOf(PluginContentReport("newly.compatible")))
    assertThat(validateProductReport(directory, "product.yaml", report)).hasSize(1)
  }

  @Test
  fun `expanded artifacts survive cleanup on report success and failure`(@TempDir directory: Path) {
    val artifact = directory.resolve("logs/packaging-reports/suite/product.yaml")
    val output = directory.resolve("build-output").createDirectories()
    val scratch = output.resolve("scratch").also { it.writeText("temporary") }
    val observed = DevDistProductReport(
      emptyList(), listOf(PluginContentReport("discovered")), listOf("shared.member"), emptyList(),
    )
    writeProductObservation(artifact, observed)
    Files.delete(scratch)
    Files.delete(output)
    val home = directory.resolve("project").createDirectories()
    assertThat(validateProductReport(home, "product.yaml", emptyReport)).hasSize(1)
    assertThat(readDevDistProductReport(artifact.readText())).isEqualTo(observed)
    home.resolve("product.yaml").writeText(writeProductReviewReport(emptyReport))
    val updated = observed.copy(platformContentModules = listOf("shared.member", "shared.new"))
    writeProductObservation(artifact, updated)
    assertThat(validateProductReport(home, "product.yaml", emptyReport)).isEmpty()
    assertThat(readDevDistProductReport(artifact.readText())).isEqualTo(updated)
  }

  @Test
  fun `review reports reuse distribution file validation`() {
    assertThatThrownBy {
      writeProductReviewReport(emptyReport.copy(distFiles = listOf(DistFileRow(null, null, null, "../outside"))))
    }.hasMessageContaining("Invalid distribution path")
    val text = writeProductReviewReport(emptyReport.copy(distFiles = listOf(DistFileRow("linux", null, null, "bin/tool"))))
    assertThatThrownBy { readProductReviewReport(text.replace("linux", "unknown")) }.hasMessageContaining("Unknown OS")
    assertThatThrownBy { readProductReviewReport(text.replace("bin/tool", "../outside")) }.hasMessageContaining("Invalid distribution path")
  }
}
