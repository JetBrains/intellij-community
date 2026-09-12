package com.intellij.platform.lsp.unit

import com.intellij.platform.lsp.impl.features.documentation.LspDocumentationData
import com.intellij.platform.lsp.impl.features.documentation.createLspDocumentationData
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class LspDocumentationDataTest {
  private fun markdown(text: String): LspDocumentationData = createLspDocumentationData(MarkupContent(MarkupKind.MARKDOWN, text))

  @Test
  fun `triple backtick fence splits into definition and description`() {
    val data = markdown("```kotlin\nfun foo()\n```\nDoc")
    assertEquals("fun foo()", data.definitionCodeBlock)
    assertEquals("kotlin", data.definitionLanguage)
    assertEquals("\nDoc", data.description)
    assertEquals(LspDocumentationData.DescriptionMarkup.MARKDOWN, data.descriptionMarkup)
  }

  @Test
  fun `quadruple backtick fence leaves no backtick in the description`() {
    val data = markdown("````kotlin\nprivate fun bundledEulaFile(): Path\n````\n")
    assertEquals("private fun bundledEulaFile(): Path", data.definitionCodeBlock)
    assertEquals("kotlin", data.definitionLanguage)
    assertTrue(data.description.isNullOrBlank(), "description: '${data.description}'")
  }

  @Test
  fun `backticks inside the code stay in the definition`() {
    val data = markdown("````kotlin\nval s = ```\n````\nDoc")
    assertEquals("val s = ```", data.definitionCodeBlock)
    assertEquals("\nDoc", data.description)
  }

  @Test
  fun `closing fence may be longer than the opening one`() {
    val data = markdown("```\ncode\n`````\nDoc")
    assertEquals("code", data.definitionCodeBlock)
    assertNull(data.definitionLanguage)
    assertEquals("\nDoc", data.description)
  }

  @Test
  fun `text without a leading fence is a markdown description`() {
    val data = markdown("Plain **doc**\n```\ncode\n```")
    assertNull(data.definitionCodeBlock)
    assertEquals("Plain **doc**\n```\ncode\n```", data.description)
    assertEquals(LspDocumentationData.DescriptionMarkup.MARKDOWN, data.descriptionMarkup)
  }

  @Test
  fun `unclosed fence is a markdown description`() {
    val data = markdown("```kotlin\nfun foo()")
    assertNull(data.definitionCodeBlock)
    assertEquals("```kotlin\nfun foo()", data.description)
  }

  @Test
  fun `plain text markup is kept as plain text`() {
    val data = createLspDocumentationData(MarkupContent(MarkupKind.PLAINTEXT, "```not a fence```"))
    assertNull(data.definitionCodeBlock)
    assertEquals("```not a fence```", data.description)
    assertEquals(LspDocumentationData.DescriptionMarkup.PLAIN, data.descriptionMarkup)
  }
}
