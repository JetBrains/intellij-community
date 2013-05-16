/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.service.task.ui;

import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.ExternalSystemUiAware;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskDescriptor;
import com.intellij.openapi.externalSystem.service.ui.DefaultExternalSystemUiAware;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil;
import com.intellij.util.containers.ContainerUtilRt;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * @author Denis Zhdanov
 * @since 5/12/13 10:28 PM
 */
public class ExternalSystemTasksTreeModel extends DefaultTreeModel {

  @NotNull private final TreeNode[] myNodeHolder  = new TreeNode[1];
  @NotNull private final int[]      myIndexHolder = new int[1];
  @NotNull private final ExternalSystemUiAware myUiAware;

  public ExternalSystemTasksTreeModel(@NotNull ProjectSystemId externalSystemId) {
    super(new ExternalSystemNode<String>(new ExternalSystemNodeDescriptor<String>("", "", null)));
    ExternalSystemManager<?,?,?,?,?> manager = ExternalSystemApiUtil.getManager(externalSystemId);
    if (manager instanceof ExternalSystemUiAware) {
      myUiAware = (ExternalSystemUiAware)manager;
    }
    else {
      myUiAware = DefaultExternalSystemUiAware.INSTANCE;
    }
  }

  /**
   * Ensures that current model has a top-level node which corresponds to the given external project info holder
   *
   * @param externalProject  target external project info holder
   */
  @SuppressWarnings("unchecked")
  @NotNull
  public ExternalSystemNode<ProjectNodeElement> ensureProjectNodeExists(@NotNull ProjectData externalProject) {
    ExternalSystemNode<?> root = getRoot();

    // Remove outdated projects.
    for (int i = root.getChildCount() - 1; i >= 0; i--) {
      ExternalSystemNode<?> child = root.getChildAt(i);
      Object element = child.getDescriptor().getElement();
      if (element instanceof ProjectNodeElement
          && ((ProjectNodeElement)element).path.equals(externalProject.getLinkedExternalProjectPath()))
      {
        return (ExternalSystemNode<ProjectNodeElement>)child;
      }
    }
    ProjectNodeElement element = new ProjectNodeElement(externalProject.getName(), externalProject.getLinkedExternalProjectPath());
    ExternalSystemNodeDescriptor<ProjectNodeElement> descriptor = descriptor(element, myUiAware.getProjectIcon());
    myIndexHolder[0] = root.getChildCount();
    ExternalSystemNode<ProjectNodeElement> result = new ExternalSystemNode<ProjectNodeElement>(descriptor);
    root.add(result);
    nodesWereInserted(root, myIndexHolder);
    return result;
  }

  public void ensureSubProjectsStructure(@NotNull ProjectData topLevelProject, @NotNull List<ModuleData> subProjects) {
    ExternalSystemNode<ProjectNodeElement> topLevelProjectNode = ensureProjectNodeExists(topLevelProject);
    Map<String/*config path*/, ModuleData> toAdd = ContainerUtilRt.newHashMap();
    final TObjectIntHashMap<String/* sub-project config path */> subProjectWeights = new TObjectIntHashMap<String>();
    int w = 0;
    for (ModuleData subProject : subProjects) {
      toAdd.put(subProject.getExternalConfigPath(), subProject);
      subProjectWeights.put(subProject.getExternalConfigPath(), w++);
    }

    final TObjectIntHashMap<Object> taskWeights = new TObjectIntHashMap<Object>();
    for (int i = 0; i < topLevelProjectNode.getChildCount(); i++) {
      ExternalSystemNode<?> child = topLevelProjectNode.getChildAt(i);
      Object childElement = child.getDescriptor().getElement();
      if (childElement instanceof ExternalSystemTaskDescriptor) {
        taskWeights.put(childElement, subProjects.size() + i);
        continue;
      }
      
      if (toAdd.remove(((ProjectNodeElement)childElement).path) == null) {
        topLevelProjectNode.remove(child);
        myIndexHolder[0] = i;
        myNodeHolder[0] = child;
        nodesWereRemoved(topLevelProjectNode, myIndexHolder, myNodeHolder);
        //noinspection AssignmentToForLoopParameter
        i--;
      }
    }
    if (!toAdd.isEmpty()) {
      for (Map.Entry<String, ModuleData> entry : toAdd.entrySet()) {
        ProjectNodeElement element = new ProjectNodeElement(entry.getValue().getName(), entry.getValue().getExternalConfigPath());
        topLevelProjectNode.add(new ExternalSystemNode<ProjectNodeElement>(descriptor(element, myUiAware.getProjectIcon())));
      }
    }

    
    ExternalSystemUiUtil.sort(topLevelProjectNode, this, new Comparator<TreeNode>() {
      @Override
      public int compare(TreeNode o1, TreeNode o2) {
        // A node might be one of the following:
        //   1. Sub-project node;
        //   2. Top-level project's task node;
        // We want to put top-level project's tasks before sub-projects and preserve relative order between them.
        return getWeight(o1) - getWeight(o2);
      }

      private int getWeight(@NotNull TreeNode node) {
        if (!(node instanceof ExternalSystemNode<?>)) {
          return 0;
        }
        Object element = ((ExternalSystemNode)node).getDescriptor().getElement();
        if (element instanceof ProjectNodeElement) {
          return subProjectWeights.get(((ProjectNodeElement)element).path);
        }
        else if (element instanceof ExternalSystemTaskDescriptor) {
          return taskWeights.get(element);
        }
        else {
          return 0;
        }
      }
    });
  }

  @NotNull
  private static <T> ExternalSystemNodeDescriptor<T> descriptor(@NotNull T element, @Nullable Icon icon) {
    return new ExternalSystemNodeDescriptor<T>(element, element.toString(), icon);
  }
  
  @NotNull
  public ExternalSystemNode<?> getRoot() {
    return (ExternalSystemNode<?>)super.getRoot();
  }
  
  private static class ProjectNodeElement {
    @NotNull public final String name;
    @NotNull public final String path;

    ProjectNodeElement(@NotNull String name, @NotNull String path) {
      this.name = name;
      this.path = path;
    }

    @Override
    public String toString() {
      return name;
    }
  }
}
