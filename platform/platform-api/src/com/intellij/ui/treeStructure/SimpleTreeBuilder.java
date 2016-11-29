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
package com.intellij.ui.treeStructure;

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.Comparator;

public class SimpleTreeBuilder extends AbstractTreeBuilder {
  public SimpleTreeBuilder(JTree tree, DefaultTreeModel treeModel, AbstractTreeStructure treeStructure, @Nullable Comparator comparator) {
    //noinspection unchecked
    super(tree, treeModel, treeStructure, comparator);
  }

  @Override
  public boolean isAlwaysShowPlus(NodeDescriptor nodeDescriptor) {
    return ((SimpleNode) nodeDescriptor).isAlwaysShowPlus();
  }

  @Override
  public boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
    return ((SimpleNode) nodeDescriptor).isAutoExpandNode();
  }

  @Override
  public final void updateFromRoot() {
    updateFromRoot(false);
  }

  public void updateFromRoot(boolean rebuild) {
    if (rebuild) {
      cleanUpStructureCaches();
    }

    if (EventQueue.isDispatchThread()) {
      super.queueUpdate();
    }
    else {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (!isDisposed()) {
          super.queueUpdate();
        }
      });
    }
  }

  protected final DefaultMutableTreeNode createChildNode(final NodeDescriptor childDescr) {
    return new PatchedDefaultMutableTreeNode(childDescr);
  }

  private void cleanUpStructureCaches() {
    if (!(getTreeStructure() instanceof SimpleTreeStructure)) return;
    ((SimpleTreeStructure)getTreeStructure()).clearCaches();
  }

  public SimpleTreeBuilder initRoot() {
    initRootNode();
    return this;
  }

}
