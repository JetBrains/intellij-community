package org.jetbrains.jewel.markdown

import org.intellij.lang.annotations.Language
import org.jetbrains.jewel.markdown.MarkdownBlock.BlockQuote
import org.jetbrains.jewel.markdown.MarkdownBlock.CodeBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.CodeBlock.FencedCodeBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.CodeBlock.IndentedCodeBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.Heading
import org.jetbrains.jewel.markdown.MarkdownBlock.Heading.H1
import org.jetbrains.jewel.markdown.MarkdownBlock.Heading.H2
import org.jetbrains.jewel.markdown.MarkdownBlock.Heading.H3
import org.jetbrains.jewel.markdown.MarkdownBlock.Heading.H4
import org.jetbrains.jewel.markdown.MarkdownBlock.Heading.H5
import org.jetbrains.jewel.markdown.MarkdownBlock.Heading.H6
import org.jetbrains.jewel.markdown.MarkdownBlock.HtmlBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.Image
import org.jetbrains.jewel.markdown.MarkdownBlock.ListBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.ListBlock.OrderedList
import org.jetbrains.jewel.markdown.MarkdownBlock.ListBlock.UnorderedList
import org.jetbrains.jewel.markdown.MarkdownBlock.ListItem
import org.jetbrains.jewel.markdown.MarkdownBlock.Paragraph
import org.jetbrains.jewel.markdown.MarkdownBlock.ThematicBreak
import org.junit.Assert

fun List<MarkdownBlock>.assertEquals(vararg expected: MarkdownBlock) {
    val differences = findDifferences(expected.toList(), indentSize = 0)
    Assert.assertTrue(
        "The following differences were found:\n\n" +
            "${differences.joinToString("\n").replace('\t', '→')}\n\n",
        differences.isEmpty(),
    )
}

fun List<MarkdownBlock>.findDifferences(
    expected: List<MarkdownBlock>,
    indentSize: Int,
): List<String> = buildList {
    val indent = " ".repeat(indentSize)
    val thisSize = this@findDifferences.size
    if (expected.size != thisSize) {
        add("$indent * Content size mismatch. Was $thisSize, but we expected ${expected.size}")
        add("$indent     Actual:   ${this@findDifferences}")
        add("$indent     Expected: $expected\n")
        add("$indent   ℹ️ Note: skipping cells comparison as it's meaningless")
        return@buildList
    }

    for ((i, item) in this@findDifferences.withIndex()) {
        val difference = item.findDifferenceWith(expected[i], indentSize + 2)
        if (difference.isNotEmpty()) {
            add(
                "$indent * Item #$i is not the same as the expected value.\n\n" +
                    "${difference.joinToString("\n")}\n",
            )
        }
    }
}

private fun MarkdownBlock.findDifferenceWith(
    expected: MarkdownBlock,
    indentSize: Int,
): List<String> {
    val indent = " ".repeat(indentSize)
    if (this.javaClass != expected.javaClass) {
        return listOf(
            "$indent * Block type mismatch.\n\n" +
                "$indent     Actual:   ${javaClass.name}\n" +
                "$indent     Expected: ${expected.javaClass.name}\n",
        )
    }

    return when (this) {
        is Paragraph -> diffParagraph(this, expected, indent)
        is BlockQuote -> content.findDifferences((expected as BlockQuote).content, indentSize)
        is HtmlBlock -> diffHtmlBlock(this, expected, indent)
        is FencedCodeBlock -> diffFencedCodeBlock(this, expected, indent)
        is IndentedCodeBlock -> diffIndentedCodeBlock(this, expected, indent)
        is Heading -> diffHeading(this, expected, indent)
        is Image -> diffImage(this, expected, indent)
        is ListBlock -> diffList(this, expected, indentSize, indent)
        is ListItem -> content.findDifferences((expected as ListItem).content, indentSize)
        is ThematicBreak -> emptyList() // They can only differ in their node
        else -> error("Unsupported MarkdownBlock: ${this.javaClass.name}")
    }
}

private fun diffParagraph(actual: Paragraph, expected: MarkdownBlock, indent: String) = buildList {
    if (actual.inlineContent != (expected as Paragraph).inlineContent) {
        add(
            "$indent * Paragraph raw content mismatch.\n\n" +
                "$indent     Actual:   ${actual.inlineContent}\n" +
                "$indent     Expected: ${expected.inlineContent}\n",
        )
    }
}

private fun diffHtmlBlock(actual: HtmlBlock, expected: MarkdownBlock, indent: String) = buildList {
    if (actual.content != (expected as HtmlBlock).content) {
        add(
            "$indent * HTML block content mismatch.\n\n" +
                "$indent     Actual:   ${actual.content}\n" +
                "$indent     Expected: ${expected.content}\n",
        )
    }
}

