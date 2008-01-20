package com.intellij.psi.impl.cache.index;

import com.intellij.lexer.Lexer;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.IndexDataConsumer;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 20, 2008
 */
public abstract class LexerBasedTodoIndexer implements DataIndexer<TodoIndexEntry, Integer, FileBasedIndex.FileContent> {
  public void map(final FileBasedIndex.FileContent inputData, final IndexDataConsumer<TodoIndexEntry, Integer> consumer) {
    final Lexer filterLexer = createLexer(consumer);
    final CharSequence chars = inputData.content;
    filterLexer.start(chars, 0, chars.length(),0);
    while (filterLexer.getTokenType() != null) {
      filterLexer.advance();
    }
  }

  protected abstract Lexer createLexer(IndexDataConsumer<TodoIndexEntry, Integer> consumer);
}