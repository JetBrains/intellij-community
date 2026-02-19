// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.util.text.Strings;
import com.intellij.util.PatternUtil;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

public class WildcardFileNameMatcher implements FileNameMatcher {
  private final String myPattern;
  private final MaskMatcher myMatcher;

  private interface MaskMatcher {
    boolean matches(@NotNull CharSequence filename);
  }

  private static final class RegexpMatcher implements MaskMatcher {
    private final Pattern pattern;

    RegexpMatcher(@NotNull String pattern) {
      this.pattern = PatternUtil.fromMask(pattern);
    }

    @Override
    public boolean matches(final @NotNull CharSequence filename) {
      return pattern.matcher(filename).matches();
    }
  }

  private static final class SuffixMatcher implements MaskMatcher {
    private final String mySuffix;

    SuffixMatcher(@NotNull String suffix) {
      mySuffix = suffix;
    }

    @Override
    public boolean matches(final @NotNull CharSequence filename) {
      return Strings.endsWith(filename, mySuffix);
    }
  }

  private static final class PrefixMatcher implements MaskMatcher {
    private final String myPrefix;

    private PrefixMatcher(@NotNull String prefix) {
      myPrefix = prefix;
    }

    @Override
    public boolean matches(final @NotNull CharSequence filename) {
      return Strings.startsWith(filename, 0, myPrefix);
    }
  }

  private static final class InfixMatcher implements MaskMatcher {
    private final String myInfix;

    InfixMatcher(@NotNull String infix) {
      myInfix = infix;
    }

    @Override
    public boolean matches(final @NotNull CharSequence filename) {
      return Strings.contains(filename, myInfix);
    }
  }

  /**
   * Use {@link org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory#createMatcher(String)} instead of direct call to constructor
   */
  public WildcardFileNameMatcher(@NotNull String pattern) {
    myPattern = pattern;
    myMatcher = createMatcher(pattern);
  }

  private static @NotNull MaskMatcher createMatcher(final @NotNull String pattern) {
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
  public boolean acceptsCharSequence(@NotNull CharSequence fileName) {
    return myMatcher.matches(fileName);
  }

  @Override
  public @NotNull String getPresentableString() {
    return myPattern;
  }


  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final WildcardFileNameMatcher that = (WildcardFileNameMatcher)o;

    return myPattern.equals(that.myPattern);
  }

  @Override
  public int hashCode() {
    return myPattern.hashCode();
  }

  public @NotNull String getPattern() {
    return myPattern;
  }

  @Override
  public String toString() {
    return myPattern;
  }
}
