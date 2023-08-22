// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.jvm.transfer;

import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.dataFlow.value.DfaControlTransferValue;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class ExceptionTransfer implements DfaControlTransferValue.TransferTarget {
  private final @NotNull TypeConstraint myThrowable;

  public ExceptionTransfer(@NotNull TypeConstraint throwable) { 
    myThrowable = throwable; 
  }

  public @NotNull TypeConstraint getThrowable() {
    return myThrowable;
  }

  @Override
  public String toString() {return "Exception(" + myThrowable + ")";}

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ExceptionTransfer transfer = (ExceptionTransfer)o;
    return myThrowable.equals(transfer.myThrowable);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myThrowable);
  }
}
