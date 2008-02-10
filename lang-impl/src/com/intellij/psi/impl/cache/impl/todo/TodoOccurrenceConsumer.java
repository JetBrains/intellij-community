package com.intellij.psi.impl.cache.impl.todo;

import com.intellij.psi.impl.cache.impl.BaseFilterLexer;
import com.intellij.psi.impl.cache.impl.CacheUtil;
import com.intellij.psi.search.IndexPattern;
import gnu.trove.TObjectIntHashMap;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 17, 2008
 */
public class TodoOccurrenceConsumer implements BaseFilterLexer.OccurrenceConsumer {
  private final TObjectIntHashMap<IndexPattern> myTodoOccurrences;
  
  public TodoOccurrenceConsumer() {
    myTodoOccurrences = new TObjectIntHashMap<IndexPattern>();
    for (IndexPattern pattern : CacheUtil.getIndexPatterns()) {
      myTodoOccurrences.put(pattern, 0);
    }
  }
  
  public void addOccurrence(final CharSequence charSequence, final int start, final int end, final int occurrenceMask) {
    // empty
  }

  public void addOccurrence(final char[] chars, final int start, final int end, final int occurrenceMask) {
    // empty
  }

  public void incTodoOccurrence(final IndexPattern pattern) {
    myTodoOccurrences.adjustValue(pattern, 1);
  }

  public boolean canConsumeTodoOccurrences() {
    return true;
  }
  
  public int getOccurrenceCount(IndexPattern pattern) {
    return myTodoOccurrences.get(pattern); 
  }
}