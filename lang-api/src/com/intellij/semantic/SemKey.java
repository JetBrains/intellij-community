/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.semantic;

import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author peter
 */
public class SemKey<T extends SemElement> {
  private static AtomicInteger counter = new AtomicInteger(0);
  private final String myDebugName;
  private final SemKey<? super T>[] mySupers;
  private final int myUniqueId;

  private SemKey(String debugName, SemKey<? super T>... supers) {
    myDebugName = debugName;
    mySupers = supers;
    myUniqueId = counter.getAndIncrement();
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

  public static <T extends SemElement> SemKey<T> createKey(String debugName, SemKey<? super T>... supers) {
    return new SemKey<T>(debugName, supers);
  }

  public int getUniqueId() {
    return myUniqueId;
  }

  public <K extends T> SemKey<K> subKey(@NonNls String debugName, SemKey<? super T>... otherSupers) {
    if (otherSupers.length == 0) {
      return new SemKey<K>(debugName, this);
    }
    return new SemKey<K>(debugName, ArrayUtil.append(otherSupers, this));
  }
}
