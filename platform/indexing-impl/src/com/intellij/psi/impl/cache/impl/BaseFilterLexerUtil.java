/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi.impl.cache.impl;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.Key;
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry;
import com.intellij.psi.impl.cache.impl.id.IdTableBuilding;
import com.intellij.psi.impl.cache.impl.id.LexingIdIndexer;
import com.intellij.psi.impl.cache.impl.todo.TodoIndexEntry;
import com.intellij.psi.impl.cache.impl.todo.TodoIndexers;
import com.intellij.psi.search.IndexPattern;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.IdDataConsumer;
import gnu.trove.THashMap;

import java.util.Collections;
import java.util.Map;

public class BaseFilterLexerUtil {
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

    final boolean needTodo = TodoIndexers.needsTodoIndex(content.getFile());
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
