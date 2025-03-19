// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.jdi;

import com.intellij.debugger.engine.DebuggerUtils;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class GeneratedLocation implements Location {
  private final int myLineNumber;
  private final @NotNull ReferenceType myReferenceType;
  private final @Nullable Method myMethod;
  private final @NotNull String myMethodName;

  public GeneratedLocation(@NotNull ReferenceType type, @NotNull String methodName, int lineNumber) {
    myLineNumber = lineNumber;
    myReferenceType = type;
    myMethodName = methodName;
    myMethod = DebuggerUtils.findMethod(myReferenceType, methodName, null);
  }

  @Override
  public ReferenceType declaringType() {
    return myReferenceType;
  }

  @Override
  public Method method() {
    return myMethod;
  }

  public @NotNull String methodName() {
    return myMethodName;
  }

  @Override
  public long codeIndex() {
    return -2; // to be never equal to any LocationImpl
  }

  @Override
  public String sourceName() throws AbsentInformationException {
    return myReferenceType.sourceName();
  }

  @Override
  public String sourceName(String stratum) throws AbsentInformationException {
    return firstOrThrow(myReferenceType.sourceNames(stratum));
  }

  @Override
  public String sourcePath() throws AbsentInformationException {
    return firstOrThrow(myReferenceType.sourcePaths(myReferenceType.defaultStratum()));
  }

  @Override
  public String sourcePath(String stratum) throws AbsentInformationException {
    return firstOrThrow(myReferenceType.sourcePaths(stratum));
  }

  @Override
  public int lineNumber() {
    return myLineNumber;
  }

  @Override
  public int lineNumber(String s) {
    return myLineNumber;
  }

  @Override
  public VirtualMachine virtualMachine() {
    return myReferenceType.virtualMachine();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null || getClass() != other.getClass()) return false;
    GeneratedLocation location = (GeneratedLocation)other;
    return myLineNumber == location.myLineNumber &&
           myReferenceType.equals(location.myReferenceType) &&
           myMethodName.equals(location.myMethodName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myReferenceType, myMethodName, myLineNumber);
  }

  // Same as in LocationImpl
  @Override
  public int compareTo(@NotNull Location o) {
    int res = method().compareTo(o.method());
    if (res != 0) {
      return res;
    }
    return Long.compare(codeIndex(), o.codeIndex());
  }

  @Override
  public String toString() {
    return myReferenceType.name() + "." + myMethodName + ":" + myLineNumber;
  }

  private static String firstOrThrow(@NotNull List<String> list) throws AbsentInformationException {
    if (list.isEmpty()) throw new AbsentInformationException();
    return list.get(0);
  }
}
