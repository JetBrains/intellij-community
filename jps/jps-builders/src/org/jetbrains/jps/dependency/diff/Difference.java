// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.diff;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.impl.Containers;

import java.util.*;

import static org.jetbrains.jps.javac.Iterators.*;

public interface Difference {

  boolean unchanged();

  interface Change<T, D extends Difference> {
    T getPast();
    
    T getNow();

    D getDiff();

    static <T, D extends Difference> Change<T, D> create(T past, T now, D diff) {
      return new Change<>() {
        @Override
        public T getPast() {
          return past;
        }

        @Override
        public T getNow() {
          return now;
        }

        @Override
        public D getDiff() {
          return diff;
        }
      };

    }
  }

  interface Specifier<T, D extends Difference> {
    default Iterable<T> added() {
      return Collections.emptyList();
    }

    default Iterable<T> removed() {
      return Collections.emptyList();
    }

    default Iterable<Change<T, D>> changed() {
      return Collections.emptyList();
    }

    default boolean unchanged() {
      return isEmpty(added()) && isEmpty(removed()) && isEmpty(changed());
    }
  }

  static <T> Specifier<T, ?> diff(@Nullable Iterable<T> past, @Nullable Iterable<T> now) {
    if (isEmpty(past)) {
      if (isEmpty(now)) {
        return new Specifier<>() {
          @Override
          public boolean unchanged() {
            return true;
          }
        };
      }
      return new Specifier<>() {
        @Override
        public Iterable<T> added() {
          return now;
        }

        @Override
        public boolean unchanged() {
          return false;
        }
      };
    }
    else if (isEmpty(now)) {
      return new Specifier<>() {
        @Override
        public Iterable<T> removed() {
          return past;
        }

        @Override
        public boolean unchanged() {
          return false;
        }
      };
    }

    Set<T> pastSet = past instanceof Set? (Set<T>)past : collect(past, new HashSet<>());
    Set<T> nowSet = now instanceof Set? (Set<T>)now : collect(now, new HashSet<>());

    Iterable<T> added = lazy(() -> collect(filter(nowSet, elem -> !pastSet.contains(elem)), new ArrayList<>()));
    Iterable<T> removed = lazy(() -> collect(filter(pastSet, elem -> !nowSet.contains(elem)), new ArrayList<>()));

    return new Specifier<>() {
      private Boolean isUnchanged;
      @Override
      public Iterable<T> added() {
        return added;
      }

      @Override
      public Iterable<T> removed() {
        return removed;
      }

      @Override
      public boolean unchanged() {
        return isUnchanged != null? isUnchanged : (isUnchanged = pastSet.equals(nowSet)).booleanValue();
      }
    };
  }

  static <T extends DiffCapable<T, D>, D extends Difference> Specifier<T, D> deepDiff(@Nullable Iterable<T> past, @Nullable Iterable<T> now) {
    if (isEmpty(past)) {
      if (isEmpty(now)) {
        return new Specifier<>() {
          @Override
          public boolean unchanged() {
            return true;
          }
        };
      }
      return new Specifier<>() {
        @Override
        public Iterable<T> added() {
          return now;
        }

        @Override
        public boolean unchanged() {
          return false;
        }
      };
    }
    else if (isEmpty(now)) {
      return new Specifier<>() {
        @Override
        public Iterable<T> removed() {
          return past;
        }

        @Override
        public boolean unchanged() {
          return false;
        }
      };
    }

    Set<T> pastSet = collect(past, Containers.createCustomPolicySet(T::isSame, T::diffHashCode));
    Set<T> nowSet = collect(now, Containers.createCustomPolicySet(T::isSame, T::diffHashCode));

    Iterable<T> added = lazy(() -> collect(filter(nowSet, obj -> !pastSet.contains(obj)), new ArrayList<>()));
    Iterable<T> removed = lazy(() -> collect(filter(pastSet, obj -> !nowSet.contains(obj)), new ArrayList<>()));

    Iterable<Change<T, D>> changed = lazy(() -> {
      final Map<T, T> nowMap = Containers.createCustomPolicyMap(T::isSame, T::diffHashCode);
      for (T s : nowSet) {
        if (pastSet.contains(s)) {
          nowMap.put(s, s);
        }
      }
      final List<Change<T, D>> result = new ArrayList<>(0);
      for (T before : pastSet) {
        T after = nowMap.get(before);
        if (after != null) {
          D diff = after.difference(before);
          if (!diff.unchanged()) {
            result.add(Change.create(before, after, diff));
          }
        }
      }
      return result;
    });

    return new Specifier<>() {
      @Override
      public Iterable<T> added() {
        return added;
      }

      @Override
      public Iterable<T> removed() {
        return removed;
      }

      @Override
      public Iterable<Change<T, D>> changed() {
        return changed;
      }
    };
  }
  
}
