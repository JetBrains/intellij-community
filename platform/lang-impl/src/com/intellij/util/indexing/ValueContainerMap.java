package com.intellij.util.indexing;

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
    public void save(final DataOutput out, @NotNull final ValueContainer<T> container) throws IOException {
      saveImpl(out, container);
    }

    public static void saveInvalidateCommand(final DataOutput out, int inputId) throws IOException {
      DataInputOutputUtil.writeSINT(out, -inputId);
    }

    private void saveImpl(final DataOutput out, @NotNull final ValueContainer<T> container) throws IOException {
      DataInputOutputUtil.writeSINT(out, container.size());
      for (final Iterator<T> valueIterator = container.getValueIterator(); valueIterator.hasNext();) {
        final T value = valueIterator.next();
        myExternalizer.save(out, value);

        final ValueContainer.IntIterator ids = container.getInputIdsIterator(value);
        if (ids != null) {
          DataInputOutputUtil.writeSINT(out, ids.size());
          while (ids.hasNext()) {
            final int id = ids.next();
            DataInputOutputUtil.writeSINT(out, id);
          }
        }
        else {
          DataInputOutputUtil.writeSINT(out, 0);
        }
      }
    }

    @NotNull
    @Override
    public ValueContainerImpl<T> read(final DataInput in) throws IOException {
      DataInputStream stream = (DataInputStream)in;
      final ValueContainerImpl<T> valueContainer = new ValueContainerImpl<T>();

      while (stream.available() > 0) {
        final int valueCount = DataInputOutputUtil.readSINT(in);
        if (valueCount < 0) {
          valueContainer.removeAssociatedValue(-valueCount);
          valueContainer.setNeedsCompacting(true);
        }
        else {
          for (int valueIdx = 0; valueIdx < valueCount; valueIdx++) {
            final T value = myExternalizer.read(in);
            final int idCount = DataInputOutputUtil.readSINT(in);
            valueContainer.ensureFileSetCapacityForValue(value, idCount);
            for (int i = 0; i < idCount; i++) {
              final int id = DataInputOutputUtil.readSINT(in);
              valueContainer.addValue(id, value);
            }
          }
        }
      }
      return valueContainer;
    }
  }

}
