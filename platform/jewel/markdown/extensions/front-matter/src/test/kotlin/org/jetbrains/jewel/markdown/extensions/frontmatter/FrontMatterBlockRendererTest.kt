package org.jetbrains.jewel.markdown.extensions.frontmatter

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.markdown.rendering.DefaultMarkdownBlockRenderer
import org.jetbrains.jewel.markdown.testing.MarkdownTestTheme
import org.jetbrains.jewel.markdown.testing.createMarkdownTestStyling
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
public class FrontMatterBlockRendererTest {
    @Test
    public fun `scalar and list entries are rendered`() {
        runComposeUiTest {
            val markdownStyling = createMarkdownTestStyling()
            val frontMatterStyling =
                FrontMatterStyling(
                    borderColor = Color.Black,
                    background = Color.White,
                    borderWidth = 1.dp,
                    cellPadding = PaddingValues(4.dp),
                )
            val frontMatterRendererExtension = FrontMatterRendererExtension(frontMatterStyling)
            val processor = MarkdownProcessor(listOf(FrontMatterProcessorExtension))

            val markdown =
                """
            ---
            title: Hello World
            tags:
                - a
                - b
            ---
        """
                    .trimIndent()

            val blocks = processor.processMarkdownDocument(markdown)

            setContent {
                MarkdownTestTheme {
                    DefaultMarkdownBlockRenderer(markdownStyling, listOf(frontMatterRendererExtension))
                        .render(blocks, enabled = true, onUrlClick = {}, onTextClick = {}, modifier = Modifier)
                }
            }

            onNodeWithText("title").assertIsDisplayed()
            onNodeWithText("Hello World").assertIsDisplayed()
            onNodeWithText("tags").assertIsDisplayed()
            onNodeWithText("a").assertIsDisplayed()
            onNodeWithText("b").assertIsDisplayed()
        }
    }

    @Test
    public fun `createValueContent with Scalar value should return MarkdownBlock with Text`() {
        val text = "hi this is a test"

        val result = createValueContent(FrontMatter.Value.Scalar(text))

        assertEquals(MarkdownBlock.Paragraph(listOf(InlineMarkdown.Text(text))), result)
    }

    @Test
    public fun `createValueContent with ListValue value should return MarkdownBlock with UnorderedList`() {
        val result = createValueContent(FrontMatter.Value.ListValue(listOf("a", "b", "c")))

        assertEquals(
            MarkdownBlock.ListBlock.UnorderedList(
                children =
                    listOf(
                        MarkdownBlock.ListItem(MarkdownBlock.Paragraph(listOf(InlineMarkdown.Text("a")))),
                        MarkdownBlock.ListItem(MarkdownBlock.Paragraph(listOf(InlineMarkdown.Text("b")))),
                        MarkdownBlock.ListItem(MarkdownBlock.Paragraph(listOf(InlineMarkdown.Text("c")))),
                    ),
                isTight = true,
                marker = "-",
            ),
            result,
        )
    }
}
