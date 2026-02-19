// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.jdi;

import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.jdi.SlotLocalVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author Eugene Zhuravlev
 */
public class DecompiledLocalVariable implements SlotLocalVariable {
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

  @Override
  public int slot() {
    return mySlot;
  }

  @Override
  public @Nullable String signature() {
    return mySignature;
  }

  public boolean isParam() {
    return myIsParam;
  }

  public @NotNull String getDefaultName() {
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

  public @NotNull Collection<String> getMatchedNames() {
    return myMatchedNames;
  }

  @Override
  public String toString() {
    return getDisplayName() + " (slot " + mySlot + ", " + mySignature + ")";
  }

  public static int getParamId(@Nullable String name) {
    if (!StringUtil.isEmpty(name)) {
      return StringUtil.parseInt(StringUtil.substringAfter(name, PARAM_PREFIX), -1);
    }
    return -1;
  }
}
