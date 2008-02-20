package com.intellij.util.indexing;

import com.intellij.openapi.util.Computable;

import java.util.Iterator;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 20, 2007
 */
public class ChangeTrackingValueContainer<Value> extends ValueContainer<Value>{
  private final ValueContainerImpl<Value> myAdded;
  private final ValueContainerImpl<Value> myRemoved;
  private final Computable<ValueContainer<Value>> myInitializer;
  private ValueContainerImpl<Value> myMerged = null;

  public ChangeTrackingValueContainer(Computable<ValueContainer<Value>> initializer) {
    myInitializer = initializer;
    myAdded = new ValueContainerImpl<Value>();
    myRemoved = new ValueContainerImpl<Value>();
  }

  public void addValue(int inputId, Value value) {
    if (myMerged != null) {
      myMerged.addValue(inputId, value);
    }
    myAdded.addValue(inputId, value);
    myRemoved.removeValue(inputId, value);
  }

  public void removeValue(int inputId, Value value) {
    if (myMerged != null) {
      myMerged.removeValue(inputId, value);
    }
    myRemoved.addValue(inputId, value);
    myAdded.removeValue(inputId, value);
  }

  public int size() {
    return getMergedData().size();
  }

  public Iterator<Value> getValueIterator() {
    return getMergedData().getValueIterator();
  }

  public List<Value> toValueList() {
    return getMergedData().toValueList();
  }

  public int[] getInputIds(final Value value) {
    return getMergedData().getInputIds(value);
  }

  public boolean isAssociated(final Value value, final int inputId) {
    return getMergedData().isAssociated(value, inputId);
  }

  public IntIterator getInputIdsIterator(final Value value) {
    return getMergedData().getInputIdsIterator(value);
  }

  private ValueContainer<Value> getMergedData() {
    if (myMerged != null) {
      return myMerged;
    }
    myMerged = new ValueContainerImpl<Value>();

    final ValueContainer<Value> fromDisk = myInitializer.compute();
    
    final ValueContainerImpl.ContainerAction<Value> addAction = new ValueContainerImpl.ContainerAction<Value>() {
      public void perform(final int id, final Value value) {
        myMerged.addValue(id, value);
      }
    };
    final ValueContainerImpl.ContainerAction<Value> removeAction = new ValueContainerImpl.ContainerAction<Value>() {
      public void perform(final int id, final Value value) {
        myMerged.removeValue(id, value);
      }
    };

    fromDisk.forEach(addAction);
    myRemoved.forEach(removeAction);
    myAdded.forEach(addAction);
    
    return myMerged;
  }

  public boolean isDirty() {
    return myAdded.size() > 0 || myRemoved.size() > 0;
  }

  public boolean canUseDataAppend() {
    return myRemoved.size() == 0;
  }
  
  public ValueContainer<Value> getDataToAppend() {
    return myAdded;
  }
}