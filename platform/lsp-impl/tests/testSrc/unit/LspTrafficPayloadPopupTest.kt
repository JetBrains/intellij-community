package com.intellij.platform.lsp.unit

import com.intellij.platform.lsp.api.LspBundle
import com.intellij.platform.lsp.impl.serviceView.buildPayloadPopupText
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

@TestApplication
internal class LspTrafficPayloadPopupTest {
  @Test
  fun `valid json payload is pretty printed`() {
    val text = buildPayloadPopupText("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"processId":42}}""", truncated = false)
    assertEquals(
      """
      {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "initialize",
        "params": {
          "processId": 42
        }
      }
      """.trimIndent(),
      text)
  }

  @Test
  fun `html is not escaped in pretty printed payload`() {
    val text = buildPayloadPopupText("""{"text":"a<b>&c"}""", truncated = false)
    assertEquals(
      """
      {
        "text": "a<b>&c"
      }
      """.trimIndent(),
      text)
  }

  @Test
  fun `malformed json payload is shown as is`() {
    val malformed = """{"jsonrpc": "2.0", "id": 1, "method"""
    assertEquals(malformed, buildPayloadPopupText(malformed, truncated = false))
  }

  @Test
  fun `truncated payload gets a truncation notice`() {
    val text = buildPayloadPopupText("""{"id":1}""", truncated = true)
    val notice = LspBundle.message("services.lsp.traffic.popup.payload.truncated")
    assertEquals(
      """
      {
        "id": 1
      }

      $notice
      """.trimIndent(),
      text)
  }

  @Test
  fun `non-truncated payload has no truncation notice`() {
    assertFalse("truncated" in buildPayloadPopupText("""{"id":1}""", truncated = false))
  }
}
