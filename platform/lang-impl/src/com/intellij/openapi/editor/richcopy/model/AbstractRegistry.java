// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.richcopy.model;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public abstract class AbstractRegistry<T> {
  @SuppressWarnings("SSBasedInspection") private final @NotNull Int2ObjectOpenHashMap<T> myDataById = new Int2ObjectOpenHashMap<>();

  private transient Object2IntMap<T> myIdsByData = new Object2IntOpenHashMap<>();

  public @NotNull T dataById(int id) throws IllegalArgumentException {
    T result = myDataById.get(id);
    if (result == null) {
      throw new IllegalArgumentException("No data is registered for id " + id);
    }
    return result;
  }
  
  public int getId(@NotNull T data) throws IllegalStateException {
    if (myIdsByData == null) {
      throw new IllegalStateException(String.format(
        "Can't register data '%s'. Reason: the %s registry is already sealed", data, getClass().getName()
      ));
    }
    int id = myIdsByData.getInt(data);
    if (id <= 0) {
      id = myIdsByData.size() + 1;
      myDataById.put(id, data);
      myIdsByData.put(data, id);
    }
    return id;
  }

  public int[] getAllIds() {
    int[] result = myDataById.keySet().toIntArray();
    Arrays.sort(result);
    return result;
  }

  public int size() {
    return myDataById.size();
  }

  public void seal() {
    myIdsByData = null;
    myDataById.trim();
  }
}
