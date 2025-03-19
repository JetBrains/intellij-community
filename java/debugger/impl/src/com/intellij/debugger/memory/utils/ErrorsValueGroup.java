// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.utils;

import com.intellij.icons.AllIcons;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XNamedValue;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ErrorsValueGroup extends XValueGroup {
  private final Map<String, List<XNamedValue>> myErrorMessage2ValueMap = new HashMap<>();

  public ErrorsValueGroup() {
    super("Errors");
  }

  public void addErrorValue(@NotNull String message, @NotNull XNamedValue value) {
    List<XNamedValue> lst;
    if (!myErrorMessage2ValueMap.containsKey(message)) {
      myErrorMessage2ValueMap.put(message, new ArrayList<>());
    }

    lst = myErrorMessage2ValueMap.get(message);
    lst.add(value);
  }

  public boolean isEmpty() {
    return myErrorMessage2ValueMap.isEmpty();
  }

  @Override
  public @Nullable Icon getIcon() {
    return AllIcons.General.Error;
  }

  @Override
  public void computeChildren(@NotNull XCompositeNode node) {
    XValueChildrenList lst = new XValueChildrenList();
    myErrorMessage2ValueMap.keySet().forEach(s -> lst.addTopGroup(new MyErrorsValueGroup(s)));
    node.addChildren(lst, true);
  }

  private class MyErrorsValueGroup extends XValueGroup {

    @Override
    public void computeChildren(@NotNull XCompositeNode node) {
      XValueChildrenList lst = new XValueChildrenList();
      String name = getName();
      myErrorMessage2ValueMap.get(name).forEach(lst::add);
      node.addChildren(lst, true);
    }

    MyErrorsValueGroup(@NotNull String name) {
      super(name);
    }
  }
}
