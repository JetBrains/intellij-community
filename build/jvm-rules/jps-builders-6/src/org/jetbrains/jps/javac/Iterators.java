// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.javac;

import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class Iterators {
  @SuppressWarnings("rawtypes")
  private static final BooleanFunction NOT_NULL_FILTER = new BooleanFunction() {
    @Override
    public boolean fun(Object s) {
      return s != null;
    }
  };

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

  public static <T> T find(Iterable<? extends T> iterable, BooleanFunction<? super T> cond) {
    if (iterable != null) {
      for (T o : iterable) {
        if (cond.fun(o)) {
          return o;
        }
      }
    }
    return null;
  }

  public static <C extends Collection<? super T>, T> C collect(Iterable<? extends T> iterable, C acc) {
    if (iterable != null) {
      for (T t : iterable) {
        acc.add(t);
      }
    }
    return acc;
  }

  public interface Provider<T> {
    T get();
  }
  
  public interface Function<S, T> {
    T fun(S s);
  }

  public interface BiFunction<S1, S2, T> {
    T fun(S1 s1, S2 s2);
  }

  public interface BooleanFunction<T> {
    boolean fun(T t);
  }
  
  public static <T> Iterable<T> lazy(final Provider<? extends Iterable<T>> provider) {
    return new Iterable<T>() {
      private Iterable<T> myDelegate = null;
      @NotNull
      @Override
      public Iterator<T> iterator() {
        return getDelegate().iterator();
      }

      private Iterable<T> getDelegate() {
        Iterable<T> delegate = myDelegate;
        if (delegate == null) {
          myDelegate = delegate = provider.get();
        }
        return delegate;
      }
    };
  }

  @SuppressWarnings("unchecked")
  public static <T> Iterable<T> flat(final Iterable<? extends T> first, final Iterable<? extends T> second) {
    if (isEmptyCollection(first)) {
      return isEmptyCollection(second)? Collections.<T>emptyList() : (Iterable<T>)second;
    }
    if (isEmptyCollection(second)) {
      return (Iterable<T>)first;
    }
    return new Iterable<T>() {
      @Override
      @NotNull
      public Iterator<T> iterator() {
        return flat(first.iterator(), second.iterator());
      }
    };
  }

  public static <T> Iterator<T> flat(final Iterator<? extends T> first, final Iterator<? extends T> second) {
    return new BaseIterator<T>() {
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
    return isEmptyCollection(parts)? Collections.<T>emptyList() : new Iterable<T>() {
      @NotNull
      @Override
      public Iterator<T> iterator() {
        return flat(map(parts.iterator(), new Function<Iterable<? extends T>, Iterator<T>>() {
          @Override
          public Iterator<T> fun(Iterable<? extends T> itr) {
            return asIterator(itr);
          }
        }));
      }
    };
  }

  public static <T> Iterator<T> flat(final Iterator<? extends Iterator<T>> groupsIterator) {
    return new BaseIterator<T>() {
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
    return from == null? Collections.<I>emptyIterator() : (Iterator<I>)from.iterator();
  }

  public static <T> Iterable<T> asIterable(final T elem) {
    return new Iterable<T>() {
      @NotNull
      @Override
      public Iterator<T> iterator() {
        return asIterator(elem);
      }
    };
  }

  public static <T> Iterable<T> asIterable(final T[] elem) {
    return elem == null? Collections.<T>emptyList() : Arrays.asList(elem);
  }

  public static <T> Iterable<T> reverse(final List<T> list) {
    return new Iterable<T>() {
      @NotNull
      @Override
      public Iterator<T> iterator() {
        final ListIterator<T> li = list.listIterator(list.size());
        return new BaseIterator<T>() {
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
    return new BaseIterator<T>() {
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
    return isEmptyCollection(from)? Collections.<O>emptyList() : new Iterable<O>() {
      @NotNull
      @Override
      public Iterator<O> iterator() {
        return map(from.iterator(), mapper);
      }
    };
  }

  public static <I,O> Iterator<O> map(final Iterator<? extends I> it, final Function<? super I, ? extends O> mapper) {
    return new BaseIterator<O>() {
      @Override
      public boolean hasNext() {
        return it.hasNext();
      }

      @Override
      public O next() {
        return mapper.fun(it.next());
      }
    };
  }

  public static <T> Iterable<T> filter(final Iterable<? extends T> it, final BooleanFunction<? super T> predicate) {
    return isEmptyCollection(it)? Collections.<T>emptyList() : new Iterable<T>() {
      @NotNull
      @Override
      public Iterator<T> iterator() {
        return filter(it.iterator(), predicate);
      }
    };
  }

  public static <T> Iterator<T> filter(final Iterator<? extends T> it, final BooleanFunction<? super T> predicate) {
    return new BaseIterator<T>() {
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
          if (predicate.fun(next)) {
            isPending = true;
            current = next;
            break;
          }
        }
      }
    };
  }

  public static <T> Iterable<T> filterWithOrder(final Iterable<? extends T> from, final Iterable<? extends BooleanFunction<? super T>> predicates) {
    return isEmptyCollection(predicates) || isEmptyCollection(from)? Collections.<T>emptyList() : new Iterable<T>() {
      @NotNull
      @Override
      public Iterator<T> iterator() {
        return filterWithOrder(from.iterator(), predicates.iterator());
      }
    };
  }

  public static <T> Iterator<T> filterWithOrder(final Iterator<? extends T> from, final Iterator<? extends BooleanFunction<? super T>> predicates) {
    return flat(map(predicates, new Function<BooleanFunction<? super T>, Iterator<T>>() {
      final List<T> buffer = new LinkedList<>();
      @Override
      public Iterator<T> fun(BooleanFunction<? super T> pred) {
        if (!buffer.isEmpty()) {
          for (Iterator<T> it = buffer.iterator(); it.hasNext(); ) {
            final T elem = it.next();
            if (pred.fun(elem)) {
              it.remove();
              return asIterator(elem);
            }
          }
        }
        while(from.hasNext()) {
          final T elem = from.next();
          if (pred.fun(elem)) {
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
    return isEmptyCollection(it)? Collections.<T>emptyList() : new Iterable<T>() {
      @NotNull
      @Override
      public Iterator<T> iterator() {
        return unique(it.iterator());
      }
    };
  }

  public static <T> Iterator<T> unique(final Iterator<? extends T> it) {
    return filter(it, new BooleanFunction<T>() {
      private Set<T> processed;
      @Override
      public boolean fun(T t) {
        if (processed == null) {
          processed = new HashSet<>();
        }
        return processed.add(t);
      }
    });
  }

  public static <T> Iterable<T> uniqueBy(final Iterable<? extends T> it, final Provider<? extends BooleanFunction<T>> predicateFactory) {
    return isEmptyCollection(it)? Collections.<T>emptyList() : new Iterable<T>() {
      @NotNull
      @Override
      public Iterator<T> iterator() {
        return filter(it.iterator(), predicateFactory.get());
      }
    };
  }

  @SuppressWarnings("unchecked")
  public static <T> BooleanFunction<? super T> notNullFilter() {
    return (BooleanFunction<T>)NOT_NULL_FILTER;
  }

  public static <T> boolean equals(Iterable<? extends T> s1, Iterable<? extends T> s2) {
    return equals(s1, s2, new BiFunction<T, T, Boolean>() {
      @Override
      public Boolean fun(T t1, T t2) {
        return t1.equals(t2);
      }
    });
  }

  public static <T> boolean equals(Iterable<? extends T> s1, Iterable<? extends T> s2, BiFunction<? super T, ? super T, Boolean> comparator) {
    Iterator<? extends T> it2 = s2.iterator();
    for (T elem : s1) {
      if (!it2.hasNext()) {
        return false;
      }
      if (!comparator.fun(elem, it2.next())) {
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

            return flat(asIterator(elem), new LazyIterator<T>() {
              @Override
              protected Iterator<? extends T> create() {
                return tailOf(elem);
              }
            });
          }

          @NotNull
          private Iterator<T> tailOf(final T elem) {
            final Iterable<? extends T> tail = filter(step.fun(elem), new BooleanFunction<T>() {
              @Override
              public boolean fun(T e) {
                return !traversed.contains(e);
              }
            });
            return flat(tail.iterator(), flat(map(tail.iterator(), new Function<T, Iterator<T>>() {
              @Override
              public Iterator<T> fun(T obj) {
                return recurse(obj, false);
              }
            })));
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
              return flat(map(step.fun(elem).iterator(), new Function<T, Iterator<T>>() {
                @Override
                public Iterator<T> fun(T obj) {
                  return recurse(obj, true);
                }
              }));
            }

            Iterator<? extends T> tail = new LazyIterator<T>() {
              @Override
              protected Iterator<? extends T> create() {
                return step.fun(elem).iterator();
              }
            };
            return flat(asIterator(elem), flat(map(tail, new Function<T, Iterator<T>>() {
              @Override
              public Iterator<T> fun(T obj) {
                return recurse(obj, true);
              }
            })));
          }

        }.recurse(item, includeHead);
      }
    };
  }

  private abstract static class BaseIterator<T> implements Iterator<T> {
    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  private abstract static class LazyIterator<T> implements Iterator<T> {
    private Iterator<? extends T> myDelegate;

    protected abstract Iterator<? extends T> create();

    @Override
    public boolean hasNext() {
      return getDelegate().hasNext();
    }

    @Override
    public T next() {
      return getDelegate().next();
    }

    @Override
    public void remove() {
      getDelegate().remove();
    }

    private Iterator<? extends T> getDelegate() {
      Iterator<? extends T> delegate = myDelegate;
      if (delegate == null) {
        myDelegate = delegate = create();
      }
      return delegate;
    }
  }
}
