// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.properties;

import com.intellij.util.containers.BidirectionalMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class MagicIntegerConstAccessor extends ExternalStringAccessor<Integer> implements CodeStyleChoiceList {
  private final BidirectionalMap<Integer, String> myValueMap = new BidirectionalMap<>();

  public MagicIntegerConstAccessor(@NotNull Object object,
                                   @NotNull Field field,
                                   int[] intValues,
                                   String[] strValues) {
    super(object, field);
    for (int i = 0; i < Math.min(intValues.length, strValues.length); i ++) {
      myValueMap.put(intValues[i], strValues[i]);
    }
  }

  @Nullable
  @Override
  protected final Integer fromExternal(@NotNull String str) {
    List<Integer> keys = myValueMap.getKeysByValue(str);
    return keys != null && keys.size() > 0 ? keys.get(0) : null;
  }

  @NotNull
  @Override
  protected final String toExternal(@NotNull Integer value) {
    return myValueMap.get(value);
  }

  @NotNull
  @Override
  public final List<String> getChoices() {
    return new ArrayList<>(myValueMap.values());
  }
}
