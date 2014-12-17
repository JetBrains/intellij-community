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

import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author peter
 */
public class CollectionPattern<T> extends ObjectPattern<Collection<T>, CollectionPattern<T>> {
  private static final InitialPatternCondition CONDITION = new InitialPatternCondition<Collection>(Collection.class) {
    public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
      return o instanceof Collection;
    }
  };

  protected CollectionPattern() {
    super(CONDITION);
  }

  public CollectionPattern<T> all(final ElementPattern<? extends T> pattern) {
    return with(new PatternCondition<Collection<T>>("all") {
      public boolean accepts(@NotNull final Collection<T> collection, final ProcessingContext context) {
        for (final T t : collection) {
          if (!pattern.accepts(t, context)) return false;
        }
        return true;
      }
    });
  }

  public CollectionPattern<T> atLeastOne(final ElementPattern<? extends T> pattern) {
    return with(new PatternCondition<Collection<T>>("atLeastOne") {
      public boolean accepts(@NotNull final Collection<T> collection, final ProcessingContext context) {
        for (final T t : collection) {
          if (pattern.accepts(t, context)) return true;
        }
        return false;
      }
    });
  }

  public CollectionPattern<T> filter(final ElementPattern<? extends T> elementPattern, final ElementPattern<Collection<T>> continuationPattern) {
    return with(new PatternCondition<Collection<T>>("filter") {
      public boolean accepts(@NotNull final Collection<T> collection, final ProcessingContext context) {
        List<T> filtered = new ArrayList<T>();
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
      public boolean accepts(@NotNull final Collection<T> collection, final ProcessingContext context) {
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
      public boolean accepts(@NotNull final Collection<T> ts, final ProcessingContext context) {
        return ts.size() >= size;
      }
    });
  }

  public CollectionPattern<T> size(final int size) {
    return with(new PatternCondition<Collection<T>>("size") {
      public boolean accepts(@NotNull final Collection<T> collection, final ProcessingContext context) {
        return size == collection.size();
      }
    });
  }

  public CollectionPattern<T> last(final ElementPattern elementPattern) {
    return with(new PatternCondition<Collection<T>>("last") {
      public boolean accepts(@NotNull final Collection<T> collection, final ProcessingContext context) {
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
