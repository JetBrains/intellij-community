package com.intellij.ide.errorTreeView;

import com.intellij.util.ui.MutableErrorTreeView;

public class HotfixGate {
  private final String myGroupName;
  private final MutableErrorTreeView myView;

  public HotfixGate(final String groupName, final MutableErrorTreeView tree) {
    myGroupName = groupName;
    myView = tree;
  }

  public String getGroupName() {
    return myGroupName;
  }

  public MutableErrorTreeView getView() {
    return myView;
  }
}
