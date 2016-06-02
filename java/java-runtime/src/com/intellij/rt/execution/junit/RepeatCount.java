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

public abstract class RepeatCount {
  public static final String ONCE = "Once";
  public static final String N = "N Times";
  public static final String UNTIL_FAILURE = "Until Failure";
  public static final String UNLIMITED = "Until Stopped";
  public static final String[] REPEAT_TYPES = new String[]{ONCE, N, UNTIL_FAILURE, UNLIMITED};

  public static String getCountString(int count) {
    if (count > 1) {
      return "@" + N + count;
    }
    if (count == -1) {
      return UNLIMITED;
    }
    if (count == -2) {
      return UNTIL_FAILURE;
    }
    return ONCE;
  }

  public static int getCount(String countString) {
    if (countString.equals(ONCE)) {
      return 1;
    }

    if (countString.equals(UNLIMITED)) {
      return -1;
    }

    if (countString.equals(UNTIL_FAILURE)) {
      return -2;
    }

    final String prefix = "@" + N;
    if (countString.startsWith(prefix)) {
      try {
        return Integer.parseInt(countString.substring(prefix.length()));
      }
      catch (NumberFormatException ignore) {}
    }

    return 0;
  }
}
