// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.tree;

import com.intellij.ui.TreeExpandCollapse;

import javax.swing.*;

final class ExpandTreeNodeAction extends BaseTreeNodeAction {
  @Override
  protected void performOn(JTree tree) {
    TreeExpandCollapse.expand(tree);
  }
}
