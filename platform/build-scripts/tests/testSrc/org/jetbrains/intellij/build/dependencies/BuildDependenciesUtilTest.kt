// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies

import org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil.asText
import org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil.createDocumentBuilder
import org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil.getSingleChildElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

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
}
