// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.ui;

import com.intellij.openapi.actionSystem.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Lightweight alternative to {@link DefaultActionGroup}.
 * Does not use `createLockFreeCopyOnWriteList` and action order constraints, making it suitable for use cases with many (10k+) children actions.
 */
public class LightActionGroup extends ActionGroup {
  private final List<AnAction> myChildren = new ArrayList<>();

  public LightActionGroup() {
    this(false);
  }

  public LightActionGroup(boolean popup) {
    super(Presentation.NULL_STRING, popup);
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    return myChildren.toArray(AnAction.EMPTY_ARRAY);
  }

  public final void addAction(@NotNull AnAction action) {
    add(action);
  }

  public final void add(@NotNull AnAction action) {
    myChildren.add(action);
  }

  public final void addAll(@NotNull ActionGroup group) {
    addAll(group.getChildren(null));
  }

  public final void addAll(AnAction @NotNull ... actions) {
    myChildren.addAll(Arrays.asList(actions));
  }

  public final void addAll(@NotNull List<? extends AnAction> actions) {
    myChildren.addAll(actions);
  }

  public final void addSeparator() {
    add(Separator.create());
  }

  public void addSeparator(@Nullable @Nls String separatorText) {
    add(Separator.create(separatorText));
  }
}
