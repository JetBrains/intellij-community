package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.psi.impl.cache.index.FileTypeIdIndexer;
import com.intellij.psi.impl.cache.index.IdIndexEntry;
import com.intellij.psi.search.IndexPattern;
import com.intellij.util.indexing.IndexDataConsumer;
import gnu.trove.TObjectIntHashMap;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 17, 2008
 */
public class IndexDataOccurrenceConsumer implements BaseFilterLexer.OccurrenceConsumer{
  private final IndexDataConsumer<IdIndexEntry, Void> myIndexDataConsumer;
  private final TObjectIntHashMap<IndexPattern> myTodoOccurrences;
  
  public IndexDataOccurrenceConsumer(final IndexDataConsumer<IdIndexEntry, Void> indexDataConsumer, final boolean consumeTodos) {
    myIndexDataConsumer = indexDataConsumer;
    if (consumeTodos) {
      myTodoOccurrences = new TObjectIntHashMap<IndexPattern>();
      IndexPattern[] patterns = IdCacheUtil.getIndexPatterns();
      for (IndexPattern pattern : patterns) {
        myTodoOccurrences.put(pattern, 0);
      }
    }
    else {
      myTodoOccurrences = null;
    }
  }
  
  public void addOccurrence(final CharSequence charSequence, final int start, final int end, final int occurrenceMask) {
    FileTypeIdIndexer.addOccurrence(myIndexDataConsumer, charSequence, start, end, occurrenceMask);
  }

  public void addOccurrence(final char[] chars, final int start, final int end, final int occurrenceMask) {
    FileTypeIdIndexer.addOccurrence(myIndexDataConsumer, chars, start, end, occurrenceMask);
  }

  public void incTodoOccurrence(final IndexPattern pattern) {
    myTodoOccurrences.adjustValue(pattern, 1);
  }

  public boolean canConsumeTodoOccurrences() {
    return myTodoOccurrences != null;
  }
  
  public int getOccurrenceCount(IndexPattern pattern) {
    return myTodoOccurrences.get(pattern); 
  }
}