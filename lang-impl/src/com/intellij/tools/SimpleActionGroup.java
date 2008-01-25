package com.intellij.tools;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

import java.util.ArrayList;

import org.jetbrains.annotations.Nullable;

public class SimpleActionGroup extends ActionGroup {
  private final ArrayList<AnAction> myChildren = new ArrayList<AnAction>();

  public SimpleActionGroup() {
    super(null, false);
  }

  public void add(AnAction action) {
    myChildren.add(action);
  }

  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    return myChildren.toArray(new AnAction[myChildren.size()]);
  }

  public int getChildrenCount() {
    return myChildren.size();
  }

  public void removeAll() {
    myChildren.clear();
  }
}

