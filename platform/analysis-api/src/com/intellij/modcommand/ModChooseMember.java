// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Function;

/**
 * A command that allows to select arbitrary number of elements. In batch mode, it's assumed that default selection is selected.
 * 
 * @param title user-readable title to display in UI
 * @param elements all elements to select from
 * @param defaultSelection default selection
 * @param mode selection mode
 * @param nextCommand a function to compute the subsequent command based on the selection; will be executed in read-action
 */
@ApiStatus.Experimental
public record ModChooseMember(@NotNull @NlsContexts.PopupTitle String title,
                              @NotNull List<? extends @NotNull MemberChooserElement> elements,
                              @NotNull List<? extends @NotNull MemberChooserElement> defaultSelection,
                              @NotNull SelectionMode mode,
                              @NotNull Function<@NotNull List<? extends @NotNull MemberChooserElement>, ? extends @NotNull ModCommand> nextCommand)
  implements ModCommand {

  /**
   * Selection mode
   */
  public enum SelectionMode {
    /**
     * Selecting exactly one element is allowed
     */
    SINGLE,
    /**
     * Selecting one or zero elements is allowed
     */
    SINGLE_OR_EMPTY,
    /**
     * Selecting any non-zero amount of elements is allowed
     */
    MULTIPLE,
    /**
     * Selecting any mount of elements (including zero) is allowed
     */
    MULTIPLE_OR_EMPTY
  }
}
