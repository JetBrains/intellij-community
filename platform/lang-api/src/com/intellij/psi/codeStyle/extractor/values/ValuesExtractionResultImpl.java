// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.extractor.values;

import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ValuesExtractionResultImpl implements ValuesExtractionResult {
  protected final @NotNull List<Value> myValues;

  public ValuesExtractionResultImpl(@NotNull List<Value> values) {
    myValues = values;
  }

  @Override
  public @NotNull List<Value> getValues() {
    return myValues;
  }

  @Override
  public void applySelected() {
    for (Value value : myValues) {
      if (value.state == Value.STATE.SELECTED) {
        value.write(false);
      }
    }
  }

  @Override
  public void applyConditioned(Condition<? super Value> c, Map<Value, Object> backup) {
    for (Value value: myValues) {
      if (c.value(value)) {
        value.write(false);
      } else {
        value.value = backup.get(value);
        value.write(false);
      }
    }
  }

  @Override
  @Contract("false -> null")
  public ValuesExtractionResult apply(boolean retPrevValue) {
    if (retPrevValue) {
      final ArrayList<Value> orig = new ArrayList<>();
      for (Value value : myValues) {
        final Value old = value.write(true);
        if (old != null) {
          orig.add(old);
        }
      }
      return new Gens(orig);
    }

    for (Value value : myValues) {
      value.write(false);
    }
    return null;
  }
}
