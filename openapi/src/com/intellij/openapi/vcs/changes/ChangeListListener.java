package com.intellij.openapi.vcs.changes;

import java.util.EventListener;

/**
 * @author max
 */
public interface ChangeListListener extends EventListener {
  void changeListAdded(ChangeList list);
  void changeListRemoved(ChangeList list);
  void changeListChanged(ChangeList list);
  void changeMoved(Change change, ChangeList fromList, ChangeList toList);
  void defaultListChanged(ChangeList newDefaultList);
}
