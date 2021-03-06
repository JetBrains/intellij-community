// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.semantic;

import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Type-safe key for access to {@link SemElement}.
 * <p/>
 * Use {@link #createKey(String, SemKey[])} to create a new "root" key, on which you can attach "sub"-keys via
 * {@link #subKey(String, SemKey[])}.
 *
 * @author peter
 */
public final class SemKey<T extends SemElement> {
  private static final AtomicInteger counter = new AtomicInteger(0);
  private final String myDebugName;
  private final SemKey<? super T> @NotNull [] mySupers;
  private final List<SemKey<?>> myInheritors = ContainerUtil.createEmptyCOWList();
  private final int myUniqueId;

  @SafeVarargs
  private SemKey(String debugName, SemKey<? super T> @NotNull ... supers) {
    myDebugName = debugName;
    mySupers = supers;
    myUniqueId = counter.getAndIncrement();
    myInheritors.add(this);
    registerInheritor(this);
  }

  private void registerInheritor(SemKey<?> eachParent) {
    for (SemKey<?> superKey : eachParent.mySupers) {
      superKey.myInheritors.add(this);
      registerInheritor(superKey);
    }
  }

  public SemKey<? super T> @NotNull [] getSupers() {
    return mySupers;
  }

  public List<SemKey<?>> getInheritors() {
    return myInheritors;
  }

  public boolean isKindOf(SemKey<?> another) {
    if (another == this) return true;
    for (final SemKey<? super T> superKey : mySupers) {
      if (superKey.isKindOf(another)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    return myDebugName;
  }

  @SafeVarargs
  @NotNull
  public static <T extends SemElement> SemKey<T> createKey(String debugName, SemKey<? super T> @NotNull ... supers) {
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
  @NotNull
  public final <K extends T> SemKey<K> subKey(@NonNls String debugName, SemKey<? super K> @NotNull ... otherSupers) {
    if (otherSupers.length == 0) {
      return new SemKey<>(debugName, this);
    }
    return new SemKey<>(debugName, ArrayUtil.append(otherSupers, this));
  }
}
