// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.util.io.URLUtil
import org.jetbrains.intellij.build.impl.CompoundRunConfigurationProperties
import org.junit.Assert.assertEquals
import org.junit.Test

class CompoundRunConfigurationPropertiesTest {
  @Test
  fun smoke() {
    val properties = loadRunConfiguration("test_compound.xml")
    assertEquals("test_compound", properties.name)
    assertEquals(listOf("Clang tests", "IntelliJ Project Structure tests"), properties.toRun)
  }

  @Suppress("SameParameterValue")
  private fun loadRunConfiguration(fileName: String): CompoundRunConfigurationProperties {
    val url = CompoundRunConfigurationPropertiesTest::class.java.getResource("runConfigurations/$fileName")
    return CompoundRunConfigurationProperties.loadRunConfiguration(URLUtil.urlToFile(url!!).toPath())
  }
}