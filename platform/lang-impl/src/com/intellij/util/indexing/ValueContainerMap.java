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

import com.intellij.openapi.util.ThreadLocalCachedIntArray;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.util.io.*;
import com.intellij.util.io.DataOutputStream;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.Iterator;

/**
 * @author Dmitry Avdeev
 *         Date: 8/10/11
 */
class ValueContainerMap<Key, Value> extends PersistentHashMap<Key, ValueContainer<Value>> {
  @NotNull private final ValueContainerExternalizer<Value> myValueContainerExternalizer;

  ValueContainerMap(@NotNull final File file,
                    @NotNull KeyDescriptor<Key> keyKeyDescriptor,
                    @NotNull DataExternalizer<Value> valueExternalizer) throws IOException {

    super(file, keyKeyDescriptor, new ValueContainerExternalizer<Value>(valueExternalizer));
    myValueContainerExternalizer = (ValueContainerExternalizer<Value>)myValueExternalizer;
  }

  @NotNull
  Object getDataAccessLock() {
    return myEnumerator;
  }

  @Override
  protected void doPut(Key key, ValueContainer<Value> container) throws IOException {
    synchronized (myEnumerator) {
      ChangeTrackingValueContainer<Value> valueContainer = (ChangeTrackingValueContainer<Value>)container;
      if (!valueContainer.needsCompacting()) {
        final BufferExposingByteArrayOutputStream bytes = new BufferExposingByteArrayOutputStream();
        //noinspection IOResourceOpenedButNotSafelyClosed
        final DataOutputStream _out = new DataOutputStream(bytes);
        final TIntHashSet set = valueContainer.getInvalidated();
        if (set != null && set.size() > 0) {
          for (int inputId : set.toArray()) {
            ValueContainerExternalizer.saveInvalidateCommand(_out, inputId);
          }
        }

        final ValueContainer<Value> toAppend = valueContainer.getAddedDelta();
        if (toAppend != null && toAppend.size() > 0) {
          myValueContainerExternalizer.save(_out, toAppend);
        }

        appendData(key, new PersistentHashMap.ValueDataAppender() {
          @Override
          public void append(@NotNull final DataOutput out) throws IOException {
            out.write(bytes.getInternalBuffer(), 0, bytes.size());
          }
        });
      }
      else {
        // rewrite the value container for defragmentation
        super.doPut(key, valueContainer);
      }
    }
  }

  private static final class ValueContainerExternalizer<T> implements DataExternalizer<ValueContainer<T>> {
    @NotNull private final DataExternalizer<T> myExternalizer;

    private ValueContainerExternalizer(@NotNull DataExternalizer<T> externalizer) {
      myExternalizer = externalizer;
    }

    @Override
    public void save(@NotNull final DataOutput out, @NotNull final ValueContainer<T> container) throws IOException {
      saveImpl(out, container);
    }

    public static void saveInvalidateCommand(final DataOutput out, int inputId) throws IOException {
      DataInputOutputUtil.writeINT(out, -inputId);
    }
    private static final ThreadLocalCachedIntArray ourSpareBuffer = new ThreadLocalCachedIntArray();

    private void saveImpl(@NotNull DataOutput out, @NotNull final ValueContainer<T> container) throws IOException {
      DataInputOutputUtil.writeINT(out, container.size());

      for (final Iterator<T> valueIterator = container.getValueIterator(); valueIterator.hasNext();) {
        final T value = valueIterator.next();
        myExternalizer.save(out, value);
        ValueContainer.IntIterator ids = container.getInputIdsIterator(value);
        DataInputOutputUtil.writeINT(out, ids.size());
        if (ids.size() == 1) {
          DataInputOutputUtil.writeINT(out, ids.next()); // most common 90% case during index building
        } else {
          // serialize positive file ids with delta encoding after sorting numbers via bitset
          // todo it would be nice to have compressed random access serializable bitset or at least file ids sorted
          int max = 0, min = Integer.MAX_VALUE;

          while (ids.hasNext()) {
            final int id = ids.next();
            max = Math.max(id, max);
            min = Math.min(id, min);
          }

          assert min > 0;

          final int offset = (min >> INT_BITS_SHIFT) << INT_BITS_SHIFT;
          final int bitsLength = ((max - offset) >> INT_BITS_SHIFT) + 1;
          final int[] bits = ourSpareBuffer.getBuffer(bitsLength);
          for(int i = 0; i < bitsLength; ++i) bits[i] = 0;

          ids = container.getInputIdsIterator(value);
          while (ids.hasNext()) {
            final int id = ids.next() - offset;
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

    @NotNull
    @Override
    public ValueContainerImpl<T> read(@NotNull final DataInput in) throws IOException {
      DataInputStream stream = (DataInputStream)in;
      final ValueContainerImpl<T> valueContainer = new ValueContainerImpl<T>();

      while (stream.available() > 0) {
        final int valueCount = DataInputOutputUtil.readINT(in);
        if (valueCount < 0) {
          valueContainer.removeAssociatedValue(-valueCount);
          valueContainer.setNeedsCompacting(true);
        }
        else {
          for (int valueIdx = 0; valueIdx < valueCount; valueIdx++) {
            final T value = myExternalizer.read(in);
            final int idCount = DataInputOutputUtil.readINT(in);
            valueContainer.ensureFileSetCapacityForValue(value, idCount);
            int prev = 0;
            for (int i = 0; i < idCount; i++) {
              final int id = DataInputOutputUtil.readINT(in);
              valueContainer.addValue(prev + id, value);
              prev += id;
            }
          }
        }
      }
      return valueContainer;
    }

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
  }

}
