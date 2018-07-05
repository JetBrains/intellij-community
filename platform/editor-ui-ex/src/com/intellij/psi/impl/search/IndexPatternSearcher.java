// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.search;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.fileTypes.impl.CustomSyntaxTableFileType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.cache.CacheUtil;
import com.intellij.psi.impl.cache.TodoCacheManager;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.IndexPatternOccurrence;
import com.intellij.psi.search.IndexPatternProvider;
import com.intellij.psi.search.searches.IndexPatternSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.CharSequenceSubSequence;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public class IndexPatternSearcher implements QueryExecutor<IndexPatternOccurrence, IndexPatternSearch.SearchParameters> {
  private static final String WHITESPACE = " \t";

  @Override
  public boolean execute(@NotNull final IndexPatternSearch.SearchParameters queryParameters,
                         @NotNull final Processor<? super IndexPatternOccurrence> consumer) {
    final PsiFile file = queryParameters.getFile();
    VirtualFile virtualFile = file.getVirtualFile();
    if (file instanceof PsiBinaryFile || file instanceof PsiCompiledElement || virtualFile == null) {
      return true;
    }

    final TodoCacheManager cacheManager = TodoCacheManager.SERVICE.getInstance(file.getProject());
    final IndexPatternProvider patternProvider = queryParameters.getPatternProvider();
    int count = patternProvider != null
                ? cacheManager.getTodoCount(virtualFile, patternProvider)
                : cacheManager.getTodoCount(virtualFile, queryParameters.getPattern());
    return count == 0 || executeImpl(queryParameters, consumer);
  }

  protected static boolean executeImpl(IndexPatternSearch.SearchParameters queryParameters,
                                       Processor<? super IndexPatternOccurrence> consumer) {
    final IndexPatternProvider patternProvider = queryParameters.getPatternProvider();
    final PsiFile file = queryParameters.getFile();
    final CharSequence chars = file.getViewProvider().getContents();
    boolean multiLine = queryParameters.isMultiLine();
    List<CommentRange> commentRanges = findCommentTokenRanges(file, chars, queryParameters.getRange(), multiLine);
    TIntArrayList occurrences = new TIntArrayList(1);
    IndexPattern[] patterns = patternProvider != null ? patternProvider.getIndexPatterns()
                                                      : new IndexPattern[] {queryParameters.getPattern()};

    for (int i = 0; i < commentRanges.size(); i++) {
      occurrences.resetQuick();

      for (int j = patterns.length - 1; j >=0; --j) {
        if (!collectPatternMatches(patterns, patterns[j], chars, commentRanges, i, file, queryParameters.getRange(), consumer,
                                   occurrences, multiLine)) {
          return false;
        }
      }
    }

    return true;
  }


  private static final TokenSet COMMENT_TOKENS =
    TokenSet.create(CustomHighlighterTokenType.LINE_COMMENT, CustomHighlighterTokenType.MULTI_LINE_COMMENT);

  private static List<CommentRange> findCommentTokenRanges(final PsiFile file,
                                                           final CharSequence chars,
                                                           final TextRange range,
                                                           final boolean multiLine) {
    if (file instanceof PsiPlainTextFile) {
      FileType fType = file.getFileType();
      if (fType instanceof CustomSyntaxTableFileType) {
        Lexer lexer = SyntaxHighlighterFactory.getSyntaxHighlighter(fType, file.getProject(), file.getVirtualFile()).getHighlightingLexer();
        return findComments(lexer, chars, range, COMMENT_TOKENS, null, multiLine);
      }
      else {
        return Collections.singletonList(new CommentRange(0, file.getTextLength()));
      }
    }
    else {
      List<CommentRange> commentRanges = new ArrayList<>();
      final FileViewProvider viewProvider = file.getViewProvider();
      final Set<Language> relevantLanguages = viewProvider.getLanguages();
      for (Language lang : relevantLanguages) {
        final SyntaxHighlighter syntaxHighlighter =
          SyntaxHighlighterFactory.getSyntaxHighlighter(lang, file.getProject(), file.getVirtualFile());
        Lexer lexer = syntaxHighlighter.getHighlightingLexer();
        TokenSet commentTokens = null;
        IndexPatternBuilder builderForFile = null;
        for (IndexPatternBuilder builder : Extensions.getExtensions(IndexPatternBuilder.EP_NAME)) {
          Lexer lexerFromBuilder = builder.getIndexingLexer(file);
          if (lexerFromBuilder != null) {
            lexer = lexerFromBuilder;
            commentTokens = builder.getCommentTokenSet(file);
            builderForFile = builder;
          }
        }
        if (builderForFile == null) {
          final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(lang);
          if (parserDefinition != null) {
            commentTokens = parserDefinition.getCommentTokens();
          }
        }

        if (commentTokens != null) {
          List<CommentRange> langRanges = findComments(lexer, chars, range, commentTokens, builderForFile, multiLine);
          mergeCommentLists(commentRanges, langRanges);
        }
      }
      return commentRanges;
    }
  }

  private static void mergeCommentLists(List<CommentRange> target, List<CommentRange> source) {
    List<CommentRange> merged = target.isEmpty()
                                ? source
                                : ContainerUtil.mergeSortedLists(target, source, CommentRange.BY_START_OFFSET_THEN_BY_END_OFFSET, true);
    target.clear();
    target.addAll(merged);
  }

  private static List<CommentRange> findComments(final Lexer lexer,
                                                 final CharSequence chars,
                                                 final TextRange range,
                                                 final TokenSet commentTokens,
                                                 final IndexPatternBuilder builderForFile,
                                                 final boolean multiLine) {
    List<CommentRange> commentRanges = new ArrayList<>();
    int lastEndOffset = -1;
    for (lexer.start(chars); ; lexer.advance()) {
      IElementType tokenType = lexer.getTokenType();
      if (tokenType == null) break;

      if (range != null) {
        if (lexer.getTokenEnd() <= range.getStartOffset()) continue;
        if (lexer.getTokenStart() >= range.getEndOffset() &&
            (!multiLine || lastEndOffset < 0 || !containsOneLineBreak(chars, lastEndOffset, lexer.getTokenStart()))) break;
      }

      boolean isComment = commentTokens.contains(tokenType) || CacheUtil.isInComments(tokenType);

      if (isComment) {
        final int startDelta = builderForFile != null ? builderForFile.getCommentStartDelta(lexer.getTokenType()) : 0;
        final int endDelta = builderForFile != null ? builderForFile.getCommentEndDelta(lexer.getTokenType()) : 0;

        int start = lexer.getTokenStart() + startDelta;
        int end = lexer.getTokenEnd() - endDelta;
        assert start <= end : "Invalid comment range: " +
                              new TextRange(start, end) +
                              "; lexer token range=" +
                              new TextRange(lexer.getTokenStart(), lexer.getTokenEnd()) +
                              "; delta=" +
                              new TextRange(startDelta, endDelta) +
                              "; lexer=" +
                              lexer +
                              "; builder=" +
                              builderForFile +
                              "; chars length:" +
                              chars.length();
        assert end <= chars.length() : "Invalid comment end: " +
                                       new TextRange(start, end) +
                                       "; lexer token range=" +
                                       new TextRange(lexer.getTokenStart(), lexer.getTokenEnd()) +
                                       "; delta=" +
                                       new TextRange(startDelta, endDelta) +
                                       "; lexer=" +
                                       lexer +
                                       "; builder=" +
                                       builderForFile +
                                       "; chars length:" +
                                       chars.length();
        commentRanges.add(new CommentRange(start, end,
                                           builderForFile == null ? "" : builderForFile.getCharsAllowedInContinuationPrefix(tokenType)));
        lastEndOffset = end;
      }
    }
    return commentRanges;
  }

  private static boolean containsOneLineBreak(CharSequence text, int startOffset, int endOffset) {
    boolean hasLineBreak = false;
    for (int i = startOffset; i < endOffset; i++) {
      if (text.charAt(i) == '\n') {
        if (hasLineBreak) return false;
        hasLineBreak = true;
      }
    }
    return hasLineBreak;
  }

  private static boolean collectPatternMatches(IndexPattern[] allIndexPatterns,
                                               IndexPattern indexPattern,
                                               CharSequence chars,
                                               List<CommentRange> commentRanges,
                                               int commentNum,
                                               PsiFile file,
                                               TextRange range,
                                               Processor<? super IndexPatternOccurrence> consumer,
                                               TIntArrayList matches,
                                               boolean multiLine
                                               ) {
    CommentRange commentRange = commentRanges.get(commentNum);
    int commentStart = commentRange.startOffset;
    int commentEnd = commentRange.endOffset;
    Pattern pattern = indexPattern.getPattern();
    if (pattern != null) {
      ProgressManager.checkCanceled();

      CharSequence input = new CharSequenceSubSequence(chars, commentStart, commentEnd);
      Matcher matcher = pattern.matcher(input);
      while (true) {
        //long time1 = System.currentTimeMillis();
        boolean found = matcher.find();
        //long time2 = System.currentTimeMillis();
        //System.out.println("scanned text of length " + (lexer.getTokenEnd() - lexer.getTokenStart() + " in " + (time2 - time1) + " ms"));

        if (!found) break;
        int start = matcher.start() + commentStart;
        int end = matcher.end() + commentStart;
        if (start != end) {
          if ((range == null || range.getStartOffset() <= start && end <= range.getEndOffset()) && matches.indexOf(start) == -1) {
            List<TextRange> additionalRanges = multiLine ? findContinuation(start, chars, allIndexPatterns, commentRanges, commentNum)
                                                         : Collections.emptyList();
            if (range != null && !additionalRanges.isEmpty() &&
                additionalRanges.get(additionalRanges.size() - 1).getEndOffset() > range.getEndOffset()) {
              continue;
            }
            matches.add(start);
            IndexPatternOccurrenceImpl occurrence = new IndexPatternOccurrenceImpl(file, start, end, indexPattern, additionalRanges);
            if (!consumer.process(occurrence)) {
              return false;
            }
          }
        }

        ProgressManager.checkCanceled();
      }
    }
    return true;
  }

  private static List<TextRange> findContinuation(int mainRangeStartOffset, CharSequence text, IndexPattern[] allIndexPatterns,
                                                  List<CommentRange> commentRanges, int commentNum) {
    int lineStartOffset = CharArrayUtil.shiftBackwardUntil(text, mainRangeStartOffset - 1, "\n") + 1;
    int lineEndOffset = CharArrayUtil.shiftForwardUntil(text, mainRangeStartOffset, "\n");
    int offsetInLine = mainRangeStartOffset - lineStartOffset;
    List<TextRange> result = new ArrayList<>();
    outer:
    while (lineEndOffset < text.length()) {
      lineStartOffset = lineEndOffset + 1;
      lineEndOffset = CharArrayUtil.shiftForwardUntil(text, lineStartOffset, "\n");
      int refOffset = lineStartOffset + offsetInLine;
      int continuationStartOffset = CharArrayUtil.shiftForward(text, refOffset, lineEndOffset, WHITESPACE);
      if (continuationStartOffset == refOffset || continuationStartOffset >= lineEndOffset) break;
      if (continuationStartOffset >= commentRanges.get(commentNum).endOffset) {
        commentNum++;
        if (commentNum >= commentRanges.size() ||
            continuationStartOffset < commentRanges.get(commentNum).startOffset ||
            continuationStartOffset >= commentRanges.get(commentNum).endOffset) {
          break;
        }
      }
      CommentRange commentRange = commentRanges.get(commentNum);
      int commentStartOffset = Math.max(lineStartOffset, commentRange.startOffset);
      int continuationEndOffset = Math.min(lineEndOffset, commentRange.endOffset);
      if (refOffset < commentStartOffset ||
          CharArrayUtil.shiftBackward(text, commentStartOffset, refOffset - 1,
                                      WHITESPACE + commentRange.allowedContinuationPrefixChars) + 1 != commentStartOffset) {
        break;
      }
      CharSequence commentText = text.subSequence(commentStartOffset, continuationEndOffset);
      for (IndexPattern pattern: allIndexPatterns) {
        Pattern p = pattern.getPattern();
        if (p != null && p.matcher(commentText).find()) break outer;
      }
      result.add(new TextRange(continuationStartOffset, continuationEndOffset));
    }
    return result.isEmpty() ? Collections.emptyList() : result;
  }

  private static class CommentRange {
    private static final Comparator<CommentRange> BY_START_OFFSET_THEN_BY_END_OFFSET =
      Comparator.comparingInt((CommentRange o) -> o.startOffset).thenComparingInt((CommentRange o) -> o.endOffset);

    private final int startOffset;
    private final int endOffset;
    private final String allowedContinuationPrefixChars;

    private CommentRange(int startOffset, int endOffset) {
      this(startOffset, endOffset, "");
    }

    private CommentRange(int startOffset, int endOffset, String chars) {
      this.startOffset = startOffset;
      this.endOffset = endOffset;
      allowedContinuationPrefixChars = chars;
    }
  }
}
