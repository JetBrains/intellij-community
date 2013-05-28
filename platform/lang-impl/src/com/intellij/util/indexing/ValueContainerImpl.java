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

import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.SmartList;
import com.intellij.util.containers.EmptyIterator;
import gnu.trove.THashMap;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIterator;
import gnu.trove.TObjectObjectProcedure;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 20, 2007
 */
class ValueContainerImpl<Value> extends UpdatableValueContainer<Value> implements Cloneable{
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.indexing.ValueContainerImpl");
  private final static Object myNullValue = new Object();
  // there is no volatile as we modify under write lock and read under read lock
  // Most often (80%) we store 0 or one mapping, then we store them in two fields: myInputIdMapping, myInputIdMappingValue
  // when there are several value mapped, myInputIdMapping is THashMap<Value, Data>, myInputIdMappingValue = null
  private Object myInputIdMapping;
  private Object myInputIdMappingValue;

  @Override
  public void addValue(int inputId, Value value) {
    final Object input = getInput(value);

    if (input == null) {
      attachFileSetForNewValue(value, inputId);
    }
    else {
      final TIntHashSet idSet;
      if (input instanceof Integer) {
        idSet = new IdSet(3);
        idSet.add(((Integer)input).intValue());
        resetFileSetForValue(value, idSet);
      }
      else {
        idSet = (TIntHashSet)input;
      }
      idSet.add(inputId);
    }
  }

  private void resetFileSetForValue(Value value, Object fileSet) {
    if (!(myInputIdMapping instanceof THashMap)) myInputIdMappingValue = fileSet;
    else ((THashMap<Value, Object>)myInputIdMapping).put(value, fileSet);
  }

  @Override
  public int size() {
    return myInputIdMapping != null ? myInputIdMapping instanceof THashMap ? ((THashMap)myInputIdMapping).size(): 1 : 0;
  }

  @Override
  public void removeAssociatedValue(int inputId) {
    if (myInputIdMapping == null) return;
    List<Value> toRemove = null;
    for (final Iterator<Value> valueIterator = getValueIterator(); valueIterator.hasNext();) {
      final Value value = valueIterator.next();
      if (isAssociated(value, inputId)) {
        if (toRemove == null) toRemove = new SmartList<Value>();
        else if (ApplicationInfoImpl.getShadowInstance().isEAP()) {
          LOG.error("Expected only one value per-inputId", String.valueOf(toRemove.get(0)), String.valueOf(value));
        }
        toRemove.add(value);
      }
    }

    if (toRemove != null) {
      for (Value value : toRemove) {
        removeValue(inputId, value);
      }
    }
  }

  public boolean removeValue(int inputId, Value value) {
    final Object input = getInput(value);
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

    if (!(myInputIdMapping instanceof THashMap)) {
      myInputIdMapping = null;
      myInputIdMappingValue = null;
    } else {
      THashMap<Value, Object> mapping = (THashMap<Value, Object>)myInputIdMapping;
      mapping.remove(value);
      if (mapping.size() == 1) {
        myInputIdMapping = mapping.keySet().iterator().next();
        myInputIdMappingValue = mapping.get((Value)myInputIdMapping);
      }
    }

    return true;
  }

