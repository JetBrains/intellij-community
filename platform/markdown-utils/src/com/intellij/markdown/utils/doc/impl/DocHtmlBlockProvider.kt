// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.utils.doc.impl

import com.intellij.markdown.utils.doc.DocMarkdownToHtmlConverter
import org.intellij.markdown.lexer.Compat.assert
import org.intellij.markdown.parser.LookaheadText
import org.intellij.markdown.parser.MarkerProcessor
import org.intellij.markdown.parser.ProductionHolder
import org.intellij.markdown.parser.constraints.MarkdownConstraints
import org.intellij.markdown.parser.markerblocks.MarkerBlock
import org.intellij.markdown.parser.markerblocks.MarkerBlockProvider
import org.intellij.markdown.parser.markerblocks.impl.HtmlBlockMarkerBlock

internal object DocHtmlBlockProvider : MarkerBlockProvider<MarkerProcessor.StateInfo> {
  override fun createMarkerBlocks(pos: LookaheadText.Position,
                                  productionHolder: ProductionHolder,
                                  stateInfo: MarkerProcessor.StateInfo): List<MarkerBlock> {
    val matchingGroup = matches(pos, stateInfo.currentConstraints)
    if (matchingGroup in 0..3) {
      return listOf(HtmlBlockMarkerBlock(stateInfo.currentConstraints, productionHolder, OPEN_CLOSE_REGEXES[matchingGroup].second, pos))
    }
    return emptyList()
  }

  override fun interruptsParagraph(pos: LookaheadText.Position, constraints: MarkdownConstraints): Boolean {
    return matches(pos, constraints) in 0..4
  }

  private fun matches(pos: LookaheadText.Position, constraints: MarkdownConstraints): Int {
    if (!MarkerBlockProvider.isStartOfLineWithConstraints(pos, constraints)) {
      return -1
    }
    val text = pos.currentLineFromPosition
    val offset = MarkerBlockProvider.passSmallIndent(text)
    if (offset >= text.length || text[offset] != '<') {
      return -1
    }

    val matchResult = FIND_START_REGEX.find(text.substring(offset))
                      ?: return -1
    assert(matchResult.groups.size == OPEN_CLOSE_REGEXES.size + 2) { "There are some excess capturing groups probably!" }
    for (i in OPEN_CLOSE_REGEXES.indices) {
      if (matchResult.groups[i + 2] != null) {
        return i
      }
    }
    assert(false) { "Match found but all groups are empty!" }
    return -1
  }

  /** see {@link http://spec.commonmark.org/0.21/#html-blocks}
   *
   * nulls mean "Next line should be blank"
   * */
  private val OPEN_CLOSE_REGEXES: List<Pair<Regex, Regex?>> = listOf(
    Pair(Regex("<(?:script|pre|style)(?: |>|$)", RegexOption.IGNORE_CASE),
         Regex("</(?:script|style|pre)>", RegexOption.IGNORE_CASE)),
    Pair(Regex("<!--"), Regex("-->")),
    Pair(Regex("<\\?"), Regex("\\?>")),
    Pair(Regex("<![A-Z]"), Regex(">")),
    Pair(Regex("<!\\[CDATA\\["), Regex("]]>")),
    Pair(Regex("</?(?:${DocMarkdownToHtmlConverter.ACCEPTABLE_BLOCK_TAGS.joinToString("|")})(?: |/?>|$)", RegexOption.IGNORE_CASE), null)
  )

  private val FIND_START_REGEX = Regex(
    "^(${OPEN_CLOSE_REGEXES.joinToString(separator = "|", transform = { "(${it.first.pattern})" })})"
  )

}