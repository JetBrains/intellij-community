/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util;

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
 *
 * @author peter
 */
public class SofterReference<T> {
  private volatile Reference<T> myRef;
  private static final WeakList<SofterReference> ourRegistry = new WeakList<SofterReference>();

  @SuppressWarnings("UnusedDeclaration")
  private static final LowMemoryWatcher ourWatcher = LowMemoryWatcher.register(new Runnable() {
    @Override
    public void run() {
      for (SofterReference reference : ourRegistry.copyAndClear()) {
        reference.weaken();
      }
    }
  });

  public SofterReference(@NotNull T referent) {
    ourRegistry.add(this);
    myRef = new SoftReference<T>(referent);
  }

  private void weaken() {
    T o = myRef.get();
    if (o != null) {
      myRef = new WeakReference<T>(o);
    }
  }

  @Nullable
  public T get() {
    Reference<T> ref = myRef;
    T referent = ref.get();
    if (referent != null && ref instanceof WeakReference) {
      ourRegistry.add(this);
      myRef = new SoftReference<T>(referent);
    }
    return referent;
  }
}
