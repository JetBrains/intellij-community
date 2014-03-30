/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.debugger.jdi;

import java.util.Comparator;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/7/13
 */
public class DecompiledLocalVariable{
  public static final Comparator<DecompiledLocalVariable> COMPARATOR = new Comparator<DecompiledLocalVariable>() {
    @Override
    public int compare(DecompiledLocalVariable v1, DecompiledLocalVariable v2) {
      return v1.getSlot() - v2.getSlot();
    }
  };

  private final int mySlot;
  private final String mySignature;
  private final String myName;

  public DecompiledLocalVariable(int slot, String name, String signature) {
    mySlot = slot;
    mySignature = signature;
    myName = name;
  }

  public int getSlot() {
    return mySlot;
  }

  public String getSignature() {
    return mySignature;
  }

  public String getName() {
    return myName;
  }
}
