/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.lookup;

import com.intellij.openapi.util.Pair;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Methods in this class are not externally synchronized and may be called from several threads;
 * while this class has no mutable state, thus it's thread-safe, subclasses may not be thread safe.
 * It's a responsibility of subclasses to synchronize properly.
 * Please don't call superclass methods like addElement under the lock.
 */
public abstract class Classifier<T> {
  protected final Classifier<T> myNext;
  private final String myName;

  protected Classifier(Classifier<T> next, @NonNls String name) {
    myNext = next;
    myName = name;
  }

  public void addElement(@NotNull T t, @NotNull ProcessingContext context) {
    if (myNext != null) {
      myNext.addElement(t, context);
    }
  }

  @NotNull
  public abstract Iterable<T> classify(@NotNull Iterable<? extends T> source, @NotNull ProcessingContext context);

  /**
   * @return a mapping from the given items to objects (e.g. Comparable instances) used to sort the items in {@link #classify(Iterable, ProcessingContext)}.
   * May return an empty list if there are no suitable objects available.
   * Used for diagnostics and statistic collection.
   */
  @NotNull
  public abstract List<Pair<T, Object>> getSortingWeights(@NotNull Iterable<? extends T> items, @NotNull ProcessingContext context);

  @Nullable
  public final Classifier<T> getNext() {
    return myNext;
  }

  public void removeElement(@NotNull T element, @NotNull ProcessingContext context) {
    if (myNext != null) {
      myNext.removeElement(element, context);
    }
  }

  @NotNull
  public final String getPresentableName() {
    return myName;
  }
}
