/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;

public class DfaUnknownValue extends DfaValue {
  private static class DfaUnknownValueHolder {
    private static final DfaUnknownValue myInstance = new DfaUnknownValue();
  }

  @NotNull
  public static DfaUnknownValue getInstance() {
    return DfaUnknownValueHolder.myInstance;
  }

  private DfaUnknownValue() {
    super(null);
  }

  @Override
  public DfaValue createNegated() {
    return this;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "<unknown>";
  }

  public boolean equals(Object obj) {
    return obj == this;
  }

  public int hashCode() {
    return 0;
  }

  @Override
  public int getID() {
    return 0;
  }

}
