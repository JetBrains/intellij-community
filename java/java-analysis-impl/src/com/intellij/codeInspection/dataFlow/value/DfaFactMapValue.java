// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.codeInspection.dataFlow.DfaFactMap;
import com.intellij.codeInspection.dataFlow.DfaFactType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class DfaFactMapValue extends DfaValue {
  private final DfaFactMap myFacts;

  DfaFactMapValue(DfaValueFactory factory, DfaFactMap facts) {
    super(factory);
    myFacts = facts;
  }

  public <T> DfaValue withFact(@NotNull DfaFactType<T> factType, @Nullable T value) {
    return getFactory().getFactFactory().createValue(myFacts.with(factType, value));
  }

  public DfaFactMap getFacts() {
    return myFacts;
  }

  @Nullable
  public <T> T get(@NotNull DfaFactType<T> factType) {
    return myFacts.get(factType);
  }

  @Override
  public String toString() {
    return myFacts.toString();
  }

  public static class Factory {
    private DfaValueFactory myFactory;
    private Map<DfaFactMap, DfaFactMapValue> myValues = new HashMap<>();

    Factory(DfaValueFactory factory) {
      myFactory = factory;
    }

    public <T> DfaValue createValue(@NotNull DfaFactType<T> factType, @Nullable T value) {
      return createValue(DfaFactMap.EMPTY.with(factType, value));
    }

    public DfaValue createValue(DfaFactMap facts) {
      if (facts == DfaFactMap.EMPTY) {
        return DfaUnknownValue.getInstance();
      }
      return myValues.computeIfAbsent(facts, f -> new DfaFactMapValue(myFactory, f));
    }
  }
}
