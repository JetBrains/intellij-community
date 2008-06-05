package com.intellij.psi.impl.search;

import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public class IndexPatternSearcher implements QueryExecutor<IndexPatternOccurrence, IndexPatternSearch.SearchParameters> {
  public boolean execute(final IndexPatternSearch.SearchParameters queryParameters, final Processor<IndexPatternOccurrence> consumer) {
    final PsiFile file = queryParameters.getFile();
    if (file instanceof PsiBinaryFile || file instanceof PsiCompiledElement ||
        file.getVirtualFile() == null) {
      return true;
    }

    final CacheManager cacheManager = ((PsiManagerEx)file.getManager()).getCacheManager();
    final IndexPatternProvider patternProvider = queryParameters.getPatternProvider();
    if (patternProvider != null) {
      if (cacheManager.getTodoCount(file.getVirtualFile(), patternProvider) == 0)
        return true;
    }
    else {
      if (cacheManager.getTodoCount(file.getVirtualFile(), queryParameters.getPattern()) == 0)
        return true;
    }

    TIntArrayList commentStarts = new TIntArrayList();
    TIntArrayList commentEnds = new TIntArrayList();

    final CharSequence chars = file.getViewProvider().getContents();
    findCommentTokenRanges(file, chars, queryParameters.getRange(), commentStarts, commentEnds);

    for (int i = 0; i < commentStarts.size(); i++) {
      int commentStart = commentStarts.get(i);
      int commentEnd = commentEnds.get(i);

      if (patternProvider != null) {
        for(final IndexPattern pattern: patternProvider.getIndexPatterns()) {
          if (!collectPatternMatches(pattern, chars, commentStart, commentEnd, file, queryParameters.getRange(), consumer)) {
            return false;
          }
        }
      }
      else {
        if (!collectPatternMatches(queryParameters.getPattern(), chars, commentStart, commentEnd, file, queryParameters.getRange(), consumer)) {
          return false;
        }
      }
    }

    return true;
  }


  private static void findCommentTokenRanges(final PsiFile file,
                                             final CharSequence chars,
                                             final TextRange range,
                                             final TIntArrayList commentStarts,
                                             final TIntArrayList commentEnds) {
    if (file instanceof PsiPlainTextFile) {
      FileType fType = file.getFileType();
      synchronized (PsiLock.LOCK) {
        if (fType instanceof AbstractFileType) {
          TokenSet commentTokens = TokenSet.create(CustomHighlighterTokenType.LINE_COMMENT, CustomHighlighterTokenType.MULTI_LINE_COMMENT);
          Lexer lexer = SyntaxHighlighter.PROVIDER.create(fType, file.getProject(), file.getVirtualFile()).getHighlightingLexer();
          findComments(lexer, chars, range, commentTokens, commentStarts, commentEnds, null);
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
        final Language lang = file.getLanguage();
        final SyntaxHighlighter syntaxHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(lang, file.getProject(), file.getVirtualFile());
        Lexer lexer = syntaxHighlighter.getHighlightingLexer();
        TokenSet commentTokens = null;
        IndexPatternBuilder builderForFile = null;
        for(IndexPatternBuilder builder: Extensions.getExtensions(IndexPatternBuilder.EP_NAME)) {
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

  private static void findComments(final Lexer lexer,
                                   final CharSequence chars,
                                   final TextRange range,
                                   final TokenSet commentTokens,
                                   final TIntArrayList commentStarts, final TIntArrayList commentEnds,
                                   final IndexPatternBuilder builderForFile) {
    for (lexer.start(chars,0,chars.length(),0); ; lexer.advance()) {
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

        commentStarts.add(lexer.getTokenStart() + startDelta);
        commentEnds.add(lexer.getTokenEnd() - endDelta);
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
      ProgressManager.getInstance().checkCanceled();

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

        ProgressManager.getInstance().checkCanceled();
      }
    }
    return true;
  }
}
