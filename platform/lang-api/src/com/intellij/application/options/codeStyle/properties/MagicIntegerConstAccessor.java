// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.properties;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.IntArrayList;
import com.intellij.util.containers.IntObjectMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MagicIntegerConstAccessor extends ExternalStringAccessor<Integer> implements CodeStyleChoiceList {
  private final IntObjectMap<String> myValueMap = ConcurrentCollectionFactory.createConcurrentIntObjectMap();
  private final Map<String, IntArrayList> myValueToKeysMap = CollectionFactory.createSmallMemoryFootprintMap();

  public MagicIntegerConstAccessor(@NotNull Object object,
                                   @NotNull Field field,
                                   int @NotNull [] intValues,
                                   String @NotNull [] strValues) {
    super(object, field);
    for (int i = 0; i < Math.min(intValues.length, strValues.length); i ++) {
      String value = strValues[i];
      int intValue = intValues[i];
      myValueMap.put(intValue, value);
      myValueToKeysMap.computeIfAbsent(value, __ -> new IntArrayList()).add(intValue);
    }
  }

  @Override
  protected final @Nullable Integer fromExternal(@NotNull String str) {
    IntArrayList keys = myValueToKeysMap.get(str);
    return keys != null && keys.size() > 0 ? keys.get(0) : null;
  }

  @Override
  protected final @NotNull String toExternal(@NotNull Integer value) {
    return myValueMap.get(value.intValue());
  }

  @Override
  public final @NotNull List<String> getChoices() {
    return new ArrayList<>(myValueMap.values());
  }
}
