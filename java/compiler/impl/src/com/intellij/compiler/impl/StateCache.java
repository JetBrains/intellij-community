// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.impl;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;

public abstract class StateCache<T> {
  private PersistentHashMap<String, T> myMap;
  private final File myBaseFile;

  public StateCache(@NonNls File storePath) throws IOException {
    myBaseFile = storePath;
    myMap = createMap(storePath);
  }

  protected abstract T read(DataInput stream) throws IOException;

  protected abstract void write(T t, DataOutput out) throws IOException;

  public void force() {
    myMap.force();
  }

  public void close() throws IOException {
    myMap.close();
  }

  public boolean wipe() {
    try {
      myMap.closeAndClean();
    } catch (IOException ignored) {
    }
    try {
      myMap = createMap(myBaseFile);
    }
    catch (IOException ignored) {
      return false;
    }
    return true;
  }

  public void update(@NonNls String url, T state) throws IOException {
    if (state != null) {
      myMap.put(url, state);
    }
    else {
      remove(url);
    }
  }

  public void remove(String url) throws IOException {
    myMap.remove(url);
  }

  public T getState(String url) throws IOException {
    return myMap.get(url);
  }

  private PersistentHashMap<String, T> createMap(final File file) throws IOException {
    return new PersistentHashMap<>(file.toPath(), EnumeratorStringDescriptor.INSTANCE, new DataExternalizer<>() {
      @Override
      public void save(@NotNull final DataOutput out, final T value) throws IOException {
        StateCache.this.write(value, out);
      }

      @Override
      public T read(@NotNull final DataInput in) throws IOException {
        return StateCache.this.read(in);
      }
    });
  }

}
