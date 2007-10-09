/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.history;

public class VcsHistoryUtil {
  public static int compare(VcsFileRevision first, VcsFileRevision second) {
    if (first instanceof CurrentRevision && second instanceof CurrentRevision) {
      return compareNumbers(first, second);
    }
    if (second instanceof CurrentRevision) return -1 * compare(second, first);

    if (first instanceof CurrentRevision) {
      int result = compareNumbers(first, second);
      if (result == 0) {
        return 1;
      }
      else {
        return result;
      }
    }
    else {
      return compareNumbers(first, second);
    }
  }

  private static int compareNumbers(VcsFileRevision first, VcsFileRevision second) {
    return first.getRevisionNumber().compareTo(second.getRevisionNumber());
  }
}
