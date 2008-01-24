package com.intellij.psi.impl.cache.index;

import com.intellij.lexer.Lexer;
import com.intellij.psi.impl.cache.impl.idCache.IdCacheUtil;
import com.intellij.psi.impl.cache.impl.idCache.TodoOccurrenceConsumer;
import com.intellij.psi.search.IndexPattern;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.IndexDataConsumer;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 20, 2008
 */
public abstract class LexerBasedTodoIndexer implements DataIndexer<TodoIndexEntry, Integer, FileBasedIndex.FileContent> {
  public void map(final FileBasedIndex.FileContent inputData, final IndexDataConsumer<TodoIndexEntry, Integer> consumer) {
    
    final TodoOccurrenceConsumer todoOccurrenceConsumer = new TodoOccurrenceConsumer();
    final Lexer filterLexer = createLexer(todoOccurrenceConsumer);
    final CharSequence chars = inputData.content;
    filterLexer.start(chars, 0, chars.length(),0);
    while (filterLexer.getTokenType() != null) {
      filterLexer.advance();
    }

    for (IndexPattern indexPattern : IdCacheUtil.getIndexPatterns()) {
      consumer.consume(new TodoIndexEntry(indexPattern.getPatternString(), indexPattern.isCaseSensitive()), todoOccurrenceConsumer.getOccurrenceCount(indexPattern));
    }
  }

  protected abstract Lexer createLexer(TodoOccurrenceConsumer consumer);
}