// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.javadoc.markdown;

import com.intellij.openapi.util.text.StringUtil;
import kotlin.ranges.IntRange;
import org.intellij.markdown.MarkdownTokenTypes;
import org.intellij.markdown.parser.sequentialparsers.RangesListBuilder;
import org.intellij.markdown.parser.sequentialparsers.SequentialParser;
import org.intellij.markdown.parser.sequentialparsers.TokensCache;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/// Inline parser dedicated to finding `<code>` tags.
///
/// Only purpose is to exclude the range from further markdown processing
public class CodeTagParser implements SequentialParser {
  @Override
  public @NotNull ParsingResult parse(@NotNull TokensCache tokens, @NotNull List<IntRange> rangesToGlue) {
    var result = new SequentialParser.ParsingResultBuilder();
    var delegateIndices = new RangesListBuilder();
    TokensCache.Iterator iterator = tokens.new RangesListIterator(rangesToGlue);

    while (iterator.getType() != null) {
      if (iterator.getType() == MarkdownTokenTypes.HTML_TAG && hasOpeningTag(getText(tokens, iterator), "code")) {
        int startIndex = iterator.getIndex();
        var endIterator = findEnd(tokens, iterator.advance());

        if (endIterator != null) {
          result.withNode(new SequentialParser.Node(new IntRange(startIndex, endIterator.getIndex() + 1), JavaDocMarkdownFlavourDescriptor.RAW_TYPE));
          iterator = endIterator.advance();
          continue;
        }
      }

      delegateIndices.put(iterator.getIndex());
      iterator = iterator.advance();
    }

    return result.withFurtherProcessing(delegateIndices.get());
  }

  /// @return The iterator located at the end tag, or `null` if the end isn't found
  private static TokensCache.Iterator findEnd(@NotNull TokensCache tokens, TokensCache.Iterator iterator) {
    while (iterator.getType() != null) {
      if (iterator.getType() == MarkdownTokenTypes.HTML_TAG) {
        if (hasClosingTag(getText(tokens, iterator), "code")) {
          return iterator;
        }
      }

      iterator = iterator.advance();
    }
    return null;
  }

  private static String getText(@NotNull TokensCache tokens, TokensCache.Iterator iterator) {
    return tokens.getOriginalText().subSequence(iterator.getStart(), iterator.getEnd()).toString();
  }

  private static boolean hasOpeningTag(String tag, String tagName) {
    return StringUtil.trimEnd(StringUtil.trimStart(tag, "<"), ">").equals(tagName);
  }

  private static boolean hasClosingTag(String tag, String tagName) {
    return StringUtil.trimEnd(StringUtil.trimStart(tag, "</"), ">").equals(tagName);
  }
}
