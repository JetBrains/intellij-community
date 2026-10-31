// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayActionPayload
import com.intellij.codeInsight.hints.declarative.InlayPayload
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.StringInlayActionPayload
import com.intellij.codeInsight.hints.declarative.impl.DeclarativeInlayHintsPass
import com.intellij.codeInsight.hints.declarative.impl.InlayTreeSinkImpl
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

@TestApplication
internal class DeclarativeHintsNecromancyTest {
  @Test
  fun `unknown payload type is logged and skipped`() {
    val sink = InlayTreeSinkImpl("test.provider", emptyMap(), false, false, DeclarativeHintsNecromancyTest::class.java,
                                 DeclarativeInlayHintsPass.passSourceId)
    val payloads = listOf(InlayPayload("unknown", UnknownPayload), InlayPayload("string", StringInlayActionPayload("value")))
    sink.addPresentation(InlineInlayPosition(1, false), payloads, hintFormat = HintFormat.default) {
      text("node")
    }
    val inlayData = sink.finish().single()

    val errors = ArrayList<String>()
    val errorCollector = object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> {
        errors.add(message)
        return Action.NONE
      }
    }
    val bytes = ByteArrayOutputStream()
    LoggedErrorProcessor.executeWith(errorCollector).use {
      DataOutputStream(bytes).use { DeclarativeHintsNecromancy.buryZombie(it, DeclarativeHintsZombie(listOf(inlayData))) }
    }
    val restored = DeclarativeHintsNecromancy.exhumeZombie(DataInputStream(ByteArrayInputStream(bytes.toByteArray()))).limbs().single()

    assertEquals(1, errors.size, errors.toString())
    assertEquals(listOf("string"), restored.payloads.map { it.payloadName })
    assertEquals("value", (restored.payloads.single().payload as StringInlayActionPayload).text)
  }

  private object UnknownPayload : InlayActionPayload
}
