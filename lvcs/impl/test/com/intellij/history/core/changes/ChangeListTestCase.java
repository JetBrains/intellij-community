package com.intellij.history.core.changes;

import com.intellij.history.core.LocalVcsTestCase;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.RootEntry;

public abstract class ChangeListTestCase extends LocalVcsTestCase {
  protected Entry r = new RootEntry();
  protected ChangeList cl = new ChangeList();

  protected void applyAndAdd(Change... cc) {
    for (Change c : cc) {
      c.applyTo(r);
      cl.addChange(c);
    }
  }
}
