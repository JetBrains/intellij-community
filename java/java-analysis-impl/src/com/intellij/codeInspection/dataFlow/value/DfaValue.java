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

public class DfaValue {
  private final int myID;
  protected final DfaValueFactory myFactory;

  protected DfaValue(final DfaValueFactory factory) {
    myFactory = factory;
    myID = factory == null ? 0 : factory.registerValue(this);
  }

  public int getID() {
    return myID;
  }

  public DfaValue createNegated() {
    return DfaUnknownValue.getInstance();
  }

  public boolean equals(Object obj) {
    return obj instanceof DfaValue && getID() == ((DfaValue)obj).getID();
  }

  public int hashCode() {
    return getID();
  }
}
