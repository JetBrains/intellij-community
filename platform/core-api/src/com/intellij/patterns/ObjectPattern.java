/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.patterns;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.util.InstanceofCheckerGenerator;
import com.intellij.util.PairProcessor;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
public abstract class ObjectPattern<T, Self extends ObjectPattern<T, Self>> implements Cloneable, ElementPattern<T> {
  private InitialPatternCondition<T> myInitialCondition;
  private Object myConditions;

  protected ObjectPattern(@NotNull final InitialPatternCondition<T> condition) {
    myInitialCondition = condition;
    myConditions = null;
  }

  protected ObjectPattern(final Class<T> aClass) {
    this(new InitialPatternCondition<T>(aClass) {
      final Condition<Object> checker = InstanceofCheckerGenerator.getInstance().getInstanceofChecker(aClass);
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        return checker.value(o);
      }
    });
  }

  public final boolean accepts(@Nullable Object t) {
    return accepts(t, new ProcessingContext());
  }

  @SuppressWarnings("unchecked")
  public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
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

  @SuppressWarnings("unchecked")
  public final ElementPatternCondition<T> getCondition() {
    if (myConditions == null) {
      return new ElementPatternCondition<T>(myInitialCondition);
    }
    if (myConditions instanceof PatternCondition) {
      PatternCondition<? super T> singleCondition = (PatternCondition)myConditions;
      return new ElementPatternCondition<T>(myInitialCondition, Collections.<PatternCondition<? super T>>singletonList(singleCondition));
    }
    return new ElementPatternCondition<T>(myInitialCondition, (List)myConditions);
  }

  public Self andNot(final ElementPattern pattern) {
    ElementPattern<T> not = StandardPatterns.not(pattern);
    return and(not);
  }

  public Self andOr(@NotNull ElementPattern... patterns) {
    ElementPattern or = StandardPatterns.or(patterns);
    return and(or);
  }

  public Self and(final ElementPattern pattern) {
    return with(new PatternConditionPlus<T, T>("and", pattern) {
      @Override
      public boolean processValues(T t, ProcessingContext context, PairProcessor<T, ProcessingContext> processor) {
        return processor.process(t, context);
      }
    });
  }

  public Self equalTo(@NotNull final T o) {
    return with(new ValuePatternCondition<T>("equalTo") {
      public boolean accepts(@NotNull final T t, final ProcessingContext context) {
        return t.equals(o);
      }

      @Override
      public Collection<T> getValues() {
        return Collections.singletonList(o);
      }
    });
  }

  @NotNull
  public Self oneOf(final T... values) {
    final Collection<T> list;

    final int length = values.length;
    if (length == 1) {
      list = Collections.singletonList(values[0]);
    }
    else if (length >= 11) {
      list = new HashSet<T>(Arrays.asList(values));
    }
    else {
      list = Arrays.asList(values);
    }

    return with(new ValuePatternCondition<T>("oneOf") {

      @Override
      public Collection<T> getValues() {
        return list;
      }

      @Override
      public boolean accepts(@NotNull T t, ProcessingContext context) {
        return list.contains(t);
      }
    });
  }

  @NotNull
  public Self oneOf(final Collection<T> set) {
    return with(new ValuePatternCondition<T>("oneOf") {

      @Override
      public Collection<T> getValues() {
        return set;
      }

      @Override
      public boolean accepts(@NotNull T t, ProcessingContext context) {
        return set.contains(t);
      }
    });
  }

  public Self isNull() {
    return adapt(new ElementPatternCondition<T>(new InitialPatternCondition(Object.class) {
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        return o == null;
      }
    }));
  }

  public Self notNull() {
    return adapt(new ElementPatternCondition<T>(new InitialPatternCondition(Object.class) {
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        return o != null;
      }
    }));
  }

  public Self save(final Key<? super T> key) {
    return with(new PatternCondition<T>("save") {
      public boolean accepts(@NotNull final T t, final ProcessingContext context) {
        context.put((Key)key, t);
        return true;
      }
    });
  }

  public Self save(@NonNls final String key) {
    return with(new PatternCondition<T>("save") {
      public boolean accepts(@NotNull final T t, final ProcessingContext context) {
        context.put(key, t);
        return true;
      }
    });
  }

  public Self with(final PatternCondition<? super T> pattern) {
    final ElementPatternCondition<T> condition = getCondition().append(pattern);
    return adapt(condition);
  }

  private Self adapt(final ElementPatternCondition<T> condition) {
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

  public Self without(final PatternCondition<? super T> pattern) {
    return with(new PatternCondition<T>("without") {
      public boolean accepts(@NotNull final T o, final ProcessingContext context) {
        return !pattern.accepts(o, context);
      }
    });
  }

  public String toString() {
    return getCondition().toString();
  }

  public static class Capture<T> extends ObjectPattern<T,Capture<T>> {

    public Capture(final Class<T> aClass) {
      super(aClass);
    }

    public Capture(@NotNull final InitialPatternCondition<T> condition) {
      super(condition);
    }
  }

}
