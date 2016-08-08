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

import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Roman.Shein
 * @since 04.08.2015.
 */
public class ValuesExtractionResultImpl implements ValuesExtractionResult {
  @NotNull
  protected final List<Value> myValues;

  public ValuesExtractionResultImpl(@NotNull List<Value> values) {
    myValues = values;
  }

  @NotNull
  public List<Value> getValues() {
    return myValues;
  }

  public void applySelected() {
    for (Value value : myValues) {
      if (value.state == Value.STATE.SELECTED) {
        value.write(false);
      }
    }
  }

  @Override
  public void applyConditioned(Condition<Value> c, Map<Value, Object> backup) {
    for (Value value: myValues) {
      if (c.value(value)) {
        value.write(false);
      } else {
        value.value = backup.get(value);
        value.write(false);
      }
    }
  }

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
