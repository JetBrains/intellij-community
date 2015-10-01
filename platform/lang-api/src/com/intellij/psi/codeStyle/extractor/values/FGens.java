/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.extractor.values;

import com.intellij.psi.codeStyle.extractor.FUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class FGens extends FValuesExtractionResultImpl {

  public FGens(@NotNull List<FValue> values) {
    super(values);
  }

  public FGens(@NotNull FGens gens) {
    this(gens.copy().getValues());
  }

  public FGens mutate(int averageMutationCount) {
    if (averageMutationCount > 0) {
      // with mutation

      int commonMutagen = 0;

      for (FValue value : myValues) {
        commonMutagen += value.getMutagenFactor();
      }

      for (FValue value : myValues) {
        if (FUtils.getRandomLess(commonMutagen) < value.getMutagenFactor() * averageMutationCount) {
          final Object[] possibleValues = value.getPossibleValues();
          value.value = possibleValues[FUtils.getRandomLess(possibleValues.length)];
        }
      }
    }
    return this;
  }

  public FGens dropToInitial() {
    final ArrayList<FValue> values = new ArrayList<FValue>();
    for (FValue value : myValues) {
      final Object[] possibleValues = value.getPossibleValues();
      if (possibleValues.length > 0) {
        value.value = possibleValues[0];
      }
    }
    return this;
  }

  @NotNull
  public FGens copy() {
    final ArrayList<FValue> values = new ArrayList<FValue>();
    for (FValue value : myValues) {
      values.add(new FValue(value));
    }
    return new FGens(values);
  }


  public FGens diff(FGens newGens) {
    final List<FValue> newValues = newGens.getValues();
    final int size = myValues.size();
    assert size == newValues.size();
    final List<FValue> diff = new ArrayList<FValue>();
    for (int i = 0; i < size; ++i) {
      final FValue value = myValues.get(i);
      final FValue newValue = newValues.get(i);
      assert value.name.equals(newValue.name);
      if (!value.value.equals(newValue.value)) {
        diff.add(new FValue(value.name, value.value + "->" + newValue.value, value.serializer, value.kind));
      }
    }
    return new FGens(diff);
  }

  @NotNull
  public static FGens breed(@NotNull FGens p1, @NotNull FGens p2, int averageMutationCount) {
    final int size = p1.myValues.size();
    assert size == p2.myValues.size();

    // Crossover!
    final int crossover = size / 2;//FUtils.getRandomLess(size - 6) + 3;

    final List<FValue> values = new ArrayList<FValue>(size);
    for (int i = 0; i < size; ++i) {
      final FValue value1 = p1.myValues.get(i);
      final FValue value2 = p2.myValues.get(i);
      if (value1.kind == FValue.VAR_KIND.INDENT) {
        values.add(new FValue(value1));
      }
      else if (value1.kind == FValue.VAR_KIND.BRACE_STYLE) {
        values.add(new FValue(value2));
      }
      else if ((i & 0x1) == 1) {
        values.add(new FValue(value1));
      }
      else {
        values.add(new FValue(value2));
      }
    }
    return new FGens(values).mutate(averageMutationCount);
  }

  public void copyFrom(FGens gens) {
    if (myValues == gens.myValues) {
      return;
    }
    myValues.clear();
    myValues.addAll(gens.myValues);
  }
}
