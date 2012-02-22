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

import java.util.Iterator;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 20, 2007
 */
class ChangeTrackingValueContainer<Value> extends UpdatableValueContainer<Value>{
  private final ValueContainerImpl<Value> myAdded;
  private final ValueContainerImpl<Value> myRemoved;
  private final TIntHashSet myInvalidated;
  private final Initializer<Value> myInitializer;
  private volatile ValueContainerImpl<Value> myMerged = null;

  public interface Initializer<T> extends Computable<ValueContainer<T>> {
    Object getLock();
  }

  public ChangeTrackingValueContainer(Initializer<Value> initializer) {
    myInitializer = initializer;
    myAdded = new ValueContainerImpl<Value>();
    myRemoved = new ValueContainerImpl<Value>();
    myInvalidated = new TIntHashSet();
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

  @Override
  public void addValue(int inputId, Value value) {
    if (myMerged != null) {
      myMerged.addValue(inputId, value);
    }
    if (!myRemoved.removeValue(inputId, value)) {
      myAdded.addValue(inputId, value);
    }
  }

  @Override
  public void removeAssociatedValue(int inputId) {
    if (myMerged != null) {
      myMerged.removeAssociatedValue(inputId);
    }
    myAdded.removeAssociatedValue(inputId);
    myRemoved.removeAssociatedValue(inputId);
    myInvalidated.add(inputId);
  }

  @Override
  public boolean removeValue(int inputId, Value value) {
    if (myMerged != null) {
      myMerged.removeValue(inputId, value);
    }
    if (!myAdded.removeValue(inputId, value)) {
      if (!myInvalidated.contains(inputId)) {
        myRemoved.addValue(inputId, value);
      }
    }
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
        newMerged = ((ValueContainerImpl<Value>)fromDisk).clone();
      } else {
        newMerged = ((ChangeTrackingValueContainer<Value>)fromDisk).getMergedData().clone();
      }
      myInvalidated.forEach(new TIntProcedure() {
        @Override
        public boolean execute(int inputId) {
          newMerged.removeAssociatedValue(inputId);
          return true;
        }
      });
      myRemoved.forEach(new ContainerAction<Value>() {
        @Override
        public void perform(final int id, final Value value) {
          newMerged.removeValue(id, value);
        }
      });
      myAdded.forEach(new ContainerAction<Value>() {
        @Override
        public void perform(final int id, final Value value) {
          newMerged.removeAssociatedValue(id); // enforcing "one-value-per-file for particular key" invariant
          newMerged.addValue(id, value);
        }
      });
      setNeedsCompacting(fromDisk.needsCompacting());

      myMerged = newMerged;
      return newMerged;
    }
  }

  public boolean isDirty() {
    return myAdded.size() > 0 || myRemoved.size() > 0 || myInvalidated.size() > 0 || needsCompacting();
  }

  public ValueContainer<Value> getAddedDelta() {
    return myAdded;
  }
  
  public ValueContainer<Value> getRemovedDelta() {
    return myRemoved;
  }

  public TIntHashSet getInvalidated() {
    return myInvalidated;
  }
}