  @Override
  public Iterator<Value> getValueIterator() {
    if (myInputIdMapping != null) {
      if (!(myInputIdMapping instanceof THashMap)) {
        return new Iterator<Value>() {
          private Value value = (Value)myInputIdMapping;
          @Override
          public boolean hasNext() {
            return value != null;
          }

          @Override
          public Value next() {
            Value next = value;
            if (next == myNullValue) next = null;
            value = null;
            return next;
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      } else {
        return new Iterator<Value>() {
          final Iterator<Value> iterator = ((THashMap<Value, Object>)myInputIdMapping).keySet().iterator();

          @Override
          public boolean hasNext() {
            return iterator.hasNext();
          }

          @Override
          public Value next() {
            Value next = iterator.next();
            if (next == myNullValue) next = null;
            return next;
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }
    } else {
      return EmptyIterator.getInstance();
    }
  }

  @Override
  public List<Value> toValueList() {
    if (myInputIdMapping == null) {
      return Collections.emptyList();
    } else if (myInputIdMapping instanceof THashMap) {
      return new ArrayList<Value>(((THashMap<Value, Object>)myInputIdMapping).keySet());
    } else {
      return new SmartList<Value>((Value)myInputIdMapping);
    }
  }

  @Override
  public boolean isAssociated(Value value, final int inputId) {
    final Object input = getInput(value);
    if (input instanceof TIntHashSet) {
      return ((TIntHashSet)input).contains(inputId);
    }
    if (input instanceof Integer ){
      return inputId == ((Integer)input).intValue();
    }
    return false;
  }

  @Override
  public IntPredicate getValueAssociationPredicate(Value value) {
    final Object input = getInput(value);
    if (input == null) return EMPTY_PREDICATE;
    if (input instanceof Integer) {
      return new IntPredicate() {
        final int myId = (Integer)input;
        @Override
        public boolean contains(int id) {
          return id == myId;
        }
      };
    }
    return new IntPredicate() {
      final TIntHashSet mySet = (TIntHashSet)input;
      @Override
      boolean contains(int id) {
        return mySet.contains(id);
      }
    };
  }

  @Override
  public IntIterator getInputIdsIterator(Value value) {
    final Object input = getInput(value);
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

  private Object getInput(Value value) {
    if (myInputIdMapping == null) return null;

    value = value != null ? value:(Value)myNullValue;

    if (myInputIdMapping == value || // myNullValue is Object
        myInputIdMapping.equals(value)
       ) {
      return myInputIdMappingValue;
    }

    if (!(myInputIdMapping instanceof THashMap)) return null;
    return ((THashMap<Value, Object>)myInputIdMapping).get(value);
  }

  @Override
  public ValueContainerImpl<Value> clone() {
    try {
      final ValueContainerImpl clone = (ValueContainerImpl)super.clone();
      if (myInputIdMapping instanceof THashMap) {
        clone.myInputIdMapping = mapCopy((THashMap<Value, Object>)myInputIdMapping);
      } else if (myInputIdMappingValue instanceof TIntHashSet) {
        clone.myInputIdMappingValue = ((TIntHashSet)myInputIdMappingValue).clone();
      }
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

  public ValueContainerImpl<Value> copy() {
    ValueContainerImpl<Value> container = new ValueContainerImpl<Value>();

    if (myInputIdMapping instanceof THashMap) {
      final THashMap<Value, Object> mapping = (THashMap<Value, Object>)myInputIdMapping;
      final THashMap<Value, Object> newMapping = new THashMap<Value, Object>(mapping.size());
      container.myInputIdMapping = newMapping;

      mapping.forEachEntry(new TObjectObjectProcedure<Value, Object>() {
        @Override
        public boolean execute(Value key, Object val) {
          if (val instanceof TIntHashSet) {
            newMapping.put(key, ((TIntHashSet)val).clone());
          }
          else {
            newMapping.put(key, val);
          }
          return true;
        }
      });
    } else {
      container.myInputIdMapping = myInputIdMapping;
      container.myInputIdMappingValue = myInputIdMappingValue instanceof TIntHashSet ?
                                        ((TIntHashSet)myInputIdMappingValue).clone():myInputIdMappingValue;
    }
    return container;
  }

  void ensureFileSetCapacityForValue(Value value, int count) {
    if (count <= 1) return;
    Object input = getInput(value);

    if (input != null) {
      if (input instanceof IdSet) {
        ((IdSet)input).ensureCapacity(count);
      } else if (input instanceof Integer) {
        IdSet idSet = new IdSet(count + 1);
        idSet.add(((Integer)input).intValue());
        resetFileSetForValue(value, idSet);
      }
      return;
    }

    attachFileSetForNewValue(value, new IdSet(count));
  }

  private void attachFileSetForNewValue(Value value, Object fileSet) {
    value = value != null ? value:(Value)myNullValue;
    if (myInputIdMapping != null) {
      if (!(myInputIdMapping instanceof THashMap)) {
        Object oldMapping = myInputIdMapping;
        myInputIdMapping = new THashMap<Value, Object>(2);
        ((THashMap<Value, Object>)myInputIdMapping).put((Value)oldMapping, myInputIdMappingValue);
        myInputIdMappingValue = null;
      }
      ((THashMap<Value, Object>)myInputIdMapping).put(value, fileSet);
    } else {
      myInputIdMapping = value;
      myInputIdMappingValue = fileSet;
    }
  }

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
      int next = myValue;
      myValueRead = true;
      return next;
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

  private THashMap<Value, Object> mapCopy(final THashMap<Value, Object> map) {
    if (map == null) {
      return null;
    }
    final THashMap<Value, Object> cloned = map.clone();
    cloned.forEachEntry(new TObjectObjectProcedure<Value, Object>() {
      @Override
      public boolean execute(Value key, Object val) {
        if (val instanceof TIntHashSet) {
          cloned.put(key, ((TIntHashSet)val).clone());
        }
        return true;
      }
    });

    return cloned;
  }

  private static final IntPredicate EMPTY_PREDICATE = new IntPredicate() {
    @Override
    public boolean contains(int id) {
      return false;
    }
  };

  private static class IdSet extends TIntHashSet {

    private IdSet(final int initialCapacity) {
      super(initialCapacity, 0.98f);
    }

    @Override
    public void compact() {
      if (((int)(capacity() * _loadFactor)/ Math.max(1, size())) >= 3) {
        super.compact();
      }
    }
  }

}
