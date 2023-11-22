// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore.xml

import com.intellij.application.options.PathMacrosImpl
import com.intellij.configurationStore.XmlDataWriter
import com.intellij.openapi.components.PathMacroManager
import com.intellij.testFramework.ApplicationRule
import org.junit.Assert.assertEquals
import org.junit.ClassRule
import org.junit.Test
import java.io.StringWriter

class XmlDataWriterTest {
  @Test
  fun `xml data writer escapes ampersand correctly`() {
    val manager = object : PathMacroManager(PathMacrosImpl()) {}
    val dataWriter = XmlDataWriter("test", emptyList(), mapOf("path" to "/hey/R&D"), manager, "")
    val target = StringWriter()

    dataWriter.write(target, "\n", null)

    assertEquals("<test path=\"/hey/R&amp;D\" />", target.toString())
  }

  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }
}
