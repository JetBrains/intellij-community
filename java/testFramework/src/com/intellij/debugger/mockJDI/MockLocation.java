/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.debugger.mockJDI;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ReferenceType;

import java.util.List;

public class MockLocation extends MockMirror implements Location {
  private final int myLineNumber;
  private final String mySourcePath;
  private final String mySourceName;
  private final long myCodeIndex;
  private final Method myMethod;

  public MockLocation(final int lineNumber, final long codeIndex, final Method method, final String sourcePath, final String sourceName) {
    super((MockVirtualMachine)method.virtualMachine());
    mySourceName = sourceName;
    mySourcePath = sourcePath;
    myLineNumber = lineNumber;
    myCodeIndex = codeIndex;
    myMethod = method;
  }

  public MockLocation(final int lineNumber, final long codeIndex, final Method method) {
    super((MockVirtualMachine)method.virtualMachine());
    myLineNumber = lineNumber;
    myCodeIndex = codeIndex;
    myMethod = method;
    try {
      final List<String> paths = declaringType().sourcePaths("");
      mySourcePath = paths.isEmpty() ? null : paths.get(0);
      mySourceName = declaringType().sourceName();
    }
    catch (AbsentInformationException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public int lineNumber(String string) {
    return myLineNumber;
  }

  @Override
  public int lineNumber() {
    return myLineNumber;
  }

  @Override
  public String sourcePath(String string) {
    return mySourcePath;
  }

  @Override
  public String sourcePath() {
    return mySourcePath;
  }

  @Override
  public String sourceName(String string) {
    return mySourceName;
  }

  @Override
  public String sourceName() {
    return mySourceName;
  }

  @Override
  public long codeIndex() {
    return myCodeIndex;
  }

  @Override
  public Method method() {
    return myMethod;
  }

  @Override
  public ReferenceType declaringType() {
    return method().declaringType();
  }

  @Override
  public int compareTo(final Location o) {
    final long codeIndex = ((MockLocation)o).myCodeIndex;
    return Long.compare(myCodeIndex, codeIndex);
  }
}
