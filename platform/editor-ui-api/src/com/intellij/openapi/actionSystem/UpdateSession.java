// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Supplier;

/**
 * An interface to the current session of an {@link ActionGroup} async update and expansion.
 * To be used for recursive action updates and action-group expansions,
 * and for action-group expansion postprocessing.
 *
 * @see com.intellij.ide.actions.WeighingActionGroup
 * @see com.intellij.ide.actions.NonTrivialActionGroup
 */
public interface UpdateSession {
  UpdateSession EMPTY = new UpdateSession() {};

  /**
   * Expands the <code>actionGroup</code> and return the iterable of the visible children for this session.
   */
  default @NotNull Iterable<? extends AnAction> expandedChildren(@NotNull ActionGroup actionGroup) {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns the list of immediate <code>actionGroup</code> children for this session.
   */
  default @NotNull List<? extends AnAction> children(@NotNull ActionGroup actionGroup) {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns the updated <code>action</code> presentation for this session.
   */
  default @NotNull Presentation presentation(@NotNull AnAction action) {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns shared data for the <code>key</code> for this session.
   * This way some data can be computed once and shared between several actions.
   * Both in their <code>update</code> and <code>actionPerformed</code> methods.
   */
  default @NotNull <T> T sharedData(@NotNull Key<T> key, @NotNull Supplier<? extends T> provider) {
    return provider.get();
  }

  /**
   * Performs the computation in the correct thread and returns its value.
   * This way some data can be computed on EDT from BGT but not vice versa.
   */
  default <T> T compute(@NotNull Object action,
                        @NotNull String operationName,
                        @NotNull ActionUpdateThread updateThread,
                        @NotNull Supplier<? extends T> supplier) {
    return supplier.get();
  }
}
