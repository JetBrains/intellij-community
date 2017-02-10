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
package com.intellij.rt.execution.junit;

import junit.framework.ComparisonFailure;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * @noinspection HardCodedStringLiteral
 */
public class ComparisonDetailsExtractor  {
  private static Map EXPECTED = new HashMap();
  private static Map ACTUAL = new HashMap();
  protected String myActual = "";
  protected String myExpected = "";
  private final Throwable myAssertion;
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

  public ComparisonDetailsExtractor(Throwable assertion, String expected, String actual) {
    myAssertion = assertion;
    myActual = actual;
    myExpected = expected;
  }

  public static String getActual(Throwable assertion) throws IllegalAccessException, NoSuchFieldException {
    return get(assertion, ACTUAL, "fActual");
  }

  public static String getExpected(Throwable assertion) throws IllegalAccessException, NoSuchFieldException {
    return get(assertion, EXPECTED, "fExpected");
  }

  private static String get(final Throwable assertion, final Map staticMap, final String fieldName)
    throws IllegalAccessException, NoSuchFieldException {
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
