// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.patterns;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Provides patterns for strings, e.g. regex matching, length conditions and member of collection checks.
 * <p>
 * Please see the <a href="https://plugins.jetbrains.com/docs/intellij/element-patterns.html">IntelliJ Platform Docs</a>
 * for a high-level overview.
 *
 * @see StandardPatterns#string()
 */
public final class StringPattern extends ObjectPattern<String, StringPattern> {
  static final StringPattern STRING_PATTERN = new StringPattern();

  private StringPattern() {
    super(new InitialPatternCondition<String>(String.class) {
      @Override
      public boolean accepts(final @Nullable Object o, final ProcessingContext context) {
        return o instanceof String;
      }


      @Override
      public void append(final @NotNull @NonNls StringBuilder builder, final String indent) {
        builder.append("string()");
      }
    });
  }

  public @NotNull StringPattern startsWith(final @NonNls @NotNull String s) {
    return with(new StartsWithCondition(s));
  }

  public @NotNull StringPattern endsWith(final @NonNls @NotNull String s) {
    return with(new EndsWithCondition(s));
  }

  public @NotNull StringPattern contains(final @NonNls @NotNull String s) {
    return with(new ContainsCondition(s));
  }

  public @NotNull StringPattern containsChars(final @NonNls @NotNull String s) {
    return with(new ContainsCharsCondition(s));
  }

  public @NotNull StringPattern matches(final @NonNls @NotNull String s) {
    final String escaped = StringUtil.escapeToRegexp(s);
    if (escaped.equals(s)) {
      return equalTo(s);
    }
    return with(new MatchesCondition(s));
  }

  public @NotNull StringPattern contains(final @NonNls @NotNull ElementPattern<Character> pattern) {
    return with(new PatternCondition<String>("contains") {
      @Override
      public boolean accepts(final @NotNull String str, final ProcessingContext context) {
        for (int i = 0; i < str.length(); i++) {
          if (pattern.accepts(str.charAt(i))) return true;
        }
        return false;
      }
    });
  }

  public StringPattern longerThan(final int minLength) {
    return with(new LongerThanCondition(minLength));
  }

  public StringPattern shorterThan(final int maxLength) {
    return with(new ShorterThanCondition(maxLength));
  }

  public StringPattern withLength(final int length) {
    return with(new WithLengthCondition(length));
  }

  /**
   * Creates a pattern that matches strings ending with an uppercase letter.
   */
  public @NotNull StringPattern endsWithUppercaseLetter() {
    return with(new EndsWithUppercaseLetterCondition());
  }

  /**
   * Creates a pattern that matches strings where the second-to-last character is not a Java identifier part.
   * This is useful for detecting when the user types after a non-identifier character (e.g., space, punctuation).
   * The string must be at least 2 characters long.
   */
  public @NotNull StringPattern afterNonJavaIdentifierPart() {
    return with(new AfterNonJavaIdentifierPartCondition());
  }

  @Override
  public @NotNull StringPattern oneOf(final @NonNls String @NotNull ... values) {
    return super.oneOf(values);
  }

  public @NotNull StringPattern oneOfIgnoreCase(final @NonNls String... values) {
    return with(new CaseInsensitiveValuePatternCondition("oneOfIgnoreCase", values));
  }

  @Override
  public @NotNull StringPattern oneOf(final @NonNls Collection<String> set) {
    return super.oneOf(set);
  }

  public @NotNull StringPattern endsWithOneOf(@NotNull Iterable<String> values) {
    List<StringPattern> patternList = ContainerUtil.map(values, value -> StandardPatterns.string().endsWith(value));
    StringPattern[] patterns = patternList.toArray(new StringPattern[0]);
    return and(StandardPatterns.or(patterns));
  }

  public static @NotNull CharSequence newBombedCharSequence(@NotNull CharSequence sequence) {
    if (sequence instanceof StringUtil.BombedCharSequence) return sequence;
    return new StringUtil.BombedCharSequence(sequence) {
      @Override
      protected void checkCanceled() {
        ProgressManager.checkCanceled();
      }
    };
  }

  // Named condition classes for serialization support

  /**
   * Condition that checks if a string starts with a given prefix.
   */
  @ApiStatus.Internal
  public static final class StartsWithCondition extends PatternCondition<String> {
    private final String prefix;

    public StartsWithCondition(@NotNull String prefix) {
      super("startsWith");
      this.prefix = prefix;
    }

    public @NotNull String getPrefix() {
      return prefix;
    }

    @Override
    public boolean accepts(@NotNull String str, ProcessingContext context) {
      return StringUtil.startsWith(str, prefix);
    }
  }

