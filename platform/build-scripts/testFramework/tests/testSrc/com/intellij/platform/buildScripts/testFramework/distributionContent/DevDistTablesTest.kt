// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.distributionContent

import com.intellij.platform.distributionContent.DevDistProductReport
import com.intellij.platform.distributionContent.DistFileRow
import com.intellij.platform.distributionContent.FileEntry
import com.intellij.platform.distributionContent.PluginContentReport
import com.intellij.platform.distributionContent.ProductModuleSelection
import com.intellij.platform.distributionContent.ProductPlatformReview
import com.intellij.platform.distributionContent.ProductPublishingReview
import com.intellij.platform.distributionContent.ProductReviewReport
import com.intellij.platform.distributionContent.readDevDistProductReport
import com.intellij.platform.distributionContent.writeDevDistProductReport
import com.intellij.platform.distributionContent.writeProductReviewReport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class DevDistTablesTest {
  private val emptyReview = ProductReviewReport(
    bundledPlugins = emptyList(),
    nonBundledPlugins = emptyList(),
    publishing = ProductPublishingReview(false, emptyList(), emptyList()),
    platform = ProductPlatformReview(),
    distFiles = emptyList(),
  )

  private val review = emptyReview.copy(
    bundledPlugins = listOf(PluginContentReport(mainModule = "bundled.plugin")),
    publishing = ProductPublishingReview(true, listOf("published.plugin"), emptyList()),
    platform = ProductPlatformReview(modules = listOf(ProductModuleSelection("platform.content", loading = "required"))),
  )

  private val emptyReport = DevDistProductReport(
    bundledPlugins = emptyList(),
    nonBundledPlugins = emptyList(),
    platformContentModules = emptyList(),
    distFiles = emptyList(),
  )

  private val rows = listOf(
    DistFileRow("windows", "amd64", null, "lib/jna/amd64/jnidispatch.dll"),
    DistFileRow(null, null, null, "build.txt"),
    DistFileRow("linux", "aarch64", "GLIBC", "plugins/plugin-classpath.txt"),
    DistFileRow("mac", null, null, "bin/launcher"),
  )

  private val report = DevDistProductReport(
    bundledPlugins = listOf(PluginContentReport(mainModule = "bundled.plugin")),
    nonBundledPlugins = listOf(PluginContentReport(mainModule = "published.plugin", os = "linux", arch = "x64")),
    platformContentModules = listOf("platform.content"),
    distFiles = rows,
  )

  @Test
  fun `private observations and patches use the private project root`(@TempDir projectHome: Path) {
    val contentYamlPath = "rider/build/tests/testData/RiderPackagingTest/rider-content.yaml"
    val relativePath = Path.of(contentYamlPath)
    val communityFile = projectHome.resolve("community").resolve(relativePath)
    communityFile.parent.createDirectories()
    val originalText = writeProductReviewReport(emptyReview)
    communityFile.writeText(originalText)
    val otherProductFile = projectHome.resolve("rider/build/tests/testData/RiderPackagingTest/other-content.yaml")
    otherProductFile.parent.createDirectories()
    otherProductFile.writeText(originalText)

    val failure = validateProductReport(projectHome, contentYamlPath, review).single()
    val file = projectHome.resolve(relativePath)
    assertThat(file.exists()).isTrue()
    assertThat(file.readText()).isEmpty()
    assertThat(failure.name).isEqualTo("product-report-out-of-date")
    assertThat(failure.error.message).containsOnlyOnce("--- $relativePath").containsOnlyOnce("+++ $relativePath")
      .contains("bundledPlugins:", "publishing:", "platform:", "distFiles:")
      .doesNotContain(Path.of("community").resolve(relativePath).toString())
    assertThat(communityFile.readText()).isEqualTo(originalText)
    assertThat(otherProductFile.readText()).isEqualTo(originalText)

    file.writeText(writeProductReviewReport(review))
    assertThat(validateProductReport(projectHome, contentYamlPath, review)).isEmpty()
  }

  @Test
  fun `community observations and patches use the community project root`(@TempDir ultimateHome: Path) {
    val projectHome = ultimateHome.resolve("community").createDirectories()
    val contentYamlPath = "platform/build-scripts/testData/CommunityPackagingTest/idea-content.yaml"
    val relativePath = Path.of(contentYamlPath)

    val failure = validateProductReport(projectHome, contentYamlPath, review).single()
    val file = projectHome.resolve(relativePath)
    assertThat(file.exists()).isTrue()
    assertThat(ultimateHome.resolve(relativePath).exists()).isFalse()
    assertThat(failure.error.message).contains("--- $relativePath", "+++ $relativePath")
      .doesNotContain(Path.of("community").resolve(relativePath).toString())

    file.writeText(writeProductReviewReport(review))
    assertThat(validateProductReport(projectHome, contentYamlPath, review)).isEmpty()
  }

  @Test
  fun `each section contributes to the product patch`(@TempDir projectHome: Path) {
    val contentYamlPath = "reports/sample.yaml"
    val file = projectHome.resolve(contentYamlPath)
    file.parent.createDirectories()
    val originalText = writeProductReviewReport(emptyReview)
    val observations = listOf(
      "bundledPlugins" to emptyReview.copy(bundledPlugins = review.bundledPlugins),
      "publishing" to emptyReview.copy(publishing = review.publishing),
      "platform" to emptyReview.copy(platform = review.platform),
      "distFiles" to emptyReview.copy(distFiles = rows),
    )
    for ((section, observation) in observations) {
      file.writeText(originalText)
      val failures = validateProductReport(projectHome, contentYamlPath, observation)
      assertThat(failures).describedAs(section).hasSize(1)
      assertThat(failures.single().error.message).contains("$section:")
        .containsOnlyOnce("--- $contentYamlPath").containsOnlyOnce("+++ $contentYamlPath")
      assertThat(file.readText()).isEqualTo(originalText)

      file.writeText(writeProductReviewReport(observation))
      assertThat(validateProductReport(projectHome, contentYamlPath, observation)).describedAs(section).isEmpty()
    }
  }

  @Test
  fun `the report uses the explicit path without an extension restriction`(@TempDir projectHome: Path) {
    for (contentYamlPath in listOf("product-content", "reports/custom-content.snapshot")) {
      val failure = validateProductReport(projectHome, contentYamlPath, review).single()
      val file = projectHome.resolve(contentYamlPath)
      assertThat(file.exists()).isTrue()
      assertThat(failure.error.message).contains("--- $contentYamlPath", "+++ $contentYamlPath")

      file.writeText(writeProductReviewReport(review))
      assertThat(validateProductReport(projectHome, contentYamlPath, review)).isEmpty()
    }
    assertThat(projectHome.resolve("build/dev-dist/packaging").exists()).isFalse()
  }

  @Test
  fun `the validator refuses invalid target paths`(@TempDir projectHome: Path) {
    val existingFile = projectHome.resolve("rider-content.yaml")
    existingFile.writeText("unchanged")
    val absoluteFile = projectHome.resolve("absolute.yaml")
    val invalidPaths = listOf(
      "",
      " ",
      "\t",
      ".",
      "..",
      "./rider-content.yaml",
      "reports/./rider-content.yaml",
      "../rider-content.yaml",
      "reports/../rider-content.yaml",
      absoluteFile.toString(),
    )
    for (contentYamlPath in invalidPaths) {
      assertThatThrownBy { validateProductReport(projectHome, contentYamlPath, review) }
        .describedAs(contentYamlPath)
        .isInstanceOf(IllegalArgumentException::class.java)
        .hasMessageContaining("relative to the project root")
    }
    assertThat(existingFile.readText()).isEqualTo("unchanged")
    assertThat(absoluteFile.exists()).isFalse()
    assertThat(projectHome.resolve("reports").exists()).isFalse()
  }

  @Test
  fun `dist file observations round trip`() {
    val text = writeDevDistProductReport(report)
    val restored = readDevDistProductReport(text)

    val (windowsFile, commonFile, linuxFile, macFile) = rows
    assertThat(restored).isEqualTo(report.copy(distFiles = listOf(commonFile, linuxFile, macFile, windowsFile)))
    assertThat(writeDevDistProductReport(restored)).isEqualTo(text)
    assertThat(text).startsWith("bundledPlugins:")
    assertThat(text).contains("\nnonBundledPlugins:", "\nplatformContentModules:", "\ndistFiles:")
    assertThat(text).doesNotContain("product:", "null", "dist_files", "platformJars")
    assertThat(text).endsWith("\n")
  }

  @Test
  fun `the writer retains plugin variants and sorts identities with absent axes first`() {
    val expected = listOf(
      PluginContentReport(mainModule = "first.plugin"),
      PluginContentReport(mainModule = "first.plugin", arch = "aarch64"),
      PluginContentReport(mainModule = "first.plugin", arch = "x64"),
      PluginContentReport(mainModule = "first.plugin", os = "linux"),
      PluginContentReport(mainModule = "first.plugin", os = "linux", arch = "aarch64"),
      PluginContentReport(mainModule = "first.plugin", os = "linux", arch = "x64"),
      PluginContentReport(mainModule = "first.plugin", os = "mac", arch = "aarch64"),
      PluginContentReport(mainModule = "first.plugin", os = "windows", arch = "x64"),
      PluginContentReport(mainModule = "last.plugin"),
    )
    val selected = buildList {
      addAll(expected.reversed())
      add(expected.first())
      add(expected.last())
    }
    val text = writeDevDistProductReport(emptyReport.copy(bundledPlugins = selected, nonBundledPlugins = selected))
    val restored = readDevDistProductReport(text)
    assertThat(restored.bundledPlugins).containsExactlyElementsOf(expected)
    assertThat(restored.nonBundledPlugins).containsExactlyElementsOf(expected)
    assertThat(writeDevDistProductReport(restored)).isEqualTo(text)
  }

  @Test
  fun `bundled and non-bundled plugins remain independent sets`() {
    val bundled = PluginContentReport(mainModule = "bundled.plugin")
    val shared = PluginContentReport(mainModule = "shared.plugin", os = "linux", arch = "x64")
    val published = PluginContentReport(mainModule = "published.plugin")
    val selected = emptyReport.copy(
      bundledPlugins = listOf(shared, bundled, bundled),
      nonBundledPlugins = listOf(shared, published, shared),
    )
    val restored = readDevDistProductReport(writeDevDistProductReport(selected))
    assertThat(restored.bundledPlugins).containsExactly(bundled, shared)
    assertThat(restored.nonBundledPlugins).containsExactly(published, shared)
  }

  @Test
  fun `the writer sorts and deduplicates platform content modules`() {
    val selected = report.copy(platformContentModules = listOf("z.module", "bundled.plugin", "a.module", "a.module", "z.module"))
    val text = writeDevDistProductReport(selected)
    val restored = readDevDistProductReport(text)
    assertThat(restored.platformContentModules).containsExactly("a.module", "bundled.plugin", "z.module")
    assertThat(restored.bundledPlugins).containsExactlyElementsOf(report.bundledPlugins)
    assertThat(writeDevDistProductReport(restored)).isEqualTo(text)
  }

  @Test
  fun `the writer omits plugin content without changing the input`() {
    val bundledContent = listOf(FileEntry(name = "lib/bundled.jar"))
    val publishedContent = listOf(FileEntry(name = "lib/published.jar"))
    val bundled = PluginContentReport(mainModule = "shared.plugin", os = "linux", arch = "x64", content = bundledContent)
    val published = bundled.copy(content = publishedContent)
    val selected = emptyReport.copy(
      bundledPlugins = listOf(bundled, published, bundled),
      nonBundledPlugins = listOf(published, bundled),
    )
    val text = writeDevDistProductReport(selected)
    val restored = readDevDistProductReport(text)
    assertThat(restored.bundledPlugins).containsExactly(bundled.copy(content = emptyList()))
    assertThat(restored.nonBundledPlugins).containsExactly(published.copy(content = emptyList()))
    assertThat(text).doesNotContain("content:", "lib/bundled.jar", "lib/published.jar")
    assertThat(bundled.content).containsExactlyElementsOf(bundledContent)
    assertThat(published.content).containsExactlyElementsOf(publishedContent)
    assertThat(selected.bundledPlugins).containsExactly(bundled, published, bundled)
    assertThat(selected.nonBundledPlugins).containsExactly(published, bundled)
  }

  @Test
  fun `the reader accepts plugin content and the writer omits it`() {
    val text = """
      bundledPlugins:
        - mainModule: shared.plugin
          content:
            - name: lib/bundled.jar
      nonBundledPlugins:
        - mainModule: shared.plugin
          os: linux
          arch: x64
          content:
            - name: lib/published.jar
      platformContentModules: []
      distFiles: []
    """.trimIndent()
    val bundled = PluginContentReport(mainModule = "shared.plugin", content = listOf(FileEntry(name = "lib/bundled.jar")))
    val published = PluginContentReport(
      mainModule = "shared.plugin",
      os = "linux",
      arch = "x64",
      content = listOf(FileEntry(name = "lib/published.jar")),
    )
    val restored = readDevDistProductReport(text)
    assertThat(restored).isEqualTo(emptyReport.copy(bundledPlugins = listOf(bundled), nonBundledPlugins = listOf(published)))
    val compactText = writeDevDistProductReport(restored)
    assertThat(compactText).doesNotContain("content:", "lib/bundled.jar", "lib/published.jar")
    assertThat(readDevDistProductReport(compactText)).isEqualTo(emptyReport.copy(
      bundledPlugins = listOf(bundled.copy(content = emptyList())),
      nonBundledPlugins = listOf(published.copy(content = emptyList())),
    ))
  }

  @Test
  fun `the writer sorts groups and paths with absent axes first`() {
    val expected = listOf(
      DistFileRow(null, null, null, "lib/a.jar"),
      DistFileRow(null, null, null, "lib/z.jar"),
      DistFileRow("linux", null, null, "lib/native.so"),
      DistFileRow("linux", "aarch64", null, "lib/native.so"),
      DistFileRow("linux", "aarch64", "GLIBC", "lib/native.so"),
      DistFileRow("linux", "aarch64", "MUSL", "lib/native.so"),
      DistFileRow("linux", "amd64", null, "lib/native.so"),
      DistFileRow("mac", "aarch64", "DEFAULT", "lib/native.dylib"),
      DistFileRow("windows", "amd64", null, "lib/native.dll"),
    )
    val text = writeDevDistProductReport(emptyReport.copy(distFiles = expected.reversed()))
    assertThat(readDevDistProductReport(text).distFiles).containsExactlyElementsOf(expected)
    assertThat(writeDevDistProductReport(emptyReport.copy(distFiles = expected))).isEqualTo(text)
  }

  @Test
  fun `the writer groups equal platforms and collapses equal rows`() {
    val row = DistFileRow("linux", "amd64", null, "lib/a.so")
    val another = row.copy(path = "lib/b.so")
    val text = writeDevDistProductReport(emptyReport.copy(distFiles = listOf(another, row, row)))
    assertThat(readDevDistProductReport(text).distFiles).containsExactly(row, another)
    assertThat(text).containsOnlyOnce("os: linux").containsOnlyOnce("arch: amd64").containsOnlyOnce("paths:")
    assertThat(text).doesNotContain("libc:")
  }

  @Test
  fun `the same path retains each platform selection`() {
    val selected = listOf(
      DistFileRow(null, null, null, "build.txt"),
      DistFileRow(null, "amd64", null, "build.txt"),
      DistFileRow("linux", "amd64", "GLIBC", "build.txt"),
      DistFileRow("linux", "amd64", "MUSL", "build.txt"),
    )
    assertThat(readDevDistProductReport(writeDevDistProductReport(emptyReport.copy(distFiles = selected))).distFiles)
      .containsExactlyElementsOf(selected)
  }

  @Test
  fun `an empty observation retains its key`() {
    val text = writeDevDistProductReport(emptyReport)
    assertThat(text).isEqualTo("bundledPlugins: []\nnonBundledPlugins: []\nplatformContentModules: []\ndistFiles: []\n")
    assertThat(readDevDistProductReport(text)).isEqualTo(emptyReport)
  }

  @Test
  fun `the reader accepts comments and the writer canonicalizes repeated groups`() {
    val text = """
      # Observed files
      bundledPlugins: []
      nonBundledPlugins: []
      platformContentModules: []
      distFiles:
        - paths: [lib/b.so]
          arch: amd64
          os: linux
        - os: linux
          arch: amd64
          paths: [lib/a.so]
    """.trimIndent()
    val expected = listOf(
      DistFileRow("linux", "amd64", null, "lib/a.so"),
      DistFileRow("linux", "amd64", null, "lib/b.so"),
    )
    assertThat(writeDevDistProductReport(readDevDistProductReport(text)))
      .isEqualTo(writeDevDistProductReport(emptyReport.copy(distFiles = expected)))
  }

  @Test
  fun `paths with YAML punctuation round trip`() {
    val selected = listOf(
      DistFileRow(null, null, null, "lib/name # suffix.jar"),
      DistFileRow(null, null, null, "yes"),
      DistFileRow(null, null, null, "true"),
      DistFileRow(null, null, null, "[file]"),
      DistFileRow(null, null, null, "null"),
      DistFileRow(null, null, null, "*file"),
      DistFileRow(null, null, null, "lib/'file'.jar"),
    )
    assertThat(readDevDistProductReport(writeDevDistProductReport(emptyReport.copy(distFiles = selected))).distFiles)
      .containsExactlyInAnyOrderElementsOf(selected)
  }

  @Test
  fun `plugin and module names with YAML punctuation round trip`() {
    val names = listOf("plugin: name", "name # suffix", "[module]", "true", "yes", "null", "*alias", "name 'quoted'")
    val plugins = names.map { PluginContentReport(mainModule = it) }
    val selected = emptyReport.copy(bundledPlugins = plugins, nonBundledPlugins = plugins, platformContentModules = names)
    val text = writeDevDistProductReport(selected)
    val restored = readDevDistProductReport(text)
    assertThat(restored.bundledPlugins).containsExactlyInAnyOrderElementsOf(plugins)
    assertThat(restored.nonBundledPlugins).containsExactlyInAnyOrderElementsOf(plugins)
    assertThat(restored.platformContentModules).containsExactlyElementsOf(names.sorted())
    assertThat(writeDevDistProductReport(restored)).isEqualTo(text)
  }

  @Test
  fun `the reader requires all four sections`() {
    val text = writeDevDistProductReport(emptyReport)
    for (section in listOf("bundledPlugins", "nonBundledPlugins", "platformContentModules", "distFiles")) {
      val incompleteText = text.lineSequence().filterNot { it.startsWith("$section:") }.joinToString("\n")
      assertThatThrownBy { readDevDistProductReport(incompleteText) }.describedAs(section).isInstanceOf(Exception::class.java)
    }
  }

  @Test
  fun `the reader refuses malformed shapes and unknown keys`() {
    for (text in listOf(
      "",
      "{}",
      "[]",
      "platformJars: []",
      reportYaml() + "product: idea",
      reportYaml() + "distFiles: []",
      reportYaml(bundledPlugins = "invalid"),
      reportYaml(nonBundledPlugins = "invalid"),
      reportYaml(platformContentModules = "invalid"),
      reportYaml(distFiles = "invalid"),
      reportYaml(bundledPlugins = "[{}]"),
      reportYaml(nonBundledPlugins = "[{os: linux}]"),
      reportYaml(bundledPlugins = "[{mainModule: plugin, product: idea}]"),
      reportYaml(nonBundledPlugins = "[{mainModule: plugin, mainModule: other}]"),
      reportYaml(platformContentModules = "[{module: core}]"),
      reportYaml(distFiles = "[{os: linux}]"),
      reportYaml(distFiles = "[{paths: file}]"),
      reportYaml(distFiles = "[{paths: [file], product: idea}]"),
      reportYaml(distFiles = "[{paths: [file], os: linux, os: mac}]"),
    )) {
      assertThatThrownBy { readDevDistProductReport(text) }.describedAs(text).isInstanceOf(Exception::class.java)
    }
  }

  @Test
  fun `the reader refuses empty groups`() {
    assertThatThrownBy { readDevDistProductReport(reportYaml(distFiles = "[{paths: []}]")) }
      .hasMessageContaining("must contain a path")
  }

  @Test
  fun `the reader and writer refuse unknown axes`() {
    val invalid = listOf(
      DistFileRow("invalid", null, null, "file") to "os",
      DistFileRow(null, "invalid", null, "file") to "arch",
      DistFileRow(null, null, "invalid", "file") to "libc",
    )
    for ((row, axis) in invalid) {
      assertThatThrownBy { writeDevDistProductReport(emptyReport.copy(distFiles = listOf(row))) }.hasMessageContaining("Unknown")
      assertThatThrownBy { readDevDistProductReport(reportYaml(distFiles = "[{$axis: invalid, paths: [file]}]")) }
        .hasMessageContaining("Unknown")
      assertThatThrownBy { readDevDistProductReport(reportYaml(distFiles = "[{$axis: '', paths: [file]}]")) }
        .hasMessageContaining("Unknown")
    }
  }

  @Test
  fun `the reader and writer refuse invalid distribution paths`() {
    for (path in listOf("", " ", "/absolute", "../outside", "lib/../outside", "./file", "lib//file", "lib/", "C:/file", "lib\\file")) {
      assertThatThrownBy { writeDevDistProductReport(emptyReport.copy(distFiles = listOf(DistFileRow(null, null, null, path)))) }
        .hasMessageContaining("Invalid distribution path")
      assertThatThrownBy { readDevDistProductReport(reportYaml(distFiles = "[{paths: ['$path']}]")) }
        .hasMessageContaining("Invalid distribution path")
    }
    val controlPath = "lib/line\nfile"
    assertThatThrownBy { writeDevDistProductReport(emptyReport.copy(distFiles = listOf(DistFileRow(null, null, null, controlPath)))) }
      .hasMessageContaining("Invalid distribution path")
    assertThatThrownBy { readDevDistProductReport(reportYaml(distFiles = "[{paths: [\"lib/line\\nfile\"]}]")) }
      .hasMessageContaining("Invalid distribution path")
  }

  private fun reportYaml(
    bundledPlugins: String = "[]",
    nonBundledPlugins: String = "[]",
    platformContentModules: String = "[]",
    distFiles: String = "[]",
  ): String {
    return "bundledPlugins: $bundledPlugins\nnonBundledPlugins: $nonBundledPlugins\n" +
           "platformContentModules: $platformContentModules\ndistFiles: $distFiles\n"
  }
}
