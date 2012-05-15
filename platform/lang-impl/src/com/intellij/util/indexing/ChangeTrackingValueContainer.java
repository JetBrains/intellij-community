/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.util.indexing;

import com.intellij.openapi.util.Computable;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 20, 2007
 */
class ChangeTrackingValueContainer<Value> extends UpdatableValueContainer<Value>{
  // there is no volatile as we modify under write lock and read under read lock
  private ValueContainerImpl<Value> myAdded;
  private TIntHashSet myInvalidated;
  private volatile ValueContainerImpl<Value> myMerged;
  private final Initializer<Value> myInitializer;

  public interface Initializer<T> extends Computable<ValueContainer<T>> {
    Object getLock();
  }

  public ChangeTrackingValueContainer(Initializer<Value> initializer) {
    myInitializer = initializer;
  }

  @Override
  public void addValue(int inputId, Value value) {
    ValueContainerImpl<Value> merged = myMerged;
    if (merged != null) {
      merged.addValue(inputId, value);
    }
    ValueContainerImpl<Value> added = myAdded;
    if (added == null) {
      myAdded = added = new ValueContainerImpl<Value>();
    }
    added.addValue(inputId, value); // will flush the changes & caller should ensure exclusiveness to avoid intermediate visibility issues
  }

  @Override
  public void removeAssociatedValue(int inputId) {
    ValueContainerImpl<Value> merged = myMerged;
    if (merged != null) {
      merged.removeAssociatedValue(inputId);
    }

    ValueContainerImpl<Value> added = myAdded;
    if (added != null) added.removeAssociatedValue(inputId);

    TIntHashSet invalidated = myInvalidated;
    if (invalidated == null) {
      invalidated = new TIntHashSet(1);
    }
    invalidated.add(inputId);
    myInvalidated = invalidated; // volatile write
  }

  @Override
  public boolean removeValue(int inputId, Value value) {
    ValueContainerImpl<Value> merged = myMerged;
    if (merged != null) {
      merged.removeValue(inputId, value);
    }
    ValueContainerImpl<Value> added = myAdded;
    if (added != null) added.removeValue(inputId, value);

    return true;
  }

  @Override
  public int size() {
    return getMergedData().size();
  }

  @Override
  public Iterator<Value> getValueIterator() {
    return getMergedData().getValueIterator();
  }

  @Override
  public List<Value> toValueList() {
    return getMergedData().toValueList();
  }

  @Override
  public boolean isAssociated(final Value value, final int inputId) {
    return getMergedData().isAssociated(value, inputId);
  }

  @Override
  public IntPredicate getValueAssociationPredicate(Value value) {
    return getMergedData().getValueAssociationPredicate(value);
  }

  @Override
  public IntIterator getInputIdsIterator(final Value value) {
    return getMergedData().getInputIdsIterator(value);
  }

  public void dropMergedData() {
    myMerged = null;
  }

  // need 'synchronized' to ensure atomic initialization of merged data
  // because several threads that acquired read lock may simultaneously execute the method
  private ValueContainerImpl<Value> getMergedData() {
    ValueContainerImpl<Value> merged = myMerged;
    if (merged != null) {
      return merged;
    }
    synchronized (myInitializer.getLock()) {
      merged = myMerged;
      if (merged != null) {
        return merged;
      }

      final ValueContainer<Value> fromDisk = myInitializer.compute();
      final ValueContainerImpl<Value> newMerged;

      if (fromDisk instanceof ValueContainerImpl) {
        newMerged = ((ValueContainerImpl<Value>)fromDisk).copy();
      } else {
        newMerged = ((ChangeTrackingValueContainer<Value>)fromDisk).getMergedData().copy();
      }

      TIntHashSet invalidated = myInvalidated;
      if (invalidated != null) {
        invalidated.forEach(new TIntProcedure() {
          @Override
          public boolean execute(int inputId) {
            newMerged.removeAssociatedValue(inputId);
            return true;
          }
        });
      }

      ValueContainerImpl<Value> added = myAdded;
      if (added != null) {
        added.forEach(new ContainerAction<Value>() {
          @Override
          public boolean perform(final int id, final Value value) {
            newMerged.removeAssociatedValue(id); // enforcing "one-value-per-file for particular key" invariant
            newMerged.addValue(id, value);
            return true;
          }
        });
      }
      setNeedsCompacting(fromDisk.needsCompacting());

      myMerged = newMerged;
      return newMerged;
    }
  }

  public boolean isDirty() {
    return (myAdded != null && myAdded.size() > 0) ||
           (myInvalidated != null && !myInvalidated.isEmpty()) ||
           needsCompacting();
  }

  public @Nullable ValueContainer<Value> getAddedDelta() {
    return myAdded;
  }

  public @Nullable TIntHashSet getInvalidated() {
    return myInvalidated;
  }
}
