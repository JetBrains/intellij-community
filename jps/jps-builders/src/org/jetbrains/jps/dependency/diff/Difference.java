// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.diff;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.impl.Containers;
import org.jetbrains.jps.javac.Iterators;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

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
      return Iterators.isEmpty(added()) && Iterators.isEmpty(removed()) && Iterators.isEmpty(changed());
    }
  }

  static <T extends DiffCapable<T, D>, D extends Difference> Specifier<T, D> make(@Nullable Iterable<T> past, @Nullable Iterable<T> now) {
    if (Iterators.isEmpty(past)) {
      if (Iterators.isEmpty(now)) {
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
    else if (Iterators.isEmpty(now)) {
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

    Set<T> pastSet = Collections.unmodifiableSet(Iterators.collect(past, Containers.createCustomPolicySet(T::isSame, T::diffHashCode)));
    Set<T> nowSet = Collections.unmodifiableSet(Iterators.collect(now, Containers.createCustomPolicySet(T::isSame, T::diffHashCode)));
    final Map<T, T> nowMap = Containers.createCustomPolicyMap(T::isSame, T::diffHashCode);
    for (T s : nowSet) {
      if (pastSet.contains(s)) {
        nowMap.put(s, s);
      }
    }

    Set<T> added = Containers.createCustomPolicySet(nowSet, T::isSame, T::diffHashCode);
    added.removeAll(pastSet);

    Set<T> removed = Containers.createCustomPolicySet(pastSet, T::isSame, T::diffHashCode);
    removed.removeAll(nowSet);

    //final List<Change<T, D>> changed = new ArrayList<>(0);
    //for (T before : pastSet) {
    //  T after = nowMap.get(before);
    //  if (after == null) {
    //    continue;
    //  }
    //  D diff = after.difference(before);
    //  if (!diff.unchanged()) {
    //    changed.add(Change.create(before, after, diff));
    //  }
    //}

    // calculate changes lazily
    Iterable<Change<T, D>> changed = Iterators.filter(Iterators.map(pastSet, before -> {
      T after = nowMap.get(before);
      if (after != null) {
        D diff = after.difference(before);
        if (!diff.unchanged()) {
          return Change.create(before, after, diff);
        }
      }
      return null;
    }), Iterators.notNullFilter());

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
