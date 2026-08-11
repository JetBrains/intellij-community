// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl;

import com.intellij.find.FindModel;
import com.intellij.find.FindResult;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.LanguageUtil;
import com.intellij.lang.ParserDefinition;
import com.intellij.lexer.LayeredLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.KeyWithDefaultValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.StringPattern;
import com.intellij.psi.CustomHighlighterTokenType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.usages.ChunkExtractor;
import com.intellij.usages.impl.SyntaxHighlighterOverEditorHighlighter;
import com.intellij.util.containers.SmartHashSet;
import com.intellij.util.text.StringSearcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * Searching for a string inside comments and string literals only, which is done by lexing the file and matching within
 * the tokens the model is interested in rather than over the text as a whole.
 * <p>
 * The lexer and searcher are expensive to build, so they are cached per thread on the {@link FindModel}; that cache is
 * what makes walking a file one occurrence at a time affordable.
 */
final class CommentsAndLiteralsSearcher {
  private static final Key<ThreadLocal<SoftReference<CommentsLiteralsSearchData>>> ourCommentsLiteralsSearchDataKey =
    KeyWithDefaultValue.create("comments.literals.search.data", () -> new ThreadLocal<>());
  /** How many characters of text the comments/literals walk may cover between two cancellation checks. */
  private static final int CANCELLATION_CHECK_INTERVAL = 8 * 1024;

  private final @NotNull Project myProject;

  CommentsAndLiteralsSearcher(@NotNull Project project) {
    myProject = project;
  }

  static void clearPreviousFindData(@NotNull FindModel model) {
    model.putUserData(ourCommentsLiteralsSearchDataKey, null);
  }

  /**
   * Looks up, and creates if necessary, the lexer and searcher this thread uses to walk {@code file} for {@code model}.
   * Returns {@code null} when the file has no syntax highlighter, in which case there is nothing to search.
   */
  private @Nullable CommentsLiteralsSearchData getSearchData(@NotNull CharSequence text,
                                                             @NotNull FindModel model,
                                                             @NotNull VirtualFile file) {
    ThreadLocal<SoftReference<CommentsLiteralsSearchData>> data;
    synchronized (model) {
      data = model.getUserData(ourCommentsLiteralsSearchDataKey);
      assert data != null;
    }

    FileType ftype = file.getFileType();
    Language lang = LanguageUtil.getLanguageForPsi(myProject, file, ftype);

    SoftReference<CommentsLiteralsSearchData> currentThreadDataRef = data.get();
    CommentsLiteralsSearchData currentThreadData = currentThreadDataRef == null ? null : currentThreadDataRef.get();
    if (currentThreadData == null || !Comparing.equal(currentThreadData.lastFile, file) || !currentThreadData.model.equals(model)) {
      SyntaxHighlighter highlighter = getHighlighter(file, lang);

      if (highlighter == null) {
        // no syntax highlighter -> no search
        return null;
      }

      TokenSet tokensOfInterest = TokenSet.EMPTY;
      Set<Language> relevantLanguages;
      if (lang != null) {
        final Language finalLang = lang;
        relevantLanguages = ReadAction.computeBlocking(() -> {
          Set<Language> result = new SmartHashSet<>();
          FileViewProvider viewProvider = PsiManager.getInstance(myProject).findViewProvider(file);
          if (viewProvider != null) {
            result.addAll(viewProvider.getLanguages());
          }

          if (result.isEmpty()) {
            result.add(finalLang);
          }
          return result;
        });

        for (Language relevantLanguage : relevantLanguages) {
          tokensOfInterest = addTokenTypesForLanguage(model, relevantLanguage, tokensOfInterest);
        }
      }
      else {
        relevantLanguages = Collections.emptySet();
        if (ftype instanceof AbstractFileType) {
          if (model.isInCommentsOnly()) {
            tokensOfInterest = TokenSet.create(CustomHighlighterTokenType.LINE_COMMENT, CustomHighlighterTokenType.MULTI_LINE_COMMENT);
          }
          if (model.isInStringLiteralsOnly()) {
            tokensOfInterest = TokenSet.orSet(tokensOfInterest, TokenSet
              .create(CustomHighlighterTokenType.STRING, CustomHighlighterTokenType.SINGLE_QUOTED_STRING));
          }
        }
      }

      Matcher matcher = model.isRegularExpressions() ? FindManagerBase.compileRegExp(model, "") : null;
      // a regular expression that failed to compile falls back to searching for its source text literally
      TokenSearcher tokenSearcher = createTokenSearcher(model, matcher);

      LayeredLexer.ourDisableLayersFlag.set(Boolean.TRUE);

      try {
        SyntaxHighlighterOverEditorHighlighter highlighterAdapter = ReadAction.computeBlocking(() -> {
          return new SyntaxHighlighterOverEditorHighlighter(highlighter, file, myProject);
        });
        currentThreadData = new CommentsLiteralsSearchData(
          file,
          lang,
          relevantLanguages,
          highlighterAdapter,
          tokensOfInterest,
          tokenSearcher,
          model.clone()
        );
        currentThreadData.highlighter.restart(text);
      }
      finally {
        LayeredLexer.ourDisableLayersFlag.remove();
      }

      data.set(new SoftReference<>(currentThreadData));
    }

    return currentThreadData;
  }

