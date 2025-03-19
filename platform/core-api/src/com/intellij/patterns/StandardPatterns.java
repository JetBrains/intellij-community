// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.patterns;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Factory for {@link String}, {@link Character} and {@link Object}-based patterns. Provides methods for composing patterns
 * with e.g. logical operations.
 * <p>
 * Please see the <a href="https://plugins.jetbrains.com/docs/intellij/element-patterns.html">IntelliJ Platform Docs</a>
 * for a high-level overview.
 */
@SuppressWarnings("unchecked")
public class StandardPatterns {

  private static final FalsePattern FALSE_PATTERN = new FalsePattern();

  public static @NotNull StringPattern string() {
    return StringPattern.STRING_PATTERN;
  }

  public static @NotNull CharPattern character() {
    return new CharPattern();
  }

  public static @NotNull <T> ObjectPattern.Capture<T> instanceOf(@NotNull Class<T> aClass) {
    return new ObjectPattern.Capture<>(aClass);
  }

  @SafeVarargs
  public static @NotNull <T> ElementPattern<T> instanceOf(Class<T> @NotNull ... classes) {
    ElementPattern[] patterns = ContainerUtil.map(classes, StandardPatterns::instanceOf, new ElementPattern[0]);
    return or(patterns);
  }

  public static @NotNull <T> ElementPattern save(final Key<T> key) {
    return new ObjectPattern.Capture<>(new InitialPatternCondition(Object.class) {
      @Override
      public boolean accepts(final @Nullable Object o, final ProcessingContext context) {
        context.put(key, (T)o);
        return true;
      }

      @Override
      public void append(final @NotNull @NonNls StringBuilder builder, final String indent) {
        builder.append("save(").append(key).append(")");
      }
    });
  }

  public static @NotNull ObjectPattern.Capture<Object> object() {
    return instanceOf(Object.class);
  }

  public static @NotNull <T> ObjectPattern.Capture<T> object(@NotNull T value) {
    return instanceOf((Class<T>)value.getClass()).equalTo(value);
  }

  public static @NotNull <T> CollectionPattern<T> collection(Class<T> aClass) {
    return new CollectionPattern<>();
  }

  public static @NotNull ElementPattern get(final @NotNull @NonNls String key) {
    return new ObjectPattern.Capture(new InitialPatternCondition(Object.class) {
      @Override
      public boolean accepts(final @Nullable Object o, final ProcessingContext context) {
        return Comparing.equal(o, context.get(key));
      }

      @Override
      public void append(final @NotNull @NonNls StringBuilder builder, final String indent) {
        builder.append("get(").append(key).append(")");
      }
    });
  }

  public static @NotNull <T> CollectionPattern<T> collection() {
    return new CollectionPattern<>();
  }

  @SafeVarargs
  public static @NotNull <E> ElementPattern<E> or(final ElementPattern<? extends E> @NotNull ... patterns) {
    return new ObjectPattern.Capture<>(new InitialPatternConditionPlus(Object.class) {
      @Override
      public boolean accepts(final @Nullable Object o, final ProcessingContext context) {
        for (ElementPattern<?> pattern : patterns) {
          if (pattern.accepts(o, context)) {
            return true;
          }
        }
        return false;
      }

      @Override
      public void append(final @NotNull @NonNls StringBuilder builder, final String indent) {
        boolean first = true;
        for (ElementPattern<?> pattern : patterns) {
          if (!first) {
            builder.append("\n").append(indent);
          }
          first = false;
          pattern.getCondition().append(builder, indent + "  ");
        }
      }

      @Override
      public List<ElementPattern<?>> getPatterns() {
        return Arrays.asList(patterns);
      }
    });
  }

  @SafeVarargs
  public static @NotNull <E> ElementPattern<E> and(final ElementPattern<? extends E>... patterns) {
    final List<InitialPatternCondition> initial = new SmartList<>();
    for (ElementPattern<?> pattern : patterns) {
      initial.add(pattern.getCondition().getInitialCondition());
    }
    ObjectPattern.Capture<E> result = composeInitialConditions(initial);
    for (ElementPattern pattern : patterns) {
      for (PatternCondition<?> condition : (List<PatternCondition<?>>)pattern.getCondition().getConditions()) {
        result = result.with((PatternCondition<? super E>)condition);
      }
    }
    return result;
  }

  private static @NotNull <E> ObjectPattern.Capture<E> composeInitialConditions(final List<? extends InitialPatternCondition> initial) {
    return new ObjectPattern.Capture<>(new InitialPatternCondition(Object.class) {
      @Override
      public boolean accepts(final @Nullable Object o, final ProcessingContext context) {
        for (final InitialPatternCondition pattern : initial) {
          if (!pattern.accepts(o, context)) return false;
        }
        return true;
      }

      @Override
      public void append(final @NotNull @NonNls StringBuilder builder, final String indent) {
        boolean first = true;
        for (final InitialPatternCondition pattern : initial) {
          if (!first) {
            builder.append("\n").append(indent);
          }
          first = false;
          pattern.append(builder, indent + "  ");
        }
      }
    });
  }

  public static @NotNull <E> ObjectPattern.Capture<E> not(final ElementPattern<E> pattern) {
    return new ObjectPattern.Capture<>(new InitialPatternConditionPlus(Object.class) {
      @Override
      public boolean accepts(final @Nullable Object o, final ProcessingContext context) {
        return !pattern.accepts(o, context);
      }

      @Override
      public void append(final @NotNull @NonNls StringBuilder builder, final String indent) {
        pattern.getCondition().append(builder.append("not("), indent + "  ");
        builder.append(")");
      }

      @Override
      public List<ElementPattern<?>> getPatterns() {
        return Collections.singletonList(pattern);
      }
    });
  }

  public static @NotNull <T> ObjectPattern.Capture<T> optional(final ElementPattern<T> pattern) {
    return new ObjectPattern.Capture<>(new InitialPatternCondition(Object.class) {
      @Override
      public boolean accepts(final @Nullable Object o, final ProcessingContext context) {
        pattern.accepts(o, context);
        return true;
      }
    });
  }


  public static @NotNull <E> ElementPattern<E> alwaysFalse() {
    return FALSE_PATTERN;
  }

  private static final class FalsePattern implements ElementPattern {
    @Override
    public boolean accepts(@Nullable Object o) {
      return false;
    }

    @Override
    public boolean accepts(@Nullable Object o, ProcessingContext context) {
      return false;
    }

    @Override
    public ElementPatternCondition getCondition() {
      return new ElementPatternCondition(new InitialPatternCondition(Object.class) {
        @Override
        public boolean accepts(@Nullable Object o, ProcessingContext context) {
          return false;
        }
      });
    }
  }
}
