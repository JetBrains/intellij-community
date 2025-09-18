// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.completion.CompletionLookupArranger;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * A classifier is used to sort completion items.
 * {@link #myNext} classifier has lower priority than the current classifier.
 * <p>
 * Methods in this class are not externally synchronized and may be called from several threads;
 * while this class has no mutable state, thus it's thread-safe, subclasses may not be thread safe.
 * It's the responsibility of subclasses to synchronize properly.
 * Please don't call superclass methods like addElement under the lock.
 * <p>
 * <p>
 * Several methods of the Classifier get an instance of {@link ProcessingContext} as an argument.
 * It's storage for the contextual information used for sorting. The same context is used for all classifiers in a classifier chain.
 * You can find {@link CompletionLookupArranger#PREFIX_CHANGES} and {@link CompletionLookupArranger#WEIGHING_CONTEXT} keys in this context.
 */
public abstract class Classifier<T> {
  protected final Classifier<T> myNext;
  private final String myName;

  protected Classifier(@Nullable Classifier<T> next, @NonNls String name) {
    myNext = next;
    myName = name;
  }

  /**
   * @param t       item to add
   * @param context see {@link Classifier} doc
   */
  public void addElement(@NotNull T t, @NotNull ProcessingContext context) {
    if (myNext != null) {
      myNext.addElement(t, context);
    }
  }

  /**
   * @param source  the items to be sorted
   * @param context see {@link Classifier} doc
   * @return the sorted collection of items
   */
  public abstract @NotNull Iterable<T> classify(@NotNull Iterable<? extends T> source, @NotNull ProcessingContext context);

  /**
   * @param items   the items to be sorted
   * @param context see {@link Classifier} doc
   * @return a mapping from the given items to objects (e.g., Comparable instances) used to sort the items in {@link #classify(Iterable, ProcessingContext)}.
   * May return an empty list if there are no suitable objects available.
   * Used for diagnostics and statistic collection.
   */
  public abstract @Unmodifiable @NotNull List<Pair<T, Object>> getSortingWeights(@NotNull Iterable<? extends T> items,
                                                                                 @NotNull ProcessingContext context);

  public final @Nullable Classifier<T> getNext() {
    return myNext;
  }

  /**
   * @param element item to remove
   * @param context see {@link Classifier} doc
   */
  public void removeElement(@NotNull T element, @NotNull ProcessingContext context) {
    if (myNext != null) {
      myNext.removeElement(element, context);
    }
  }

  public final @NotNull String getPresentableName() {
    return myName;
  }
}
