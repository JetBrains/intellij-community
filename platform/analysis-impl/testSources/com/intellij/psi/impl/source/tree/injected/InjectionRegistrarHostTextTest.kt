// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.injected

import com.intellij.openapi.util.TextRange
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.impl.FakePsiElement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class InjectionRegistrarHostTextTest {
  @Test
  fun `places of one host share one host text`() {
    val host = CopyingHost("host text")
    val first = place(host, emptyList())
    val second = place(host, listOf(first))
    val third = place(host, listOf(first, second))

    assertSame(first.hostText, second.hostText)
    assertSame(first.hostText, third.hostText)
  }

  @Test
  fun `a place of another host gets the text of its own host`() {
    val first = place(CopyingHost("first host"), emptyList())
    val second = place(CopyingHost("second host"), listOf(first))

    assertEquals("second host", second.hostText)
  }

  private fun place(host: PsiLanguageInjectionHost, previous: List<PlaceInfo>): PlaceInfo =
    PlaceInfo("", "", host, TextRange(0, 1), InjectionRegistrarImpl.hostText(host, previous))

  /**
   * A host whose [getText] builds a new string on each call, like a composite host.
   */
  private class CopyingHost(private val text: String) : FakePsiElement(), PsiLanguageInjectionHost {
    override fun getParent(): PsiElement? = null

    override fun getText(): String = String(text.toCharArray())

    override fun isValidHost(): Boolean = true

    override fun updateText(text: String): PsiLanguageInjectionHost = this

    override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost> = LiteralTextEscaper.createSimple(this)
  }
}