  private static @NotNull TokenSearcher createTokenSearcher(@NotNull FindModel model, @Nullable Matcher matcher) {
    if (matcher != null) {
      return new RegexpTokenSearcher(matcher);
    }
    else {
      StringSearcher stringSearcher = new StringSearcher(model.getStringToFind(), model.isCaseSensitive(), true);
      return new PlainTokenSearcher(stringSearcher, model.getStringToFind().length());
    }
  }

  /**
   * Drives the highlighting lexer once over {@code [fromOffset, text.length())} and reports every occurrence of the
   * pattern that lies inside a comment or a string literal, in increasing offset order, until {@code processor} stops it.
   * <p>
   * The walk deliberately does no filtering of its own: which occurrences are interesting, and when to stop, is the
   * caller's business. That is what lets a caller that wants all of them get them from a single pass instead of
   * restarting the walk once per occurrence.
   */
  private static void processOccurrences(@NotNull CharSequence text,
                                         char @Nullable [] textArray,
                                         int fromOffset,
                                         @NotNull FindModel model,
                                         @NotNull CommentsLiteralsSearchData currentThreadData,
                                         @NotNull OccurrenceProcessor processor) {
    currentThreadData.highlighter.resetPosition(fromOffset);
    final Lexer lexer = currentThreadData.highlighter.getHighlightingLexer();

    IElementType tokenType;
    TokenSet tokens = currentThreadData.tokensOfInterest;

    int lastGoodOffset = 0;

    // Budget the cancellation checks in characters rather than in loop iterations: a merging lexer collapses a run of
    // same-type tokens into one, so a megabyte of comment or character data can arrive as a single token and an
    // iteration counter would let it pass unchecked.
    int nextCancellationCheckAt = 0;
    while ((tokenType = lexer.getTokenType()) != null) {
      if (lexer.getTokenEnd() >= nextCancellationCheckAt) {
        ProgressManager.checkCanceled();
        nextCancellationCheckAt = lexer.getTokenEnd() + CANCELLATION_CHECK_INTERVAL;
      }

      if (lexer.getState() == 0) lastGoodOffset = lexer.getTokenStart();

      final TextAttributesKey[] keys = currentThreadData.highlighter.getTokenHighlights(tokenType);

      if (tokens.contains(tokenType) ||
          model.isInStringLiteralsOnly() && ChunkExtractor.isHighlightedAsString(keys) ||
          model.isInCommentsOnly() && ChunkExtractor.isHighlightedAsComment(keys)
      ) {
        int start = lexer.getTokenStart();
        int end = lexer.getTokenEnd();
        if (model.isInStringLiteralsOnly()) { // skip literal quotes itself from matching
          char c = text.charAt(start);
          if (c == '"' || c == '\'') {
            while (start < end && c == text.charAt(start)) {
              ++start;
              if (c == text.charAt(end - 1) && start < end) --end;
            }
          }
        }

        final int tokenContentStart = start;

        while (true) {
          if (start >= nextCancellationCheckAt) {
            ProgressManager.checkCanceled();
            nextCancellationCheckAt = start + CANCELLATION_CHECK_INTERVAL;
          }

          Match match = currentThreadData.tokenSearcher.findNext(text, textArray, tokenContentStart, start, end);
          if (match == null) break;
          if (!processor.process(match.start, match.end, lastGoodOffset)) return;

          // step past the occurrence; the +1 keeps a zero-length regexp match from spinning on the same offset
          start = match.end + (start == end || start == match.end ? 1 : 0);
        }
      }
      else {
        Language tokenLang = tokenType.getLanguage();
        if (tokenLang != currentThreadData.lang && tokenLang != Language.ANY && !currentThreadData.relevantLanguages.contains(tokenLang)) {
          tokens = addTokenTypesForLanguage(model, tokenLang, tokens);
          currentThreadData.tokensOfInterest = tokens;
          currentThreadData.relevantLanguages.add(tokenLang);
        }
      }

      lexer.advance();
    }
  }

