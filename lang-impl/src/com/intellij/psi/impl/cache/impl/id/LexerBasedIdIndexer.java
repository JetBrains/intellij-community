package com.intellij.psi.impl.cache.impl.id;

import com.intellij.lexer.Lexer;
import com.intellij.psi.impl.cache.impl.BaseFilterLexer;
import com.intellij.psi.search.IndexPattern;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.IdDataConsumer;

import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 20, 2008
 */
public abstract class LexerBasedIdIndexer extends FileTypeIdIndexer {
  
  public final Map<IdIndexEntry,Integer> map(final FileBasedIndex.FileContent inputData) {
    final IdDataConsumer consumer = new IdDataConsumer();
    final Lexer lexer = createLexer(new OccurrenceToIdDataConsumerAdapter(consumer));
    final CharSequence chars = inputData.content;
    lexer.start(chars, 0, chars.length(),0);
    while (lexer.getTokenType() != null) {
      lexer.advance();
    }
    return consumer.getResult();
  }

  protected abstract Lexer createLexer(BaseFilterLexer.OccurrenceConsumer consumer);

  private static class OccurrenceToIdDataConsumerAdapter implements BaseFilterLexer.OccurrenceConsumer{
    private final IdDataConsumer myIndexDataConsumer;
    
    public OccurrenceToIdDataConsumerAdapter(final IdDataConsumer indexDataConsumer) {
      myIndexDataConsumer = indexDataConsumer;
    }
    
    public void addOccurrence(final CharSequence charSequence, final int start, final int end, final int occurrenceMask) {
      myIndexDataConsumer.addOccurrence(charSequence, start, end, occurrenceMask);
    }
  
    public void addOccurrence(final char[] chars, final int start, final int end, final int occurrenceMask) {
      myIndexDataConsumer.addOccurrence(chars, start, end, occurrenceMask);
    }
  
    public void incTodoOccurrence(final IndexPattern pattern) {
      // empty
    }
  
    public boolean canConsumeTodoOccurrences() {
      return false;
    }
    
    public int getOccurrenceCount(IndexPattern pattern) {
      return 0; 
    }
  }
}
