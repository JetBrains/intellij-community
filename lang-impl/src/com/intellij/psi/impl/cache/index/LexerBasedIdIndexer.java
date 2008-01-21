package com.intellij.psi.impl.cache.index;

import com.intellij.lexer.Lexer;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.IndexDataConsumer;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 20, 2008
 */
public abstract class LexerBasedIdIndexer extends FileTypeIdIndexer {
  
  public final void map(final FileBasedIndex.FileContent inputData, final IndexDataConsumer<IdIndexEntry, Void> consumer) {
    final Lexer lexer = createLexer(consumer);
    final CharSequence chars = inputData.content;
    lexer.start(chars, 0, chars.length(),0);
    while (lexer.getTokenType() != null) {
      lexer.advance();
    }
  }

  protected abstract Lexer createLexer(IndexDataConsumer<IdIndexEntry, Void> consumer);
}
