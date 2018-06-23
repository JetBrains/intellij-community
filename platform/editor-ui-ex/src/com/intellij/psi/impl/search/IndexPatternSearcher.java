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
import com.intellij.util.text.CharSequenceSubSequence;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public class IndexPatternSearcher implements QueryExecutor<IndexPatternOccurrence, IndexPatternSearch.SearchParameters> {
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
    TIntArrayList commentStarts = new TIntArrayList();
    TIntArrayList commentEnds = new TIntArrayList();

    final CharSequence chars = file.getViewProvider().getContents();
    findCommentTokenRanges(file, chars, queryParameters.getRange(), commentStarts, commentEnds);
    TIntArrayList occurrences = new TIntArrayList(1);
    IndexPattern[] patterns = patternProvider != null ? patternProvider.getIndexPatterns() : null;

    for (int i = 0; i < commentStarts.size(); i++) {
      int commentStart = commentStarts.get(i);
      int commentEnd = commentEnds.get(i);
      occurrences.resetQuick();

      if (patternProvider != null) {
        for (int j = patterns.length - 1; j >=0; --j) {
          if (!collectPatternMatches(patterns[j], chars, commentStart, commentEnd, file, queryParameters.getRange(), consumer, occurrences)) {
            return false;
          }
        }
      }
      else {
        if (!collectPatternMatches(queryParameters.getPattern(), chars, commentStart, commentEnd, file, queryParameters.getRange(),
                                   consumer, occurrences)) {
          return false;
        }
      }
    }

    return true;
  }


  private static final TokenSet COMMENT_TOKENS =
    TokenSet.create(CustomHighlighterTokenType.LINE_COMMENT, CustomHighlighterTokenType.MULTI_LINE_COMMENT);

  private static void findCommentTokenRanges(final PsiFile file,
                                             final CharSequence chars,
                                             final TextRange range,
                                             final TIntArrayList commentStarts,
                                             final TIntArrayList commentEnds) {
    if (file instanceof PsiPlainTextFile) {
      FileType fType = file.getFileType();
      if (fType instanceof CustomSyntaxTableFileType) {
        Lexer lexer = SyntaxHighlighterFactory.getSyntaxHighlighter(fType, file.getProject(), file.getVirtualFile()).getHighlightingLexer();
        findComments(lexer, chars, range, COMMENT_TOKENS, commentStarts, commentEnds, null);
      }
      else {
        commentStarts.add(0);
        commentEnds.add(file.getTextLength());
      }
    }
    else {
      final FileViewProvider viewProvider = file.getViewProvider();
      final Set<Language> relevantLanguages = viewProvider.getLanguages();
      for (Language lang : relevantLanguages) {
        final TIntArrayList commentStartsList = new TIntArrayList();
        final TIntArrayList commentEndsList = new TIntArrayList();

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
          findComments(lexer, chars, range, commentTokens, commentStartsList, commentEndsList, builderForFile);
          mergeCommentLists(commentStarts, commentEnds, commentStartsList, commentEndsList);
        }
      }
    }
  }

  private static void mergeCommentLists(TIntArrayList commentStarts,
                                        TIntArrayList commentEnds,
                                        TIntArrayList commentStartsList,
                                        TIntArrayList commentEndsList) {
    if (commentStarts.isEmpty() && commentEnds.isEmpty()) {
      commentStarts.add(commentStartsList.toNativeArray());
      commentEnds.add(commentEndsList.toNativeArray());
      return;
    }

    mergeSortedArrays(commentStarts, commentEnds, commentStartsList, commentEndsList);
  }

  /**
   * Merge sorted points, which are sorted by x and with equal x by y.
   * Result is put to x1 y1.
   */
  static void mergeSortedArrays(@NotNull TIntArrayList x1,
                                @NotNull TIntArrayList y1,
                                @NotNull TIntArrayList x2,
                                @NotNull TIntArrayList y2) {
    TIntArrayList newX = new TIntArrayList();
    TIntArrayList newY = new TIntArrayList();

    int i = 0;
    int j = 0;

    while (i < x1.size() && j < x2.size()) {
      if (x1.get(i) < x2.get(j) || x1.get(i) == x2.get(j) && y1.get(i) < y2.get(j)) {
        newX.add(x1.get(i));
        newY.add(y1.get(i));
        i++;
      }
      else if (x1.get(i) > x2.get(j) || x1.get(i) == x2.get(j) && y1.get(i) > y2.get(j)) {
        newX.add(x2.get(j));
        newY.add(y2.get(j));
        j++;
      }
      else { //equals
        newX.add(x1.get(i));
        newY.add(y1.get(i));
        i++;
        j++;
      }
    }

    while (i < x1.size()) {
      newX.add(x1.get(i));
      newY.add(y1.get(i));
      i++;
    }

    while (j < x2.size()) {
      newX.add(x2.get(j));
      newY.add(y2.get(j));
      j++;
    }

    x1.clear();
    y1.clear();
    x1.add(newX.toNativeArray());
    y1.add(newY.toNativeArray());
  }

  private static void findComments(final Lexer lexer,
                                   final CharSequence chars,
                                   final TextRange range,
                                   final TokenSet commentTokens,
                                   final TIntArrayList commentStarts,
                                   final TIntArrayList commentEnds,
                                   final IndexPatternBuilder builderForFile) {
    for (lexer.start(chars); ; lexer.advance()) {
      IElementType tokenType = lexer.getTokenType();
      if (tokenType == null) break;

      if (range != null) {
        if (lexer.getTokenEnd() <= range.getStartOffset()) continue;
        if (lexer.getTokenStart() >= range.getEndOffset()) break;
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
        commentStarts.add(start);
        commentEnds.add(end);
      }
    }
  }

  private static boolean collectPatternMatches(IndexPattern indexPattern,
                                               CharSequence chars,
                                               int commentStart,
                                               int commentEnd,
                                               PsiFile file,
                                               TextRange range,
                                               Processor<? super IndexPatternOccurrence> consumer,
                                               TIntArrayList matches
                                               ) {
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
            matches.add(start);
            if (!consumer.process(new IndexPatternOccurrenceImpl(file, start, end, indexPattern))) {
              return false;
            }
          }
        }

        ProgressManager.checkCanceled();
      }
    }
    return true;
  }
}
