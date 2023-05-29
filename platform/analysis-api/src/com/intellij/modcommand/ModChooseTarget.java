// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Function;

/**
 * An action that displays UI, so user can select one of the PSI elements,
 * and subsequent step will be invoked after that.
 *
 * @param elements  list of elements. If list contains no elements, nothing will be executed.
 *                  If list contains only one element, UI may not be shown, and the subsequent
 *                  step may be executed right away assuming that the only element is selected.
 * @param nextStep  next step generator that accepts the selected element
 * @param title     user-visible title for the element selection list
 * @param selection index of element selected by default (0 for the first element)
 * @param <T>       type of elements
 */
public record ModChooseTarget<T extends @NotNull PsiElement>(@NotNull List<@NotNull ListItem<@NotNull T>> elements,
                                                             @NotNull Function<? super @NotNull T, ? extends @NotNull ModCommand> nextStep,
                                                             @NotNull @NlsContexts.PopupTitle String title,
                                                             int selection) implements ModCommand {
  @Override
  public boolean isEmpty() {
    return elements.isEmpty();
  }

  /**
   * An item from the list used by {@link ModChooseTarget} that encapsulates PSI element and its presentation.
   * 
   * @param <T> PSI element type.
   */
  public interface ListItem<T> {
    /**
     * @return item
     */
    @NotNull T element();

    /**
     * @return selection in the editor that corresponds to this item
     */
    @NotNull TextRange selection();

    /**
     * @return user-visible representation of the element
     */
    @Override
    String toString();

    /**
     * Creates a list item
     * @param element element
     * @param text user-visible text for the element
     * @param range range to select in the editor when the element is selected
     * @return newly created ListItem that describes given element
     * @param <T> type of the PSI element
     */
    static <T extends @NotNull PsiElement> @NotNull ListItem<@NotNull T> of(@NotNull T element,
                                                                            @NotNull String text,
                                                                            @NotNull TextRange range) {
      return new ListItem<T>() {
        @NotNull
        @Override
        public T element() {
          return element;
        }

        @Override
        public @NotNull TextRange selection() {
          return range;
        }

        @Override
        public String toString() {
          return text;
        }
      };
    }
  }
}