  /**
   * Finds the one occurrence next to {@code offset} that lies inside a comment or a string literal: going forward, the
   * first one starting at or after it; going backward, the last one ending before it. Returns
   * {@link FindManagerBase#NOT_FOUND_RESULT} when there is none, or when the file has no syntax highlighter to lex it
   * with.
   * <p>
   * Only the occurrence is decided here. Whether it is acceptable -- whole words, the find context -- belongs to
   * {@link FindManagerBase}, which calls this once per occurrence, so walking a whole file means as many calls as it
   * has occurrences.
   * <p>
   * Both directions walk the token stream forward; backward simply keeps the last occurrence it passes and stops at
   * the first one reaching {@code offset}, which means a backward search always costs a walk from the start of the
   * file. A forward search tries to do better by resuming from
   * {@link CommentsLiteralsSearchData#startOffset the last point the lexer can safely be restarted at}, which only
   * advances where the lexer returns to its initial state -- in a long attribute list or a large comment it does not,
   * and the walk starts over from the beginning of the file.
   *
   * @param textArray the backing array of {@code text} where it has one, or {@code null}; an optimization that lets
   *                  the scan avoid {@link CharSequence#charAt} calls
   */
  @NotNull FindResult findInCommentsAndLiterals(@NotNull CharSequence text,
                                                char[] textArray,
                                                int offset,
                                                @NotNull FindModel model,
                                                @NotNull VirtualFile file) {
    CommentsLiteralsSearchData data = getSearchData(text, model, file);
    if (data == null) return FindManagerBase.NOT_FOUND_RESULT;

    FindResultImpl[] result = {FindManagerBase.NOT_FOUND_RESULT};
    if (model.isForward()) {
      int initialStartOffset = data.startOffset < offset ? data.startOffset : 0;
      processOccurrences(text, textArray, initialStartOffset, model, data, (start, end, lastGoodOffset) -> {
        if (start < offset) return true; // an occurrence the caller has already been given
        data.startOffset = lastGoodOffset;
        result[0] = new FindResultImpl(start, end);
        return false;
      });
    }
    else {
      // walks forward too, keeping the last occurrence that ends before `offset`
      processOccurrences(text, textArray, 0, model, data, (start, end, _) -> {
        if (end >= offset) return false;
        result[0] = new FindResultImpl(start, end);
        return true;
      });
    }
    return result[0];
  }

  private static @NotNull TokenSet addTokenTypesForLanguage(@NotNull FindModel model,
                                                            @NotNull Language lang,
                                                            @NotNull TokenSet tokensOfInterest) {
    ParserDefinition definition = LanguageParserDefinitions.INSTANCE.forLanguage(lang);
    if (definition != null) {
      tokensOfInterest = TokenSet.orSet(tokensOfInterest, model.isInCommentsOnly() ? definition.getCommentTokens() : TokenSet.EMPTY);
      tokensOfInterest = TokenSet.orSet(tokensOfInterest, model.isInStringLiteralsOnly() ? definition.getStringLiteralElements() : TokenSet.EMPTY);
    }
    return tokensOfInterest;
  }

