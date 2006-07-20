package com.intellij.openapi.vcs.changes;

import java.util.EventListener;
import java.util.Collection;

/**
 * @author max
 */
public interface ChangeListListener extends EventListener {
  void changeListAdded(ChangeList list);
  void changeListRemoved(ChangeList list);
  void changeListChanged(ChangeList list);
  void changeListRenamed(ChangeList list, String oldName);
  void changesMoved(Collection<Change> changes, ChangeList fromList, ChangeList toList);
  void defaultListChanged(ChangeList newDefaultList);
  void changeListUpdateDone();
}
