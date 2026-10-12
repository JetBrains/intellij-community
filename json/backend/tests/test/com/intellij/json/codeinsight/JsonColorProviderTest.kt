// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.codeinsight

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.LineMarkerSettings
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.ElementColorProvider
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightProjectFixture
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.ColorLineMarkerProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Color

@TestApplication
class JsonColorProviderTest {
  companion object {
    private val projectFixture = codeInsightProjectFixture()
  }

  private val fixture by codeInsightFixture(projectFixture)
  private val provider = JsonColorProvider()

  @Test
  fun `previews hex and RGB colors`() = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val cases = listOf(
        "#f00" to Color.RED,
        "#FF0000" to Color.RED,
        "#f008" to Color(255, 0, 0, 136),
        "#ff000080" to Color(255, 0, 0, 128),
        "RGB(255, 0, 0)" to Color.RED,
        "rgb(100% 0% 0%)" to Color.RED,
        "rgba(255, 0, 0, 0.5)" to Color(255, 0, 0, 128),
        "rgb(255 0 0 / 50%)" to Color(255, 0, 0, 128),
        "rgba(100%, 0%, 0%, 50%)" to Color(255, 0, 0, 128),
        "rgb(2.55e2 0 0 / .5)" to Color(255, 0, 0, 128),
        "rgb(-10 300 0)" to Color.GREEN,
        "rgb(0 0 0 / 0)" to Color(0, 0, 0, 0),
      )
      for ((text, color) in cases) {
        fixture.configureByText("colors.json", "[\"$text\"]")
        assertEquals(color, provider.getColorFrom(literalLeaf()), text)
      }
      fixture.configureByText("colors.json", "[\"\\u0023ff0000\"]")
      assertEquals(Color.RED, provider.getColorFrom(literalLeaf()))
    }
  }

  @Test
  fun `ignores property names theme files and invalid values`() = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      fixture.configureByText("colors.json", "{\"#ff0000\": \"#00ff00\"}")
      val literals = PsiTreeUtil.findChildrenOfType(fixture.file, JsonStringLiteral::class.java).toList()
      assertNull(provider.getColorFrom(literals[0].firstChild))
      assertEquals(Color.GREEN, provider.getColorFrom(literals[1].firstChild))

      fixture.configureByText("colors.theme.json", "{\"color\": \"#ff0000\"}")
      assertNull(provider.getColorFrom(literalLeaf()))

      val invalidValues = listOf(
        "hsl(0 100% 50%)", "rgb(1, 0%, 0)", "rgb(1,2,3 / .5)", "rgb(1 2)", "rgb(1e999 0 0)", "red", "#12", "#12345",
        "#ggg", "rgb()", "rgb(1, 2)", "#fff #000", "rgb(1,2,3);", "url(#fff)",
      )
      for (text in invalidValues) {
        fixture.configureByText("colors.json", "[\"$text\"]")
        assertNull(provider.getColorFrom(literalLeaf()), text)
      }
    }
  }

  @Test
  fun `picker changes preserve JSON and support repeated updates`() = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      fixture.configureByText("colors.json", "{\"color\": \"#ff000080\", \"other\": 42}")
      val element = literalLeaf()
      provider.setColorTo(element, Color(0, 255, 0, 128))
      fixture.checkResult("{\"color\": \"#00ff0080\", \"other\": 42}")
      provider.setColorTo(element, Color.BLUE)
      fixture.checkResult("{\"color\": \"#0000ffff\", \"other\": 42}")

      fixture.configureByText("colors.json", "[\"rgb(255 0 0)\"]")
      provider.setColorTo(literalLeaf(), Color.BLUE)
      fixture.checkResult("[\"rgb(0 0 255)\"]")
      val rgbElement = literalLeaf()
      provider.setColorTo(rgbElement, Color(0, 255, 0, 128))
      assertEquals(Color(0, 255, 0, 128), provider.getColorFrom(literalLeaf()))
      provider.setColorTo(rgbElement, Color.RED)
      fixture.checkResult("[\"rgb(255 0 0)\"]")
    }
  }

  @Test
  fun `picker changes preserve legacy RGB syntax`() = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val values = listOf("rgb(255, 0, 0)", "rgba(255, 0, 0, 0.5)", "rgb(100%, 0%, 0%)", "RGBA(100%, 0%, 0%, 50%)")
      for (value in values) {
        fixture.configureByText("colors.json", "{\"color\": \"$value\", \"other\": 42}")
        val element = literalLeaf()
        provider.setColorTo(element, Color.BLUE)
        fixture.checkResult("{\"color\": \"rgb(0, 0, 255)\", \"other\": 42}")
        assertEquals(Color.BLUE, provider.getColorFrom(literalLeaf()), value)

        provider.setColorTo(element, Color(0, 255, 0, 128))
        fixture.checkResult("{\"color\": \"rgba(0, 255, 0, 0.5019607843137255)\", \"other\": 42}")
        assertEquals(Color(0, 255, 0, 128), provider.getColorFrom(literalLeaf()), value)

        provider.setColorTo(element, Color.RED)
        fixture.checkResult("{\"color\": \"rgb(255, 0, 0)\", \"other\": 42}")
        assertEquals(Color.RED, provider.getColorFrom(literalLeaf()), value)
      }
    }
  }

  @Test
  fun `gutter preview uses the color setting without CSS injection`() = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      assertTrue(ElementColorProvider.EP_NAME.extensionList.any { it is JsonColorProvider })
      fixture.configureByText("colors.json", "[\"#ff0000\"]")
      val literal = literalLeaf().parent as PsiLanguageInjectionHost
      assertNull(InjectedLanguageManager.getInstance(fixture.project).getInjectedPsiFiles(literal))
      val settings = LineMarkerSettings.getSettings()
      val wasEnabled = settings.isEnabled(ColorLineMarkerProvider.INSTANCE)
      try {
        settings.setEnabled(ColorLineMarkerProvider.INSTANCE, true)
        assertEquals(1, fixture.findAllGutters().size)
        settings.setEnabled(ColorLineMarkerProvider.INSTANCE, false)
        DaemonCodeAnalyzer.getInstance(fixture.project).restart(fixture.file, "Color preview setting changed")
        assertTrue(fixture.findAllGutters().isEmpty())
      }
      finally {
        settings.setEnabled(ColorLineMarkerProvider.INSTANCE, wasEnabled)
      }
    }
  }

  private fun literalLeaf(): PsiElement =
    PsiTreeUtil.findChildrenOfType(fixture.file, JsonStringLiteral::class.java).last { !it.isPropertyName }.firstChild
}
