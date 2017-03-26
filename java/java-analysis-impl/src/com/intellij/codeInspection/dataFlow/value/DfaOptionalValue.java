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

public class DfaOptionalValue extends DfaValue {
  final boolean myPresent;

  protected DfaOptionalValue(DfaValueFactory factory, boolean isPresent) {
    super(factory);
    myPresent = isPresent;
  }

  public boolean isPresent() {
    return myPresent;
  }

  public String toString() {
    return myPresent ? "Optional with value" : "Empty optional";
  }

  public static class Factory {
    private final DfaOptionalValue myPresent, myAbsent;

    Factory(DfaValueFactory factory) {
      myPresent = new DfaOptionalValue(factory, true);
      myAbsent = new DfaOptionalValue(factory, false);
    }

    public DfaOptionalValue getOptional(boolean present) {
      return present ? myPresent : myAbsent;
    }
  }
}
