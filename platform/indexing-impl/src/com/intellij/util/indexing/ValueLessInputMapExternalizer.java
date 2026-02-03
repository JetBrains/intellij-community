// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * Externalizer for forward indexes, specialized version of {@link InputMapExternalizer} for value-less indexes:
 * indexes there Value=Void, i.e. always null.
 * Those indexes implement {@link ScalarIndexExtension}.
 * <p>Binary format created by this externalizer is compatible with that of default {@link InputMapExternalizer}
 * created for the same index extension
 * <p>
 * TODO RC: specialize even more for Key=Integer (heaviest of our indexes!)
 */
@Internal
public final class ValueLessInputMapExternalizer<Key> implements DataExternalizer<Map<Key, Void>> {
  private final DataExternalizer<Collection<Key>> keysExternalizer;

  public ValueLessInputMapExternalizer(@NotNull DataExternalizer<Collection<Key>> keysExternalizer) {
    this.keysExternalizer = keysExternalizer;
  }

  @Override
  public void save(@NotNull DataOutput stream, Map<Key, Void> data) throws IOException {
    int size = data.size();
    DataInputOutputUtil.writeINT(stream, size);
    if (size == 0) return;

    keysExternalizer.save(stream, data.keySet());
  }

  @Override
  public Map<Key, Void> read(@NotNull DataInput in) throws IOException {
    int entriesCount = DataInputOutputUtil.readINT(in);
    if (entriesCount == 0) return Collections.emptyMap();
    Collection<Key> keys = keysExternalizer.read(in);
    //noinspection SSBasedInspection
    return new MapWithNullValues<>(new ObjectOpenHashSet<>(keys));
  }


  private static final class MapWithNullValues<Key> extends AbstractMap<Key, Void> {
    private final Set<Key> keys;

    private MapWithNullValues(@NotNull Set<Key> keys) {
      this.keys = keys;
    }

    @Override
    public int size() {
      return keys.size();
    }

    @Override
    public boolean containsKey(Object key) {
      return keys.contains(key);
    }

    @Override
    public boolean containsValue(Object value) {
      return value == null;
    }

    @Override
    public Void get(Object key) {
      return null;
    }

    @Override
    public @Nullable Void put(Key key, Void value) {
      if (value != null) {
        throw new IllegalArgumentException("Only null values are accepted, but got: " + value);
      }
      keys.add(key);
      return null;
    }

    @Override
    public Void remove(Object key) {
      keys.remove(key);
      return null;
    }

    @Override
    public void putAll(@NotNull Map<? extends Key, ? extends Void> m) {
      keys.addAll(m.keySet());
    }

    @Override
    public void clear() {
      keys.clear();
    }

    @Override
    public void forEach(BiConsumer<? super Key, ? super Void> action) {
      Objects.requireNonNull(action);
      for (Key key : keys) {
        action.accept(key, null);
      }
    }

    @Override
    public @NotNull Set<Entry<Key, Void>> entrySet() {
      return new AbstractSet<>() {
        @Override
        public Iterator<Entry<Key, Void>> iterator() {
          Iterator<Key> keysIterator = keys.iterator();
          return new Iterator<>() {
            @Override
            public boolean hasNext() {
              return keysIterator.hasNext();
            }

            @Override
            public Entry<Key, Void> next() {
              Key key = keysIterator.next();
              return new Entry<>() {
                @Override
                public Key getKey() {
                  return key;
                }

                @Override
                public Void getValue() {
                  return null;
                }

                @Override
                public Void setValue(Void value) {
                  return null;
                }
              };
            }
          };
        }

        @Override
        public int size() {
          return keys.size();
        }
      };
    }
  }
}
