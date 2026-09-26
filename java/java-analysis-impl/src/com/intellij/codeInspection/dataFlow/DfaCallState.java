// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import org.jetbrains.annotations.NotNull;

public final class DfaCallState {
  final @NotNull DfaMemoryState myMemoryState;
  final @NotNull DfaCallArguments myCallArguments;
  final @NotNull DfaValue myReturnValue;

  public DfaCallState(@NotNull DfaMemoryState state,
               @NotNull DfaCallArguments arguments,
               @NotNull DfaValue returnValue) {
    myMemoryState = state;
    myCallArguments = arguments;
    myReturnValue = returnValue;
  }

  public @NotNull DfaMemoryState getMemoryState() {
    return myMemoryState;
  }

  public @NotNull DfaCallArguments getCallArguments() {
    return myCallArguments;
  }

  public @NotNull DfaValue getReturnValue() {
    return myReturnValue;
  }

  public @NotNull DfaCallState withMemoryState(@NotNull DfaMemoryState state) {
    return new DfaCallState(state, myCallArguments, myReturnValue);
  }

  public @NotNull DfaCallState withArguments(@NotNull DfaCallArguments arguments) {
    return new DfaCallState(myMemoryState, arguments, myReturnValue);
  }

  public @NotNull DfaCallState withReturnValue(@NotNull DfaValue returnValue) {
    return new DfaCallState(myMemoryState, myCallArguments, returnValue);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DfaCallState that)) return false;
    return myMemoryState.equals(that.myMemoryState) && myCallArguments.equals(that.myCallArguments);
  }

  @Override
  public int hashCode() {
    return 31 * myMemoryState.hashCode() + myCallArguments.hashCode();
  }
}
