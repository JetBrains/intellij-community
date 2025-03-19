// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.patterns;

import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Provides conditions to check collections of patterns.
 * <p>
 * Please see the <a href="https://plugins.jetbrains.com/docs/intellij/element-patterns.html">IntelliJ Platform Docs</a>
 * for a high-level overview.
 *
 * @see PlatformPatterns#collection()
 */
public class CollectionPattern<T> extends ObjectPattern<Collection<T>, CollectionPattern<T>> {
  private static final InitialPatternCondition CONDITION = new InitialPatternCondition<Collection>(Collection.class) {
    @Override
    public boolean accepts(final @Nullable Object o, final ProcessingContext context) {
      return o instanceof Collection;
    }
  };

  protected CollectionPattern() {
    super(CONDITION);
  }

  public CollectionPattern<T> all(final ElementPattern<? extends T> pattern) {
    return with(new PatternCondition<Collection<T>>("all") {
      @Override
      public boolean accepts(final @NotNull Collection<T> collection, final ProcessingContext context) {
        for (final T t : collection) {
          if (!pattern.accepts(t, context)) return false;
        }
        return true;
      }
    });
  }

  public CollectionPattern<T> atLeastOne(final ElementPattern<? extends T> pattern) {
    return with(new PatternCondition<Collection<T>>("atLeastOne") {
      @Override
      public boolean accepts(final @NotNull Collection<T> collection, final ProcessingContext context) {
        for (final T t : collection) {
          if (pattern.accepts(t, context)) return true;
        }
        return false;
      }
    });
  }

  public CollectionPattern<T> filter(final ElementPattern<? extends T> elementPattern, final ElementPattern<Collection<T>> continuationPattern) {
    return with(new PatternCondition<Collection<T>>("filter") {
      @Override
      public boolean accepts(final @NotNull Collection<T> collection, final ProcessingContext context) {
        List<T> filtered = new ArrayList<>();
        for (final T t : collection) {
          if (elementPattern.accepts(t, context)) {
            filtered.add(t);
          }
        }
        return continuationPattern.accepts(filtered, context);
      }
    });
  }

  public CollectionPattern<T> first(final ElementPattern<? extends T> elementPattern) {
    return with(new PatternCondition<Collection<T>>("first") {
      @Override
      public boolean accepts(final @NotNull Collection<T> collection, final ProcessingContext context) {
        return !collection.isEmpty() &&
               elementPattern.accepts(collection.iterator().next(), context);
      }
    });
  }

  public CollectionPattern<T> empty() {
    return size(0);
  }

  public CollectionPattern<T> notEmpty() {
    return atLeast(1);
  }

  public CollectionPattern<T> atLeast(final int size) {
    return with(new PatternCondition<Collection<T>>("atLeast") {
      @Override
      public boolean accepts(final @NotNull Collection<T> ts, final ProcessingContext context) {
        return ts.size() >= size;
      }
    });
  }

  public CollectionPattern<T> size(final int size) {
    return with(new PatternCondition<Collection<T>>("size") {
      @Override
      public boolean accepts(final @NotNull Collection<T> collection, final ProcessingContext context) {
        return size == collection.size();
      }
    });
  }

  public CollectionPattern<T> last(final ElementPattern elementPattern) {
    return with(new PatternCondition<Collection<T>>("last") {
      @Override
      public boolean accepts(final @NotNull Collection<T> collection, final ProcessingContext context) {
        if (collection.isEmpty()) {
          return false;
        }
        T last = null;
        for (final T t : collection) {
          last = t;
        }
        return elementPattern.accepts(last, context);
      }
    });
  }
}
