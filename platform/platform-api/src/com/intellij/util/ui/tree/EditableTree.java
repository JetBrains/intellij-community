// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.tree;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreePath;

@ApiStatus.Experimental
public interface EditableTree {
  Key<EditableTree> KEY = Key.create("EditableTree");

  /**
   * @param presentation the presentation of visible tree.StartEditingAction to configure
   * @param path         the tree path to the editable node
   */
  void updateAction(@NotNull Presentation presentation, @NotNull TreePath path);
}
