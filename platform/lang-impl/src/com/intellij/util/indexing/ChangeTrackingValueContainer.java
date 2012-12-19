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

    if (myAdded == null) myAdded = new ValueContainerImpl<Value>();
    myAdded.addValue(inputId, value);
  }

  @Override
  public void removeAssociatedValue(int inputId) {
    ValueContainerImpl<Value> merged = myMerged;
    if (merged != null) {
      merged.removeAssociatedValue(inputId);
    }

    if (myAdded != null) myAdded.removeAssociatedValue(inputId);

    if (myInvalidated == null) myInvalidated = new TIntHashSet(1);
    myInvalidated.add(inputId);
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

      if (myInvalidated != null) {
        myInvalidated.forEach(new TIntProcedure() {
          @Override
          public boolean execute(int inputId) {
            newMerged.removeAssociatedValue(inputId);
            return true;
          }
        });
      }

      if (myAdded != null) {
        myAdded.forEach(new ContainerAction<Value>() {
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
