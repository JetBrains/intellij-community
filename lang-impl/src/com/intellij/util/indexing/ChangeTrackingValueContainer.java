package com.intellij.util.indexing;

import com.intellij.openapi.util.Computable;

import java.util.Iterator;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 20, 2007
 */
class ChangeTrackingValueContainer<Value> extends UpdatableValueContainer<Value>{
  private final ValueContainerImpl<Value> myAdded;
  private final ValueContainerImpl<Value> myRemoved;
  private final Initializer<Value> myInitializer;
  private volatile ValueContainerImpl<Value> myMerged = null;

  public interface Initializer<T> extends Computable<ValueContainer<T>> {
    Object getLock();
  }

  public ChangeTrackingValueContainer(Initializer<Value> initializer) {
    myInitializer = initializer;
    myAdded = new ValueContainerImpl<Value>();
    myRemoved = new ValueContainerImpl<Value>();
  }

  //public void log(String op, int id, final Value value) {
  //  System.out.print("@" + mcount + ": ");
  //  System.out.print(op);
  //  System.out.print("(" + id + ")");
  //  System.out.print(" value=" + value + " ");
  //  System.out.print("+[" + myAdded.dumpInputIdMapping() + "], ");
  //  System.out.print("-[" + myRemoved.dumpInputIdMapping() + "], ");
  //  System.out.println("*[" + (myMerged != null ? myMerged.dumpInputIdMapping() : "null") + "] ");
  //}

  public void addValue(int inputId, Value value) {
    if (myMerged != null) {
      myMerged.addValue(inputId, value);
    }
    if (!myRemoved.removeValue(inputId, value)) {
      myAdded.addValue(inputId, value);
    }
  }

  public boolean removeValue(int inputId, Value value) {
    if (myMerged != null) {
      myMerged.removeValue(inputId, value);
    }
    if (!myAdded.removeValue(inputId, value)) {
      myRemoved.addValue(inputId, value);
    }
    return true;
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
  // need 'synchronized' to ensure atomic initialization of merged data
  // because several threads that acquired read lock may simultaneously execute the method
  private ValueContainer<Value> getMergedData() {
    synchronized (myInitializer.getLock()) {
      if (myMerged != null) {
        return myMerged;
      }
      myMerged = new ValueContainerImpl<Value>();

      final ValueContainer<Value> fromDisk = myInitializer.compute();

      final ContainerAction<Value> addAction = new ContainerAction<Value>() {
        public void perform(final int id, final Value value) {
          myMerged.addValue(id, value);
        }
      };
      final ContainerAction<Value> removeAction = new ContainerAction<Value>() {
        public void perform(final int id, final Value value) {
          myMerged.removeValue(id, value);
        }
      };

      fromDisk.forEach(addAction);
      myRemoved.forEach(removeAction);
      myAdded.forEach(addAction);

      return myMerged;
    }
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
