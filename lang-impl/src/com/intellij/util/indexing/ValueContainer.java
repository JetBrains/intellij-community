package com.intellij.util.indexing;

import java.util.Iterator;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 14, 2007
 */
public interface ValueContainer<Value> {
  
  interface IntIterator {
    boolean hasNext();
    
    int next();

    int size();
  }
  
  IntIterator getInputIdsIterator(Value value);

  Iterator<Value> getValueIterator();

  int[] getInputIds(Value value);

  List<Value> toValueList();

  int size();
}
