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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.SmartList;
import com.intellij.util.containers.EmptyIterator;
import gnu.trove.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 20, 2007
 */
class ValueContainerImpl<Value> extends UpdatableValueContainer<Value> implements Cloneable{
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.indexing.ValueContainerImpl");
  private final static Object myNullValue = new Object();
  private static final int MAX_FILES = 20000;
  // there is no volatile as we modify under write lock and read under read lock
  // Most often (80%) we store 0 or one mapping, then we store them in two fields: myInputIdMapping, myInputIdMappingValue
  // when there are several value mapped, myInputIdMapping is THashMap<Value, Data>, myInputIdMappingValue = null
  private Object myInputIdMapping;
  private Object myInputIdMappingValue;

  @Override
  public void addValue(int inputId, Value value) {
    final Object fileSetObject = getFileSetObject(value);

    if (fileSetObject == null) {
      attachFileSetForNewValue(value, inputId);
    }
    else {
      final TIntHashSet idSet;
      if (fileSetObject instanceof Integer) {
        idSet = new IdSet(3);
        idSet.add(((Integer)fileSetObject).intValue());
        idSet.add(inputId);
        resetFileSetForValue(value, idSet);
      }
      else if (fileSetObject instanceof TIntHashSet) {
        idSet = (TIntHashSet)fileSetObject;
        idSet.add(inputId);

        if (idSet.size() > MAX_FILES) {
          resetFileSetForValue(value, new IdBitSet(idSet));
        }
      } else if (fileSetObject instanceof IdBitSet) {
        ((IdBitSet)fileSetObject).set(inputId);
      }
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

    List<Object> fileSetObjects = null;
    List<Value> valueObjects = null;

    for (final ValueIterator<Value> valueIterator = getValueIterator(); valueIterator.hasNext();) {
      final Value value = valueIterator.next();

      if (valueIterator.getValueAssociationPredicate().contains(inputId)) {
        if (fileSetObjects == null) {
          fileSetObjects = new SmartList<Object>();
          valueObjects = new SmartList<Value>();
        }
        else if (ApplicationManager.getApplication().isEAP()) {
          LOG.error("Expected only one value per-inputId", String.valueOf(fileSetObjects.get(0)), String.valueOf(value));
        }
        fileSetObjects.add(valueIterator.getFileSetObject());
        valueObjects.add(value);
      }
    }

    if (fileSetObjects != null) {
      for (int i = 0, len = valueObjects.size(); i < len; ++i) {
        removeValue(inputId, fileSetObjects.get(i), valueObjects.get(i));
      }
    }
  }

  private boolean removeValue(int inputId, Object fileSetObject, Value value) {
    if (fileSetObject == null) {
      return false;
    }

    if (fileSetObject instanceof TIntHashSet) {
      final TIntHashSet idSet = (TIntHashSet)fileSetObject;
      final boolean reallyRemoved = idSet.remove(inputId);
      if (reallyRemoved) {
        idSet.compact();
      }
      if (!idSet.isEmpty()) {
        return reallyRemoved;
      }
    }
    else if (fileSetObject instanceof Integer) {
      if (((Integer)fileSetObject).intValue() != inputId) {
        return false;
      }
    } else if (fileSetObject instanceof IdBitSet) {
      IdBitSet bitSet = (IdBitSet)fileSetObject;
      boolean removed = bitSet.remove(inputId);
      if (bitSet.numberOfBitsSet() > 0) return removed;
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

  @NotNull
  @Override
  public ValueIterator<Value> getValueIterator() {
    if (myInputIdMapping != null) {
      if (!(myInputIdMapping instanceof THashMap)) {
        return new ValueIterator<Value>() {
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

          @NotNull
          @Override
          public IntIterator getInputIdsIterator() {
            return getInputIdsIteratorOutOfFileSetObject(getFileSetObject());
          }

          @NotNull
          @Override
          public IntPredicate getValueAssociationPredicate() {
            return getValueAssociationPredicateOutOfFileSetObject(getFileSetObject());
          }

          @Override
          public Object getFileSetObject() {
            return myInputIdMappingValue;
          }
        };
      } else {
        return new ValueIterator<Value>() {
          private Map.Entry<Value, Object> current;
          private final Iterator<Map.Entry<Value, Object>> iterator = ((THashMap<Value, Object>)myInputIdMapping).entrySet().iterator();

          @Override
          public boolean hasNext() {
            return iterator.hasNext();
          }

          @Override
          public Value next() {
            Value next = (current = iterator.next()).getKey();
            if (next == myNullValue) next = null;
            return next;
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }

          @NotNull
          @Override
          public IntIterator getInputIdsIterator() {
            return getInputIdsIteratorOutOfFileSetObject(getFileSetObject());
          }

          @NotNull
          @Override
          public IntPredicate getValueAssociationPredicate() {
            return getValueAssociationPredicateOutOfFileSetObject(getFileSetObject());
          }

          @Override
          public Object getFileSetObject() {
            if (current == null) throw new IllegalStateException();
            return current.getValue();
          }
        };
      }
    } else {
      return emptyIterator;
    }
  }

  static class EmptyValueIterator<Value> extends EmptyIterator<Value> implements ValueIterator<Value> {

    @NotNull
    @Override
    public IntIterator getInputIdsIterator() {
      throw new IllegalStateException();
    }

    @NotNull
    @Override
    public IntPredicate getValueAssociationPredicate() {
      throw new IllegalStateException();
    }

    @Override
    public Object getFileSetObject() {
      throw new IllegalStateException();
    }
  }

  private static final EmptyValueIterator emptyIterator = new EmptyValueIterator();

  @NotNull
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
    final Object fileSetObject = getFileSetObject(value);
    if (fileSetObject instanceof TIntHashSet) {
      return ((TIntHashSet)fileSetObject).contains(inputId);
    }
    if (fileSetObject instanceof Integer ){
      return inputId == ((Integer)fileSetObject).intValue();
    }
    if (fileSetObject instanceof IdBitSet) {
      return ((IdBitSet)fileSetObject).get(inputId);
    }
    return false;
  }

  @NotNull
  @Override
  public IntPredicate getValueAssociationPredicate(Value value) {
    return getValueAssociationPredicateOutOfFileSetObject(getFileSetObject(value));
  }

  private static IntPredicate getValueAssociationPredicateOutOfFileSetObject(final Object fileSetObject) {
    if (fileSetObject == null) return EMPTY_PREDICATE;
    if (fileSetObject instanceof Integer) {
      return new IntPredicate() {
        final int myId = (Integer)fileSetObject;
        @Override
        public boolean contains(int id) {
          return id == myId;
        }
      };
    }
    if (fileSetObject instanceof IdBitSet) {
      return new IntPredicate() {
        final IdBitSet myIdBitSet = (IdBitSet)fileSetObject;
        @Override
        boolean contains(int id) {
          return myIdBitSet.get(id);
        }
      };
    }
    return new IntPredicate() {
      final TIntHashSet mySet = (TIntHashSet)fileSetObject;
      @Override
      boolean contains(int id) {
        return mySet.contains(id);
      }
    };
  }

  @NotNull
  @Override
  public IntIterator getInputIdsIterator(Value value) {
    return getInputIdsIteratorOutOfFileSetObject(getFileSetObject(value));
  }

  private static IntIterator getInputIdsIteratorOutOfFileSetObject(final Object fileSetObject) {
    final IntIterator it;
    if (fileSetObject instanceof TIntHashSet) {
      it = new IntSetIterator((TIntHashSet)fileSetObject);
    }
    else if (fileSetObject instanceof Integer ){
      it = new SingleValueIterator(((Integer)fileSetObject).intValue());
    } else if (fileSetObject instanceof IdBitSet) {
      it = new IntIterator() {
        private final IdBitSet myIdBitSet = (IdBitSet)fileSetObject;
        private int nextSetBit = myIdBitSet.nextSetBit(0);

        @Override
        public boolean hasNext() {
          return nextSetBit != -1;
        }

        @Override
        public int next() {
          int setBit = nextSetBit;
          nextSetBit = myIdBitSet.nextSetBit(setBit + 1);
          return setBit;
        }

        @Override
        public int size() {
          return myIdBitSet.numberOfBitsSet();
        }
      };
    }
    else {
      it = EMPTY_ITERATOR;
    }
    return it;
  }

  private Object getFileSetObject(Value value) {
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
      } else if (myInputIdMappingValue instanceof IdBitSet) {
        clone.myInputIdMappingValue = ((IdBitSet)myInputIdMappingValue).clone();
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

  @NotNull
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
          } else if (val instanceof IdBitSet) {
            newMapping.put(key, ((IdBitSet)val).clone());
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
                                        ((TIntHashSet)myInputIdMappingValue).clone():
                                        myInputIdMappingValue instanceof IdBitSet ?
                                        ((IdBitSet)myInputIdMappingValue).clone():myInputIdMappingValue;
    }
    return container;
  }

  void ensureFileSetCapacityForValue(Value value, int count) {
    if (count <= 1) return;
    Object input = getFileSetObject(value);

    if (input != null) {
      if (input instanceof Integer) {
        IdSet idSet = new IdSet(count + 1);
        idSet.add(((Integer)input).intValue());
        resetFileSetForValue(value, idSet);
      } else if (input instanceof IdSet) {
        IdSet idSet = (IdSet)input;
        int nextSize = idSet.size() + count;
        if (nextSize <= MAX_FILES) idSet.ensureCapacity(count);
        else {
          resetFileSetForValue(value, new IdBitSet(idSet));
        }
      }
      return;
    }

    final Object fileSet = count > MAX_FILES ? new IdBitSet(count): new IdSet(count);
    attachFileSetForNewValue(value, fileSet);
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
        } else if (val instanceof IdBitSet) {
          cloned.put(key, ((IdBitSet)val).clone());
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

  private static class IdBitSet implements Cloneable {
    private static final int SHIFT = 6;
    private static final int BITS_PER_WORD = 1 << SHIFT;
    private static final int MASK = BITS_PER_WORD - 1;
    private long[] myBitMask;
    private int myBitsSet;
    private int myLastUsedSlot;

    public IdBitSet(TIntHashSet set) {
      this(calcMax(set));
      set.forEach(new TIntProcedure() {
        @Override
        public boolean execute(int value) {
          set(value);
          return true;
        }
      });
    }

    private static int calcMax(TIntHashSet set) {
      final int[] minMax = new int[2];
      minMax[0] = set.iterator().next();
      minMax[1] = minMax[0];
      set.forEach(new TIntProcedure() {
        @Override
        public boolean execute(int value) {
          minMax[0] = Math.min(minMax[0], value);
          minMax[1] = Math.max(minMax[1], value);
          return true;
        }
      });
      return minMax[1];
    }

    public IdBitSet(int max) {
      myBitMask = new long[(calcCapacity(max) >> SHIFT) + 1];
    }

    public void set(int bitIndex) {
      boolean set = get(bitIndex);
      if (!set) {
        ++myBitsSet;
        int wordIndex = bitIndex >> SHIFT;
        if (wordIndex >= myBitMask.length) {
          long[] n = new long[Math.max(calcCapacity(myBitMask.length), wordIndex + 1)];
          System.arraycopy(myBitMask, 0, n, 0, myBitMask.length);
          myBitMask = n;
        }
        myBitMask[wordIndex] |= 1L << (bitIndex & MASK);
        myLastUsedSlot = Math.max(myLastUsedSlot, wordIndex);
      }
    }

    private static int calcCapacity(int length) {
      return length + 3 * (length / 5);
    }

    int numberOfBitsSet() {
      return myBitsSet;
    }

    boolean remove(int bitIndex) {
      if (!get(bitIndex)) return false;
      --myBitsSet;
      int wordIndex = bitIndex >> SHIFT;
      myBitMask[wordIndex] &= ~(1L << (bitIndex & MASK));
      if (wordIndex == myLastUsedSlot) {
        while(myLastUsedSlot >= 0 && myBitMask[myLastUsedSlot] == 0) --myLastUsedSlot;
      }
      return true;
    }

    boolean get(int bitIndex) {
      int wordIndex = bitIndex >> SHIFT;
      boolean result = false;
      if (wordIndex < myBitMask.length) {
        result = (myBitMask[wordIndex] & (1L << (bitIndex & MASK))) != 0;
      }

      return result;
    }

    public IdBitSet clone() {
      try {
        IdBitSet clone = (IdBitSet)super.clone();
        if (myBitMask.length != myLastUsedSlot + 1) { // trim to size
          long[] longs = new long[myLastUsedSlot + 1];
          System.arraycopy(myBitMask, 0, longs, 0, longs.length);
          myBitMask = longs;
        }
        clone.myBitMask = myBitMask.clone();
        return clone;
      } catch (CloneNotSupportedException ex) {
        LOG.error(ex);
        return null;
      }
    }

    public int nextSetBit(int bitIndex) {
      int wordIndex = bitIndex >> SHIFT;
      if (wordIndex >= myBitMask.length) {
        return -1;
      }

      long word = myBitMask[wordIndex] & (-1L << bitIndex);

      while (true) {
        if (word != 0) {
          return (wordIndex * BITS_PER_WORD) + Long.numberOfTrailingZeros(word);
        }
        if (++wordIndex == myBitMask.length) {
          return -1;
        }
        word = myBitMask[wordIndex];
      }
    }
  }
}
