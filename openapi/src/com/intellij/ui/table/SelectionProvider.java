package com.intellij.ui.table;

import java.util.Collection;

/**
 * author: lesya
 */
public interface SelectionProvider {
  Collection getSelection();

  void addSelection(Object item);

  void clearSelection();
}
