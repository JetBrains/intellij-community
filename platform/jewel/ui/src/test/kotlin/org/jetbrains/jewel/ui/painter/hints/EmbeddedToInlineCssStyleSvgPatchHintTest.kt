// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.painter.hints

import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.jetbrains.jewel.ui.painter.PainterHint
import org.jetbrains.jewel.ui.painter.PainterProviderScope
import org.junit.Assert.fail
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

@Suppress("LargeClass")
internal class EmbeddedToInlineCssStyleSvgPatchHintTest {

    private val hint = EmbeddedToInlineCssStyleSvgPatchHint

    private val noOpPainterScope =
        object : PainterProviderScope {
            override val rawPath: String = ""
            override val path: String = ""
            override val acceptedHints: List<PainterHint> = emptyList()
            override val density: Float = 1f
            override val fontScale: Float = 1f
        }

    private fun inlineEmbeddedCssStyles(element: Element) {
        with(noOpPainterScope) { hint.run { patch(element) } }
    }

    @Test
    fun `inline simple class selector`() {
        doTestEmbeddedCssInlining(
            input =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <style type="text/css">
                        .st0 { fill: red; opacity: 0.5; }
                    </style>
                    <circle class="st0" cx="10" cy="10" r="5"/>
                </svg>
            """,
            expectedOutput =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <circle style="fill:red;opacity:0.5" cx="10" cy="10" r="5"/>
                </svg>
            """,
        )
    }

    @Test
    fun `parse multiple selectors in one rule`() {
        doTestEmbeddedCssInlining(
            input =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <style type="text/css">
                        .st0, .st1 { fill: red; opacity: 0.5; }
                    </style>
                    <circle class="st0" cx="10" cy="10" r="5"/>
                    <rect class="st1" x="0" y="0" width="10" height="10"/>
                </svg>
            """,
            expectedOutput =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <circle style="fill:red;opacity:0.5" cx="10" cy="10" r="5"/>
                    <rect style="fill:red;opacity:0.5" x="0" y="0" width="10" height="10"/>
                </svg>
            """,
        )
    }

    @Test
    fun `parse minified CSS`() {
        doTestEmbeddedCssInlining(
            input =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <style type="text/css">.st0{fill:red;opacity:0.5;}.st1{fill:blue;}</style>
                    <circle class="st0" cx="10" cy="10" r="5"/>
                </svg>
            """,
            expectedOutput =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <circle style="fill:red;opacity:0.5" cx="10" cy="10" r="5"/>
                </svg>
            """,
        )
    }

    @Test
    fun `parse CSS with comments`() {
        doTestEmbeddedCssInlining(
            input =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <style type="text/css">
                        /* This is a comment */
                        .st0 { 
                            fill: red; /* inline comment */ 
                            opacity: 0.5; 
                        }
                        /* Another comment */
                    </style>
                    <circle class="st0" cx="10" cy="10" r="5"/>
                </svg>
            """,
            expectedOutput =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <circle style="fill:red;opacity:0.5" cx="10" cy="10" r="5"/>
                </svg>
            """,
        )
    }

    @Test
    fun `parse CSS with CDATA section`() {
        doTestEmbeddedCssInlining(
            input =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <style type="text/css">
                        <![CDATA[
                        .st0 { fill: red; opacity: 0.5; }
                        ]]>
                    </style>
                    <circle class="st0" cx="10" cy="10" r="5"/>
                </svg>
            """,
            expectedOutput =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <circle style="fill:red;opacity:0.5" cx="10" cy="10" r="5"/>
                </svg>
            """,
        )
    }

