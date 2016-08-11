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
package com.intellij.codeInspection.ui;


import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.offlineViewer.OfflineProblemDescriptorNode;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.util.containers.FactoryMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import java.util.Map;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
@SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
public class ExcludedInspectionTreeNodesManager {
  private final Map<Class, Set<Object>> myExcludedNodeObjects = new FactoryMap<Class, Set<Object>>() {
    @Nullable
    @Override
    protected Set<Object> create(Class key) {
      return new THashSet<>();
    }
  };

  private final Map<String, Set<Object>> myExcludedByTool = new FactoryMap<String, Set<Object>>() {
    @Nullable
    @Override
    protected Set<Object> create(String key) {
      return new THashSet<>();
    }
  };

  private final boolean myOffline;
  private final boolean mySingleInspectionRun;

  public ExcludedInspectionTreeNodesManager(boolean offline, boolean singleInspectionRun) {
    myOffline = offline;
    mySingleInspectionRun = singleInspectionRun;
  }

  public synchronized boolean isExcluded(InspectionTreeNode node) {
    if (!mySingleInspectionRun && (node instanceof RefElementNode || node instanceof InspectionModuleNode || node instanceof InspectionPackageNode)) {
      return myExcludedByTool.get(findContainingToolName(node)).contains(node.getUserObject());
    } else {
      final Set<?> excluded = myExcludedNodeObjects.get(node.getClass());
      return excluded.contains(node.getUserObject());
    }
  }

  public synchronized void exclude(InspectionTreeNode node) {
    if (!mySingleInspectionRun && (node instanceof RefElementNode || node instanceof InspectionModuleNode || node instanceof InspectionPackageNode)) {
      myExcludedByTool.get(findContainingToolName(node)).add(node.getUserObject());
    } else {
      myExcludedNodeObjects.get(node.getClass()).add(node.getUserObject());
    }
  }

  public synchronized void amnesty(InspectionTreeNode node) {
    if (!mySingleInspectionRun && (node instanceof RefElementNode || node instanceof InspectionModuleNode || node instanceof InspectionPackageNode)) {
      myExcludedByTool.get(findContainingToolName(node)).remove(node.getUserObject());
    }
    else {
      myExcludedNodeObjects.get(node.getClass()).remove(node.getUserObject());
    }
  }

  public synchronized boolean containsRefEntity(@NotNull RefEntity entity, @NotNull InspectionToolWrapper wrapper) {
    return myExcludedByTool.get(wrapper.getShortName()).contains(entity);
  }

  public synchronized boolean containsProblemDescriptor(@NotNull CommonProblemDescriptor descriptor) {
    return myExcludedNodeObjects.get(myOffline ? OfflineProblemDescriptorNode.class : ProblemDescriptionNode.class).contains(descriptor);
  }

  public synchronized boolean containsInspectionNode(@NotNull InspectionToolWrapper wrapper) {
    return myExcludedNodeObjects.get(InspectionNode.class).contains(wrapper);
  }

  @NotNull
  private static String findContainingToolName(@NotNull InspectionTreeNode node) {
    TreeNode parent = node.getParent();
    while (!(parent instanceof InspectionNode) && parent != null) {
      parent = parent.getParent();
    }
    if (parent == null) return "";
    return ((InspectionNode)parent).getToolWrapper().getShortName();
  }
}
