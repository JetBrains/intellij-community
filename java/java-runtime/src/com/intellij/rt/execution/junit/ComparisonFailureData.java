// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.rt.execution.junit;

import java.io.File;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class ComparisonFailureData {

  public static final String JUNIT_3_COMPARISON_FAILURE = "junit.framework.ComparisonFailure";
  public static final String JUNIT_4_COMPARISON_FAILURE = "org.junit.ComparisonFailure";

  private static final String ASSERTION_CLASS_NAME = "java.lang.AssertionError";
  private static final String ASSERTION_FAILED_CLASS_NAME = "junit.framework.AssertionFailedError";

  private static final String OPENTEST4J_ASSERTION = "org.opentest4j.AssertionFailedError";
  private static final String OPENTEST4J_VALUE_WRAPPER = "org.opentest4j.ValueWrapper";
  private static final String OPENTEST4J_FILE_INFO = "org.opentest4j.FileInfo";
  private static final Charset OPENTEST4J_FILE_CONTENT_CHARSET = StandardCharsets.UTF_8;

  private final String myExpected;
  private final String myActual;
  private final String myExpectedFilePath;
  private final String myActualFilePath;

  private static final Map<Class<?>, Field> EXPECTED = new HashMap<>();
  private static final Map<Class<?>, Field> ACTUAL = new HashMap<>();

  static {
    try {
      init(JUNIT_3_COMPARISON_FAILURE);
      init(JUNIT_4_COMPARISON_FAILURE);
    }
    catch (Throwable ignored) { }
  }

  private static void init(String exceptionClassName) throws NoSuchFieldException, ClassNotFoundException {
    Class<?> exceptionClass = Class.forName(exceptionClassName, false, ComparisonFailureData.class.getClassLoader());
    final Field expectedField = exceptionClass.getDeclaredField("fExpected");
    expectedField.setAccessible(true);
    EXPECTED.put(exceptionClass, expectedField);

    final Field actualField = exceptionClass.getDeclaredField("fActual");
    actualField.setAccessible(true);
    ACTUAL.put(exceptionClass, actualField);
  }

  public ComparisonFailureData(AssertionValue expected, AssertionValue actual) {
    this(expected.text, actual.text, expected.path, actual.path);
  }

  public ComparisonFailureData(String expected, String actual) {
    this(expected, actual, null);
  }

  public ComparisonFailureData(String expected, String actual, String expectedFilePath) {
    this(expected, actual, expectedFilePath, null);
  }

  public ComparisonFailureData(String expected, String actual, String expectedFilePath, String actualFilePath) {
    myExpected = expected;
    myActual = actual;
    myExpectedFilePath = expectedFilePath != null ? new File(expectedFilePath).getAbsolutePath() : null;
    myActualFilePath = actualFilePath != null ? new File(actualFilePath).getAbsolutePath() : null;
  }

  public static void registerSMAttributes(ComparisonFailureData notification,
                                          String trace,
                                          String failureMessage,
                                          Map<String, String> attrs,
                                          Throwable throwable) {
    registerSMAttributes(notification, trace, failureMessage, attrs, throwable, "Comparison Failure: ", "expected:<");
  }

  public static void registerSMAttributes(ComparisonFailureData notification, String trace,
                                          String failureMessage,
                                          Map<String, String> attrs,
                                          Throwable throwable,
                                          String comparisonFailurePrefix,
                                          final String expectedPrefix) {

    final int failureIdx = failureMessage != null ? trace.indexOf(failureMessage) : -1;
    final int failureMessageLength = failureMessage != null ? failureMessage.length() : 0;
    String details = failureIdx > -1 ? trace.substring(failureIdx + failureMessageLength) : trace;

    if (notification != null) {
      final int expectedIdx = trace.indexOf(expectedPrefix);
      final String comparisonFailureMessage;
      if (expectedIdx > 0) {
        comparisonFailureMessage = trace.substring(0, expectedIdx);
      }
      else if (failureIdx > -1) {
        comparisonFailureMessage = trace.substring(0, failureIdx + failureMessageLength);
      }
      else {
        comparisonFailureMessage = (failureMessageLength > 0 ? failureMessage + "\n" : "") + comparisonFailurePrefix;
      }
      if (!attrs.containsKey("message")) {
        attrs.put("message", comparisonFailureMessage);
      }

      final String expectedFilePath = notification.getExpectedFilePath();
      final String actualFilePath = notification.getActualFilePath();
      final String expected = notification.getExpected();
      final String actual = notification.getActual();

      int fullLength = (expectedFilePath == null && expected != null ? expected.length() : 0) +
                       (actualFilePath == null && actual != null ? actual.length() : 0) +
                       details.length() +
                       comparisonFailureMessage.length() + 100;
      if (expectedFilePath != null) {
        attrs.put("expectedFile", expectedFilePath);
      }
      else {
        writeDiffSide(attrs, "expected", expected, fullLength);
      }

      if (actualFilePath != null) {
        attrs.put("actualFile", actualFilePath);
      }
      else {
        writeDiffSide(attrs, "actual", actual, fullLength);
      }
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
    attrs.put("details", details);
  }

  private static void writeDiffSide(Map<String, String> attrs, final String expectedOrActualPrefix, final String text, int fullLength) {
    String property = System.getProperty("idea.test.cyclic.buffer.size");

    int threshold;
    try {
      threshold = Integer.parseInt(property);
    }
    catch (NumberFormatException ignored) {
      threshold = -1;
    }

    if (threshold > 0 && fullLength > threshold) {
      try {
        //noinspection SSBasedInspection
        File tempFile = File.createTempFile(expectedOrActualPrefix, "");
        try (OutputStream stream = Files.newOutputStream(tempFile.toPath())) {
          stream.write(text.getBytes(StandardCharsets.UTF_8), 0, text.length());
        }
        attrs.put(expectedOrActualPrefix + "File", tempFile.getAbsolutePath());
        attrs.put(expectedOrActualPrefix + "IsTempFile", "true");
        return;
      }
      catch (Throwable ignored) {}
    }
    attrs.put(expectedOrActualPrefix, text);
  }

  public static boolean isInstance(Class<?> aClass, String className) {
    if (aClass == null) return false;
    if (className.equals(aClass.getName())) return true;
    return isInstance(aClass.getSuperclass(), className);
  }

  public static boolean isAssertionError(Class<?> throwableClass) {
    return isInstance(throwableClass, ASSERTION_CLASS_NAME) ||
           isInstance(throwableClass, ASSERTION_FAILED_CLASS_NAME) ||
           isInstance(throwableClass, OPENTEST4J_ASSERTION);
  }

  public static boolean isComparisonFailure(Class<?> aClass) {
    return isInstance(aClass, JUNIT_3_COMPARISON_FAILURE) ||
           isInstance(aClass, JUNIT_4_COMPARISON_FAILURE) ||
           isInstance(aClass, OPENTEST4J_ASSERTION);
  }

  public String getExpectedFilePath() {
    return myExpectedFilePath;
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
    ComparisonFailureData failureData;

    failureData = createFileComparisonData(assertion);
    if (failureData != null) return failureData;

    failureData = createJunitComparisonFailure(assertion);
    if (failureData != null) return failureData;

    failureData = createOpentest4jAssertion(assertion);
    return failureData;
  }

  private static ComparisonFailureData createFileComparisonData(Throwable assertion) {
    if (assertion instanceof FileComparisonData) {
      final FileComparisonData comparisonFailure = (FileComparisonData)assertion;
      String actual = comparisonFailure.getActualStringPresentation();
      String expected = comparisonFailure.getExpectedStringPresentation();
      if (actual != null && expected != null) {
        return new ComparisonFailureData(expected, actual, comparisonFailure.getFilePath(), comparisonFailure.getActualFilePath());
      }
    }
    return null;
  }

  private static ComparisonFailureData createOpentest4jAssertion(Throwable assertion) {
    try {
      if (isInstance(assertion.getClass(), OPENTEST4J_ASSERTION)) {
        Method isExpectedDefinedMethod = assertion.getClass().getMethod("isExpectedDefined");
        Method isActualDefinedMethod = assertion.getClass().getMethod("isActualDefined");
        boolean isExpectedDefined = ((Boolean)isExpectedDefinedMethod.invoke(assertion)).booleanValue();
        boolean isActualDefined = ((Boolean)isActualDefinedMethod.invoke(assertion)).booleanValue();
        if (isExpectedDefined && isActualDefined) {
          Method expectedMethod = assertion.getClass().getMethod("getExpected");
          Method actualMethod = assertion.getClass().getMethod("getActual");
          Object expectedValueWrapper = expectedMethod.invoke(assertion);
          Object actualValueWrapper = actualMethod.invoke(assertion);
          AssertionValue expected = getOpentest4jAssertionValue(expectedValueWrapper);
          AssertionValue actual = getOpentest4jAssertionValue(actualValueWrapper);
          return new ComparisonFailureData(expected, actual);
        }
      }
    }
    catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException ignored) {
    }
    return null;
  }

  private static AssertionValue getOpentest4jAssertionValue(Object valueWrapper)
    throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {

    if (isInstance(valueWrapper.getClass(), OPENTEST4J_VALUE_WRAPPER)) {
      Method valueMethod = valueWrapper.getClass().getMethod("getValue");
      Object value = valueMethod.invoke(valueWrapper);
      if (value != null && isInstance(value.getClass(), OPENTEST4J_FILE_INFO)) {
        Method contentAsStringMethod = value.getClass().getMethod("getContentsAsString", Charset.class);
        String valueString = (String)contentAsStringMethod.invoke(value, OPENTEST4J_FILE_CONTENT_CHARSET);
        Method pathMethod = value.getClass().getMethod("getPath");
        String valuePath = (String)pathMethod.invoke(value);
        return new AssertionValue(valueString, valuePath);
      }
      else {
        Method stringRepresentationMethod = valueWrapper.getClass().getMethod("getStringRepresentation");
        String valueString = (String)stringRepresentationMethod.invoke(valueWrapper);
        return new AssertionValue(valueString, null);
      }
    }
    return null;
  }

  private static ComparisonFailureData createJunitComparisonFailure(Throwable assertion) {
    try {
      String expected = getExpected(assertion);
      String actual = getActual(assertion);
      return new ComparisonFailureData(expected, actual);
    }
    catch (IllegalAccessException | NoSuchFieldException e) {
      return null;
    }
  }

  public static String getActual(Throwable assertion) throws IllegalAccessException, NoSuchFieldException {
    return get(assertion, ACTUAL, "fActual");
  }

  public static String getExpected(Throwable assertion) throws IllegalAccessException, NoSuchFieldException {
    return get(assertion, EXPECTED, "fExpected");
  }

  private static String get(final Throwable assertion, final Map<Class<?>, Field> staticMap, final String fieldName) throws IllegalAccessException, NoSuchFieldException {
    Class<? extends Throwable> assertionClass = assertion.getClass();
    for (Class<?> comparisonClass : staticMap.keySet()) {
      if (comparisonClass.isAssignableFrom(assertionClass)) {
        return (String)staticMap.get(comparisonClass).get(assertion);
      }
    }

    Field field = assertionClass.getDeclaredField(fieldName);
    field.setAccessible(true);
    return (String)field.get(assertion);
  }

  public static class AssertionValue {

    public final String text;
    public final String path;

    public AssertionValue(String text, String path) {
      this.text = text;
      this.path = path;
    }
  }
}
