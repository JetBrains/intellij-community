// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.patterns;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Collections;
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
    return with(new PatternCondition<String>("startsWith") {
      @Override
      public boolean accepts(final @NotNull String str, final ProcessingContext context) {
        return StringUtil.startsWith(str, s);
      }
    });
  }

  public @NotNull StringPattern endsWith(final @NonNls @NotNull String s) {
    return with(new PatternCondition<String>("endsWith") {
      @Override
      public boolean accepts(final @NotNull String str, final ProcessingContext context) {
        return StringUtil.endsWith(str, s);
      }
    });
  }

  public @NotNull StringPattern contains(final @NonNls @NotNull String s) {
    return with(new PatternCondition<String>("contains") {
      @Override
      public boolean accepts(final @NotNull String str, final ProcessingContext context) {
        return StringUtil.contains(str, s);
      }
    });
  }

  public @NotNull StringPattern containsChars(final @NonNls @NotNull String s) {
    return with(new PatternCondition<String>("containsChars") {
      @Override
      public boolean accepts(final @NotNull String str, final ProcessingContext context) {
        return StringUtil.containsAnyChar(str, s);
      }
    });
  }

  public @NotNull StringPattern matches(final @NonNls @NotNull String s) {
    final String escaped = StringUtil.escapeToRegexp(s);
    if (escaped.equals(s)) {
      return equalTo(s);
    }
    // may throw PatternSyntaxException here
    final Pattern pattern = Pattern.compile(s);
    return with(new ValuePatternCondition<String>("matches") {
      @Override
      public boolean accepts(final @NotNull String str, final ProcessingContext context) {
        return pattern.matcher(newBombedCharSequence(str)).matches();
      }

      @Override
      public @Unmodifiable Collection<String> getValues() {
        return Collections.singleton(s);
      }
    });
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
    return with(new PatternCondition<String>("longerThan") {
      @Override
      public boolean accepts(final @NotNull String s, final ProcessingContext context) {
        return s.length() > minLength;
      }
    });
  }

  public StringPattern shorterThan(final int maxLength) {
    return with(new PatternCondition<String>("shorterThan") {
      @Override
      public boolean accepts(final @NotNull String s, final ProcessingContext context) {
        return s.length() < maxLength;
      }
    });
  }

  public StringPattern withLength(final int length) {
    return with(new PatternCondition<String>("withLength") {
      @Override
      public boolean accepts(final @NotNull String s, final ProcessingContext context) {
        return s.length() == length;
      }
    });
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

  public static @NotNull CharSequence newBombedCharSequence(@NotNull CharSequence sequence) {
    if (sequence instanceof StringUtil.BombedCharSequence) return sequence;
    return new StringUtil.BombedCharSequence(sequence) {
      @Override
      protected void checkCanceled() {
        ProgressManager.checkCanceled();
      }
    };
  }
}
