// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.lookup;

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * Listener to receive notifications for events in lookup.
 *
 * @see Lookup#addLookupListener(LookupListener)
 */
public interface LookupListener extends EventListener {
  default void lookupShown(@NotNull LookupEvent event) {
  }

  /*
   * Note: this event comes inside the command that performs inserting of text into the editor and is
   * called before the lookup string is inserted into the document. If any listener returns false,
   * the lookup string is not inserted.
   */
  default boolean beforeItemSelected(@NotNull LookupEvent event) {
    return true;
  }

  /*
   * Note: this event comes inside the command that performs inserting of text into the editor.
   */
  default void itemSelected(@NotNull LookupEvent event) {
  }

  default void lookupCanceled(@NotNull LookupEvent event) {
  }

  default void currentItemChanged(@NotNull LookupEvent event) {
  }

  /**
   * Fired when the contents or the selection of the lookup list is changed (items added by
   * background calculation, selection moved by the user, etc.)
   */
  default void uiRefreshed() {
  }

  default void focusDegreeChanged() {
  }
}