  /**
   * Condition that checks if a string ends with a given suffix.
   */
  @ApiStatus.Internal
  public static final class EndsWithCondition extends PatternCondition<String> {
    private final String suffix;

    public EndsWithCondition(@NotNull String suffix) {
      super("endsWith");
      this.suffix = suffix;
    }

    public @NotNull String getSuffix() {
      return suffix;
    }

    @Override
    public boolean accepts(@NotNull String str, ProcessingContext context) {
      return StringUtil.endsWith(str, suffix);
    }
  }

  /**
   * Condition that checks if a string contains a given substring.
   */
  @ApiStatus.Internal
  public static final class ContainsCondition extends PatternCondition<String> {
    private final String substring;

    public ContainsCondition(@NotNull String substring) {
      super("contains");
      this.substring = substring;
    }

    public @NotNull String getSubstring() {
      return substring;
    }

    @Override
    public boolean accepts(@NotNull String str, ProcessingContext context) {
      return StringUtil.contains(str, substring);
    }
  }

  /**
   * Condition that checks if a string contains any character from a given set.
   */
  @ApiStatus.Internal
  public static final class ContainsCharsCondition extends PatternCondition<String> {
    private final String chars;

    public ContainsCharsCondition(@NotNull String chars) {
      super("containsChars");
      this.chars = chars;
    }

    public @NotNull String getChars() {
      return chars;
    }

    @Override
    public boolean accepts(@NotNull String str, ProcessingContext context) {
      return StringUtil.containsAnyChar(str, chars);
    }
  }

  /**
   * Condition that checks if a string matches a given regex pattern.
   */
  @ApiStatus.Internal
  public static final class MatchesCondition extends ValuePatternCondition<String> {
    private final String regex;
    private final Pattern pattern;

    public MatchesCondition(@NotNull String regex) {
      super("matches");
      this.regex = regex;
      this.pattern = Pattern.compile(regex);
    }

    public @NotNull String getRegex() {
      return regex;
    }

    @Override
    public boolean accepts(@NotNull String str, ProcessingContext context) {
      return pattern.matcher(newBombedCharSequence(str)).matches();
    }

    @Override
    public @Unmodifiable Collection<String> getValues() {
      return Collections.singleton(regex);
    }
  }

  /**
   * Condition that checks if a string is longer than a given length.
   */
  @ApiStatus.Internal
  public static final class LongerThanCondition extends PatternCondition<String> {
    private final int minLength;

    public LongerThanCondition(int minLength) {
      super("longerThan");
      this.minLength = minLength;
    }

    public int getMinLength() {
      return minLength;
    }

    @Override
    public boolean accepts(@NotNull String s, ProcessingContext context) {
      return s.length() > minLength;
    }
  }

  /**
   * Condition that checks if a string is shorter than a given length.
   */
  @ApiStatus.Internal
  public static final class ShorterThanCondition extends PatternCondition<String> {
    private final int maxLength;

    public ShorterThanCondition(int maxLength) {
      super("shorterThan");
      this.maxLength = maxLength;
    }

    public int getMaxLength() {
      return maxLength;
    }

    @Override
    public boolean accepts(@NotNull String s, ProcessingContext context) {
      return s.length() < maxLength;
    }
  }

  /**
   * Condition that checks if a string has exactly a given length.
   */
  @ApiStatus.Internal
  public static final class WithLengthCondition extends PatternCondition<String> {
    private final int length;

    public WithLengthCondition(int length) {
      super("withLength");
      this.length = length;
    }

    public int getLength() {
      return length;
    }

    @Override
    public boolean accepts(@NotNull String s, ProcessingContext context) {
      return s.length() == length;
    }
  }

  /**
   * Condition that checks if a string ends with an uppercase letter.
   */
  @ApiStatus.Internal
  public static final class EndsWithUppercaseLetterCondition extends PatternCondition<String> {
    public EndsWithUppercaseLetterCondition() {
      super("endsWithUppercaseLetter");
    }

    @Override
    public boolean accepts(@NotNull String s, ProcessingContext context) {
      return !s.isEmpty() && Character.isUpperCase(s.charAt(s.length() - 1));
    }
  }

  /**
   * Condition that checks if the second-to-last character is not a Java identifier part.
   * The string must be at least 2 characters long.
   */
  @ApiStatus.Internal
  public static final class AfterNonJavaIdentifierPartCondition extends PatternCondition<String> {
    public AfterNonJavaIdentifierPartCondition() {
      super("afterNonJavaIdentifierPart");
    }

    @Override
    public boolean accepts(@NotNull String s, ProcessingContext context) {
      return s.length() > 1 && !Character.isJavaIdentifierPart(s.charAt(s.length() - 2));
    }
  }
}
