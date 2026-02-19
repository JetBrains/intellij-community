// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.util.containers.WeakList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;

/**
 * A reference whose referent may be garbage-collected when there's low free memory, not only when there's none.
 * Use for objects which retain lots of memory and whose loss is not very expensive.
 */
public final class SofterReference<T> {
  private volatile Reference<T> myRef;
  private static final WeakList<SofterReference<?>> ourRegistry = new WeakList<>();

  private static void onLowMemory() {
    for (SofterReference<?> reference : ourRegistry.copyAndClear()) {
      reference.weaken();
    }
  }

  static {
    LowMemoryWatcher.register(() -> onLowMemory(), ApplicationManager.getApplication());
  }

  public SofterReference(@NotNull T referent) {
    ourRegistry.add(this);
    myRef = new SoftReference<>(referent);
  }

  private void weaken() {
    T o = myRef.get();
    if (o != null) {
      myRef = new WeakReference<>(o);
    }
  }

  public @Nullable T get() {
    Reference<T> ref = myRef;
    T referent = ref.get();
    if (referent != null && ref instanceof WeakReference) {
      ourRegistry.add(this);
      myRef = new SoftReference<>(referent);
    }
    return referent;
  }
}
