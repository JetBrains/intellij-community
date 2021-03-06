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
package com.intellij.rt.execution.testFrameworks;

import com.intellij.rt.execution.junit.ComparisonFailureData;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AbstractExpectedPatterns {

  private static final Pattern ASSERT_EQUALS_PATTERN = Pattern.compile("expected:<(.*)> but was:<(.*)>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
  private static final Pattern ASSERT_EQUALS_CHAINED_PATTERN = Pattern.compile("but was:<(.*)>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

  protected static void registerPatterns(String[] patternStrings, List<Pattern> patterns) {
    for (String string : patternStrings) {
      patterns.add(Pattern.compile(string, Pattern.DOTALL | Pattern.CASE_INSENSITIVE));
    }
  }

  protected static ComparisonFailureData createExceptionNotification(String message, List<Pattern> patterns) {
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
    final Matcher matcher = pattern.matcher(message);
    if (matcher.find() && matcher.end() == message.length()) {
      return new ComparisonFailureData(matcher.group(1).replaceAll("\\\\n", "\n"), 
                                       matcher.group(2).replaceAll("\\\\n", "\n"));
    }
    return null;
  }
}
