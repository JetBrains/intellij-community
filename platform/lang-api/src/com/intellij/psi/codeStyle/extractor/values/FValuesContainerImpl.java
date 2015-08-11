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

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Roman.Shein
 * @since 04.08.2015.
 */
public class FValuesContainerImpl implements FValuesContainer {
  @NotNull
  protected final List<FValue> myValues;

  public FValuesContainerImpl(@NotNull List<FValue> values) {
    myValues = values;
  }

  @NotNull
  public List<FValue> getValues() {
    return myValues;
  }

  public void applySelected() {
    for (FValue value : myValues) {
      if (value.state == FValue.STATE.SELECTED) {
        value.write(false);
      }
    }
  }

  @Contract("false -> null")
  public FValuesContainer apply(boolean retPrevValue) {
    if (retPrevValue) {
      final ArrayList<FValue> orig = new ArrayList<FValue>();
      for (FValue value : myValues) {
        final FValue old = value.write(true);
        if (old != null) {
          orig.add(old);
        }
      }
      return new FGens(orig);
    }

    for (FValue value : myValues) {
      value.write(false);
    }
    return null;
  }
}
