// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import com.intellij.util.io.URLUtil
import org.jetbrains.intellij.build.impl.KotlinRunConfigurationProperties
import org.junit.Assert
import org.junit.Test


/**
 * @author Aleksey.Rostovskiy
 */
class KotlinRunConfigurationPropertiesTest {
  @Test
  fun `load configuration`() {
    val properties = loadRunConfiguration("kotlin_configuration.xml")
    Assert.assertEquals("TestRunConfiguration", properties.name)
    Assert.assertEquals("example.module", properties.moduleName)
    Assert.assertEquals("com.example.classKt", properties.mainClassName)
    Assert.assertEquals(listOf("-ea", "-Xmx512m"), properties.vmParameters)
    Assert.assertEquals(mapOf("foo" to "1", "bar" to "2"), properties.envVariables)
  }

  private fun loadRunConfiguration(fileName: String): KotlinRunConfigurationProperties {
    val url = KotlinRunConfigurationPropertiesTest::class.java.getResource("runConfigurations/$fileName")
    return KotlinRunConfigurationProperties.loadRunConfiguration(URLUtil.urlToFile(url), MockBuildMessages())
  }
}