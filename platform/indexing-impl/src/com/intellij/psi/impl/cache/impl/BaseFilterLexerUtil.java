// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.cache.impl;

import com.intellij.lexer.Lexer;
import com.intellij.psi.impl.cache.impl.id.IdDataConsumer;
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry;
import com.intellij.psi.impl.cache.impl.id.IdTableBuilding;
import com.intellij.psi.impl.cache.impl.id.LexingIdIndexer;
import com.intellij.psi.impl.cache.impl.todo.TodoIndexEntry;
import com.intellij.psi.impl.cache.impl.todo.TodoIndexers;
import com.intellij.psi.search.IndexPattern;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.indexing.FileContent;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class BaseFilterLexerUtil {

  public static @NotNull Map<IdIndexEntry, Integer> calcIdEntries(@NotNull FileContent content,
                                                                  @NotNull IdAndToDoScannerBasedOnFilterLexer indexer) {
    boolean needIdIndex = IdTableBuilding.getFileTypeIndexer(content.getFileType()) instanceof LexingIdIndexer;
    if (!needIdIndex) return Collections.emptyMap();
    IdDataConsumer consumer = new IdDataConsumer();
    OccurrenceConsumer occurrenceConsumer = new OccurrenceConsumer(consumer, false);
    Lexer filterLexer = indexer.createLexer(occurrenceConsumer);
    filterLexer.start(content.getContentAsText());
    while (filterLexer.getTokenType() != null) {
      filterLexer.advance();
    }
    return consumer.getResult();
  }

  public static @NotNull Map<TodoIndexEntry, Integer> calcTodoEntries(@NotNull FileContent content,
                                                                      @NotNull IdAndToDoScannerBasedOnFilterLexer indexer) {
    boolean needTodo = TodoIndexers.needsTodoIndex(content) || content.getFile() instanceof LightVirtualFile;
    IndexPattern[] todoPatterns = needTodo ? IndexPatternUtil.getIndexPatterns() : IndexPattern.EMPTY_ARRAY;
    if (todoPatterns.length == 0) return Collections.emptyMap();

    OccurrenceConsumer occurrenceConsumer = new OccurrenceConsumer(null, true);
    Lexer filterLexer = indexer.createLexer(occurrenceConsumer);
    filterLexer.start(content.getContentAsText());

    while (filterLexer.getTokenType() != null) {
      filterLexer.advance();
    }

    Map<TodoIndexEntry, Integer> todoMap = null;
    for (IndexPattern indexPattern : todoPatterns) {
      int count = occurrenceConsumer.getOccurrenceCount(indexPattern);
      if (count <= 0) continue;
      if (todoMap == null) todoMap = new HashMap<>();
      todoMap.put(new TodoIndexEntry(indexPattern.getPatternString(), indexPattern.isCaseSensitive()), count);
    }
    return todoMap != null ? todoMap : Collections.emptyMap();
  }
}
