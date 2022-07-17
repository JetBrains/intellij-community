// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.properties;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.BidirectionalMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class EnumPropertyAccessor extends ExternalStringAccessor<Enum<?>> implements CodeStyleChoiceList {

  private final Class<? extends Enum<?>> myEnumClass;
  private final BidirectionalMap<String, Enum<?>> myEnumMap = new BidirectionalMap<>();

  public EnumPropertyAccessor(@NotNull Object object, @NotNull Field field) {
    super(object, field);
    //noinspection unchecked
    myEnumClass = (Class<? extends Enum<?>>)field.getType().asSubclass(Enum.class);
    fillEnumMap();
  }

  private void fillEnumMap() {
    final Enum<?>[] enumConstants = myEnumClass.getEnumConstants();
    if (enumConstants != null) {
      for (Enum<?> enumConstant : enumConstants) {
        myEnumMap.put(getExternalString(enumConstant), enumConstant);
      }
    }
  }

  private static String getExternalString(Enum<?> enumConstant) {
    return StringUtil.toLowerCase(enumConstant.toString().replace(" ", "_"));
  }

  @NotNull
  @Override
  public List<String> getChoices() {
    return new ArrayList<>(myEnumMap.keySet());
  }

  @Nullable
  @Override
  protected Enum<?> fromExternal(@NotNull String str) {
    return myEnumMap.get(str);
  }

  @NotNull
  @Override
  protected String toExternal(@NotNull Enum<?> value) {
    List<String> names = myEnumMap.getKeysByValue(value);
    assert names != null && names.size() > 0 : "Unexpected value " + value;
    return names.get(0);
  }
}
