/*
 * Copyright 2000-2006 JetBrains s.r.o.
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
 *
 */

package com.intellij.openapi.fileTypes;

import com.intellij.util.PatternUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;

/**
 * @author max
 */
public class WildcardFileNameMatcher implements FileNameMatcher {
  private final String myPattern;
  private final Matcher myMatcher;

  public WildcardFileNameMatcher(@NotNull @NonNls String pattern) {
    myPattern = pattern;
    myMatcher = PatternUtil.fromMask(pattern).matcher("");
  }

  public boolean accept(String fileName) {
    synchronized (myMatcher) {
      myMatcher.reset(fileName);
      return myMatcher.matches();
    }
  }

  @NonNls
  @NotNull
  public String getPresentableString() {
    return myPattern;
  }


  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final WildcardFileNameMatcher that = (WildcardFileNameMatcher)o;

    if (!myPattern.equals(that.myPattern)) return false;

    return true;
  }

  public int hashCode() {
    return myPattern.hashCode();
  }

  public String getPattern() {
    return myPattern;
  }
}
