package com.intellij.psi.impl.cache.index;

import com.intellij.lexer.Lexer;
import com.intellij.psi.impl.cache.impl.idCache.IdCacheUtil;
import com.intellij.psi.impl.cache.impl.idCache.TodoOccurrenceConsumer;
import com.intellij.psi.search.IndexPattern;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileBasedIndex;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 20, 2008
 */
public abstract class LexerBasedTodoIndexer implements DataIndexer<TodoIndexEntry, Integer, FileBasedIndex.FileContent> {
  public Map<TodoIndexEntry,Integer> map(final FileBasedIndex.FileContent inputData) {
    
    final TodoOccurrenceConsumer todoOccurrenceConsumer = new TodoOccurrenceConsumer();
    final Lexer filterLexer = createLexer(todoOccurrenceConsumer);
    final CharSequence chars = inputData.content;
    filterLexer.start(chars, 0, chars.length(),0);
    while (filterLexer.getTokenType() != null) {
      filterLexer.advance();
    }
    final Map<TodoIndexEntry,Integer> map = new HashMap<TodoIndexEntry, Integer>();
    for (IndexPattern indexPattern : IdCacheUtil.getIndexPatterns()) {
      final int count = todoOccurrenceConsumer.getOccurrenceCount(indexPattern);
      if (count > 0) {
        map.put(new TodoIndexEntry(indexPattern.getPatternString(), indexPattern.isCaseSensitive()), count);
      }
    }
    return map;
  }

  protected abstract Lexer createLexer(TodoOccurrenceConsumer consumer);
}