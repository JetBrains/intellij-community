// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.distributionContent

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ContentReportZipParsingTest {
  @Test
  fun `reads content report zip without module set entries`(@TempDir tempDir: Path) {
    val reportFile = writeContentReportZip(tempDir.resolve("content-report.zip"))

    assertEmptyContent(readContentReportZip(reportFile))
  }

  @Test
  fun `ignores unexpected module set entries in content report zip`(@TempDir tempDir: Path) {
    val reportFile = writeContentReportZip(
      reportFile = tempDir.resolve("content-report.zip"),
      extraEntries = mapOf("moduleSets/broken.yaml" to ": not valid yaml"),
    )

    assertEmptyContent(readContentReportZip(reportFile))
  }

  private fun assertEmptyContent(report: ParsedContentReport) {
    assertThat(report.platform).isEmpty()
    assertThat(report.productModules).isEmpty()
    assertThat(report.bundled).isEmpty()
    assertThat(report.nonBundled).isEmpty()
  }

  private fun writeContentReportZip(reportFile: Path, extraEntries: Map<String, String> = emptyMap()): Path {
    ZipOutputStream(Files.newOutputStream(reportFile)).use { zip ->
      writeEntry(zip = zip, name = "platform.yaml", data = "[]\n")
      writeEntry(zip = zip, name = "product-modules.yaml", data = "[]\n")
      writeEntry(zip = zip, name = "bundled-plugins.yaml", data = "[]\n")
      writeEntry(zip = zip, name = "non-bundled-plugins.yaml", data = "[]\n")
      for ((name, data) in extraEntries) {
        writeEntry(zip = zip, name = name, data = data)
      }
    }
    return reportFile
  }

  private fun writeEntry(zip: ZipOutputStream, name: String, data: String) {
    zip.putNextEntry(ZipEntry(name))
    zip.write(data.toByteArray(StandardCharsets.UTF_8))
    zip.closeEntry()
  }
}
