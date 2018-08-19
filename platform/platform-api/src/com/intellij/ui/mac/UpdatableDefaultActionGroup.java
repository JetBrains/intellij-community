// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;

import java.util.Collection;

public class UpdatableDefaultActionGroup extends DefaultActionGroup {
  public static final String PROP_CHILDREN = "DefaultActionGroup.children";

  public void replaceAll(Collection<? extends AnAction> newActions) {
    final AnAction[] prev = getChildren(null);
    removeAll();
    addAll(newActions);
    firePropertyChange(PROP_CHILDREN, prev, getChildren(null));
  }
}
