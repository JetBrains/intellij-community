/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.ide;

import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author yole
*/
public class DefaultTreeExpander implements TreeExpander {
  private final JTree myTree;

  public DefaultTreeExpander(@NotNull final JTree tree) {
    myTree = tree;
  }

  @Override
  public void expandAll() {
    TreeUtil.expandAll(myTree);
    showSelectionCentered();
  }

  @Override
  public boolean canExpand() {
    return myTree.isShowing();
  }

  @Override
  public void collapseAll() {
    TreeUtil.collapseAll(myTree, 1);
    showSelectionCentered();
  }

  private void showSelectionCentered() {
    int[] rows = myTree.getSelectionRows();
    if (rows != null && rows.length > 0) {
      TreeUtil.showRowCentered(myTree, rows[0], false);
    }
  }

  @Override
  public boolean canCollapse() {
    return myTree.isShowing();
  }
}