  private static @Nullable SyntaxHighlighter getHighlighter(VirtualFile file, @Nullable Language lang) {
    SyntaxHighlighter syntaxHighlighter = lang != null ? SyntaxHighlighterFactory.getSyntaxHighlighter(lang, null, file) : null;
    if (lang == null || syntaxHighlighter instanceof PlainSyntaxHighlighter) {
      syntaxHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(file.getFileType(), null, file);
    }

    return syntaxHighlighter;
  }

  /**
   * Receives the occurrences found by {@link #processOccurrences}, in increasing offset order.
   */
  @FunctionalInterface
  private interface OccurrenceProcessor {
    /**
     * @param lastGoodOffset start of the last token at which the lexer was in its initial state, i.e. the furthest point
     *                       a later call may safely resume lexing from
     * @return {@code false} to stop the walk
     */
    boolean process(int startOffset, int endOffset, int lastGoodOffset);
  }

  /** Where an occurrence was found, filled in by {@link TokenSearcher#findNext}. */
  private record Match(int start, int end) {
  }

  /**
   * How occurrences are located inside a single token. Which of the two applies is decided once, when the search data
   * is built, and cannot change afterwards -- so the walk neither chooses between them nor has to consider that
   * neither might be set.
   */
  private sealed interface TokenSearcher {
    /**
     * Finds the first occurrence within {@code [start, end)}, where the token being searched begins at
     * {@code tokenContentStart}.
     *
     * @return Match or null if nothing is found
     */
    @Nullable Match findNext(@NotNull CharSequence text,
                             char @Nullable [] textArray,
                             int tokenContentStart,
                             int start,
                             int end);
  }

  /**
   * @param patternLength taken from the string to find rather than from the searcher, whose pattern may have a
   *                      different length after the case transform it applies
   */
  private record PlainTokenSearcher(@NotNull StringSearcher searcher, int patternLength) implements TokenSearcher {
    @Override
    public @Nullable Match findNext(@NotNull CharSequence text,
                                    char @Nullable [] textArray,
                                    int tokenContentStart,
                                    int start,
                                    int end) {
      int found = searcher.scan(text, textArray, start, end);
      if (found == -1 || found < start) return null;
      return new Match(found, found + patternLength);
    }
  }

  private record RegexpTokenSearcher(@NotNull Matcher matcher) implements TokenSearcher {
    @Override
    public @Nullable Match findNext(@NotNull CharSequence text,
                                    char @Nullable [] textArray,
                                    int tokenContentStart,
                                    int start,
                                    int end) {
      if (start > end) return null;
      matcher.reset(StringPattern.newBombedCharSequence(text.subSequence(tokenContentStart, end)));
      matcher.region(start - tokenContentStart, end - tokenContentStart);
      matcher.useTransparentBounds(true);
      if (!matcher.find()) return null;
      return new Match(tokenContentStart + matcher.start(), tokenContentStart + matcher.end());
    }
  }

  private static final class CommentsLiteralsSearchData {
    @NotNull final VirtualFile lastFile;
    final @Nullable Language lang;
    int startOffset;
    @NotNull final SyntaxHighlighterOverEditorHighlighter highlighter;

    @NotNull TokenSet tokensOfInterest;
    @NotNull final TokenSearcher tokenSearcher;
    @NotNull final Set<Language> relevantLanguages;
    @NotNull final FindModel model;

    CommentsLiteralsSearchData(@NotNull VirtualFile lastFile,
                               @Nullable Language lang,
                               @NotNull Set<Language> relevantLanguages,
                               @NotNull SyntaxHighlighterOverEditorHighlighter highlighter,
                               @NotNull TokenSet tokensOfInterest,
                               @NotNull TokenSearcher tokenSearcher,
                               @NotNull FindModel model) {
      this.lastFile = lastFile;
      this.lang = lang;
      this.highlighter = highlighter;
      this.tokensOfInterest = tokensOfInterest;
      this.tokenSearcher = tokenSearcher;
      this.relevantLanguages = relevantLanguages;
      this.model = model;
    }
  }
}
