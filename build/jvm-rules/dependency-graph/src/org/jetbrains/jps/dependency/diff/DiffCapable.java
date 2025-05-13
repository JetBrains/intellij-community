// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.diff;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;

public interface DiffCapable<T extends DiffCapable<T, D>, D extends Difference> {
  boolean isSame(DiffCapable<?, ?> other);

  int diffHashCode();
  
  D difference(T past);

  interface Adapter<T, D extends Difference> extends DiffCapable<Adapter<T, D>, D> {
    T getValue();
  }

  static <T> Adapter<T, Difference> wrap(T value) {
    return wrap(value, Objects::equals, Objects::hashCode, (past, now) -> () -> Objects.equals(past, now));
  }

  static <T, D extends Difference> Adapter<T, D> wrap(T value, BiPredicate<? super T, ? super T> isSameImpl, Function<? super T, Integer> diffHashImpl, BiFunction<? super T, ? super T, ? extends D> diffImpl) {
    return new Adapter<>() {
      @Override
      public T getValue() {
        return value;
      }

      @Override
      public boolean isSame(DiffCapable<?, ?> other) {
        //noinspection unchecked
        return other instanceof Adapter && isSameImpl.test(value, ((Adapter<T, D>)other).getValue());
      }

      @Override
      public int diffHashCode() {
        return diffHashImpl.apply(value);
      }

      @Override
      public D difference(Adapter<T, D> past) {
        return diffImpl.apply(past.getValue(), value);
      }
    };
  }

}
