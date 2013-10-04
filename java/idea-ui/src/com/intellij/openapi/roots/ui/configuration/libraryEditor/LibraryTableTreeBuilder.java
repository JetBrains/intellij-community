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
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.IndexComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.StatusBarProgress;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;

class LibraryTableTreeBuilder extends AbstractTreeBuilder {
  public LibraryTableTreeBuilder(JTree tree, DefaultTreeModel treeModel, AbstractTreeStructure treeStructure) {
    super(tree, treeModel, treeStructure, IndexComparator.INSTANCE);
    initRootNode();
  }

  @Override
  protected boolean isAlwaysShowPlus(NodeDescriptor nodeDescriptor) {
    return false;
  }

  @Override
  protected boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
    final Object element = nodeDescriptor.getElement();
    final Object rootElement = getTreeStructure().getRootElement();
    return rootElement.equals(element) || element instanceof OrderRootTypeElement || element instanceof ItemElement;
  }

  @Override
  protected boolean isSmartExpand() {
    return false;
  }

  @Override
  @NotNull
  protected ProgressIndicator createProgressIndicator() {
    return new StatusBarProgress();
  }
}
