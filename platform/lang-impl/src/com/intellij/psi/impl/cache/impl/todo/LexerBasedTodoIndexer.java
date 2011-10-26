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

package com.intellij.psi.impl.cache.impl.todo;

import com.intellij.lexer.Lexer;
import com.intellij.psi.impl.cache.impl.IndexPatternUtil;
import com.intellij.psi.search.IndexPattern;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileContent;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 20, 2008
 */
public abstract class LexerBasedTodoIndexer implements DataIndexer<TodoIndexEntry, Integer, FileContent> {
  @Override
  @NotNull
  public Map<TodoIndexEntry,Integer> map(final FileContent inputData) {
    
    final TodoOccurrenceConsumer todoOccurrenceConsumer = new TodoOccurrenceConsumer();
    final Lexer filterLexer = createLexer(todoOccurrenceConsumer);
    final CharSequence chars = inputData.getContentAsText();
    filterLexer.start(chars);
    while (filterLexer.getTokenType() != null) {
      filterLexer.advance();
    }
    final Map<TodoIndexEntry,Integer> map = new HashMap<TodoIndexEntry, Integer>();
    for (IndexPattern indexPattern : IndexPatternUtil.getIndexPatterns()) {
      final int count = todoOccurrenceConsumer.getOccurrenceCount(indexPattern);
      if (count > 0) {
        map.put(new TodoIndexEntry(indexPattern.getPatternString(), indexPattern.isCaseSensitive()), count);
      }
    }
    return map;
  }

  protected abstract Lexer createLexer(TodoOccurrenceConsumer consumer);
}
