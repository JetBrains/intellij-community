package com.intellij.psi.impl.source.tree.injected;

import com.intellij.util.SmartList;

/**
 * @author cdr
 */
class Places extends SmartList<Place> {
  public boolean isValid() {
    for (Place place : this) {
      if (!place.isValid()) return false;
    }
    return true;
  }
}