/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import javax.swing.*;

/**
 * @author yole
*/
public class DefaultTreeExpander implements TreeExpander {
  private JTree myTree;

  public DefaultTreeExpander(final JTree tree) {
    myTree = tree;
  }

  public void expandAll() {
    TreeUtil.expandAll(myTree);
  }

  public boolean canExpand() {
    return true;
  }

  public void collapseAll() {
    TreeUtil.collapseAll(myTree, 0);
  }

  public boolean canCollapse() {
    return true;
  }
}