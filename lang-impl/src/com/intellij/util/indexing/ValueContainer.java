package com.intellij.util.indexing;

import java.util.Iterator;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 14, 2007
 */
public abstract class ValueContainer<Value> {
  interface IntIterator {
    boolean hasNext();
    
    int next();

    int size();
  }
  
  public abstract IntIterator getInputIdsIterator(Value value);

  public abstract boolean isAssociated(Value value, int inputId);
  
  public abstract Iterator<Value> getValueIterator();

  public abstract int[] getInputIds(Value value);

  public abstract List<Value> toValueList();

  public abstract int size();


  public static interface ContainerAction<T> {
    void perform(int id, T value);
  }

  public final void forEach(final ContainerAction<Value> action) {
    for (final Iterator<Value> valueIterator = getValueIterator(); valueIterator.hasNext();) {
      final Value value = valueIterator.next();
      for (final IntIterator intIterator = getInputIdsIterator(value); intIterator.hasNext();) {
        action.perform(intIterator.next(), value);
      }
    }
  }
}
