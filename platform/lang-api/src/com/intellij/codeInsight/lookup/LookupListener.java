// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.lookup;

import java.util.EventListener;

/**
 * Listener to receive notifications for events in lookup.
 *
 * @see Lookup#addLookupListener(LookupListener)
 */
public interface LookupListener extends EventListener {
  /*
   * Note: this event comes inside the command that performs inserting of text into the editor.
   */
  default void itemSelected(LookupEvent event) {
  }

  default void lookupCanceled(LookupEvent event) {
  }

  default void currentItemChanged(LookupEvent event) {
  }
}
