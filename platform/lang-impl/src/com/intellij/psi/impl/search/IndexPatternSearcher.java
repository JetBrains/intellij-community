/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.psi.impl.search;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.cache.CacheManager;
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

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public class IndexPatternSearcher implements QueryExecutor<IndexPatternOccurrence, IndexPatternSearch.SearchParameters> {
  public boolean execute(final IndexPatternSearch.SearchParameters queryParameters, final Processor<IndexPatternOccurrence> consumer) {
    final PsiFile file = queryParameters.getFile();
    VirtualFile virtualFile = file.getVirtualFile();
    if (file instanceof PsiBinaryFile || file instanceof PsiCompiledElement || virtualFile == null) {
      return true;
    }

    final CacheManager cacheManager = ((PsiManagerEx)file.getManager()).getCacheManager();
    final IndexPatternProvider patternProvider = queryParameters.getPatternProvider();
    int count = patternProvider != null
                ? cacheManager.getTodoCount(virtualFile, patternProvider)
                : cacheManager.getTodoCount(virtualFile, queryParameters.getPattern());
    if (count == 0) return true;

    TIntArrayList commentStarts = new TIntArrayList();
    TIntArrayList commentEnds = new TIntArrayList();

    final CharSequence chars = file.getViewProvider().getContents();
    findCommentTokenRanges(file, chars, queryParameters.getRange(), commentStarts, commentEnds);

    for (int i = 0; i < commentStarts.size(); i++) {
      int commentStart = commentStarts.get(i);
      int commentEnd = commentEnds.get(i);

      if (patternProvider != null) {
        for (final IndexPattern pattern : patternProvider.getIndexPatterns()) {
          if (!collectPatternMatches(pattern, chars, commentStart, commentEnd, file, queryParameters.getRange(), consumer)) {
            return false;
          }
        }
      }
      else {
        if (!collectPatternMatches(queryParameters.getPattern(), chars, commentStart, commentEnd, file, queryParameters.getRange(),
                                   consumer)) {
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
      synchronized (PsiLock.LOCK) {
        if (fType instanceof AbstractFileType) {
          Lexer lexer = SyntaxHighlighter.PROVIDER.create(fType, file.getProject(), file.getVirtualFile()).getHighlightingLexer();
          findComments(lexer, chars, range, COMMENT_TOKENS, commentStarts, commentEnds, null);
        }
        else {
          commentStarts.add(0);
          commentEnds.add(file.getTextLength());
        }
      }
    }
    else {
      // collect comment offsets to prevent long locks by PsiManagerImpl.LOCK
      synchronized (PsiLock.LOCK) {
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
            findComments(lexer, chars, range, commentTokens, commentStarts, commentEnds, builderForFile);
          }
        }
      }
    }
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

      boolean isComment = commentTokens.contains(tokenType);
      if (!isComment) {
        final Language commentLang = tokenType.getLanguage();
        final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(commentLang);
        if (parserDefinition != null) {
          final TokenSet langCommentTokens = parserDefinition.getCommentTokens();
          isComment = langCommentTokens.contains(tokenType);
        }
      }

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
                                               Processor<IndexPatternOccurrence> consumer) {
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
          if (range == null || range.getStartOffset() <= start && end <= range.getEndOffset()) {
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
