// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.newStructureView.TreeActionsOwner;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.ide.util.treeView.smartTree.TreeAction;

import java.util.HashSet;
import java.util.Set;

/**
* @author Konstantin Bulenkov
*/
final class TreeStructureActionsOwner implements TreeActionsOwner {
  private final Set<TreeAction> myActions = new HashSet<>();
  private final StructureViewModel myModel;

  TreeStructureActionsOwner(StructureViewModel model) {
    myModel = model;
  }

  @Override
  public void setActionActive(String name, boolean state) {
  }

  @Override
  public boolean isActionActive(String name) {
    for (final Sorter sorter : myModel.getSorters()) {
      if (sorter.getName().equals(name)) {
        if (!sorter.isVisible()) return true;
      }
    }
    for(TreeAction action: myActions) {
      if (action.getName().equals(name)) return true;
    }
    return false;
  }

  public void setActionIncluded(final TreeAction filter, final boolean selected) {
    if (selected) {
      myActions.add(filter);
    }
    else {
      myActions.remove(filter);
    }
  }
}
