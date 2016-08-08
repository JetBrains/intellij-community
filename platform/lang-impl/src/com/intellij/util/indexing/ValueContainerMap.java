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

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.annotations.NotNull;

import java.io.*;

/**
 * @author Dmitry Avdeev
 *         Date: 8/10/11
 */
class ValueContainerMap<Key, Value> extends PersistentHashMap<Key, ValueContainer<Value>> {
  @NotNull private final DataExternalizer<Value> myValueExternalizer;
  private final boolean myKeyIsUniqueForIndexedFile;

  ValueContainerMap(@NotNull final File file,
                    @NotNull KeyDescriptor<Key> keyKeyDescriptor,
                    @NotNull DataExternalizer<Value> valueExternalizer,
                    boolean keyIsUniqueForIndexedFile
                    ) throws IOException {
    super(file, keyKeyDescriptor, new ValueContainerExternalizer<>(valueExternalizer));
    myValueExternalizer = valueExternalizer;
    myKeyIsUniqueForIndexedFile = keyIsUniqueForIndexedFile;
  }

  @NotNull
  Object getDataAccessLock() {
    return myEnumerator;
  }

  @Override
  protected void doPut(Key key, ValueContainer<Value> container) throws IOException {
    synchronized (myEnumerator) {
      ChangeTrackingValueContainer<Value> valueContainer = (ChangeTrackingValueContainer<Value>)container;

      // try to accumulate index value calculated for particular key to avoid fragmentation: usually keys are scattered across many files
      // note that keys unique for indexed file have their value calculated at once (e.g. key is file id, index calculates something for particular
      // file) and there is no benefit to accumulate values for particular key because only one value exists
      if (!valueContainer.needsCompacting() && !myKeyIsUniqueForIndexedFile) {
        final BufferExposingByteArrayOutputStream bytes = new BufferExposingByteArrayOutputStream();
        //noinspection IOResourceOpenedButNotSafelyClosed
        final DataOutputStream _out = new DataOutputStream(bytes);
        valueContainer.saveTo(_out, myValueExternalizer);

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
    @NotNull private final DataExternalizer<T> myValueExternalizer;

    private ValueContainerExternalizer(@NotNull DataExternalizer<T> valueExternalizer) {
      myValueExternalizer = valueExternalizer;
    }

    @Override
    public void save(@NotNull final DataOutput out, @NotNull final ValueContainer<T> container) throws IOException {
      container.saveTo(out, myValueExternalizer);
    }

    @NotNull
    @Override
    public ValueContainerImpl<T> read(@NotNull final DataInput in) throws IOException {
      final ValueContainerImpl<T> valueContainer = new ValueContainerImpl<>();

      valueContainer.readFrom((DataInputStream)in, myValueExternalizer);
      return valueContainer;
    }
  }
}