private fun diffFencedCodeBlock(actual: FencedCodeBlock, expected: MarkdownBlock, indent: String) =
    buildList {
        if (actual.mimeType != (expected as FencedCodeBlock).mimeType) {
            add(
                "$indent * Fenced code block mime type mismatch.\n\n" +
                    "$indent     Actual:   ${actual.mimeType}\n" +
                    "$indent     Expected: ${expected.mimeType}",
            )
        }

        if (actual.content != expected.content) {
            add(
                "$indent * Fenced code block content mismatch.\n\n" +
                    "$indent     Actual:   ${actual.content}\n" +
                    "$indent     Expected: ${expected.content}",
            )
        }
    }

private fun diffIndentedCodeBlock(actual: CodeBlock, expected: MarkdownBlock, indent: String) =
    buildList {
        if (actual.content != (expected as IndentedCodeBlock).content) {
            add(
                "$indent * Indented code block content mismatch.\n\n" +
                    "$indent     Actual:   ${actual.content}\n" +
                    "$indent     Expected: ${expected.content}",
            )
        }
    }

private fun diffHeading(actual: Heading, expected: MarkdownBlock, indent: String) = buildList {
    if (actual.inlineContent != (expected as Heading).inlineContent) {
        add(
            "$indent * Heading raw content mismatch.\n\n" +
                "$indent     Actual:   ${actual.inlineContent}\n" +
                "$indent     Expected: ${expected.inlineContent}",
        )
    }
}

private fun diffImage(actual: Image, expected: MarkdownBlock, indent: String) = buildList {
    if (actual.url != (expected as Image).url) {
        add(
            "$indent * Image URL mismatch.\n\n" +
                "$indent     Actual:   ${actual.url}\n" +
                "$indent     Expected: ${expected.url}",
        )
    }

    if (actual.altString != expected.altString) {
        add(
            "$indent * Image alt string mismatch.\n\n" +
                "$indent     Actual:   ${actual.altString}\n" +
                "$indent     Expected: ${expected.altString}",
        )
    }
}

private fun diffList(actual: ListBlock, expected: MarkdownBlock, indentSize: Int, indent: String) =
    buildList {
        addAll(actual.items.findDifferences((expected as ListBlock).items, indentSize))

        if (actual.isTight != expected.isTight) {
            add(
                "$indent * List isTight mismatch.\n\n" +
                    "$indent     Actual:   ${actual.isTight}\n" +
                    "$indent     Expected: ${expected.isTight}",
            )
        }

        when (actual) {
            is OrderedList -> {
                if (actual.startFrom != (expected as OrderedList).startFrom) {
                    add(
                        "$indent * List startFrom mismatch.\n\n" +
                            "$indent     Actual:   ${actual.startFrom}\n" +
                            "$indent     Expected: ${expected.startFrom}",
                    )
                }

                if (actual.delimiter != expected.delimiter) {
                    add(
                        "$indent * List delimiter mismatch.\n\n" +
                            "$indent     Actual:   ${actual.delimiter}\n" +
                            "$indent     Expected: ${expected.delimiter}",
                    )
                }
            }

            is UnorderedList -> {
                if (actual.bulletMarker != (expected as UnorderedList).bulletMarker) {
                    add(
                        "$indent * List bulletMarker mismatch.\n\n" +
                            "$indent     Actual:   ${actual.bulletMarker}\n" +
                            "$indent     Expected: ${expected.bulletMarker}",
                    )
                }
            }
        }
    }

fun paragraph(@Language("Markdown") content: String) = Paragraph(InlineMarkdown(content))

fun indentedCodeBlock(content: String) = IndentedCodeBlock(content)

fun fencedCodeBlock(content: String, mimeType: MimeType? = null) =
    FencedCodeBlock(content, mimeType)

fun blockQuote(vararg contents: MarkdownBlock) = BlockQuote(contents.toList())

fun unorderedList(
    vararg items: ListItem,
    isTight: Boolean = true,
    bulletMarker: Char = '-',
) = UnorderedList(items.toList(), isTight, bulletMarker)

fun orderedList(
    vararg items: ListItem,
    isTight: Boolean = true,
    startFrom: Int = 1,
    delimiter: Char = '.',
) = OrderedList(items.toList(), isTight, startFrom, delimiter)

fun listItem(vararg items: MarkdownBlock) = ListItem(items.toList())

fun heading(level: Int, @Language("Markdown") content: String) =
    when (level) {
        1 -> H1(InlineMarkdown(content))
        2 -> H2(InlineMarkdown(content))
        3 -> H3(InlineMarkdown(content))
        4 -> H4(InlineMarkdown(content))
        5 -> H5(InlineMarkdown(content))
        6 -> H6(InlineMarkdown(content))
        else -> error("Invalid heading level $level")
    }

fun htmlBlock(content: String) = HtmlBlock(content)
