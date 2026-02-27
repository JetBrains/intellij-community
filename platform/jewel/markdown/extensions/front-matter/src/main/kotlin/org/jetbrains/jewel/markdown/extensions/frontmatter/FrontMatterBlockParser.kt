package org.jetbrains.jewel.markdown.extensions.frontmatter

import org.commonmark.node.Block
import org.commonmark.parser.block.AbstractBlockParser
import org.commonmark.parser.block.AbstractBlockParserFactory
import org.commonmark.parser.block.BlockContinue
import org.commonmark.parser.block.BlockStart
import org.commonmark.parser.block.MatchedBlockParser
import org.commonmark.parser.block.ParserState

internal class FrontMatterBlockParser : AbstractBlockParser() {
    private val fmBlock = FrontMatterBlock()

    private var currentBlock: CurrentBlock = CurrentBlock.KeyValue()
        set(value) {
            flushCurrentKey()
            field = value
        }

    private var done = false

    override fun getBlock(): Block = fmBlock

    override fun tryContinue(state: ParserState): BlockContinue? {
        if (done) return BlockContinue.none()

        val line = state.line.content.toString()

        if (line.trimEnd() == "---") {
            flushAll()
            done = true
            return BlockContinue.finished()
        }

        if (!parseLine(line)) return BlockContinue.none()

        return BlockContinue.atIndex(state.index)
    }

    private fun parseLine(line: String): Boolean =
        when (val block = currentBlock) {
            is CurrentBlock.KeyValue -> parseKeyLine(line, block)
            is CurrentBlock.Scalar -> parseScalarLine(line, block)
        }

    private fun parseScalarLine(line: String, block: CurrentBlock.Scalar): Boolean {
        val trimmed = line.trimEnd()

        // Detect indent from first non-empty content line
        if (block.indent == null) {
            if (trimmed.isEmpty()) {
                block.lines.add("")
                return true
            }
            val indent = line.indentWidth()
            if (indent < block.minimumIndent) {
                flushAll()
                val keyBlock = currentBlock as? CurrentBlock.KeyValue ?: return false
                return parseKeyLine(line, keyBlock)
            }
            block.indent = indent
        }

        val indent = block.indent!!

        // Blank lines are always part of the scalar content
        if (trimmed.isEmpty()) {
            block.lines.add("")
            return true
        }

        val lineIndent = line.indentWidth()
        if (lineIndent >= indent) {
            block.lines.add(line.substring(indent))
            return true
        }

        // Line is not indented enough -- end of block scalar
        flushAll()
        val keyBlock = currentBlock as? CurrentBlock.KeyValue ?: return false
        return parseKeyLine(line, keyBlock)
    }

    private fun parseKeyLine(line: String, block: CurrentBlock.KeyValue): Boolean {
        val trimmed = line.trimEnd()

        if (trimmed.isBlank()) {
            return true
        }

        val listItemMatch = LIST_ITEM_REGEX.matchEntire(trimmed)
        // If a list item, add it to the current block
        if (listItemMatch != null) {
            if (block.currentKey == null) return false
            block.currentValues.add(parseStringValue(listItemMatch.groupValues[1]))
            return true
        }

        val keyValue = trimmed.split(':', limit = 2)
        if (keyValue.size < 2) {
            // not a "key: value" pair
            return false
        }

        val (key, value) = keyValue.map { it.trim() }

        if (key.isBlank() || key.startsWith('-')) {
            return false
        }

        if (value.isBlank()) {
            // Bare "key:" without the "value" -- could be on the next line
            currentBlock = CurrentBlock.KeyValue(currentKey = key)
            return true
        }

        // Check for the block scalar indicator (e.g. "key: >+"
        val blockScalarMatch = BLOCK_SCALAR_REGEX.matchEntire(value)
        if (blockScalarMatch != null) {
            currentBlock =
                CurrentBlock.Scalar(
                    currentKey = key,
                    indicator = blockScalarMatch.groupValues[1][0],
                    chomping = blockScalarMatch.groupValues[2].firstOrNull(),
                    minimumIndent = line.indentWidth() + 1,
                )
            return true
        }

        // Simple "key: value"
        currentBlock = CurrentBlock.KeyValue(currentKey = key, currentValues = mutableListOf(parseStringValue(value)))
        return true
    }

