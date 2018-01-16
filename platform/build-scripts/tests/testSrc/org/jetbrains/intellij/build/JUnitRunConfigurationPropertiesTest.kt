/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.intellij.build

import com.intellij.util.io.URLUtil
import org.jetbrains.intellij.build.impl.JUnitRunConfigurationProperties
import org.junit.Assert.assertEquals
import org.junit.Test

class JUnitRunConfigurationPropertiesTest {
  @Test
  fun `load test class`() {
    val properties = loadRunConfiguration("test_class.xml")
    assertEquals("test class", properties.name)
    assertEquals("main-module", properties.moduleName)
    assertEquals(listOf("com.example.test.MainTest"), properties.testClassPatterns)
    assertEquals(listOf("-ea", "-Xmx512m"), properties.vmParameters)
    assertEquals(listOf("artifact1", "artifact2"), properties.requiredArtifacts)
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
    assertEquals(listOf("-ea"), properties.vmParameters)
    assertEquals(emptyList<String>(), properties.requiredArtifacts)
  }

  private fun loadRunConfiguration(fileName: String): JUnitRunConfigurationProperties {
    val url = JUnitRunConfigurationPropertiesTest::class.java.getResource("runConfigurations/$fileName")
    return JUnitRunConfigurationProperties.loadRunConfiguration(URLUtil.urlToFile(url), MockBuildMessages())
  }
}