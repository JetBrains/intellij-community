// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision

import com.intellij.codeInsight.codeVision.ui.model.CounterCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.TextCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.ZombieCodeVisionEntry
import com.intellij.codeInsight.hints.codeVision.CodeVisionLimb
import com.intellij.codeInsight.hints.codeVision.CodeVisionZombie
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

@TestApplication
internal class CodeVisionZombieTest {
  @Test
  fun `text entry keeps its short text and long presentation`() {
    val entry = TextCodeVisionEntry(
      text = "usages",
      providerId = "Usages",
      longPresentation = "Usages not available",
      tooltip = "Solution-wide analysis is off",
    )

    val restored = restore(entry)

    assertEquals("usages", restored.text)
    assertEquals("Usages not available", restored.longPresentation)
    assertEquals("Solution-wide analysis is off", restored.tooltip)
    assertNull(restored.count)
  }

  @Test
  fun `counter entry keeps its count separate from its short text`() {
    val entry = CounterCodeVisionEntry(
      count = 3,
      text = "usages",
      providerId = "Usages",
      icon = null,
      longPresentation = "3 usages in the project",
      tooltip = "Show usages",
      extraActions = emptyList(),
    )

    val restored = restore(entry)

    assertEquals(3, restored.count)
    assertEquals("usages", restored.text)
    assertEquals("3 usages in the project", restored.longPresentation)
  }

  private fun restore(entry: CodeVisionEntry): ZombieCodeVisionEntry {
    val limb = CodeVisionLimb(TextRange(10, 20) to entry)
    val necromancy = CodeVisionZombie.Necromancy
    val bytes = ByteArrayOutputStream()
    DataOutputStream(bytes).use { output ->
      necromancy.buryZombie(output, necromancy.formZombie(listOf(limb)))
    }
    val restored = DataInputStream(ByteArrayInputStream(bytes.toByteArray())).use { input ->
      necromancy.exhumeZombie(input).limbs().single()
    }
    assertEquals(limb, restored)
    return restored.asEntry()
  }
}
