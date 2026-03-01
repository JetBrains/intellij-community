// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.newStructureView;

import com.intellij.ide.util.treeView.smartTree.TreeAction;
import org.jetbrains.annotations.NotNull;

class TreeActionOwnerWrapper implements TreeActionsOwnerEx {
  private final TreeActionsOwner myDelegate;

  TreeActionOwnerWrapper(TreeActionsOwner delegate) {
    myDelegate = delegate;
  }

  @Override
  public void setActionActive(@NotNull TreeAction action, boolean state) {
    myDelegate.setActionActive(action.getName(), state);
  }

  @Override
  public boolean isActionActive(@NotNull TreeAction action) {
    return myDelegate.isActionActive(action.getName());
  }

  @Override
  public void setActionActive(String name, boolean state) {
    myDelegate.setActionActive(name, state);
  }

  @Override
  public boolean isActionActive(String name) {
    return myDelegate.isActionActive(name);
  }
}
