/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Tagir Valeev
 */
public class DfaRangeValue extends DfaValue {
  private final LongRangeSet myValue;

  DfaRangeValue(DfaValueFactory factory, @NotNull LongRangeSet value) {
    super(factory);
    myValue = value;
  }

  public DfaRangeValue intersect(LongRangeSet value) {
    return myFactory.getRangeFactory().create(myValue.intersect(value));
  }

  public LongRangeSet getValue() {
    return myValue;
  }

  @Override
  public String toString() {
    return myValue.toString();
  }

  public static class Factory {
    private Map<LongRangeSet, DfaRangeValue> myValues = new HashMap<>();
    private DfaValueFactory myFactory;

    Factory(DfaValueFactory factory) {
      myFactory = factory;
    }

    /**
     * Any value of given type (if type is supported)
     *
     * @param type type to create a range-value from
     * @return DfaRangeValue representing range of given type
     */
    @Nullable
    public DfaRangeValue create(PsiType type) {
      LongRangeSet domain = LongRangeSet.fromType(type);
      return domain == null ? null : create(domain);
    }

    @NotNull
    public DfaRangeValue create(LongRangeSet value) {
      return myValues.computeIfAbsent(value, val -> new DfaRangeValue(myFactory, val));
    }
  }
}
