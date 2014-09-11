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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ThreadLocalCachedIntArray;
import com.intellij.util.SmartList;
import com.intellij.util.containers.EmptyIterator;
import com.intellij.util.indexing.containers.ChangeBufferingList;
import com.intellij.util.indexing.containers.IdSet;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import gnu.trove.THashMap;
import gnu.trove.TObjectObjectProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
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
    final Object fileSetObject = getFileSetObject(value);

    if (fileSetObject == null) {
      attachFileSetForNewValue(value, inputId);
    }
    else if (fileSetObject instanceof Integer) {
      ChangeBufferingList list = new ChangeBufferingList();
      list.add(((Integer)fileSetObject).intValue());
      list.add(inputId);
      resetFileSetForValue(value, list);
    }
    else {
      ((ChangeBufferingList)fileSetObject).add(inputId);
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

  static final ThreadLocal<ID> ourDebugIndexInfo = new ThreadLocal<ID>();

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
        else if (DebugAssertions.DEBUG) {
          LOG.error("Expected only one value per-inputId for " + ourDebugIndexInfo.get(), String.valueOf(fileSetObjects.get(0)), String.valueOf(value));
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

  void removeValue(int inputId, Value value) {
    removeValue(inputId, getFileSetObject(value), value);
  }

  private void removeValue(int inputId, Object fileSet, Value value) {
    if (fileSet == null) {
      return;
    }

    if (fileSet instanceof ChangeBufferingList) {
      final ChangeBufferingList changesList = (ChangeBufferingList)fileSet;
      changesList.remove(inputId);
      if (!changesList.isEmpty()) return;
    }
    else if (fileSet instanceof Integer) {
      if (((Integer)fileSet).intValue() != inputId) {
        return;
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
  }

  @NotNull
  @Override
  public ValueIterator<Value> getValueIterator() {
    if (myInputIdMapping != null) {
      if (!(myInputIdMapping instanceof THashMap)) {
        return new ValueIterator<Value>() {
          private Value value = (Value)myInputIdMapping;

          @NotNull
          @Override
          public IntIterator getInputIdsIterator() {
            return getIntIteratorOutOfFileSetObject(getFileSetObject());
          }

          @NotNull
          @Override
          public IntPredicate getValueAssociationPredicate() {
            return getPredicateOutOfFileSetObject(getFileSetObject());
          }

          @Override
          public Object getFileSetObject() {
            return myInputIdMappingValue;
          }

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
        return new ValueIterator<Value>() {
          private Map.Entry<Value, Object> current;
          final Iterator<Map.Entry<Value, Object>> iterator = ((THashMap<Value, Object>)myInputIdMapping).entrySet().iterator();

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
            return getIntIteratorOutOfFileSetObject(getFileSetObject());
          }

          @NotNull
          @Override
          public IntPredicate getValueAssociationPredicate() {
            return getPredicateOutOfFileSetObject(getFileSetObject());
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

  @NotNull
  @Override
  public IntPredicate getValueAssociationPredicate(Value value) {
    return getPredicateOutOfFileSetObject(getFileSetObject(value));
  }

  private static @NotNull IntPredicate getPredicateOutOfFileSetObject(@Nullable Object input) {
    if (input == null) return EMPTY_PREDICATE;

    if (input instanceof Integer) {
      final int singleId = (Integer)input;

      return new IntPredicate() {
        @Override
        public boolean contains(int id) {
          return id == singleId;
        }
      };
    }
    return ((ChangeBufferingList)input).intPredicate();
  }

  @NotNull
  @Override
  public IntIterator getInputIdsIterator(Value value) {
    return getIntIteratorOutOfFileSetObject(getFileSetObject(value));
  }

  private static @NotNull IntIterator getIntIteratorOutOfFileSetObject(@Nullable Object input) {
    if (input == null) return EMPTY_ITERATOR;
    if (input instanceof Integer){
      return new SingleValueIterator(((Integer)input).intValue());
    } else {
      return ((ChangeBufferingList)input).intIterator();
    }
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
      } else if (myInputIdMappingValue instanceof ChangeBufferingList) {
        clone.myInputIdMappingValue = ((ChangeBufferingList)myInputIdMappingValue).clone();
      }
      return clone;
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  private static final IntIterator EMPTY_ITERATOR = new IntIterator() {
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

    @Override
    public boolean hasAscendingOrder() {
      return true;
    }

    @Override
    public IntIterator createCopyInInitialState() {
      return this;
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
          if (val instanceof ChangeBufferingList) {
            newMapping.put(key, ((ChangeBufferingList)val).clone());
          } else {
            newMapping.put(key, val);
          }
          return true;
        }
      });
    } else {
      container.myInputIdMapping = myInputIdMapping;
      container.myInputIdMappingValue = myInputIdMappingValue instanceof ChangeBufferingList ?
                                        ((ChangeBufferingList)myInputIdMappingValue).clone():
                                        myInputIdMappingValue;
    }
    return container;
  }

  private void ensureFileSetCapacityForValue(Value value, int count) {
    if (count <= 1) return;
    Object fileSetObject = getFileSetObject(value);

    if (fileSetObject != null) {
      if (fileSetObject instanceof Integer) {
        ChangeBufferingList list = new ChangeBufferingList(count + 1);
        list.add(((Integer)fileSetObject).intValue());
        resetFileSetForValue(value, list);
      } else if (fileSetObject instanceof ChangeBufferingList) {
        ChangeBufferingList list = (ChangeBufferingList)fileSetObject;
        list.ensureCapacity(count);
      }
      return;
    }

    final Object fileSet = new ChangeBufferingList(count);
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

  private static final ThreadLocalCachedIntArray ourSpareBuffer = new ThreadLocalCachedIntArray();

  private static final int INT_BITS_SHIFT = 5;
  private static int nextSetBit(int bitIndex, int[] bits, int bitsLength) {
    int wordIndex = bitIndex >> INT_BITS_SHIFT;
    if (wordIndex >= bitsLength) {
      return -1;
    }

    int word = bits[wordIndex] & (-1 << bitIndex);

    while (true) {
      if (word != 0) {
        return (wordIndex << INT_BITS_SHIFT) + Long.numberOfTrailingZeros(word);
      }
      if (++wordIndex == bitsLength) {
        return -1;
      }
      word = bits[wordIndex];
    }
  }

  @Override
  public void saveTo(DataOutput out, DataExternalizer<Value> externalizer) throws IOException {
    DataInputOutputUtil.writeINT(out, size());

    for (final ValueIterator<Value> valueIterator = getValueIterator(); valueIterator.hasNext();) {
      final Value value = valueIterator.next();
      externalizer.save(out, value);
      Object fileSetObject = valueIterator.getFileSetObject();

      if (fileSetObject instanceof Integer) {
        DataInputOutputUtil.writeINT(out, (Integer)fileSetObject); // most common 90% case during index building
      } else {
        // serialize positive file ids with delta encoding
        ChangeBufferingList originalInput = (ChangeBufferingList)fileSetObject;
        IntIterator intIterator = originalInput.rawIntIterator();
        DataInputOutputUtil.writeINT(out, -intIterator.size());

        if (intIterator.hasAscendingOrder()) {
          IdSet checkSet = originalInput.getCheckSet();
          if (checkSet != null && checkSet.size() != intIterator.size()) {  // debug code
            int a = 1; assert false;
          }
          int prev = 0;

          while (intIterator.hasNext()) {
            int fileId = intIterator.next();
            if (checkSet != null && !checkSet.contains(fileId)) { // debug code
              int a = 1; assert false;
            }
            DataInputOutputUtil.writeINT(out, fileId - prev);
            prev = fileId;
          }
        } else {
          // sorting numbers via bitset before writing deltas
          int max = 0, min = Integer.MAX_VALUE;

          while(intIterator.hasNext()) {
            int nextInt = intIterator.next();
            max = Math.max(max, nextInt);
            min = Math.min(min, nextInt);
          }

          assert min > 0;

          final int offset = (min >> INT_BITS_SHIFT) << INT_BITS_SHIFT;
          final int bitsLength = ((max - offset) >> INT_BITS_SHIFT) + 1;
          final int[] bits = ourSpareBuffer.getBuffer(bitsLength);
          for(int i = 0; i < bitsLength; ++i) bits[i] = 0;

          intIterator = originalInput.rawIntIterator();
          while(intIterator.hasNext()) {
            final int id = intIterator.next() - offset;
            bits[id >> INT_BITS_SHIFT] |= (1 << (id));
          }

          int pos = nextSetBit(0, bits, bitsLength);
          int prev = 0;

          while (pos != -1) {
            DataInputOutputUtil.writeINT(out, pos + offset - prev);
            prev = pos + offset;
            pos = nextSetBit(pos + 1, bits, bitsLength);
          }
        }
      }
    }
  }

  static final int NUMBER_OF_VALUES_THRESHOLD = 20;

  public void readFrom(DataInputStream stream, DataExternalizer<Value> externalizer) throws IOException {
    FileId2ValueMapping<Value> mapping = null;

    while (stream.available() > 0) {
      final int valueCount = DataInputOutputUtil.readINT(stream);
      if (valueCount < 0) {
        // ChangeTrackingValueContainer marked inputId as invalidated, see ChangeTrackingValueContainer.saveTo
        final int inputId = -valueCount;

        if (mapping == null && size() > NUMBER_OF_VALUES_THRESHOLD) { // avoid O(NumberOfValues)
          mapping = new FileId2ValueMapping<Value>(this);
        }

        boolean doCompact;
        if(mapping != null) {
          doCompact = mapping.removeFileId(inputId);
        } else {
          removeAssociatedValue(inputId);
          doCompact = true;
        }

        if (doCompact) setNeedsCompacting(true);
      }
      else {
        for (int valueIdx = 0; valueIdx < valueCount; valueIdx++) {
          final Value value = externalizer.read(stream);
          int idCountOrSingleValue = DataInputOutputUtil.readINT(stream);

          if (idCountOrSingleValue > 0) {
            addValue(idCountOrSingleValue, value);
            if (mapping != null) mapping.associateFileIdToValue(idCountOrSingleValue, value);
          } else {
            idCountOrSingleValue = -idCountOrSingleValue;
            ensureFileSetCapacityForValue(value, idCountOrSingleValue);
            int prev = 0;

            for (int i = 0; i < idCountOrSingleValue; i++) {
              final int id = DataInputOutputUtil.readINT(stream);
              addValue(prev + id, value);
              if (mapping != null) mapping.associateFileIdToValue(prev + id, value);
              prev += id;
            }
          }
        }
      }
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

    @Override
    public boolean hasAscendingOrder() {
      return true;
    }

    @Override
    public IntIterator createCopyInInitialState() {
      return new SingleValueIterator(myValue);
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
        if (val instanceof ChangeBufferingList) {
          cloned.put(key, ((ChangeBufferingList)val).clone());
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
}
