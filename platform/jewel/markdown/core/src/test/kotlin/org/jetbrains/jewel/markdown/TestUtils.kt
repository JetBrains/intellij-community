package org.jetbrains.jewel.markdown

import org.commonmark.internal.InlineParserContextImpl
import org.commonmark.internal.InlineParserImpl
import org.commonmark.internal.LinkReferenceDefinitions
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.commonmark.parser.SourceLine
import org.commonmark.parser.SourceLines
import org.commonmark.renderer.html.HtmlRenderer
import org.intellij.lang.annotations.Language
import org.jetbrains.jewel.markdown.MarkdownBlock.BlockQuote
import org.jetbrains.jewel.markdown.MarkdownBlock.CodeBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.CodeBlock.FencedCodeBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.CodeBlock.IndentedCodeBlock
import org.jetbrains.jewel.markdown.MarkdownBlock.Heading
import org.jetbrains.jewel.markdown.MarkdownBlock.HtmlBlock
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
        is BlockQuote -> children.findDifferences((expected as BlockQuote).children, indentSize)
        is HtmlBlock -> diffHtmlBlock(this, expected, indent)
        is FencedCodeBlock -> diffFencedCodeBlock(this, expected, indent)
        is IndentedCodeBlock -> diffIndentedCodeBlock(this, expected, indent)
        is Heading -> diffHeading(this, expected, indent)
        is ListBlock -> diffList(this, expected, indentSize, indent)
        is ListItem -> children.findDifferences((expected as ListItem).children, indentSize)
        is ThematicBreak -> emptyList() // They can only differ in their node
        else -> error("Unsupported MarkdownBlock: ${this.javaClass.name}")
    }
}

private var htmlRenderer = HtmlRenderer.builder().build()

fun BlockWithInlineMarkdown.toHtml() = buildString {
    for (node in this@toHtml.inlineContent) {
        // new lines are rendered as spaces in tests
        append(htmlRenderer.render(node.nativeNode).replace("\n", " "))
    }
}

private fun diffParagraph(actual: Paragraph, expected: MarkdownBlock, indent: String) = buildList {
    val actualInlineHtml = actual.toHtml()
    val expectedInlineHtml = (expected as Paragraph).toHtml()
    if (actualInlineHtml != expectedInlineHtml) {
        add(
            "$indent * Paragraph raw content mismatch.\n\n" +
                "$indent     Actual:   $actualInlineHtml\n" +
                "$indent     Expected: $expectedInlineHtml\n",
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
    val actualInlineHtml = actual.toHtml()
    val expectedInlineHtml = (expected as Heading).toHtml()
    if (actualInlineHtml != expectedInlineHtml) {
        add(
            "$indent * Heading raw content mismatch.\n\n" +
                "$indent     Actual:   $actualInlineHtml\n" +
                "$indent     Expected: $expectedInlineHtml",
        )
    }
}

private fun diffList(actual: ListBlock, expected: MarkdownBlock, indentSize: Int, indent: String) =
    buildList {
        addAll(actual.children.findDifferences((expected as ListBlock).children, indentSize))

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
                if (actual.marker != (expected as UnorderedList).marker) {
                    add(
                        "$indent * List bulletMarker mismatch.\n\n" +
                            "$indent     Actual:   ${actual.marker}\n" +
                            "$indent     Expected: ${expected.marker}",
                    )
                }
            }
        }
    }

private val parser = Parser.builder().build()

private fun Node.children() = buildList {
    var child = firstChild
    while (child != null) {
        add(child)
        child = child.next
    }
}

/** skip root Document and Paragraph nodes */
private fun inlineMarkdowns(content: String): List<InlineMarkdown> {
    val document = parser.parse(content).firstChild ?: return emptyList()
    return if (document.firstChild is org.commonmark.node.Paragraph) {
        document.firstChild
    } else {
        document
    }.children().map { x -> x.toInlineNode() }
}

private val inlineParser = InlineParserImpl(InlineParserContextImpl(emptyList(), LinkReferenceDefinitions()))

fun paragraph(@Language("Markdown") content: String): Paragraph = Paragraph(
    object : org.commonmark.node.CustomBlock() {}.let { block ->
        inlineParser.parse(SourceLines.of(content.lines().map { SourceLine.of(it, null) }), block)
        block
    }.children().map { x -> x.toInlineNode() },
)

fun heading(level: Int, @Language("Markdown") content: String) = Heading(
    object : org.commonmark.node.CustomBlock() {}.let { block ->
        inlineParser.parse(SourceLines.of(SourceLine.of(content, null)), block)
        block
    }.children().map { x -> x.toInlineNode() },
    level,
)

fun indentedCodeBlock(content: String) = IndentedCodeBlock(content)

fun fencedCodeBlock(content: String, mimeType: MimeType? = null) =
    FencedCodeBlock(content, mimeType)

fun blockQuote(vararg contents: MarkdownBlock) = BlockQuote(contents.toList())

fun unorderedList(
    vararg items: ListItem,
    isTight: Boolean = true,
    marker: String = "-",
) = UnorderedList(items.toList(), isTight, marker)

fun orderedList(
    vararg items: ListItem,
    isTight: Boolean = true,
    startFrom: Int = 1,
    delimiter: String = ".",
) = OrderedList(items.toList(), isTight, startFrom, delimiter)

fun listItem(vararg items: MarkdownBlock) = ListItem(items.toList())

fun htmlBlock(content: String) = HtmlBlock(content)

fun thematicBreak() = ThematicBreak