    private fun flushBlockScalar() {
        val block = currentBlock as? CurrentBlock.Scalar ?: return
        val indicator = block.indicator
        val chomping = block.chomping
        val lines = block.lines.toMutableList()

        // Count and remove trailing blank lines
        var trailingBlanks = 0
        for (i in lines.lastIndex downTo 0) {
            if (lines[i].isNotEmpty()) break
            trailingBlanks++
        }

        // Build the content text (without trailing blanks)
        if (trailingBlanks == lines.size) {
            // Empty block scalar -- no content
            currentBlock =
                CurrentBlock.KeyValue(
                    currentKey = block.currentKey,
                    currentValues = block.currentValues.toMutableList(),
                )
            return
        }

        val contentLines = lines.subList(0, lines.size - trailingBlanks)

        val contentText =
            when (indicator) {
                '|' -> contentLines.joinToString("\n")
                '>' -> foldLines(contentLines)
                else -> contentLines.joinToString("\n")
            }

        // Apply chomping to determine trailing newlines
        val value =
            when (chomping) {
                '-' -> contentText
                '+' -> contentText + "\n".repeat(trailingBlanks + 1)
                else -> contentText + "\n" // clip: single trailing newline
            }

        currentBlock = CurrentBlock.KeyValue(currentKey = block.currentKey, currentValues = mutableListOf(value))
    }

    private fun foldLines(lines: List<String>): String {
        return buildString {
            var i = 0
            var hasTextOnLine = false
            while (i < lines.size) {
                val line = lines[i]
                if (line.isEmpty()) {
                    // Blank line -- paragraph break
                    appendLine()
                    appendLine()
                    hasTextOnLine = false
                    // Skip consecutive blank lines
                    while (i + 1 < lines.size && lines[i + 1].isEmpty()) {
                        i++
                    }
                } else {
                    if (hasTextOnLine) {
                        append(" ")
                    }
                    append(line)
                    hasTextOnLine = !line.endsWith("\n")
                }
                i++
            }
        }
    }

    private fun flushCurrentKey() {
        val block = currentBlock as? CurrentBlock.KeyValue ?: return
        val key = block.currentKey ?: return
        val node = FrontMatterNode(key, block.currentValues.toList())
        fmBlock.appendChild(node)
        block.currentKey = null
        block.currentValues.clear()
    }

    override fun closeBlock() {
        flushAll()
    }

    private fun flushAll() {
        flushBlockScalar()
        flushCurrentKey()
    }

    private sealed interface CurrentBlock {
        class KeyValue(var currentKey: String? = null, var currentValues: MutableList<String> = mutableListOf()) :
            CurrentBlock

        class Scalar(
            var currentKey: String,
            var currentValues: MutableList<String> = mutableListOf(),
            var indicator: Char,
            var chomping: Char?,
            var minimumIndent: Int = 1,
            var indent: Int? = null,
            var lines: MutableList<String> = mutableListOf(),
        ) : CurrentBlock
    }

    internal class Factory : AbstractBlockParserFactory() {
        override fun tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): BlockStart? {
            // Front matter must be at the very beginning of the document
            val parent = matchedBlockParser.matchedBlockParser.block
            if (parent !is org.commonmark.node.Document) return BlockStart.none()
            if (parent.firstChild != null) return BlockStart.none()

            val line = state.line.content.toString()
            if (state.nextNonSpaceIndex != 0) return BlockStart.none()

            if (line.trimEnd() == "---") {
                return BlockStart.of(FrontMatterBlockParser()).atIndex(state.line.content.length)
            }
            return BlockStart.none()
        }
    }

    companion object {
        private val BLOCK_SCALAR_REGEX = Regex("^([|>])([+-]?)$")
        private val LIST_ITEM_REGEX = Regex("^\\s*-\\s+(.+)$")

        private fun parseStringValue(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.length >= 2 && (trimmed.surroundedBy('\'') || trimmed.surroundedBy('"'))) {
                return trimmed.substring(1, trimmed.length - 1)
            }
            return trimmed
        }

        private fun String.indentWidth(): Int = indexOfFirst { !it.isWhitespace() }.let { if (it == -1) length else it }

        private fun String.surroundedBy(char: Char): Boolean = first() == char && last() == char
    }
}
