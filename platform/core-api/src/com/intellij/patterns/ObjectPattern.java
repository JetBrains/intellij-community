// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.patterns;

import com.intellij.openapi.util.Key;
import com.intellij.util.PairProcessor;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class ObjectPattern<T, Self extends ObjectPattern<T, Self>> implements Cloneable, ElementPattern<T> {
  private InitialPatternCondition<T> myInitialCondition;
  private Object myConditions;

  protected ObjectPattern(final @NotNull InitialPatternCondition<T> condition) {
    myInitialCondition = condition;
    myConditions = null;
  }

  protected ObjectPattern(@NotNull Class<T> aClass) {
    this(new InitialPatternCondition<T>(aClass) {
      @Override
      public boolean accepts(final @Nullable Object o, final ProcessingContext context) {
        return aClass.isInstance(o);
      }
    });
  }

  @Override
  public final boolean accepts(@Nullable Object t) {
    return accepts(t, new ProcessingContext());
  }

  @Override
  @SuppressWarnings("unchecked")
  public boolean accepts(final @Nullable Object o, final ProcessingContext context) {
    if (!myInitialCondition.accepts(o, context)) return false;
    if (myConditions == null) return true;
    if (o == null) return false;

    if (myConditions instanceof PatternCondition) {
      return ((PatternCondition)myConditions).accepts(o, context);
    }

    List<PatternCondition<T>> list = (List<PatternCondition<T>>)myConditions;
    final int listSize = list.size();
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < listSize; i++) {
      if (!list.get(i).accepts((T)o, context)) return false;
    }
    return true;
  }

  @Override
  @SuppressWarnings("unchecked")
  public final @NotNull ElementPatternCondition<T> getCondition() {
    if (myConditions == null) {
      return new ElementPatternCondition<>(myInitialCondition);
    }
    if (myConditions instanceof PatternCondition) {
      PatternCondition<? super T> singleCondition = (PatternCondition)myConditions;
      return new ElementPatternCondition<>(myInitialCondition, Collections.singletonList(singleCondition));
    }
    return new ElementPatternCondition<>(myInitialCondition, (List)myConditions);
  }

  public @NotNull Self andNot(final ElementPattern pattern) {
    ElementPattern<T> not = StandardPatterns.not(pattern);
    return and(not);
  }

  public @NotNull Self andOr(ElementPattern @NotNull ... patterns) {
    ElementPattern or = StandardPatterns.or(patterns);
    return and(or);
  }

  public @NotNull Self and(final ElementPattern pattern) {
    return with(new PatternConditionPlus<T, T>("and", pattern) {
      @Override
      public boolean processValues(T t, ProcessingContext context, PairProcessor<? super T, ? super ProcessingContext> processor) {
        return processor.process(t, context);
      }
    });
  }

  public @NotNull Self equalTo(final @NotNull T o) {
    return with(new ValuePatternCondition<T>("equalTo") {
      @Override
      public boolean accepts(final @NotNull T t, final ProcessingContext context) {
        return t.equals(o);
      }

      @Override
      public @Unmodifiable Collection<T> getValues() {
        return Collections.singletonList(o);
      }
    });
  }

  public @NotNull Self oneOf(final T @NotNull ... values) {
    final Collection<T> list;

    final int length = values.length;
    if (length == 1) {
      list = Collections.singletonList(values[0]);
    }
    else if (length >= 11) {
      list = ContainerUtil.newHashSet(values);
    }
    else {
      list = Arrays.asList(values);
    }

    return with(new ValuePatternCondition<T>("oneOf") {

      @Override
      public @Unmodifiable Collection<T> getValues() {
        return list;
      }

      @Override
      public boolean accepts(@NotNull T t, ProcessingContext context) {
        return list.contains(t);
      }
    });
  }

  public @NotNull Self oneOf(final Collection<T> set) {
    return with(new ValuePatternCondition<T>("oneOf") {

      @Override
      public @Unmodifiable Collection<T> getValues() {
        return set;
      }

      @Override
      public boolean accepts(@NotNull T t, ProcessingContext context) {
        return set.contains(t);
      }
    });
  }

  public @NotNull Self isNull() {
    //noinspection Convert2Diamond (would break compilation: IDEA-168317)
    return adapt(new ElementPatternCondition<T>(new InitialPatternCondition(Object.class) {
      @Override
      public boolean accepts(final @Nullable Object o, final ProcessingContext context) {
        return o == null;
      }
    }));
  }

  public @NotNull Self notNull() {
    //noinspection Convert2Diamond (would break compilation: IDEA-168317)
    return adapt(new ElementPatternCondition<T>(new InitialPatternCondition(Object.class) {
      @Override
      public boolean accepts(final @Nullable Object o, final ProcessingContext context) {
        return o != null;
      }
    }));
  }

  public @NotNull Self save(@NotNull Key<? super T> key) {
    return with(new PatternCondition<T>("save") {
      @Override
      public boolean accepts(final @NotNull T t, final ProcessingContext context) {
        context.put(key, t);
        return true;
      }
    });
  }

  public @NotNull Self save(final @NonNls String key) {
    return with(new PatternCondition<T>("save") {
      @Override
      public boolean accepts(final @NotNull T t, final ProcessingContext context) {
        context.put(key, t);
        return true;
      }
    });
  }

  public @NotNull Self with(@NotNull PatternCondition<? super T> pattern) {
    final ElementPatternCondition<T> condition = getCondition().append(pattern);
    return adapt(condition);
  }

  private @NotNull Self adapt(@NotNull ElementPatternCondition<T> condition) {
    try {
      final ObjectPattern s = (ObjectPattern)clone();
      s.myInitialCondition = condition.getInitialCondition();
      List<PatternCondition<? super T>> conditions = condition.getConditions();
      s.myConditions = conditions.isEmpty() ? null : conditions.size() == 1 ? conditions.get(0) : conditions;
      //noinspection unchecked
      return (Self)s;
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  public @NotNull Self without(final PatternCondition<? super T> pattern) {
    return with(new PatternCondition<T>("without") {
      @Override
      public boolean accepts(final @NotNull T o, final ProcessingContext context) {
        return !pattern.accepts(o, context);
      }
    });
  }

  @Override
  public String toString() {
    return getCondition().toString();
  }

  public static final class Capture<T> extends ObjectPattern<T,Capture<T>> {
    public Capture(@NotNull Class<T> aClass) {
      super(aClass);
    }

    public Capture(@NotNull InitialPatternCondition<T> condition) {
      super(condition);
    }
  }
}
