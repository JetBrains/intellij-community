/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;

import java.io.DataOutput;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 14, 2007
 */
public abstract class ValueContainer<Value> {
  public interface IntIterator {
    boolean hasNext();

    int next();

    int size();

    boolean hasAscendingOrder();

    IntIterator createCopyInInitialState();
  }

  public interface IntPredicate {
    boolean contains(int id);
  }

  @NotNull
  public abstract IntIterator getInputIdsIterator(Value value);

  @NotNull
  public abstract IntPredicate getValueAssociationPredicate(Value value);

  @NotNull
  public abstract ValueIterator<Value> getValueIterator();

  public interface ValueIterator<Value> extends Iterator<Value> {
    @NotNull
    IntIterator getInputIdsIterator();

    @NotNull
    IntPredicate getValueAssociationPredicate();

    Object getFileSetObject();
  }

  @NotNull
  public abstract List<Value> toValueList();

  public abstract int size();


  public interface ContainerAction<T> {
    boolean perform(int id, T value);
  }

  public final boolean forEach(@NotNull ContainerAction<Value> action) {
    for (final ValueIterator<Value> valueIterator = getValueIterator(); valueIterator.hasNext();) {
      final Value value = valueIterator.next();
      for (final IntIterator intIterator = valueIterator.getInputIdsIterator(); intIterator.hasNext();) {
        if (!action.perform(intIterator.next(), value)) return false;
      }
    }
    return true;
  }

  private volatile boolean myNeedsCompacting = false;

  boolean needsCompacting() {
    return myNeedsCompacting;
  }

  void setNeedsCompacting(boolean value) {
    myNeedsCompacting = value;
  }

  public abstract void saveTo(DataOutput out, DataExternalizer<Value> externalizer) throws IOException;
}
