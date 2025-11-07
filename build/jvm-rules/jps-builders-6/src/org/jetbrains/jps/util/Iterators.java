// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.util;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class Iterators {

  public static boolean isEmpty(Iterable<?> iterable) {
    return isEmptyCollection(iterable) || !iterable.iterator().hasNext();
  }

  public static boolean isEmptyCollection(Iterable<?> iterable) {
    return iterable == null || iterable instanceof Collection && ((Collection<?>)iterable).isEmpty();
  }

  public static <T> boolean contains(Iterable<? extends T> iterable, @NotNull T obj) {
    if (iterable instanceof Collection) {
      return ((Collection<?>)iterable).contains(obj);
    }
    if (iterable != null) {
      for (T o : iterable) {
        if (obj.equals(o)) {
          return true;
        }
      }
    }
    return false;
  }

  public static int count(Iterable<?> iterable) {
    if (iterable instanceof Collection) {
      return ((Collection<?>)iterable).size();
    }
    int count = 0;
    for (Iterator<?> it = iterable.iterator(); it.hasNext(); it.next()) {
      count += 1;
    }
    return count;
  }

  public static <T> T find(Iterable<? extends T> iterable, Predicate<? super T> cond) {
    if (iterable != null) {
      for (T o : iterable) {
        if (cond.test(o)) {
          return o;
        }
      }
    }
    return null;
  }

  public static <C extends Collection<? super T>, T> C collect(Iterable<? extends T> iterable, C acc) {
    return collect(iterable != null? iterable.iterator() : null, acc);
  }

  public static <C extends Collection<? super T>, T> C collect(Iterator<? extends T> it, C acc) {
    if (it != null) {
      while (it.hasNext()) {
        acc.add(it.next());
      }
    }
    return acc;
  }

  public static <T> Iterable<T> lazyIterable(final Supplier<? extends Iterable<T>> provider) {
    final Supplier<? extends Iterable<T>> delegate = cachedValue(provider);
    return () -> delegate.get().iterator();
  }

  public static <T> Iterator<T> lazyIterator(final Supplier<? extends Iterator<T>> provider) {
    return new Iterator<T>() {
      private final Supplier<? extends Iterator<T>> delegate = cachedValue(provider);
      @Override
      public boolean hasNext() {
        return delegate.get().hasNext();
      }

      @Override
      public T next() {
        return delegate.get().next();
      }
    };
  }

  @SuppressWarnings("unchecked")
  public static <T> Iterable<T> flat(final Iterable<? extends T> first, final Iterable<? extends T> second) {
    if (isEmptyCollection(first)) {
      return isEmptyCollection(second) ? Collections.emptyList() : (Iterable<T>)second;
    }
    if (isEmptyCollection(second)) {
      return (Iterable<T>)first;
    }
    return () -> flat(first.iterator(), second.iterator());
  }

  public static <T> Iterator<T> flat(final Iterator<? extends T> first, final Iterator<? extends T> second) {
    return new Iterator<T>() {
      @Override
      public boolean hasNext() {
        return first.hasNext() || second.hasNext();
      }

      @Override
      public T next() {
        return first.hasNext()? first.next() : second.next();
      }
    };
  }

  public static <T> Iterable<T> flat(final Collection<? extends Iterable<? extends T>> parts) {
    if (parts.isEmpty()) {
      return Collections.emptyList();
    }
    if (parts.size() == 1) {
      //noinspection unchecked
      return (Iterable<T>)parts.iterator().next();
    }
    return flat((Iterable<? extends Iterable<? extends T>>)parts);
  }

  public static <T> Iterable<T> flat(final Iterable<? extends Iterable<? extends T>> parts) {
    return isEmptyCollection(parts) ? Collections.emptyList() : () -> flat(map(parts.iterator(), Iterators::asIterator));
  }

  public static <T> Iterator<T> flat(final Iterator<? extends Iterator<T>> groupsIterator) {
    return new Iterator<T>() {
      private Iterator<T> currentGroup;

      @Override
      public boolean hasNext() {
        return findNext() != null;
      }

      @Override
      public T next() {
        Iterator<T> group = findNext();
        if (group != null) {
          return group.next();
        }
        throw new NoSuchElementException();
      }

      private Iterator<T> findNext() {
        if (currentGroup == null || !currentGroup.hasNext()) {
          do {
            currentGroup = groupsIterator.hasNext() ? groupsIterator.next() : null;
          }
          while (currentGroup != null && !currentGroup.hasNext());
        }
        return currentGroup;
      }
    };
  }

  public static <I> Iterator<I> asIterator(final Iterable<? extends I> from) {
    //noinspection unchecked
    return from == null ? Collections.emptyIterator() : (Iterator<I>)from.iterator();
  }

  public static <T> Iterable<T> asIterable(final T elem) {
    return () -> asIterator(elem);
  }

  public static <T> Iterable<T> asIterable(final T[] elem) {
    return elem == null ? Collections.emptyList() : Arrays.asList(elem);
  }

  public static <T> Iterable<T> reverse(final List<T> list) {
    return new Iterable<T>() {
      @NotNull
      @Override
      public Iterator<T> iterator() {
        final ListIterator<T> li = list.listIterator(list.size());
        return new Iterator<T>() {
          @Override
          public boolean hasNext() {
            return li.hasPrevious();
          }

          @Override
          public T next() {
            return li.previous();
          }
        };
      }
    };
  }

  public static <T> Iterator<T> asIterator(final T elem) {
    return new Iterator<T>() {
      private boolean available = true;

      @Override
      public boolean hasNext() {
        return available;
      }

      @Override
      public T next() {
        if (available) {
          available = false;
          return elem;
        }
        throw new NoSuchElementException();
      }
    };
  }

  public static <I,O> Iterable<O> map(final Iterable<? extends I> from, final Function<? super I, ? extends O> mapper) {
    return isEmptyCollection(from) ? Collections.emptyList() : () -> map(from.iterator(), mapper);
  }

  public static <I,O> Iterator<O> map(final Iterator<? extends I> it, final Function<? super I, ? extends O> mapper) {
    return new Iterator<O>() {
      @Override
      public boolean hasNext() {
        return it.hasNext();
      }

      @Override
      public O next() {
        return mapper.apply(it.next());
      }
    };
  }

  public static <T> Iterable<T> filter(final Iterable<? extends T> it, final Predicate<? super T> predicate) {
    return isEmptyCollection(it) ? Collections.emptyList() : () -> filter(it.iterator(), predicate);
  }

  public static <T> Iterator<T> filter(final Iterator<? extends T> it, final Predicate<? super T> predicate) {
    return new Iterator<T>() {
      private T current = null;
      private boolean isPending = false;

      @Override
      public boolean hasNext() {
        if (!isPending) {
          findNext();
        }
        return isPending;
      }

      @Override
      public T next() {
        try {
          if (!isPending) {
            findNext();
            if (!isPending) {
              throw new NoSuchElementException();
            }
          }
          return current;
        }
        finally {
          current = null;
          isPending = false;
        }
      }

      private void findNext() {
        isPending = false;
        current = null;
        while (it.hasNext()) {
          final T next = it.next();
          if (predicate.test(next)) {
            isPending = true;
            current = next;
            break;
          }
        }
      }
    };
  }

  public static <T> Iterable<T> filterWithOrder(final Iterable<? extends T> from, final Iterable<? extends Predicate<? super T>> predicates) {
    return isEmptyCollection(predicates) || isEmptyCollection(from)? Collections.emptyList() : () -> filterWithOrder(from.iterator(), predicates.iterator());
  }

  public static <T> Iterator<T> filterWithOrder(final Iterator<? extends T> from, final Iterator<? extends Predicate<? super T>> predicates) {
    return flat(map(predicates, new Function<Predicate<? super T>, Iterator<T>>() {
      final List<T> buffer = new LinkedList<>();
      @Override
      public Iterator<T> apply(Predicate<? super T> pred) {
        if (!buffer.isEmpty()) {
          for (Iterator<T> it = buffer.iterator(); it.hasNext(); ) {
            final T elem = it.next();
            if (pred.test(elem)) {
              it.remove();
              return asIterator(elem);
            }
          }
        }
        while(from.hasNext()) {
          final T elem = from.next();
          if (pred.test(elem)) {
            return asIterator(elem);
          }
          buffer.add(elem);
        }
        buffer.clear();
        return Collections.emptyIterator();
      }
    }));
  }

  public static <T> Iterable<T> unique(final Iterable<? extends T> it) {
    return isEmptyCollection(it) ? Collections.emptyList() : () -> unique(it.iterator());
  }

  public static <T> Iterator<T> unique(final Iterator<? extends T> it) {
    Supplier<Set<Object>> processed = cachedValue(HashSet::new);
    return filter(it, e -> processed.get().add(e));
  }

  public static <T> Iterable<T> uniqueBy(final Iterable<? extends T> it, final Supplier<? extends Predicate<T>> predicateFactory) {
    return isEmptyCollection(it) ? Collections.emptyList() : () -> filter(it.iterator(), predicateFactory.get());
  }

  public static <T> boolean equals(Iterable<? extends T> s1, Iterable<? extends T> s2) {
    return equals(s1, s2, Object::equals);
  }

  public static <T> boolean equals(Iterable<? extends T> s1, Iterable<? extends T> s2, BiFunction<? super T, ? super T, Boolean> comparator) {
    Iterator<? extends T> it2 = s2.iterator();
    for (T elem : s1) {
      if (!it2.hasNext()) {
        return false;
      }
      if (!comparator.apply(elem, it2.next())) {
        return false;
      }
    }
    return !it2.hasNext();
  }

  public static <T> int hashCode(Iterable<? extends T> s) {
    int result = 1;
    for (T elem : s) {
      result = 31 * result + (elem == null? 0 : elem.hashCode());
    }
    return result;
  }

  public static <T> Iterable<T> recurse(final T item, final Function<? super T, ? extends Iterable<? extends T>> step, final boolean includeHead) {
    return new Iterable<T>() {
      @NotNull
      @Override
      public Iterator<T> iterator() {
        return new Object() {
          private final Set<T> traversed = new HashSet<>();

          private Iterator<T> recurse(final T elem, boolean includeHead) {
            if (!traversed.add(elem)) {
              return Collections.emptyIterator();
            }

            if (!includeHead) {
              return tailOf(elem);
            }

            return flat(asIterator(elem), lazyIterator(() -> tailOf(elem)));
          }

          @NotNull
          private Iterator<T> tailOf(final T elem) {
            final Iterable<? extends T> tail = filter(step.apply(elem), e -> !traversed.contains(e));
            return flat(tail.iterator(), flat(map(tail.iterator(), e -> recurse(e, false))));
          }

        }.recurse(item, includeHead);
      }
    };
  }

  public static <T> Iterable<T> recurseDepth(final T item, final Function<? super T, ? extends Iterable<? extends T>> step, final boolean includeHead) {
    return new Iterable<T>() {
      @NotNull
      @Override
      public Iterator<T> iterator() {
        return new Object() {
          private final Set<T> visited = new HashSet<>();

          private Iterator<T> recurse(final T elem, boolean includeHead) {
            if (!visited.add(elem)) {
              return Collections.emptyIterator();
            }

            if (!includeHead) {
              return flat(map(step.apply(elem).iterator(), e -> recurse(e, true)));
            }

            Iterator<? extends T> tail = lazyIterator(() -> step.apply(elem).iterator());
            return flat(asIterator(elem), flat(map(tail, e -> recurse(e, true))));
          }

        }.recurse(item, includeHead);
      }
    };
  }

  private static <V> Supplier<V> cachedValue(Supplier<V> valueFactory) {
    return new Supplier<V>() {
      private Object[] computed;

      @Override
      public V get() {
        //noinspection unchecked
        return computed == null? (V)(computed = new Object[] {valueFactory.get()})[0] : (V)computed[0];
      }
    };
  }
}
