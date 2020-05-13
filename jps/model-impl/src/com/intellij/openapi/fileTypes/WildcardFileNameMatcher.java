// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.fileTypes;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PatternUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;

public class WildcardFileNameMatcher implements FileNameMatcher {
  private final String myPattern;
  private final MaskMatcher myMatcher;

  private interface MaskMatcher {
    boolean matches(CharSequence filename);
  }

  private static final class RegexpMatcher implements MaskMatcher {
    private final Matcher myMatcher;

    private RegexpMatcher(String pattern) {
      myMatcher = PatternUtil.fromMask(pattern).matcher("");
    }

    @Override
    public boolean matches(final CharSequence filename) {
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

    @Override
    public boolean matches(final CharSequence filename) {
      return StringUtil.endsWith(filename, mySuffix);
    }
  }

  private static final class PrefixMatcher implements MaskMatcher {
    private final String myPrefix;

    private PrefixMatcher(final String prefix) {
      myPrefix = prefix;
    }

    @Override
    public boolean matches(final CharSequence filename) {
      return StringUtil.startsWith(filename, myPrefix);
    }
  }

  private static final class InfixMatcher implements MaskMatcher {
    private final String myInfix;

    private InfixMatcher(final String infix) {
      myInfix = infix;
    }

    @Override
    public boolean matches(final CharSequence filename) {
      return StringUtil.contains(filename, myInfix);
    }
  }

  /**
   * Use {@link org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory#createMatcher(String)} instead of direct call to constructor
   */
  public WildcardFileNameMatcher(@NotNull @NonNls String pattern) {
    myPattern = pattern;
    myMatcher = createMatcher(pattern);
  }

  private static MaskMatcher createMatcher(final String pattern) {
    int len = pattern.length();
    if (len > 1 && pattern.indexOf('?') < 0) {
      if (pattern.charAt(0) == '*' && pattern.indexOf('*', 1) < 0) {
        return new SuffixMatcher(pattern.substring(1));
      }
      if (pattern.indexOf('*') == len - 1) {
        return new PrefixMatcher(pattern.substring(0, len - 1));
      }
      if (len > 2 && pattern.charAt(0) == '*' && pattern.indexOf('*', 1) == len - 1) {
        return new InfixMatcher(pattern.substring(1, len - 1));
      }
    }
    return new RegexpMatcher(pattern);
  }

  @Override
  public boolean acceptsCharSequence(@NonNls @NotNull CharSequence fileName) {
    return myMatcher.matches(fileName);
  }

  @Override
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
