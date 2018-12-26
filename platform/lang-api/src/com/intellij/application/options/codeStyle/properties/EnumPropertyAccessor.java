// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.properties;

import com.intellij.util.containers.BidirectionalMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EnumPropertyAccessor extends CodeStylePropertyAccessor<Enum> implements CodeStyleChoiceList{

  private final Class myEnumClass;
  private final BidirectionalMap<String,Enum> myEnumMap = new BidirectionalMap<>();

  public EnumPropertyAccessor(@NotNull Object object, @NotNull Field field) {
    super(object, field);
    myEnumClass = field.getType();
    fillEnumMap();
  }

  private void fillEnumMap() {
    final Object[] enumConstants = myEnumClass.getEnumConstants();
    if (enumConstants != null) {
      for (Object enumConstant : enumConstants) {
        myEnumMap.put(enumConstant.toString().toLowerCase(Locale.ENGLISH), (Enum)enumConstant);
      }
    }
  }

  @NotNull
  @Override
  public List<String> getChoices() {
    return new ArrayList<>(myEnumMap.keySet());
  }

  @Nullable
  @Override
  protected Enum parseString(@NotNull String str) {
    return myEnumMap.get(str);
  }

  @NotNull
  @Override
  protected String asString(@NotNull Enum value) {
    List<String> names = myEnumMap.getKeysByValue(value);
    assert names != null && names.size() > 0 : "Unexpected value " + value.toString();
    return names.get(0);
  }
}
