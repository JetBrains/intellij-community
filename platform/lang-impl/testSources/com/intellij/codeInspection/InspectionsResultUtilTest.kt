// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.openapi.util.JDOMUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Files
import java.nio.file.Path

class InspectionsResultUtilTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `description with illegal character is written as valid XML`() {
    val tool = mock<InspectionToolWrapper<*, *>>()
    whenever(tool.groupDisplayName).thenReturn("Test group")
    whenever(tool.groupPath).thenReturn(emptyArray())
    whenever(tool.shortName).thenReturn("TestInspection")
    whenever(tool.defaultLevel).thenReturn(HighlightDisplayLevel.WARNING)
    whenever(tool.displayName).thenReturn("Test inspection")
    whenever(tool.loadDescription()).thenReturn("<html>Before\u001bAfter</html>")
    val profile = mock<InspectionProfile>()
    whenever(profile.getInspectionTools(null)).thenReturn(listOf(tool))
    val output = tempDir.resolve("descriptions.xml")

    InspectionsResultUtil.describeInspections(output, "Test profile", profile)

    val inspection = JDOMUtil.load(Files.readString(output)).getChild("group").getChild("inspection")
    assertThat(inspection.text).isEqualTo("<html>Before?After</html>")
  }
}
