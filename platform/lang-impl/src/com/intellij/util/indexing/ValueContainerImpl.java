/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIterator;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 20, 2007
 */
class ValueContainerImpl<Value> extends UpdatableValueContainer<Value> implements Cloneable{
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.indexing.ValueContainerImpl");

  private HashMap<Value, Object> myInputIdMapping;

  public ValueContainerImpl() {
    myInputIdMapping = new HashMap<Value, Object>(16, 0.98f);
  }

  @Override
  public void addValue(int inputId, Value value) {
    final Object input = myInputIdMapping.get(value);
    if (input == null) {
      //idSet = new TIntHashSet(3, 0.98f);
      myInputIdMapping.put(value, inputId);
    }
    else {
      final TIntHashSet idSet;
      if (input instanceof Integer) {
        idSet = new IdSet(3, 0.98f);
        idSet.add(((Integer)input).intValue());
        myInputIdMapping.put(value, idSet);
      }
      else {
        idSet = (TIntHashSet)input;
      }
      idSet.add(inputId);
    }
  }

  @Override
  public int size() {
    return myInputIdMapping.size();
  }

  @Override
  public void removeAssociatedValue(int inputId) {
    final List<Value> toRemove = new ArrayList<Value>(1);
    for (final Iterator<Value> valueIterator = getValueIterator(); valueIterator.hasNext();) {
      final Value value = valueIterator.next();
      if (isAssociated(value, inputId)) {
        LOG.assertTrue(toRemove.isEmpty(), "Expected only one value per-inputId");
        toRemove.add(value);
      }
    }

    if (!toRemove.isEmpty()) {
      for (Value value : toRemove) {
        removeValue(inputId, value);
      }
    }
  }

  @Override
  public boolean removeValue(int inputId, Value value) {
    final Object input = myInputIdMapping.get(value);
    if (input == null) {
      return false;
    }
    if (input instanceof TIntHashSet) {
      final TIntHashSet idSet = (TIntHashSet)input;
      final boolean reallyRemoved = idSet.remove(inputId);
      if (reallyRemoved) {
        idSet.compact();
      }
      if (!idSet.isEmpty()) {
        return reallyRemoved;
      }
    }
    else if (input instanceof Integer) {
      if (((Integer)input).intValue() != inputId) {
        return false;
      }
    }
    myInputIdMapping.remove(value);
    return true;
  }

  @Override
  public Iterator<Value> getValueIterator() {
    return Collections.unmodifiableSet(myInputIdMapping.keySet()).iterator();
  }

  @Override
  public List<Value> toValueList() {
    if (myInputIdMapping.isEmpty()) {
      return Collections.emptyList();
    }
    return new ArrayList<Value>(myInputIdMapping.keySet());
  }

  @Override
  public boolean isAssociated(final Value value, final int inputId) {
    final Object input = myInputIdMapping.get(value);
    if (input instanceof TIntHashSet) {
      return ((TIntHashSet)input).contains(inputId);
    }
    if (input instanceof Integer ){
      return inputId == ((Integer)input).intValue();
    }
    return false;
  }

  @Override
  public IntIterator getInputIdsIterator(final Value value) {
    final Object input = myInputIdMapping.get(value);
    final IntIterator it;
    if (input instanceof TIntHashSet) {
      it = new IntSetIterator((TIntHashSet)input);
    }
    else if (input instanceof Integer ){
      it = new SingleValueIterator(((Integer)input).intValue());
    }
    else {
      it = EMPTY_ITERATOR;
    }
    return it;
  }

  @Override
  public ValueContainerImpl<Value> clone() {
    try {
      final ValueContainerImpl clone = (ValueContainerImpl)super.clone();
      clone.myInputIdMapping = mapCopy(myInputIdMapping);
      return clone;
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  public static final IntIterator EMPTY_ITERATOR = new IntIterator() {
    @Override
    public boolean hasNext() {
      return false;
    }

    @Override
    public int next() {
      return 0;
    }

    @Override
    public int size() {
      return 0;
    }
  };

  private static class SingleValueIterator implements IntIterator {
    private final int myValue;
    private boolean myValueRead = false;

    private SingleValueIterator(int value) {
      myValue = value;
    }

    @Override
    public boolean hasNext() {
      return !myValueRead;
    }

    @Override
    public int next() {
      try {
        return myValue;
      }
      finally {
        myValueRead = true;
      }
    }

    @Override
    public int size() {
      return 1;
    }
  }

  private static class IntSetIterator implements IntIterator {
    private final TIntIterator mySetIterator;
    private final int mySize;

    public IntSetIterator(final TIntHashSet set) {
      mySetIterator = set.iterator();
      mySize = set.size();
    }

    @Override
    public boolean hasNext() {
      return mySetIterator.hasNext();
    }

    @Override
    public int next() {
      return mySetIterator.next();
    }

    @Override
    public int size() {
      return mySize;
    }
  }

  private HashMap<Value, Object> mapCopy(final HashMap<Value, Object> map) {
    if (map == null) {
      return null;
    }
    final HashMap<Value, Object> cloned = (HashMap<Value, Object>)map.clone();
    for (Value key : cloned.keySet()) {
      final Object val = cloned.get(key);
      if (val instanceof TIntHashSet) {
        cloned.put(key, ((TIntHashSet)val).clone());
      }
    }
    return cloned;
  }

  private static class IdSet extends TIntHashSet {

    private IdSet(final int initialCapacity, final float loadFactor) {
      super(initialCapacity, loadFactor);
    }

    @Override
    public void compact() {
      if (((int)(capacity() * _loadFactor)/ Math.max(1, size())) >= 3) {
        super.compact();
      }
    }
  }

}
