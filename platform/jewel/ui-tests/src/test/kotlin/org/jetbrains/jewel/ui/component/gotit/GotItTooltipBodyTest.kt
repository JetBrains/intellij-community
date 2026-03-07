// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component.gotit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalTextApi::class)
class GotItTooltipBodyTest {
    private val linkColor = Color(0xFFFF0000)
    private val codeColor = Color(0xFF00FF00)
    private val codeBg = Color(0xFF0000FF)

    private val colors =
        GotItColors(
            foreground = Color.Black,
            background = Color.White,
            stepForeground = Color.Black,
            secondaryActionForeground = Color.Black,
            headerForeground = Color.Black,
            balloonBorderColor = Color.Black,
            imageBorderColor = Color.Black,
            link = linkColor,
            codeForeground = codeColor,
            codeBackground = codeBg,
        )

    @Test
    fun `empty body produces empty annotated string`() {
        val body = buildGotItBody {}
        val result = buildBodyAnnotatedString(body, colors)
        assertEquals("", result.text)
    }

    @Test
    fun `plain segment produces its text`() {
        val body = buildGotItBody { append("Hello") }
        val result = buildBodyAnnotatedString(body, colors)
        assertEquals("Hello", result.text)
    }

    @Test
    fun `bold segment text is included`() {
        val body = buildGotItBody { bold("World") }
        val result = buildBodyAnnotatedString(body, colors)
        assertEquals("World", result.text)
    }

    @Test
    fun `code segment text is included`() {
        val body = buildGotItBody { code("fn()") }
        val result = buildBodyAnnotatedString(body, colors)
        assertEquals("fn()", result.text)
    }

    @Test
    fun `should not append code segment if the text is empty`() {
        val body = buildGotItBody {
            append("empty code: ")
            code("")
        }
        val result = buildBodyAnnotatedString(body, colors)
        assertEquals("empty code: ", result.text)
    }

    @Test
    fun `inline link segment text is included`() {
        val body = buildGotItBody { link("click") {} }
        val result = buildBodyAnnotatedString(body, colors)
        assertEquals("click", result.text)
    }

    @Test
    fun `browser link segment text is included`() {
        val body = buildGotItBody { browserLink("docs", "https://example.com") }
        val result = buildBodyAnnotatedString(body, colors)
        assertEquals("docs", result.text)
    }

    @Test
    fun `multiple segments concatenate in order`() {
        val body = buildGotItBody {
            append("A")
            bold("B")
            code("C")
        }
        val result = buildBodyAnnotatedString(body, colors)
        assertEquals("ABC", result.text)
    }

    @Test
    fun `plain segment has no span styles`() {
        val body = buildGotItBody { append("Hello") }
        val result = buildBodyAnnotatedString(body, colors)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun `bold segment has FontWeight Bold span covering the full text`() {
        val body = buildGotItBody { bold("World") }
        val result = buildBodyAnnotatedString(body, colors)
        assertEquals(1, result.spanStyles.size)
        assertEquals(FontWeight.Bold, result.spanStyles[0].item.fontWeight)
        assertEquals(0, result.spanStyles[0].start)
        assertEquals("World".length, result.spanStyles[0].end)
    }

    @Test
    fun `code segment produces an inline content annotation spanning its text`() {
        val body = buildGotItBody { code("fn()") }
        val result = buildBodyAnnotatedString(body, colors)
        val annotations = result.getStringAnnotations(0, result.length)
        assertEquals(1, annotations.size)
        assertEquals(0, annotations[0].start)
        assertEquals("fn()".length, annotations[0].end)
    }

    @Test
    fun `code segment has no span styles`() {
        val body = buildGotItBody { code("fn()") }
        val result = buildBodyAnnotatedString(body, colors)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun `bold and plain segments produce only one span scoped to the bold text`() {
        val body = buildGotItBody {
            append("plain")
            bold("bold")
        }
        val result = buildBodyAnnotatedString(body, colors)
        assertEquals(1, result.spanStyles.size)
        assertEquals("plain".length, result.spanStyles[0].start)
        assertEquals("plain".length + "bold".length, result.spanStyles[0].end)
    }

    @Test
    fun `inline link has a Clickable link annotation`() {
        val body = buildGotItBody { link("click me") {} }
        val result = buildBodyAnnotatedString(body, colors)
        val links = result.getLinkAnnotations(0, result.length)
        assertEquals(1, links.size)
        assertTrue(links[0].item is LinkAnnotation.Clickable)
    }

    @Test
    fun `inline link Clickable tag equals segment text`() {
        val body = buildGotItBody { link("click me") {} }
        val result = buildBodyAnnotatedString(body, colors)
        val links = result.getLinkAnnotations(0, result.length)
        assertEquals("click me", (links[0].item as LinkAnnotation.Clickable).tag)
    }

    @Test
    fun `inline link annotation spans the link text exactly`() {
        val body = buildGotItBody { link("click me") {} }
        val result = buildBodyAnnotatedString(body, colors)
        val links = result.getLinkAnnotations(0, result.length)
        assertEquals(0, links[0].start)
        assertEquals("click me".length, links[0].end)
    }

    @Test
    fun `browser link has a Url link annotation`() {
        val body = buildGotItBody { browserLink("docs", "https://example.com") }
        val result = buildBodyAnnotatedString(body, colors)
        val links = result.getLinkAnnotations(0, result.length)
        assertEquals(1, links.size)
        assertTrue(links[0].item is LinkAnnotation.Url)
    }

    @Test
    fun `browser link Url matches the provided uri`() {
        val body = buildGotItBody { browserLink("docs", "https://example.com") }
        val result = buildBodyAnnotatedString(body, colors)
        val links = result.getLinkAnnotations(0, result.length)
        assertEquals("https://example.com", (links[0].item as LinkAnnotation.Url).url)
    }

    @Test
    fun `icon segment text uses contentDescription as alternate text`() {
        val body = buildGotItBody { icon("my icon") {} }
        val result = buildBodyAnnotatedString(body, colors)
        assertEquals("my icon", result.text)
    }

    @Test
    fun `icon segment with null contentDescription uses replacement char`() {
        val body = buildGotItBody { icon(null) {} }
        val result = buildBodyAnnotatedString(body, colors)
        assertEquals("\uFFFD", result.text)
    }

    @Test
    fun `icon segment produces an inline content annotation spanning the alternate text`() {
        val body = buildGotItBody { icon("star") {} }
        val result = buildBodyAnnotatedString(body, colors)
        val annotations = result.getStringAnnotations(0, result.length)
        assertEquals(1, annotations.size)
        assertEquals(0, annotations[0].start)
        assertEquals("star".length, annotations[0].end)
    }

    @Test
    fun `icon segment has no span styles`() {
        val body = buildGotItBody { icon("star") {} }
        val result = buildBodyAnnotatedString(body, colors)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun `two icon segments at different positions get distinct annotation ids`() {
        val body = buildGotItBody {
            icon("first") {}
            icon("second") {}
        }
        val result = buildBodyAnnotatedString(body, colors)
        val annotations = result.getStringAnnotations(0, result.length)
        assertEquals(2, annotations.size)
        assertTrue(
            "Expected distinct annotation ids but got: ${annotations.map { it.item }}",
            annotations[0].item != annotations[1].item,
        )
    }
}
