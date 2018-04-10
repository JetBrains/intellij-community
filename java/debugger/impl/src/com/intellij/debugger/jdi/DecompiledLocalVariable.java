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
package com.intellij.debugger.jdi;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author Eugene Zhuravlev
 */
public class DecompiledLocalVariable{
  public static final String PARAM_PREFIX = "param_";
  public static final String SLOT_PREFIX = "slot_";
  private final int mySlot;
  private final String mySignature;
  private final boolean myIsParam;
  private final Collection<String> myMatchedNames;

  public DecompiledLocalVariable(int slot, boolean isParam, @Nullable String signature, @NotNull Collection<String> names) {
    mySlot = slot;
    myIsParam = isParam;
    mySignature = signature;
    myMatchedNames = names;
  }

  public int getSlot() {
    return mySlot;
  }

  @Nullable
  public String getSignature() {
    return mySignature;
  }

  public boolean isParam() {
    return myIsParam;
  }

  @NotNull
  public String getDefaultName() {
    return (myIsParam ? PARAM_PREFIX : SLOT_PREFIX) + mySlot;
  }

  public String getDisplayName() {
    String nameString = StringUtil.join(myMatchedNames, " | ");
    if (myIsParam && myMatchedNames.size() == 1) {
      return nameString;
    }
    else if (!myMatchedNames.isEmpty()) {
      return nameString + " (" + getDefaultName() + ")";
    }
    return getDefaultName();
  }

  @NotNull
  public Collection<String> getMatchedNames() {
    return myMatchedNames;
  }

  @Override
  public String toString() {
    return getDisplayName() + " (slot " + mySlot + ", " + mySignature + ")";
  }

  public static int getParamId(@Nullable String name) {
    if (!StringUtil.isEmpty(name)) {
      String idString = StringUtil.substringAfter(name, PARAM_PREFIX);
      if (idString != null) {
        try {
          return Integer.parseInt(idString);
        }
        catch (NumberFormatException ignored) {
        }
      }
    }
    return -1;
  }
}
