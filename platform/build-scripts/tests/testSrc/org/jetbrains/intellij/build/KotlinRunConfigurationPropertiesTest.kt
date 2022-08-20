// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.util.io.URLUtil
import org.jetbrains.intellij.build.impl.KotlinRunConfigurationProperties
import org.junit.Assert.assertEquals
import org.junit.Test

class KotlinRunConfigurationPropertiesTest {
  @Test
  fun `load configuration`() {
    val properties = loadRunConfiguration("kotlin_configuration.xml")
    assertEquals("TestRunConfiguration", properties.name)
    assertEquals("example.module", properties.moduleName)
    assertEquals("com.example.classKt", properties.mainClassName)
    assertEquals(listOf("-ea", "-Xmx512m"), properties.vmParameters)
    assertEquals(mapOf("foo" to "1", "bar" to "2"), properties.envVariables)
  }

  private fun loadRunConfiguration(fileName: String): KotlinRunConfigurationProperties {
    val url = KotlinRunConfigurationPropertiesTest::class.java.getResource("runConfigurations/$fileName")
    return KotlinRunConfigurationProperties.loadRunConfiguration(URLUtil.urlToFile(url).toPath())
  }
}