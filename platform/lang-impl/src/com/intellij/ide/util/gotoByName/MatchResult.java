/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.util.gotoByName;

import org.jetbrains.annotations.NotNull;

public class MatchResult implements Comparable<MatchResult> {
  public final String elementName;
  final int matchingDegree;
  final boolean startMatch;

  public MatchResult(String elementName, int matchingDegree, boolean startMatch) {
    this.elementName = elementName;
    this.matchingDegree = matchingDegree;
    this.startMatch = startMatch;
  }

  @Override
  public int compareTo(@NotNull MatchResult that) {
    boolean start1 = startMatch;
    boolean start2 = that.startMatch;
    if (start1 != start2) return start1 ? -1 : 1;

    int degree1 = matchingDegree;
    int degree2 = that.matchingDegree;
    if (degree2 < degree1) return -1;
    if (degree2 > degree1) return 1;

    return elementName.compareToIgnoreCase(that.elementName);
  }
}
