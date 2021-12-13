// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.utils

import com.intellij.markdown.utils.lang.HtmlSyntaxHighlighter
import com.intellij.openapi.util.text.HtmlChunk
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.parser.LinkMap
import org.junit.Test
import java.net.URI
import kotlin.test.assertEquals

class MarkdownToHtmlConverterTest {
  private val emptyFlavourDescriptor = object : GFMFlavourDescriptor() {
    override fun createHtmlGeneratingProviders(linkMap: LinkMap, baseURI: URI?): Map<IElementType, GeneratingProvider> {
      val providers = super.createHtmlGeneratingProviders(linkMap, baseURI).toMutableMap()
      providers[MarkdownElementTypes.CODE_FENCE] = CodeFenceSyntaxHighlighterGeneratingProvider(
        object : HtmlSyntaxHighlighter {
          override fun color(language: String?, rawContent: String): HtmlChunk {
            return if (language == "empty")
              HtmlChunk.text(rawContent)
            else
              HtmlChunk.text(SYNTAX_HIGHLIGHTER_RESULT).wrapWith("pre")
          }
        }
      )

      return providers
    }
  }

  private val converter = MarkdownToHtmlConverter(emptyFlavourDescriptor)

  @Test
  fun `java syntax highlighter call check`() {
    val markdownText = """
      ```java
      public class A {
        public static void main(String[] args) {
            System.out.println("Hello, world!");
        }
      }
    """.trimIndent()

    // language=HTML
    val htmlText = """
    <body>  
        <code class="language-java">
            <pre>${SYNTAX_HIGHLIGHTER_RESULT}</pre>
        </code>
    </body>
    """.trimIndent()

    markdownText shouldBe htmlText
  }

  @Test
  fun `kotlin syntax highlighter call check`() {
    val markdownText = """
      ```kotlin
      class A {
        companion object {
          @JvmStatic
          fun main(args: Array<String>) {
              println("Multi to multi suggestion")
          }
        }
      }
    """.trimIndent()

    // language=HTML
    val htmlText = """
    <body>
        <code class="language-kotlin">    
            <pre>${SYNTAX_HIGHLIGHTER_RESULT}</pre>
        </code>
    </body>
    """.trimIndent()

    markdownText shouldBe htmlText
  }

  @Test
  fun `syntax highlighter call check without language`() {
    val markdownText = """
      ```empty
      class A
    """.trimIndent()

    // language=HTML
    val htmlText = """
    <body>
        <code class="language-empty">
            class A
        </code>
    </body>
    """.trimIndent()

    markdownText shouldBe htmlText
  }

  private infix fun String.shouldBe(htmlText: String) {
    assertEquals(
      htmlText.lines().joinToString("") { it.trim() },
      converter.convertMarkdownToHtml(this)
    )
  }

  private companion object {
    private const val SYNTAX_HIGHLIGHTER_RESULT = "Empty line"
  }
}