    @Test
    fun `parse URL references`() {
        doTestEmbeddedCssInlining(
            input =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <defs>
                        <linearGradient id="gradient1"/>
                    </defs>
                    <style type="text/css">
                        .st0 { fill: url(#gradient1); stroke: orange; }
                    </style>
                    <circle class="st0" cx="10" cy="10" r="5"/>
                </svg>
            """,
            expectedOutput =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <defs>
                        <linearGradient id="gradient1"/>
                    </defs>
                    <circle style="fill:url(#gradient1);stroke:orange" cx="10" cy="10" r="5"/>
                </svg>
            """,
        )
    }

    @Test
    fun `ignore ID selectors`() {
        doTestEmbeddedCssInlining(
            input =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <style type="text/css">
                        .st0 { fill: red; }
                        #myId { fill: blue; }
                    </style>
                    <circle class="st0" id="myId" cx="10" cy="10" r="5"/>
                </svg>
            """,
            expectedOutput =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <circle style="fill:red" id="myId" cx="10" cy="10" r="5"/>
                </svg>
            """,
        )
    }

    @Test
    fun `ignore element selectors`() {
        doTestEmbeddedCssInlining(
            input =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <style type="text/css">
                        .st0 { fill: red; }
                        circle { fill: blue; }
                    </style>
                    <circle class="st0" cx="10" cy="10" r="5"/>
                </svg>
            """,
            expectedOutput =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <circle style="fill:red" cx="10" cy="10" r="5"/>
                </svg>
            """,
        )
    }

    @Test
    fun `ignore attribute selectors`() {
        doTestEmbeddedCssInlining(
            input =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <style type="text/css">
                        .st0 { fill: red; }
                        [cx="10"] { fill: blue; }
                    </style>
                    <circle class="st0" cx="10" cy="10" r="5"/>
                </svg>
            """,
            expectedOutput =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <circle style="fill:red" cx="10" cy="10" r="5"/>
                </svg>
            """,
        )
    }

    @Test
    fun `handle multiple style blocks`() {
        doTestEmbeddedCssInlining(
            input =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <style type="text/css">
                        .st0 { fill: red; }
                    </style>
                    <style type="text/css">
                        .st1 { fill: blue; }
                    </style>
                    <circle class="st0" cx="10" cy="10" r="5"/>
                    <rect class="st1" x="0" y="0" width="10" height="10"/>
                </svg>
            """,
            expectedOutput =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <circle style="fill:red" cx="10" cy="10" r="5"/>
                    <rect style="fill:blue" x="0" y="0" width="10" height="10"/>
                </svg>
            """,
        )
    }

    @Test
    fun `inline multiple classes - later overrides earlier`() {
        doTestEmbeddedCssInlining(
            input =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <style type="text/css">
                        .base-style { fill: green; opacity: 0.5; stroke: purple; }
                        .override-opacity { opacity: 0.9; }
                    </style>
                    <circle class="base-style override-opacity" cx="50" cy="150" r="30"/>
                </svg>
            """,
            expectedOutput =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <circle style="fill:green;opacity:0.9;stroke:purple" cx="50" cy="150" r="30"/>
                </svg>
            """,
        )
    }

    @Test
    fun `inline style takes precedence over class style`() {
        doTestEmbeddedCssInlining(
            input =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <style type="text/css">
                        .blue-rect { fill: blue; stroke: black; stroke-width: 2; }
                    </style>
                    <rect class="blue-rect" style="fill: orange" x="120" y="120" width="60" height="60"/>
                </svg>
            """,
            expectedOutput =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <rect style="fill:blue;stroke:black;stroke-width:2;fill:orange" x="120" y="120" width="60" height="60"/>
                </svg>
            """,
        )
    }

    @Test
    fun `complex cascade - multiple classes and inline`() {
        doTestEmbeddedCssInlining(
            input =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <style type="text/css">
                        .red-circle { fill: red; opacity: 0.8; }
                        .override-opacity { opacity: 0.9; }
                    </style>
                    <circle class="red-circle override-opacity" style="stroke: white; stroke-width: 4" cx="50" cy="250" r="30"/>
                </svg>
            """,
            expectedOutput =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <circle style="fill:red;opacity:0.9;stroke:white;stroke-width:4" cx="50" cy="250" r="30"/>
                </svg>
            """,
        )
    }

    @Test
    fun `class not found - remove class attribute`() {
        doTestEmbeddedCssInlining(
            input =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <style type="text/css">
                        .st0 { fill: red; }
                    </style>
                    <rect class="nonexistent-class" x="320" y="120" width="60" height="60" fill="gray"/>
                </svg>
            """,
            expectedOutput =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <rect x="320" y="120" width="60" height="60" fill="gray"/>
                </svg>
            """,
        )
    }

    @Test
    fun `class not found but inline style exists - preserve inline`() {
        doTestEmbeddedCssInlining(
            input =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <style type="text/css">
                        .st0 { fill: red; }
                    </style>
                    <circle class="nonexistent" style="fill: blue" cx="10" cy="10" r="5"/>
                </svg>
            """,
            expectedOutput =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <circle style="fill: blue" cx="10" cy="10" r="5"/>
                </svg>
            """,
        )
    }

    @Test
    fun `empty class attribute`() {
        doTestEmbeddedCssInlining(
            input =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <style type="text/css">
                        .st0 { fill: red; }
                    </style>
                    <circle class="" cx="250" cy="250" r="30" fill="maroon"/>
                </svg>
            """,
            expectedOutput =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <circle cx="250" cy="250" r="30" fill="maroon"/>
                </svg>
            """,
        )
    }

    @Test
    fun `multiple whitespace in class attribute`() {
        doTestEmbeddedCssInlining(
            input =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <style type="text/css">
                        .blue-rect { fill: blue; stroke: black; stroke-width: 2; }
                        .yellow-shape { fill: yellow; opacity: 0.6; }
                    </style>
                    <rect class="blue-rect    yellow-shape" x="320" y="220" width="60" height="60"/>
                </svg>
            """,
            expectedOutput =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <rect style="fill:yellow;stroke:black;stroke-width:2;opacity:0.6" x="320" y="220" width="60" height="60"/>
                </svg>
            """,
        )
    }

    @Test
    fun `elements without class attribute unchanged`() {
        doTestEmbeddedCssInlining(
            input =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <style type="text/css">
                        .st0 { fill: red; }
                    </style>
                    <rect x="120" y="220" width="60" height="60" fill="teal"/>
                </svg>
            """,
            expectedOutput =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <rect x="120" y="220" width="60" height="60" fill="teal"/>
                </svg>
            """,
        )
    }

    @Test
    fun `preserve gradient references`() {
        doTestEmbeddedCssInlining(
            input =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <defs>
                        <linearGradient id="XMLID_2_">
                            <stop offset="0" style="stop-color:#FDD900"/>
                        </linearGradient>
                    </defs>
                    <style type="text/css">
                        .st0 { fill: url(#XMLID_2_); stroke: orange; stroke-width: 3; }
                    </style>
                    <circle class="st0" cx="250" cy="150" r="30"/>
                </svg>
            """,
            expectedOutput =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <defs>
                        <linearGradient id="XMLID_2_">
                            <stop offset="0" style="stop-color:#FDD900"/>
                        </linearGradient>
                    </defs>
                    <circle style="fill:url(#XMLID_2_);stroke:orange;stroke-width:3" cx="250" cy="150" r="30"/>
                </svg>
            """,
        )
    }

    @Test
    fun `no style elements - no changes`() {
        doTestEmbeddedCssInlining(
            input =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <circle cx="10" cy="10" r="5"/>
                </svg>
            """,
            expectedOutput =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <circle cx="10" cy="10" r="5"/>
                </svg>
            """,
        )
    }

    @Test
    fun `empty style block`() {
        doTestEmbeddedCssInlining(
            input =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <style type="text/css"></style>
                    <circle cx="10" cy="10" r="5"/>
                </svg>
            """,
            expectedOutput =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <circle cx="10" cy="10" r="5"/>
                </svg>
            """,
        )
    }

    @Test
    fun `style with only whitespace`() {
        doTestEmbeddedCssInlining(
            input =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <style type="text/css">
                        
                        
                    </style>
                    <circle class="st0" cx="10" cy="10" r="5"/>
                </svg>
            """,
            expectedOutput =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <circle cx="10" cy="10" r="5"/>
                </svg>
            """,
        )
    }

    @Test
    fun `non-CSS style elements ignored`() {
        doTestEmbeddedCssInlining(
            input =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <style type="text/css">
                        .st0 { fill: red; }
                    </style>
                    <style type="text/xsl">
                        <!-- XSL content -->
                    </style>
                    <circle class="st0" cx="10" cy="10" r="5"/>
                </svg>
            """,
            expectedOutput =
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <circle style="fill:red" cx="10" cy="10" r="5"/>
                </svg>
            """,
        )
    }

    @Test
    fun `handle common SVG export pattern with numbered classes`() {
        doTestEmbeddedCssInlining(
            input =
                """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">
                    <defs>
                        <linearGradient id="XMLID_2_">
                            <stop offset="0" style="stop-color:#FDD900"/>
                            <stop offset="1" style="stop-color:#E29F25"/>
                        </linearGradient>
                    </defs>
                    <style type="text/css">
                        .st0{fill-rule:evenodd;clip-rule:evenodd;fill:url(#XMLID_2_);}
                        .st1{fill-rule:evenodd;clip-rule:evenodd;fill:#E6A323;}
                        .st9{opacity:0.5;fill:#D58128;}
                    </style>
                    <circle class="st0" cx="256.2" cy="255.4" r="167.7"/>
                    <path class="st1" d="M487.1,134.8"/>
                    <path class="st9" d="M248.7,422.5"/>
                </svg>
            """,
            expectedOutput =
                """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">
                    <defs>
                        <linearGradient id="XMLID_2_">
                            <stop offset="0" style="stop-color:#FDD900"/>
                            <stop offset="1" style="stop-color:#E29F25"/>
                        </linearGradient>
                    </defs>
                    <circle style="fill-rule:evenodd;clip-rule:evenodd;fill:url(#XMLID_2_)" cx="256.2" cy="255.4" r="167.7"/>
                    <path style="fill-rule:evenodd;clip-rule:evenodd;fill:#E6A323" d="M487.1,134.8"/>
                    <path style="opacity:0.5;fill:#D58128" d="M248.7,422.5"/>
                </svg>
            """,
        )
    }

    private fun doTestEmbeddedCssInlining(input: String, expectedOutput: String) {
        val inputDom = input.trimIndent().toDomElement()
        val expectedOutputDom = expectedOutput.trimIndent().toDomElement()

        inlineEmbeddedCssStyles(inputDom)

        assertElementEquals(input, expectedOutputDom, inputDom)
    }

    private fun assertElementEquals(input: String, expectedOutput: Element, actualOutput: Element) {
        normalizeAttributeOrder(expectedOutput)
        normalizeAttributeOrder(actualOutput)

        val expectedXml = expectedOutput.prettyPrintXml()
        val actualXml = actualOutput.prettyPrintXml()

        if (expectedXml != actualXml) {
            val errorMessage = buildString {
                appendLine("For given SVG input, inlined SVG output content doesn't match: ")
                appendLine("Input:")
                appendLine(input.trimIndent())
                appendLine()
                appendLine("Expected xml content:")
                appendLine(expectedXml)
                appendLine()
                appendLine("Actual xml content:")
                appendLine(actualXml)
                appendLine()
            }

            fail(errorMessage)
        }
    }

    private fun String.toDomElement(): Element = parseSvg(this).documentElement

    private fun parseSvg(svgString: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        return builder.parse(InputSource(StringReader(svgString)))
    }

    private fun normalizeAttributeOrder(element: Element) {
        val attrs = element.attributes

        // Extract attributes into a sorted list
        val sorted = (0 until attrs.length).map { attrs.item(it) }.sortedBy { it.nodeName }

        // Remove all current attributes
        while (attrs.length > 0) {
            attrs.removeNamedItem(attrs.item(0).nodeName)
        }

        // Re-add in sorted order
        for (attr in sorted) {
            element.setAttribute(attr.nodeName, attr.nodeValue)
        }

        // Recurse children
        for (i in 0 until element.childNodes.length) {
            val child = element.childNodes.item(i)
            if (child is Element) {
                normalizeAttributeOrder(child)
            }
        }
    }

    private fun Node.prettyPrintXml(): String {
        fun normalizeWhitespace(xml: String): String = xml.replace(Regex("\\n\\s*\\n"), "\n").trim()

        val transformerFactory = TransformerFactory.newInstance()

        val transformer =
            transformerFactory.newTransformer().apply {
                setOutputProperty(OutputKeys.INDENT, "yes")
                setOutputProperty(OutputKeys.ENCODING, "UTF-8")
                setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
            }

        val writer = StringWriter()
        transformer.transform(DOMSource(this), StreamResult(writer))

        return normalizeWhitespace(writer.toString().trim())
    }
}
