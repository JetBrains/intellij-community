// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies

import com.intellij.util.io.createDirectories
import com.intellij.util.io.delete
import org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil.asText
import org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil.createDocumentBuilder
import org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil.getSingleChildElement
import org.jetbrains.intellij.build.impl.tar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.writeText

class BuildDependenciesUtilTest {
  @Test fun normalization() {
    assertEquals("xxx", BuildDependenciesUtil.normalizeEntryName("///////xxx\\"))
    assertEquals("..xxx", BuildDependenciesUtil.normalizeEntryName("///////..xxx"))
  }

  @Test fun validation() {
    assertThrows("Entry names should not be blank", Exception::class.java) { BuildDependenciesUtil.normalizeEntryName("/") }
    assertThrows("Invalid entry name: ../xxx", Exception::class.java) { BuildDependenciesUtil.normalizeEntryName("/../xxx") }
    assertThrows("Normalized entry name should not contain '//': a//xxx", Exception::class.java) { BuildDependenciesUtil.normalizeEntryName("a/\\xxx") }
  }

  @Test
  fun elementAsString() {
    val testContent = """
        <?xml version="1.0" encoding="UTF-8"?>
        <module type="JAVA_MODULE" version="4">
          <component name="NewModuleRootManager" inherit-compiler-output="true">
            <exclude-output />
            <orderEntry type="module-library">
            </orderEntry>
          </component>
        </module>
      """.trimIndent()
    val documentBuilder = createDocumentBuilder()
    val document = documentBuilder.parse(testContent.byteInputStream())
    val component = document.documentElement.getSingleChildElement("component")
    assertEquals("""
      <component inherit-compiler-output="true" name="NewModuleRootManager">
          <exclude-output/>
          <orderEntry type="module-library">
          </orderEntry>
        </component>
    """.trimIndent(), component.asText)
  }

  @Test
  fun `extractTar should handle multiple root directory entries`() {
    val archiveFile = Files.createTempFile("test", ".tar.gz") ?: error("Failed to create temp file")
    val inputDir = Files.createTempDirectory("test-input") ?: error("Failed to create temp directory")
    val outputDir = inputDir.resolve("output")
    val dir1 = inputDir.resolve("dir1")
    val dir2 = inputDir.resolve("dir2")
    val aDir = dir1.resolve("a")
    aDir.createDirectories()
    aDir.resolve("a.txt").writeText("a")
    val bDir = dir2.resolve("b")
    bDir.createDirectories()
    bDir.resolve("b.txt").writeText("b")

    tar(archiveFile, inputDir.name, listOf(dir1, dir2), emptySet(), 0)
    BuildDependenciesUtil.extractTarGz(archiveFile, outputDir, stripRoot = true)

    assertTrue(outputDir.resolve("a").resolve("a.txt").exists())
    assertTrue(outputDir.resolve("b").resolve("b.txt").exists())

    archiveFile.delete()
    inputDir.delete(true)
  }
}
