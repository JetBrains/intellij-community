// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.tools;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/** @deprecated Use {@link com.intellij.openapi.actionSystem.DefaultActionGroup} or {@link ActionGroup} directly */
@Deprecated
public class SimpleActionGroup extends ActionGroup {
  private final ArrayList<AnAction> myChildren = new ArrayList<>();

  public SimpleActionGroup() {
    super(Presentation.NULL_STRING, false);
  }

  public void add(AnAction action) {
    myChildren.add(action);
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    return myChildren.toArray(AnAction.EMPTY_ARRAY);
  }

  public int getChildrenCount() {
    return myChildren.size();
  }

  public void removeAll() {
    myChildren.clear();
  }
}

