// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.semantic;

import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Type-safe key for access to {@link SemElement}.
 * <p/>
 * Use {@link #createKey(String, SemKey[])} to create a new "root" key, on which you can attach "sub"-keys via
 * {@link #subKey(String, SemKey[])}.
 */
public final class SemKey<T extends SemElement> {
  private static final AtomicInteger ourCounter = new AtomicInteger(0);

  private final String myDebugName;
  private final SemKey<? super T> @NotNull [] mySupers;
  private final int myUniqueId;

  @SafeVarargs
  private SemKey(String debugName, SemKey<? super T> @NotNull ... supers) {
    myDebugName = debugName;
    mySupers = supers;
    myUniqueId = ourCounter.getAndIncrement();
  }

  public SemKey<? super T> @NotNull [] getSupers() {
    return mySupers;
  }

  @Override
  public String toString() {
    return myDebugName;
  }

  @SafeVarargs
  public static @NotNull <T extends SemElement> SemKey<T> createKey(String debugName, SemKey<? super T> @NotNull ... supers) {
    return new SemKey<>(debugName, supers);
  }

  @Override
  public int hashCode() {
    return myUniqueId;
  }

  public int getUniqueId() {
    return myUniqueId;
  }

  @SafeVarargs
  public final @NotNull <K extends T> SemKey<K> subKey(@NonNls String debugName, SemKey<? super K> @NotNull ... otherSupers) {
    if (otherSupers.length == 0) {
      return new SemKey<>(debugName, this);
    }
    return new SemKey<>(debugName, ArrayUtil.append(otherSupers, this));
  }
}
