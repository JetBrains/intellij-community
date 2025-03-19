// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.io.URLUtil
import org.jetbrains.intellij.build.impl.JUnitRunConfigurationProperties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class JUnitRunConfigurationPropertiesTest {
  @JvmField
  @Rule
  val tempDir = TempDirectory()

  @Test
  fun `load test class`() {
    val properties = loadRunConfiguration("test_class.xml")
    assertEquals("test class", properties.name)
    assertEquals("main-module", properties.moduleName)
    assertEquals(listOf("com.example.test.MainTest"), properties.testClassPatterns)
    assertEquals(listOf("-ea", "-Xmx512m"), properties.vmParameters)
    assertEquals(listOf("artifact1", "artifact2"), properties.requiredArtifacts)
    assertEquals(mapOf("foo" to "1", "bar" to "2"), properties.envVariables)
  }

  @Test
  fun `load test package`() {
    val properties = loadRunConfiguration("test_package.xml")
    assertEquals("test package", properties.name)
    assertEquals("main-module", properties.moduleName)
    assertEquals(listOf("com.example.test.*"), properties.testClassPatterns)
    assertEquals(listOf("-ea"), properties.vmParameters)
    assertEquals(emptyList<String>(), properties.requiredArtifacts)
  }

  @Test
  fun `load test pattern`() {
    val properties = loadRunConfiguration("test_pattern.xml")
    assertEquals("test pattern", properties.name)
    assertEquals("main-module", properties.moduleName)
    assertEquals(listOf("com.example.Test", "com.example.package..*"), properties.testClassPatterns)
    assertEquals(listOf("-ea", "-Dintellij.build.test.patterns.escaped=true"), properties.vmParameters)
    assertEquals(emptyList<String>(), properties.requiredArtifacts)
  }

  @Test(expected = RuntimeException::class)
  fun `load test with method fork mode`() {
    loadRunConfiguration("test_method_fork_mode.xml")
  }

  @Test
  fun `load default options`() {
    val properties = loadRunConfiguration("test_default_options.xml")
    assertEquals(listOf("com.example.test.MainTest"), properties.testClassPatterns)
    assertEquals(listOf("-ea"), properties.vmParameters)
  }

  private fun loadRunConfiguration(fileName: String): JUnitRunConfigurationProperties {
    val url = JUnitRunConfigurationPropertiesTest::class.java.getResource("runConfigurations/$fileName")
    assertNotNull(url)
    val file = if (url!!.protocol == URLUtil.JAR_PROTOCOL) {
      tempDir.newFile(fileName, url.openStream().use { it.readBytes() })
    }
    else {
      URLUtil.urlToFile(url)
    }
    return JUnitRunConfigurationProperties.loadRunConfiguration(file.toPath())
  }
}