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
  private final MaskMatcher myMatcher;

  private interface MaskMatcher {
    boolean matches(String filename);
  }

  private static final class RegexpMatcher implements MaskMatcher {
    private final Matcher myMatcher;

    private RegexpMatcher(String pattern) {
      myMatcher = PatternUtil.fromMask(pattern).matcher("");
    }

    public boolean matches(final String filename) {
      synchronized (myMatcher) {
        myMatcher.reset(filename);
        return myMatcher.matches();
      }
    }
  }

  private static final class SuffixMatcher implements MaskMatcher {
    private final String mySuffix;

    private SuffixMatcher(final String suffix) {
      mySuffix = suffix;
    }

    public boolean matches(final String filename) {
      return filename.endsWith(mySuffix);
    }
  }

  public WildcardFileNameMatcher(@NotNull @NonNls String pattern) {
    myPattern = pattern;
    myMatcher = createMatcher(pattern);
  }

  private static MaskMatcher createMatcher(final String pattern) {
    if (pattern.length() > 1 && pattern.charAt(0) == '*' && pattern.indexOf('*', 1) < 0 && pattern.indexOf('?') < 0) {
      return new SuffixMatcher(pattern.substring(1));
    }
    return new RegexpMatcher(pattern);
  }

  public boolean accept(@NotNull String fileName) {
    return myMatcher.matches(fileName);
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
