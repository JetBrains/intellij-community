/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.rt.execution.junit;

import junit.framework.ComparisonFailure;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class ComparisonFailureData {
  private static final String ASSERTION_CLASS_NAME = "java.lang.AssertionError";
  private static final String ASSERTION_FAILED_CLASS_NAME = "junit.framework.AssertionFailedError";

  private final String myExpected;
  private final String myActual;
  private final String myFilePath;
  private final String myActualFilePath;

  private static Map EXPECTED = new HashMap();
  private static Map ACTUAL = new HashMap();

  static {
    try {
      init(ComparisonFailure.class);
      init(org.junit.ComparisonFailure.class);
    }
    catch (Throwable e) {
    }
  }

  private static void init(Class exceptionClass) throws NoSuchFieldException {
    final Field expectedField = exceptionClass.getDeclaredField("fExpected");
    expectedField.setAccessible(true);
    EXPECTED.put(exceptionClass, expectedField);

    final Field actualField = exceptionClass.getDeclaredField("fActual");
    actualField.setAccessible(true);
    ACTUAL.put(exceptionClass, actualField);
  }

  public ComparisonFailureData(String expected, String actual) {
    this(expected, actual, null);
  }

  public ComparisonFailureData(String expected, String actual, String filePath) {
    this(expected, actual, filePath, null);
  }

  public ComparisonFailureData(String expected, String actual, String filePath, String actualFilePath) {
    myExpected = expected;
    myActual = actual;
    myFilePath = filePath;
    myActualFilePath = actualFilePath;
  }

  public static void registerSMAttributes(ComparisonFailureData notification,
                                          String trace,
                                          String failureMessage,
                                          Map attrs, 
                                          Throwable throwable) {

    final int failureIdx = failureMessage != null ? trace.indexOf(failureMessage) : -1;
    final int failureMessageLength = failureMessage != null ? failureMessage.length() : 0;
    attrs.put("details", failureIdx > -1 ? trace.substring(failureIdx + failureMessageLength) : trace);
 
    if (notification != null) {
      attrs.put("expected", notification.getExpected());
      attrs.put("actual", notification.getActual());

      final String filePath = notification.getFilePath();
      if (filePath != null) {
        attrs.put("expectedFile", filePath);
      }
      final String actualFilePath = notification.getActualFilePath();
      if (actualFilePath != null) {
        attrs.put("actualFile", actualFilePath);
      }
      final int expectedIdx = trace.indexOf("expected:<");
      final String comparisonFailureMessage;
      if (expectedIdx > 0) {
        comparisonFailureMessage = trace.substring(0, expectedIdx);
      }
      else if (failureIdx > -1) {
        comparisonFailureMessage = trace.substring(0, failureIdx + failureMessageLength);
      }
      else {
        comparisonFailureMessage = (failureMessageLength > 0 ? failureMessage + "\n" : "") + "Comparison Failure: ";
      }
      attrs.put("message", comparisonFailureMessage);
    }
    else {
      Throwable throwableCause = null;
      try {
        throwableCause = throwable.getCause();
      }
      catch (Throwable ignored) {}

      if (!isAssertionError(throwable.getClass()) && !isAssertionError(throwableCause != null ? throwableCause.getClass() : null)) {
        attrs.put("error", "true");
      }
      attrs.put("message", failureIdx > -1 ? trace.substring(0, failureIdx + failureMessageLength) 
                                           : failureMessage != null ? failureMessage : "");
    }
  }

  public static boolean isAssertionError(Class throwableClass) {
    if (throwableClass == null) return false;
    final String throwableClassName = throwableClass.getName();
    if (throwableClassName.equals(ASSERTION_CLASS_NAME) || throwableClassName.equals(ASSERTION_FAILED_CLASS_NAME)) return true;
    return isAssertionError(throwableClass.getSuperclass());
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

  public static ComparisonFailureData create(Throwable assertion) {
    if (assertion instanceof FileComparisonFailure) {
      final FileComparisonFailure comparisonFailure = (FileComparisonFailure)assertion;
      return new ComparisonFailureData(comparisonFailure.getExpected(), comparisonFailure.getActual(), 
                                       comparisonFailure.getFilePath(), comparisonFailure.getActualFilePath());
    }
    try {
      return new ComparisonFailureData(getExpected(assertion), getActual(assertion));
    }
    catch (Throwable e) {
      return null;
    }
  }

  public static String getActual(Throwable assertion) throws IllegalAccessException, NoSuchFieldException {
     return get(assertion, ACTUAL, "fActual");
   }
 
   public static String getExpected(Throwable assertion) throws IllegalAccessException, NoSuchFieldException {
     return get(assertion, EXPECTED, "fExpected");
   }
 
   private static String get(final Throwable assertion, final Map staticMap, final String fieldName) throws IllegalAccessException, NoSuchFieldException {
     String actual;
     if (assertion instanceof ComparisonFailure) {
       actual = (String)((Field)staticMap.get(ComparisonFailure.class)).get(assertion);
     }
     else if (assertion instanceof org.junit.ComparisonFailure) {
       actual = (String)((Field)staticMap.get(org.junit.ComparisonFailure.class)).get(assertion);
     }
     else {
       Field field = assertion.getClass().getDeclaredField(fieldName);
       field.setAccessible(true);
       actual = (String)field.get(assertion);
     }
     return actual;
   }
}
