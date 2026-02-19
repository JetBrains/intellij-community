// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.javac;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

abstract class ValueSupplier<T, E extends Throwable> {

  public abstract T get() throws E;

  public static <T, E extends Throwable> ValueSupplier<T, E> asCaching(final ValueSupplier<? extends T, ? extends E> delegate) {
    return new ValueSupplier<T, E>() {
      private Reference<T> myRef;
      
      @Override
      public T get() throws E {
        T value = (myRef == null? null : myRef.get());
        if (value == null) {
          myRef = new WeakReference<>(value = delegate.get());
        }
        return value;
      }
    };
  }
}
