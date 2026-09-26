// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import java.lang.ref.WeakReference;
import java.util.function.Supplier;

public final class PatchedWeakReference<T> extends WeakReference<T> implements Supplier<T> {
  public PatchedWeakReference(T referent) {
    super(referent);
  }
}
