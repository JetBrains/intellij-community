// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests for [ListTemplatesHandler.filterTemplatesByPrefix], the prefix filter shared by the live template completion
 * contributor, the mod-completion provider and the explicit "Insert Live Template" action.
 *
 * The filter must behave identically for a local client and for a remote one (Code With Me / split mode):
 * the prefix is taken from the document text, which is the same on both sides. See IJPL-257111, where a remote-only
 * branch made every applicable template match an empty prefix, and CWM-1051, which that branch tried to fix.
 */
@TestApplication
internal class ListTemplatesHandlerFilterTest {
  private val templates = listOf(
    newTemplate("dep"),
    newTemplate("repo"),
    newTemplate("T"),
  )

  /** IJPL-257111: typing `org.` inside a `<groupId>` leaves an empty prefix, so no template may match. */
  @Test
  fun `no templates after a dot for a local client`() {
    assertEquals(emptyMap<TemplateImpl, String>(), filterAtEndOf("<groupId>org."))
  }

  /** IJPL-257111: the remote client must not see the whole template list just because the prefix is empty. */
  @Test
  fun `no templates after a dot for a remote client`() {
    assertEquals(emptyMap<TemplateImpl, String>(), underRemoteClientId { filterAtEndOf("<groupId>org.") })
  }

  @Test
  fun `no templates after a tag start for a local client`() {
    assertEquals(emptyMap<TemplateImpl, String>(), filterAtEndOf("<groupId>"))
  }

  @Test
  fun `no templates after a tag start for a remote client`() {
    assertEquals(emptyMap<TemplateImpl, String>(), underRemoteClientId { filterAtEndOf("<groupId>") })
  }

  /** CWM-1051: a typed prefix must still match, for a local client... */
  @Test
  fun `typed prefix matches for a local client`() {
    assertEquals(mapOf(templateFor("dep") to "dep"), filterAtEndOf("<groupId>dep"))
    assertEquals(mapOf(templateFor("dep") to "de"), filterAtEndOf("<groupId>de"))
    assertEquals(mapOf(templateFor("repo") to "repo"), filterAtEndOf("<groupId>repo"))
    assertEquals(mapOf(templateFor("T") to "T"), filterAtEndOf("<groupId>T"))
  }

  /** ...and for a remote one, which is what CWM-1051 was about. */
  @Test
  fun `typed prefix matches for a remote client`() {
    underRemoteClientId {
      assertEquals(mapOf(templateFor("dep") to "dep"), filterAtEndOf("<groupId>dep"))
      assertEquals(mapOf(templateFor("dep") to "de"), filterAtEndOf("<groupId>de"))
      assertEquals(mapOf(templateFor("repo") to "repo"), filterAtEndOf("<groupId>repo"))
      assertEquals(mapOf(templateFor("T") to "T"), filterAtEndOf("<groupId>T"))
    }
  }

  /** A prefix that continues an identifier is not a template prefix, locally or remotely. */
  @Test
  fun `prefix inside an identifier does not match`() {
    assertEquals(emptyMap<TemplateImpl, String>(), filterAtEndOf("mydep"))
    assertEquals(emptyMap<TemplateImpl, String>(), underRemoteClientId { filterAtEndOf("mydep") })
  }

  /**
   * The explicit "Insert Live Template" action passes `searchInDescription = true`; an empty prefix must not match
   * descriptions either. That action shows the full list through its own fallback in
   * [ListTemplatesHandler.invoke], not through this filter.
   */
  @Test
  fun `empty prefix does not match descriptions`() {
    val described = newTemplate("dep").also { it.description = "dependency" }
    assertEquals(emptyMap<TemplateImpl, String>(), filterAtEndOf("<groupId>org.", listOf(described), searchInDescription = true))
    assertEquals(emptyMap<TemplateImpl, String>(),
                 underRemoteClientId { filterAtEndOf("<groupId>org.", listOf(described), searchInDescription = true) })
  }

  private fun filterAtEndOf(
    text: String,
    templates: List<TemplateImpl> = this.templates,
    searchInDescription: Boolean = false,
  ): Map<TemplateImpl, String> {
    val document: Document = EditorFactory.getInstance().createDocument(text)
    return ListTemplatesHandler.filterTemplatesByPrefix(templates, document, text.length, false, searchInDescription)
  }

  private fun templateFor(key: String): TemplateImpl = templates.single { it.key == key }

  private fun <T> underRemoteClientId(action: () -> T): T =
    ClientId.withExplicitClientId(ClientId("IJPL-257111-remote-client"), action)

  private fun newTemplate(key: String): TemplateImpl = TemplateImpl(key, "<$key>\$END\$</$key>", "test")
}
