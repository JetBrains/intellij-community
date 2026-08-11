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
        return FindManagerBase.NOT_FOUND_RESULT;
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
      StringSearcher searcher = matcher != null ? null : new StringSearcher(model.getStringToFind(), model.isCaseSensitive(), true);
      LayeredLexer.ourDisableLayersFlag.set(Boolean.TRUE);

      try {
        SyntaxHighlighterOverEditorHighlighter highlighterAdapter = ReadAction.computeBlocking(() -> {
          return new SyntaxHighlighterOverEditorHighlighter(highlighter, file, myProject);
        });
        currentThreadData = new CommentsLiteralsSearchData(
          file,
          relevantLanguages,
          highlighterAdapter,
          tokensOfInterest,
          searcher,
          matcher,
          model.clone()
        );
        currentThreadData.highlighter.restart(text);
      }
      finally {
        LayeredLexer.ourDisableLayersFlag.remove();
      }

      data.set(new SoftReference<>(currentThreadData));
    }

    int initialStartOffset = model.isForward() && currentThreadData.startOffset < offset ? currentThreadData.startOffset : 0;
    currentThreadData.highlighter.resetPosition(initialStartOffset);
    final Lexer lexer = currentThreadData.highlighter.getHighlightingLexer();

    IElementType tokenType;
    TokenSet tokens = currentThreadData.tokensOfInterest;

    int lastGoodOffset = 0;
    boolean scanningForward = model.isForward();
    FindResultImpl prevFindResult = FindManagerBase.NOT_FOUND_RESULT;

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

          FindResultImpl findResult = null;

          if (currentThreadData.searcher != null) {
            int matchStart = currentThreadData.searcher.scan(text, textArray, start, end);

            if (matchStart != -1 && matchStart >= start) {
              final int matchEnd = matchStart + model.getStringToFind().length();
              if (matchStart >= offset || !scanningForward) {
                findResult = new FindResultImpl(matchStart, matchEnd);
              }
              else {
                start = matchEnd;
                continue;
              }
            }
          }
          else if (start <= end) {
            currentThreadData.matcher.reset(StringPattern.newBombedCharSequence(text.subSequence(tokenContentStart, end)));
            currentThreadData.matcher.region(start - tokenContentStart, end - tokenContentStart);
            currentThreadData.matcher.useTransparentBounds(true);
            if (currentThreadData.matcher.find()) {
              final int matchEnd = tokenContentStart + currentThreadData.matcher.end();
              int matchStart = tokenContentStart + currentThreadData.matcher.start();
              if (matchStart >= offset || !scanningForward) {
                findResult = new FindResultImpl(matchStart, matchEnd);
              }
              else {
                int diff = 0;
                if (start == end || start == matchEnd) {
                  diff = 1;
                }
                start = matchEnd + diff;
                continue;
              }
            }
          }

          if (findResult != null) {
            if (scanningForward) {
              currentThreadData.startOffset = lastGoodOffset;
              return findResult;
            }
            else {

              if (findResult.getEndOffset() >= offset) return prevFindResult;
              prevFindResult = findResult;
              start = findResult.getEndOffset();
              continue;
            }
          }
          break;
        }
      }
      else {
        Language tokenLang = tokenType.getLanguage();
        if (tokenLang != lang && tokenLang != Language.ANY && !currentThreadData.relevantLanguages.contains(tokenLang)) {
          tokens = addTokenTypesForLanguage(model, tokenLang, tokens);
          currentThreadData.tokensOfInterest = tokens;
          currentThreadData.relevantLanguages.add(tokenLang);
        }
      }

      lexer.advance();
    }

    return prevFindResult;
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

  private static final class CommentsLiteralsSearchData {
    @NotNull final VirtualFile lastFile;
    int startOffset;
    @NotNull final SyntaxHighlighterOverEditorHighlighter highlighter;

    @NotNull TokenSet tokensOfInterest;
    @Nullable final StringSearcher searcher;
    @Nullable final Matcher matcher;
    @NotNull final Set<Language> relevantLanguages;
    @NotNull final FindModel model;

    CommentsLiteralsSearchData(@NotNull VirtualFile lastFile,
                               @NotNull Set<Language> relevantLanguages,
                               @NotNull SyntaxHighlighterOverEditorHighlighter highlighter,
                               @NotNull TokenSet tokensOfInterest,
                               @Nullable StringSearcher searcher,
                               @Nullable Matcher matcher,
                               @NotNull FindModel model) {
      this.lastFile = lastFile;
      this.highlighter = highlighter;
      this.tokensOfInterest = tokensOfInterest;
      this.searcher = searcher;
      this.matcher = matcher;
      this.relevantLanguages = relevantLanguages;
      this.model = model;
    }
  }
}
