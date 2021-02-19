// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.util.SmartList;
import com.intellij.util.indexing.impl.InputIndexDataExternalizer;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class InputMapExternalizer<Key, Value> implements DataExternalizer<Map<Key, Value>> {
  private final DataExternalizer<Value> myValueExternalizer;
  private final DataExternalizer<Collection<Key>> myKeysExternalizer;
  private final boolean myValuesAreNullAlways;

  public InputMapExternalizer(@NotNull IndexExtension<Key, Value, ?> extension) {
    myValueExternalizer = extension.getValueExternalizer();
    myKeysExternalizer = extension instanceof CustomInputsIndexFileBasedIndexExtension
    ? ((CustomInputsIndexFileBasedIndexExtension<Key>)extension).createExternalizer()
    : new InputIndexDataExternalizer<>(extension.getKeyDescriptor(), ((IndexExtension<Key, ?, ?>)extension).getName());
    myValuesAreNullAlways = extension instanceof ScalarIndexExtension;
  }

  @Override
  public void save(@NotNull DataOutput stream, Map<Key, Value> data) throws IOException {
    int size = data.size();
    DataInputOutputUtil.writeINT(stream, size);
    Collection<Key> keysForNullValue = null;
    Map<Value, Collection<Key>> values = null;

    if (size <= 0) return;

    if (myValuesAreNullAlways) {
      keysForNullValue = data.keySet();
    }
    else {
      values = new HashMap<>();
      for (Map.Entry<Key, Value> e : data.entrySet()) {
        Value value = e.getValue();

        Collection<Key> keys = value != null ? values.get(value) : keysForNullValue;
        if (keys == null) {
          if (value != null) {
            values.put(value, keys = new SmartList<>());
          }
          else {
            keys = keysForNullValue = new SmartList<>();
          }
        }
        keys.add(e.getKey());
      }
    }

    if (keysForNullValue != null) {
      myValueExternalizer.save(stream, null);
      myKeysExternalizer.save(stream, keysForNullValue);
    }

    if (values != null) {
      for (Value value : values.keySet()) {
        myValueExternalizer.save(stream, value);
        myKeysExternalizer.save(stream, values.get(value));
      }
    }
  }

  @Override
  public Map<Key, Value> read(@NotNull DataInput in) throws IOException {
    int pairs = DataInputOutputUtil.readINT(in);
    if (pairs == 0) return Collections.emptyMap();
    Map<Key, Value> result = new HashMap<>(pairs);
    while (((InputStream)in).available() > 0) {
      Value value = myValueExternalizer.read(in);
      Collection<Key> keys = myKeysExternalizer.read(in);
      for (Key k : keys) result.put(k, value);
    }
    return result;
  }
}
