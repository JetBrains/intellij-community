/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.ui.tree;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.profile.codeInspection.ui.inspectionsTree.InspectionsConfigTreeComparator;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

/**
 * @author Dmitry Batkovich
 */
public class InspectionTree extends Tree {
  private InspectionTreeBuilder myBuilder;

  public InspectionTree(InspectionTreeBuilder builder) {
    super(new DefaultTreeModel(new DefaultMutableTreeNode()));
    myBuilder = builder;

    setShowsRootHandles(true);
    UIUtil.setLineStyleAngled(this);

    TreeUtil.installActions(this);
    new TreeSpeedSearch(this, new Convertor<TreePath, String>() {
      @Override
      public String convert(TreePath o) {
        return InspectionsConfigTreeComparator.getDisplayTextToSort(o.getLastPathComponent().toString());
      }
    });
  }

  @TestOnly
  public InspectionTreeBuilder getBuilder() {
    return myBuilder;
  }
}

