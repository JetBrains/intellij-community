package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.psi.impl.cache.index.FileTypeIdIndexer;
import com.intellij.psi.impl.cache.index.IdIndexEntry;
import com.intellij.util.indexing.IndexDataConsumer;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 17, 2008
 */
public class IndexDataOccurrenceConsumer implements BaseFilterLexer.OccurrenceConsumer{
  private final IndexDataConsumer<IdIndexEntry, Void> myIndexDataConsumer; 
  
  public IndexDataOccurrenceConsumer(final IndexDataConsumer<IdIndexEntry, Void> indexDataConsumer) {
    myIndexDataConsumer = indexDataConsumer;
  }
  
  public void addOccurrence(final CharSequence charSequence, final int start, final int end, final int occurrenceMask) {
    FileTypeIdIndexer.addOccurrence(myIndexDataConsumer, charSequence, start, end, occurrenceMask);
  }

  public void addOccurrence(final char[] chars, final int start, final int end, final int occurrenceMask) {
    FileTypeIdIndexer.addOccurrence(myIndexDataConsumer, chars, start, end, occurrenceMask);
  }
}