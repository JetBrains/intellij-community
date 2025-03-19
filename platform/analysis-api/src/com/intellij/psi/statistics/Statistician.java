// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.statistics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An extension that allows to provide {@link StatisticsInfo} for various elements in various contexts.
 * Different subsystems can then use that statistics to sort suggestions, preselect most likely variants etc.
 */
public abstract class Statistician<T,Loc> {

  /**
   * @return A "statistics info" for the given element at the given location. Extensions are queried in their loading order
   * until any one of them returns a non-null value. An extension can return {@link StatisticsInfo#EMPTY} meaning
   * that the statistics shouldn't be tracked for {@code element}.
   */
  public abstract @Nullable StatisticsInfo serialize(@NotNull T element, @NotNull Loc location);
}
