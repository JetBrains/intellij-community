// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.util.io.write
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.nio.file.Path

class PluginRepositoryXmlGeneratorTest {
  // IJPL-148728
  @Test
  fun testDownloadUrlsAreProperlyEncoded(@TempDir tmpDir: Path) {
    val pluginPath = tmpDir.resolve("auto-uploading/JPA Buddy-251.20241018.zip")
    pluginPath.write("hello".toByteArray())

    PluginRepositoryXmlGeneratorTestUtils.generatePluginRepositoryMetaFile(
      listOf(
        PluginRepositorySpec(pluginPath, """
          <idea-plugin>
            <id>com.example.empty-plugin</id>
            <name>Empty plugin</name>
            <vendor>Me</vendor>
          </idea-plugin>
        """.trimIndent().toByteArray())
      ),
      tmpDir.resolve("custom-repo"),
      "251.239"
    )

    val pluginsXml = tmpDir.resolve("custom-repo/plugins.xml").toUri().toURL()
    val expectedDownloadUrl = "../auto-uploading/JPA%20Buddy-251.20241018.zip"

    Assertions.assertEquals("""
        <?xml version="1.0" encoding="UTF-8"?>
        <plugin-repository>
          <category name="Misc">
        <idea-plugin size="5">
          <name>Empty plugin</name>
          <id>com.example.empty-plugin</id>
          <version>null</version>
          <idea-version since-build="251.239" until-build="251.239"/>
          <vendor>Me</vendor>
          <download-url>$expectedDownloadUrl</download-url>
          
          
        </idea-plugin>  </category>
        </plugin-repository>

    """.trimIndent(), pluginsXml.readText())

    val downloadUrl = URL(pluginsXml, expectedDownloadUrl)
    Assertions.assertTrue(downloadUrl.readBytes().isNotEmpty(), "Downloading by relative path should work")
    Assertions.assertDoesNotThrow { URI(expectedDownloadUrl) }

    // the following works, yet URL spec prohibits unencoded spaces
    // the assertion is here mostly to be notified if this behavior ever changes (which is unlikely)
    // however, if it does, it may cause a regression
    val literalSpaceDownloadUrl = expectedDownloadUrl.replace("%20", " ")
    Assertions.assertDoesNotThrow {
      val wrongUrl = URL(pluginsXml, literalSpaceDownloadUrl)
      Assertions.assertTrue(wrongUrl.readBytes().isNotEmpty())
    }
    Assertions.assertThrows(URISyntaxException::class.java) {
      URI(literalSpaceDownloadUrl)
    }
  }
}