/*
 * @author max
 */
package com.intellij.ide.navigationToolbar;

import com.intellij.util.messages.Topic;

public interface NavBarModelListener {
  Topic<NavBarModelListener> NAV_BAR = Topic.create("Navigation Bar model changes", NavBarModelListener.class);

  void modelChanged();
  void selectionChanged();
}
