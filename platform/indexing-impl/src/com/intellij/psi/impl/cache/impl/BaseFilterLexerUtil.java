// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.cache.impl;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.Key;
import com.intellij.psi.impl.cache.impl.id.IdDataConsumer;
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry;
import com.intellij.psi.impl.cache.impl.id.IdTableBuilding;
import com.intellij.psi.impl.cache.impl.id.LexingIdIndexer;
import com.intellij.psi.impl.cache.impl.todo.TodoIndexEntry;
import com.intellij.psi.impl.cache.impl.todo.TodoIndexers;
import com.intellij.psi.search.IndexPattern;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.indexing.FileContent;
import gnu.trove.THashMap;

import java.util.Collections;
import java.util.Map;

public final class BaseFilterLexerUtil {
  private static final Key<ScanContent> scanContentKey = Key.create("id.todo.scan.content");
  private static final ScanContent EMPTY = new ScanContent(Collections.emptyMap(), Collections.emptyMap());

  public static ScanContent scanContent(FileContent content, IdAndToDoScannerBasedOnFilterLexer indexer) {
    IndexPattern[] patterns = IndexPatternUtil.getIndexPatterns();
    if (patterns.length <= 0) return EMPTY;

    ScanContent data = content.getUserData(scanContentKey);
    if (data != null) {
      content.putUserData(scanContentKey, null);
      return data;
    }

    final boolean needTodo = TodoIndexers.needsTodoIndex(content.getFile()) || content.getFile() instanceof LightVirtualFile;
    final boolean needIdIndex = IdTableBuilding.getFileTypeIndexer(content.getFileType()) instanceof LexingIdIndexer;

    final IdDataConsumer consumer = needIdIndex ? new IdDataConsumer() : null;
    final OccurrenceConsumer todoOccurrenceConsumer = new OccurrenceConsumer(consumer, needTodo);
    final Lexer filterLexer = indexer.createLexer(todoOccurrenceConsumer);
    filterLexer.start(content.getContentAsText());

    while (filterLexer.getTokenType() != null) filterLexer.advance();

    Map<TodoIndexEntry,Integer> todoMap = null;
    if (needTodo) {
      for (IndexPattern indexPattern : patterns) {
          final int count = todoOccurrenceConsumer.getOccurrenceCount(indexPattern);
          if (count > 0) {
            if (todoMap == null) todoMap = new THashMap<>();
            todoMap.put(new TodoIndexEntry(indexPattern.getPatternString(), indexPattern.isCaseSensitive()), count);
          }
        }
    }

    data = new ScanContent(
      consumer != null? consumer.getResult():Collections.emptyMap(),
      todoMap != null ? todoMap: Collections.emptyMap()
    );
    if (needIdIndex && needTodo) content.putUserData(scanContentKey, data);
    return data;
  }

  public static class ScanContent {
    public final Map<IdIndexEntry, Integer> idMap;
    public final Map<TodoIndexEntry,Integer> todoMap;

    public ScanContent(Map<IdIndexEntry, Integer> _idMap, Map<TodoIndexEntry, Integer> _todoMap) {
      idMap = _idMap;
      todoMap = _todoMap;
    }
  }
}
