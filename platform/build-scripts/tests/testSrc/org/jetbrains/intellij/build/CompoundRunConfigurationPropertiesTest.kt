// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.io.URLUtil
import org.jetbrains.intellij.build.impl.CompoundRunConfigurationProperties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class CompoundRunConfigurationPropertiesTest {
  @JvmField
  @Rule
  val tempDir = TempDirectory()

  @Test
  fun smoke() {
    val properties = loadRunConfiguration("test_compound.xml")
    assertEquals("test_compound", properties.name)
    assertEquals(listOf("Clang tests", "IntelliJ Project Structure tests"), properties.toRun)
  }

  @Suppress("SameParameterValue")
  private fun loadRunConfiguration(fileName: String): CompoundRunConfigurationProperties {
    val url = CompoundRunConfigurationPropertiesTest::class.java.getResource("runConfigurations/$fileName")
    assertNotNull(url)
    val file = if (url!!.protocol == URLUtil.JAR_PROTOCOL) {
      tempDir.newFile(fileName, url.openStream().use { it.readBytes() })
    }
    else {
      URLUtil.urlToFile(url)
    }
    return CompoundRunConfigurationProperties.loadRunConfiguration(file.toPath())
  }
}