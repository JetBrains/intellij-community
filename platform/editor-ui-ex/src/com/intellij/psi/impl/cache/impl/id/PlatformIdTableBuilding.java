// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.cache.impl.id;

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.InternalFileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.impl.CustomSyntaxTableFileType;
import com.intellij.openapi.util.Key;
import com.intellij.psi.CustomHighlighterTokenType;
import com.intellij.psi.impl.cache.CacheUtil;
import com.intellij.psi.impl.cache.impl.BaseFilterLexer;
import com.intellij.psi.impl.cache.impl.IndexPatternUtil;
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer;
import com.intellij.psi.impl.cache.impl.todo.TodoIndexEntry;
import com.intellij.psi.impl.cache.impl.todo.TodoIndexers;
import com.intellij.psi.impl.cache.impl.todo.VersionedTodoIndexer;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileContent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Author: dmitrylomov
 */
public abstract class PlatformIdTableBuilding {
  public static final Key<EditorHighlighter> EDITOR_HIGHLIGHTER = new Key<>("Editor");
  private static final TokenSet ABSTRACT_FILE_COMMENT_TOKENS = TokenSet.create(CustomHighlighterTokenType.LINE_COMMENT, CustomHighlighterTokenType.MULTI_LINE_COMMENT);

  private PlatformIdTableBuilding() {}

  @Nullable
  public static DataIndexer<TodoIndexEntry, Integer, FileContent> getTodoIndexer(FileType fileType) {
    final DataIndexer<TodoIndexEntry, Integer, FileContent> extIndexer = TodoIndexers.INSTANCE.forFileType(fileType);
    if (extIndexer != null) {
      return extIndexer;
    }

    if (fileType instanceof LanguageFileType) {
      final Language lang = ((LanguageFileType)fileType).getLanguage();
      final ParserDefinition parserDef = LanguageParserDefinitions.INSTANCE.forLanguage(lang);
      final TokenSet commentTokens = parserDef != null ? parserDef.getCommentTokens() : null;
      if (commentTokens != null) {
        return new TokenSetTodoIndexer(commentTokens);
      }
    }

    if (fileType instanceof CustomSyntaxTableFileType) {
      return new TokenSetTodoIndexer(ABSTRACT_FILE_COMMENT_TOKENS);
    }

    return null;
  }

  public static boolean checkCanUseCachedEditorHighlighter(final CharSequence chars, final EditorHighlighter editorHighlighter) {
    assert editorHighlighter instanceof LexerEditorHighlighter;
    final boolean b = ((LexerEditorHighlighter)editorHighlighter).checkContentIsEqualTo(chars);
    if (!b) {
      final Logger logger = Logger.getInstance(IdTableBuilding.class.getName());
      logger.warn("Unexpected mismatch of editor highlighter content with indexing content");
    }
    return b;
  }

  public static boolean isTodoIndexerRegistered(@NotNull FileType fileType) {
    return TodoIndexers.INSTANCE.forFileType(fileType) != null || fileType instanceof InternalFileType;
  }

  private static class TokenSetTodoIndexer extends VersionedTodoIndexer {
    @NotNull private final TokenSet myCommentTokens;

    TokenSetTodoIndexer(@NotNull final TokenSet commentTokens) {
      myCommentTokens = commentTokens;
    }

    @Override
    @NotNull
    public Map<TodoIndexEntry, Integer> map(@NotNull final FileContent inputData) {
      if (IndexPatternUtil.getIndexPatternCount() > 0) {
        final CharSequence chars = inputData.getContentAsText();
        final OccurrenceConsumer occurrenceConsumer = new OccurrenceConsumer(null, true);
        EditorHighlighter highlighter;

        final EditorHighlighter editorHighlighter = inputData.getUserData(EDITOR_HIGHLIGHTER);
        if (editorHighlighter != null && checkCanUseCachedEditorHighlighter(chars, editorHighlighter)) {
          highlighter = editorHighlighter;
        }
        else {
          highlighter = HighlighterFactory.createHighlighter(inputData.getProject(), inputData.getFile());
          highlighter.setText(chars);
        }

        final int documentLength = chars.length();
        BaseFilterLexer.TodoScanningState todoScanningState = null;
        final HighlighterIterator iterator = highlighter.createIterator(0);

        while (!iterator.atEnd()) {
          final IElementType token = iterator.getTokenType();

          if (myCommentTokens.contains(token) || CacheUtil.isInComments(token)) {
            int start = iterator.getStart();
            if (start >= documentLength) break;
            int end = iterator.getEnd();

            todoScanningState = BaseFilterLexer.advanceTodoItemsCount(
              chars.subSequence(start, Math.min(end, documentLength)),
              occurrenceConsumer,
              todoScanningState
            );
            if (end > documentLength) break;
          }
          iterator.advance();
        }
        final Map<TodoIndexEntry, Integer> map = new HashMap<>();
        for (IndexPattern pattern : IndexPatternUtil.getIndexPatterns()) {
          final int count = occurrenceConsumer.getOccurrenceCount(pattern);
          if (count > 0) {
            map.put(new TodoIndexEntry(pattern.getPatternString(), pattern.isCaseSensitive()), count);
          }
        }
        return map;
      }
      return Collections.emptyMap();
    }
  }
}
