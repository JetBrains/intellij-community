package com.intellij.codeInsight.lookup;

import java.util.EventListener;

public interface LookupListener extends EventListener {
  /**
   * Note: this event comes inside the command that performs inserting of text into the editor.
   */
  void itemSelected(LookupEvent event);

  void lookupCanceled(LookupEvent event);

  void currentItemChanged(LookupEvent event);
}