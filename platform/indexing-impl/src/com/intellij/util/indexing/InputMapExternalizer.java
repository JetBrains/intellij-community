// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.util.SmartList;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Default externalizer for forward indexes: serializes Map[Key,Value] by using index extension
 * both {@link IndexExtension#getKeyDescriptor()} and {@link IndexExtension#getValueExternalizer()},
 * so that all forward indexes internally are [Key -> ByteArraySequence(serialized Map[Key,Value])]
 *
 * @see ValueLessInputMapExternalizer
 */
@Internal
public final class InputMapExternalizer<Key, Value> implements DataExternalizer<Map<Key, Value>> {
  private final DataExternalizer<Value> myValueExternalizer;
  private final DataExternalizer<Collection<Key>> myKeysExternalizer;
  private final boolean myValuesAreNullAlways;

  public InputMapExternalizer(@NotNull DataExternalizer<Collection<Key>> keysExternalizer,
                              @NotNull DataExternalizer<Value> valueExternalizer,
                              boolean valueIsAbsent) {
    myKeysExternalizer = keysExternalizer;
    myValueExternalizer = valueExternalizer;
    myValuesAreNullAlways = valueIsAbsent;
  }

  @Override
  public void save(@NotNull DataOutput stream, Map<Key, Value> data) throws IOException {
    final int size = data.size();
    DataInputOutputUtil.writeINT(stream, size);
    if (size == 0) return;

    final Collection<Key>[] keysForNullValue = new Collection[]{null};
    Map<Value, Collection<Key>> keysPerValue = null;

    //Store Map<Key,Value> in 'inverted' form (as Map<Value, Collection<Key>>) because it usually
    // allows for more compact representation
    if (myValuesAreNullAlways) {
      keysForNullValue[0] = data.keySet();
    }
    else {
      keysPerValue = new HashMap<>();
      Map<Value, Collection<Key>> finalKeysPerValue = keysPerValue;
      data.forEach((key, value) -> {
        Collection<Key> keys = value != null ? finalKeysPerValue.get(value) : keysForNullValue[0];
        if (keys == null) {
          keys = new SmartList<>();
          if (value != null) {
            finalKeysPerValue.put(value, keys);
          }
          else {
            keysForNullValue[0] = keys;
          }
        }
        keys.add(key);
      });
    }

    if (keysForNullValue[0] != null) {
      myValueExternalizer.save(stream, null);
      myKeysExternalizer.save(stream, keysForNullValue[0]);
    }

    if (keysPerValue != null) {
      for (Value value : keysPerValue.keySet()) {
        myValueExternalizer.save(stream, value);
        myKeysExternalizer.save(stream, keysPerValue.get(value));
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
