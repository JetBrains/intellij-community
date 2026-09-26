package com.intellij.platform.lsp.unit

import com.intellij.platform.lsp.impl.serviceView.trafficPayloadPreview
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class LspTrafficPayloadPreviewTest {
  @Test
  fun `compact payload passes through unchanged`() {
    val compact = """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"processId":42}}"""
    assertEquals(compact, trafficPayloadPreview(compact))
  }

  @Test
  fun `multi-line payload collapses to one line`() {
    val multiLine = "{\n  \"jsonrpc\": \"2.0\",\n  \"id\": 1,\n  \"params\": {\n    \"processId\": 42\n  }\n}\n"
    assertEquals("""{ "jsonrpc": "2.0", "id": 1, "params": { "processId": 42 } }""", trafficPayloadPreview(multiLine))
  }

  @Test
  fun `tabs and carriage returns collapse to single spaces`() {
    assertEquals("""{ "id": 1 }""", trafficPayloadPreview("{\r\n\t\"id\":\t1\r\n}"))
  }

  @Test
  fun `truncation applies to the collapsed payload`() {
    val value = "x".repeat(20)
    val entries = List(1_000) { """"key$it": "$value"""" }
    val multiLine = entries.joinToString(separator = ",\n  ", prefix = "{\n  ", postfix = "\n}")
    val preview = trafficPayloadPreview(multiLine)
    val collapsedLength = entries.joinToString(separator = ", ", prefix = "{ ", postfix = " }").length
    assertTrue(preview.length < multiLine.length)
    assertTrue("… (${collapsedLength - 10_000} more characters)" in preview)
    assertEquals(-1, preview.indexOf('\n'))
  }
}
