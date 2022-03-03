// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.rt.execution.junit;

import junit.framework.ComparisonFailure;

import java.io.File;

public class FileComparisonFailure extends ComparisonFailure  {
  private final String myExpected;
  private final String myActual;
  private final String myFilePath;
  private final String myActualFilePath;

  public FileComparisonFailure(String message, /*@NotNull */String expected, /*@NotNull */String actual, String expectedFilePath) {
    this(message, expected, actual, expectedFilePath, null);
  }

  public FileComparisonFailure(String message, /*@NotNull */String expected, /*@NotNull */String actual, String expectedFilePath, String actualFilePath) {
    super(message, expected, actual);
    if (expected == null) throw new NullPointerException("'expected' must not be null");
    if (actual == null) throw new NullPointerException("'actual' must not be null");
    myExpected = expected;
    myActual = actual;
    myFilePath = expectedFilePath;
    if (expectedFilePath != null && !new File(expectedFilePath).isFile()) throw new NullPointerException("'expectedFilePath' should point to the existing file or be null");
    myActualFilePath = actualFilePath;
  }

  public String getFilePath() {
    return myFilePath;
  }

  public String getActualFilePath() {
    return myActualFilePath;
  }
  
  public String getExpected() {
    return myExpected;
  }

  public String getActual() {
    return myActual;
  }
}
