// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.rt.execution.testFrameworks;

import com.intellij.rt.execution.junit.ComparisonFailureData;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AbstractExpectedPatterns {

  private static final Pattern ASSERT_EQUALS_PATTERN = Pattern.compile("expected:<(.*)> but was:<(.*)>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
  private static final Pattern ASSERT_EQUALS_CHAINED_PATTERN = Pattern.compile("but was:<(.*)>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

  /**
   * System property to specify the maximum threshold for expected patterns.
   * When the message length exceeds this threshold, we won't parse the message because running regex over such messages will be very slow.
   */
  public static final String MESSAGE_LENGTH_THRESHOLD_PROPERTY = "idea.expected.message.length.threshold";

  public static final int DEFAULT_MESSAGE_LENGTH_THRESHOLD = 10_000;

  protected static void registerPatterns(String[] patternStrings, List<Pattern> patterns) {
    for (String string : patternStrings) {
      patterns.add(Pattern.compile(string, Pattern.DOTALL | Pattern.CASE_INSENSITIVE));
    }
  }

  protected static ComparisonFailureData createExceptionNotification(String message, List<Pattern> patterns) {
    if (exceedsMessageThreshold(message)) return null;
    ComparisonFailureData assertEqualsNotification = createExceptionNotification(message, ASSERT_EQUALS_PATTERN);
    if (assertEqualsNotification != null) {
      return ASSERT_EQUALS_CHAINED_PATTERN.matcher(assertEqualsNotification.getExpected()).find() ? null : assertEqualsNotification;
    }

    for (Pattern pattern : patterns) {
      ComparisonFailureData notification = createExceptionNotification(message, pattern);
      if (notification != null) {
        return notification;
      }
    }
    return null;
  }

  protected static ComparisonFailureData createExceptionNotification(String message, Pattern pattern) {
    if (exceedsMessageThreshold(message)) return null;
    final Matcher matcher = pattern.matcher(message);
    if (matcher.find() && matcher.end() == message.length()) {
      return new ComparisonFailureData(matcher.group(1).replaceAll("\\\\n", "\n"), 
                                       matcher.group(2).replaceAll("\\\\n", "\n"));
    }
    return null;
  }

  /**
   * @return whether the size of the message is too big to parse.
   */
  protected static boolean exceedsMessageThreshold(String message) {
    return message.length() > getMessageThreshold();
  }

  private static int getMessageThreshold() {
    int threshold = DEFAULT_MESSAGE_LENGTH_THRESHOLD;
    try {
      String property = System.getProperty(MESSAGE_LENGTH_THRESHOLD_PROPERTY);
      if (property == null) property = System.getProperty("idea.junit.message.length.threshold"); // legacy property that was used for JUnit
      if (property != null) {
        try {
          threshold = Integer.parseInt(property);
        }
        catch (NumberFormatException ignore) {
        }
      }
      return threshold;
    }
    catch (SecurityException ignore) {
    }
    return threshold;
  }
}
