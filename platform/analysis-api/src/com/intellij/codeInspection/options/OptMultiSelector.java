// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.openapi.util.NlsContexts;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Represents a control that allows to select multiple elements from a predefined list.
 * 
 * @param bindId identifier of binding variable used by an option controller; 
 *               the corresponding variable is expected to be a mutable {@code List<? extends OptElement>}.
 *               It's expected that it contains only the elements from the {@code elements} list
 * @param elements a complete list of elements that could be selected in the control.
 * @param mode selection mode
 */
@ApiStatus.Experimental
public record OptMultiSelector(@Language("jvm-field-name") @NotNull String bindId,
                        @NotNull List<? extends @NotNull OptElement> elements,
                        @NotNull SelectionMode mode) implements OptControl, OptRegularComponent {

  @SuppressWarnings("InjectedReferences")
  @Override
  public @NotNull OptMultiSelector prefix(@NotNull String bindPrefix) {
    return new OptMultiSelector(bindPrefix + "." + bindId, elements, mode);
  }

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
     * Selecting any non-zero number of elements is allowed
     */
    MULTIPLE,
    /**
     * Selecting any number of elements (including zero) is allowed
     */
    MULTIPLE_OR_EMPTY
  }

  /**
   * Represents a single member that could be selected in a member selector.
   * The basic implementation provides just text, so the member could be rendered in UI
   * as a simple text. It could be possible to have more complex implementations that are
   * recognized by a form renderer to display them with icons, like a tree, and so on
   */
  public interface OptElement {
    /**
     * @return text to display for the element
     */
    @NlsContexts.Label @NotNull String getText();
  }
}
